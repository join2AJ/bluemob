package com.bluemob.app.chat

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.bot.SkyAnswer
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageDao
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.data.SeenId
import com.bluemob.app.mesh.Handoff
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.MeshRouter
import com.bluemob.app.mesh.MessageLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Every chat message, saved on the phone, and the rules that get each one delivered exactly once.
 *
 * - A message gets a unique ID when it's written. Every copy carries that ID.
 * - If the person isn't in range, it waits here. The moment they connect, it goes over Bluetooth / Wi-Fi.
 * - It's re-sent on each new connection until a delivery receipt comes back (a link can drop mid-send).
 * - The receiver remembers every ID it has accepted and discards any second copy, but always answers
 *   with a receipt, so the sender stops re-sending.
 * - When the person opens the chat, a read receipt goes back (now, or the next time you're connected).
 *
 * The internet path (through a bridge and the BlueMob relay) plugs in here once the relay exists.
 */
class MessageRepository(
    private val dao: MessageDao,
    private val mesh: MessageLink,
    private val scope: CoroutineScope,
    private val sky: (String) -> SkyAnswer,
    /** Writes to the audit trail: (kind, other person's node ID, what happened). */
    private val record: (AuditKind, String, String) -> Unit = { _, _, _ -> },
    /** Called once per new incoming message (not for copies), e.g. to show a notification. */
    private val onIncoming: (peer: String, text: String) -> Unit = { _, _ -> },
    /** A message with a photo, document or voice note was saved (sent or received): its file can move now. */
    private val onAttachment: suspend (MessageEntity) -> Unit = {},
) {
    val messages: StateFlow<List<MessageEntity>> = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<Map<String, List<MessageEntity>>> =
        messages.map { all -> all.groupBy { it.peer } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    /** Conversations where the other side is "typing" (only Sky, for now). */
    val typing: StateFlow<Set<String>> = _typing.asStateFlow()

    /** Deliveries for one person run one at a time, so a message is never sent twice in one go. */
    private val deliveryLock = Mutex()

    /** The chat on screen: its incoming messages count as read right away. */
    var openConversation: String? = null
        set(value) {
            field = value
            if (value != null) markRead(value)
        }

    init {
        scope.launch {
            if (dao.count(SkyBot.NODE_ID) == 0) {
                val now = System.currentTimeMillis()
                SkyBot.greeting.forEachIndexed { i, text ->
                    dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, false, text, now + i, MessageStatus.RECEIVED))
                }
            }
        }
        scope.launch { mesh.events.collect { handle(it) } }
    }

    fun send(peer: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            if (peer == SkyBot.NODE_ID) {
                talkToSky(clean, now)
                return@launch
            }
            val inRange = mesh.isConnected(peer)
            val id = newId()
            record(AuditKind.MESSAGE, peer, "Message $id written to {name}: \"${clean.take(80)}\"")
            dao.insert(
                MessageEntity(
                    id = id, peer = peer, fromMe = true, text = clean, createdAt = now, status = MessageStatus.PENDING,
                    directState = if (inRange) PathState.TRYING else PathState.WAITING,
                    internetState = PathState.UNAVAILABLE,
                    history = event(now, "Written on your phone") +
                        event(now, if (inRange) "In range. Sending directly" else "Not in range. Looking for a way to reach them through phones nearby"),
                )
            )
            deliverAll()
        }
    }

    /**
     * Sends a photo, document or voice note. [att] holds its details (and the file's key); the encrypted file at
     * [path] goes straight to them when they're in range.
     */
    fun sendAttachment(peer: String, att: com.bluemob.app.files.Attachment, path: String, caption: String = "") {
        scope.launch {
            val now = System.currentTimeMillis()
            val id = newId()
            val inRange = mesh.isConnected(peer)
            record(AuditKind.MESSAGE, peer, "${att.kind.label} $id (${com.bluemob.app.files.Attachment.sizeText(att.size)}) sent to {name}")
            val m = MessageEntity(
                id = id, peer = peer, fromMe = true, text = caption.trim().ifBlank { att.fallbackText() }, createdAt = now, status = MessageStatus.PENDING,
                directState = if (inRange) PathState.TRYING else PathState.WAITING, internetState = PathState.UNAVAILABLE,
                history = event(now, "${att.kind.label} prepared and encrypted on your phone") +
                    event(now, if (inRange) "In range. Sending directly" else "Not in range. The message goes through the mesh; the file goes when you meet"),
                att = att.toJson(), attPath = path, attState = com.bluemob.app.files.AttState.WAITING,
            )
            dao.insert(m)
            deliverAll()
            onAttachment(m)
        }
    }

    /** Marks everything from [peer] as read and tells them, if we can. */
    fun markRead(peer: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao.unread(peer).forEach { m ->
                val told = peer != SkyBot.NODE_ID && mesh.sendReceipt(peer, m.id, read = true)
                dao.update(m.copy(status = MessageStatus.READ, readAt = now, readReceiptSent = told || peer == SkyBot.NODE_ID))
            }
        }
    }

    /** Deletes one chat from this phone (not Sky: Sky's chat is always there). Returns its attachment files to delete. */
    suspend fun deleteChat(peer: String): List<String> {
        if (peer == SkyBot.NODE_ID) return emptyList()
        val files = dao.all().filter { it.peer == peer }.mapNotNull { it.attPath }
        dao.deleteChat(peer)
        return files
    }

    fun clearAll() = scope.launch { dao.clear() }

    private suspend fun handle(event: MeshEvent) {
        when (event) {
            // Any new phone in range might be them, or someone who can carry messages toward them.
            is MeshEvent.PeerConnected, is MeshEvent.KeyLearned, MeshEvent.RouteAvailable -> deliverAll()
            is MeshEvent.MessageReceived -> receive(event)
            is MeshEvent.Receipt -> receipt(event)
            else -> Unit
        }
    }

    /** Offers every message still waiting for a receipt to the mesh, plus read receipts we owe. */
    private suspend fun deliverAll() = deliveryLock.withLock {
        val now = System.currentTimeMillis()
        dao.unacknowledgedAll().forEach { m ->
            when (val h = mesh.sendChat(m.peer, m.id, m.text, m.createdAt, m.att.ifBlank { null })) {
                is Handoff.Direct -> {
                    val note = if (m.attempts == 0) "Sent over ${h.link}" else "Sent again over ${h.link} (attempt ${m.attempts + 1}): no receipt came back last time"
                    dao.update(m.copy(status = MessageStatus.SENT, directState = PathState.TRYING, attempts = m.attempts + 1, history = m.history + event(now, note)))
                }
                is Handoff.Carried -> {
                    val people = h.via.filter { it != MeshRouter.INTERNET_NAME }
                    val notes = buildList {
                        if (people.isNotEmpty()) add("Handed to ${people.joinToString(" and ")} to carry toward them. It's encrypted: carriers can't read it")
                        if (MeshRouter.INTERNET_NAME in h.via) add("Sent to the BlueMob relay over the internet. It waits there, encrypted, until they connect")
                    }
                    dao.update(m.copy(status = MessageStatus.SENT, directState = PathState.TRYING,
                        internetState = if (MeshRouter.INTERNET_NAME in h.via) PathState.TRYING else m.internetState,
                        history = m.history + notes.joinToString("") { event(now, it) }))
                }
                Handoff.NeedsKey -> if (!m.history.contains(KEY_NOTE)) dao.update(m.copy(history = m.history + event(now, KEY_NOTE)))
                Handoff.Held -> Unit
            }
        }
        dao.readReceiptsOwedAll().forEach { m ->
            if (mesh.sendReceipt(m.peer, m.id, read = true)) dao.update(m.copy(readReceiptSent = true))
        }
    }

    private suspend fun receive(e: MeshEvent.MessageReceived) {
        val now = System.currentTimeMillis()
        val isNew = dao.markSeen(SeenId(e.messageId, now)) != -1L
        // Always answer with a receipt: if our first one was lost, the sender is still re-sending.
        mesh.sendReceipt(e.fromNodeId, e.messageId, read = false)
        if (!isNew) return // A copy we already have: discard it.
        if (openConversation != e.fromNodeId) onIncoming(e.fromNodeId, com.bluemob.app.files.Attachment.fromJson(e.att)?.let { "${it.kind.emoji} ${it.kind.label}" } ?: e.text)
        record(AuditKind.MESSAGE, e.fromNodeId, "Message ${e.messageId} received from {name} over ${mesh.linkName(e.fromNodeId)}: \"${e.text.take(80)}\"")
        val open = openConversation == e.fromNodeId
        val readNow = open && mesh.sendReceipt(e.fromNodeId, e.messageId, read = true)
        val att = com.bluemob.app.files.Attachment.fromJson(e.att)
        val saved = MessageEntity(
                id = e.messageId, peer = e.fromNodeId, fromMe = false, text = if (att != null) "" else e.text, createdAt = e.sentAt,
                status = if (open) MessageStatus.READ else MessageStatus.RECEIVED,
                readAt = if (open) now else null, readReceiptSent = readNow,
                history = event(now, if (e.viaInternet) "Received over the internet, through the BlueMob relay. End-to-end encrypted"
                    else if (e.hops <= 1) "Received over ${mesh.linkName(e.fromNodeId)}"
                    else "Received over the mesh: passed on by ${e.hops - 1} phone${if (e.hops > 2) "s" else ""}. End-to-end encrypted"),
                att = att?.toJson() ?: "", attState = if (att != null) com.bluemob.app.files.AttState.WAITING else 0,
            )
        dao.insert(saved)
        if (att != null) onAttachment(saved)
    }

    private suspend fun receipt(e: MeshEvent.Receipt) {
        val m = dao.get(e.messageId) ?: return
        if (!m.fromMe || m.peer != e.fromNodeId) return
        val now = System.currentTimeMillis()
        val link = m.deliveredVia ?: if (e.viaInternet) "the internet (BlueMob relay)" else if (e.hops <= 1) mesh.linkName(e.fromNodeId) else "the mesh (${e.hops - 1} phone${if (e.hops > 2) "s" else ""} carried it)"
        val delivered = m.copy(
            deliveredAt = m.deliveredAt ?: now, deliveredVia = link,
            // The path that delivered first is marked; the other one is no longer needed.
            directState = if (m.deliveredAt == null && e.viaInternet) (if (m.directState == PathState.DELIVERED) m.directState else PathState.CANCELLED) else if (m.deliveredAt == null) PathState.DELIVERED else m.directState,
            internetState = if (m.deliveredAt == null && e.viaInternet) PathState.DELIVERED
                else if (m.internetState == PathState.WAITING || m.internetState == PathState.TRYING) PathState.CANCELLED else m.internetState,
        )
        when {
            e.read && m.status != MessageStatus.READ -> {
                record(AuditKind.RECEIPT, m.peer, "Read receipt for ${m.id} from {name} over $link")
                dao.update(delivered.copy(status = MessageStatus.READ, readAt = now,
                    history = delivered.history + (if (m.deliveredAt == null) event(now, "Delivered over $link") else "") + event(now, "Read receipt came back over $link")))
            }
            !e.read && (m.status == MessageStatus.SENT || m.status == MessageStatus.PENDING) -> {
                record(AuditKind.RECEIPT, m.peer, "Delivery receipt for ${m.id} from {name} over $link")
                dao.update(delivered.copy(status = MessageStatus.DELIVERED,
                    history = delivered.history + event(now, "Delivered over $link. Delivery receipt came back")))
            }
        }
    }

    private suspend fun talkToSky(text: String, now: Long) {
        dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, true, text, now, MessageStatus.LOCAL))
        val answer = sky(text)
        delay(350)
        _typing.update { it + SkyBot.NODE_ID }
        delay(SkyBot.typingDelayMs(answer.text))
        _typing.update { it - SkyBot.NODE_ID }
        dao.insert(
            MessageEntity(
                newId(), SkyBot.NODE_ID, false, answer.text, System.currentTimeMillis(),
                if (openConversation == SkyBot.NODE_ID) MessageStatus.READ else MessageStatus.RECEIVED,
                actions = answer.actions.joinToString("\n") { "${it.label}|${it.target}" },
            )
        )
    }

    companion object {
        const val KEY_NOTE = "Asking phones nearby for their key, so it can be encrypted"
        fun newId(): String = "m-" + UUID.randomUUID().toString().replace("-", "").take(20)
        fun event(at: Long, text: String) = "$at|$text\n"

        /** Parses [MessageEntity.history] into (time, text) pairs. */
        fun historyOf(m: MessageEntity): List<Pair<Long, String>> = m.history.lines().filter { it.isNotBlank() }.mapNotNull {
            val i = it.indexOf('|')
            if (i < 0) null else it.substring(0, i).toLongOrNull()?.let { t -> t to it.substring(i + 1) }
        }
    }
}
