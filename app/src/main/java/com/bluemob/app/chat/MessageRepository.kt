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
    /**
     * Sky's answer. With the offline AI it's written a bit at a time: the second argument gets the answer so far, and
     * the chat shows it growing.
     */
    private val sky: suspend (String, (String) -> Unit) -> SkyAnswer,
    /** Writes to the audit trail: (kind, other person's node ID, what happened). */
    private val record: (AuditKind, String, String) -> Unit = { _, _, _ -> },
    /** Called once per new incoming message (not for copies), e.g. to show a notification. */
    private val onIncoming: (peer: String, text: String) -> Unit = { _, _ -> },
    /** A message with a photo, document or voice note was saved (sent or received): its file can move now. */
    private val onAttachment: suspend (MessageEntity) -> Unit = {},
    /** Group chats this phone is in. */
    val groups: GroupStore? = null,
    private val me: () -> Pair<String, String> = { "" to "" },
) {
    /** Every message shown in a chat (copies that only carry group messages and reactions are left out). */
    val messages: StateFlow<List<MessageEntity>> = dao.observeAll().map { all -> all.filter { !it.hidden } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

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

    fun send(peer: String, text: String, replyTo: MessageEntity? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            if (peer == SkyBot.NODE_ID) {
                talkToSky(clean, now)
                return@launch
            }
            val reply = replyTo?.let { Rich.replyField(it.id, if (it.fromMe) me().second else it.senderName.ifBlank { "" }, it.text) } ?: ""
            if (GroupStore.isGroup(peer)) { sendToGroup(peer, clean, reply, now); return@launch }
            val inRange = mesh.isConnected(peer)
            val id = newId()
            record(AuditKind.MESSAGE, peer, "Message $id written to {name}: \"${clean.take(80)}\"")
            dao.insert(
                MessageEntity(
                    id = id, peer = peer, fromMe = true, text = clean, createdAt = now, status = MessageStatus.PENDING, replyTo = reply,
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
     * A group message: shown once in the group chat, and carried to each member as its own encrypted message (so it
     * travels every way a normal message does, and each member's receipts come back).
     */
    private suspend fun sendToGroup(gid: String, text: String, reply: String, now: Long) {
        val g = groups?.get(gid) ?: return
        val (myId, myName) = me()
        val id = newId()
        val members = g.members + (myId to myName)
        val others = members.keys.filter { it != myId }
        dao.insert(MessageEntity(id = id, peer = gid, fromMe = true, text = text, createdAt = now, status = if (others.isEmpty()) MessageStatus.LOCAL else MessageStatus.SENT,
            replyTo = reply, sender = myId, senderName = myName,
            history = event(now, "Written on your phone") + event(now, "Sent to ${others.size} ${if (others.size == 1) "member" else "members"} of ${g.name}, each end-to-end encrypted")))
        val r = Rich.replyParts(reply)
        val wire = Rich(text, group = gid, groupName = g.name, members = members, groupMsgId = id, replyId = r?.first, replyName = r?.second.orEmpty(), replyText = r?.third.orEmpty()).encode()
        others.forEach { to -> dao.insert(carrier(to, wire, id, now)) }
        record(AuditKind.MESSAGE, gid, "Group message $id written to ${g.name} (${others.size} members): \"${text.take(80)}\"")
        deliverAll()
    }

    private fun carrier(to: String, wire: String, parent: String, now: Long) = MessageEntity(
        id = newId(), peer = to, fromMe = true, text = wire, createdAt = now, status = MessageStatus.PENDING,
        directState = PathState.WAITING, internetState = PathState.UNAVAILABLE, hidden = true, parent = parent,
    )

    /** Starts a group with [members] (node ID → name). Everyone hears about it with the first message. */
    fun createGroup(name: String, members: Map<String, String>): String? {
        val store = groups ?: return null
        val (myId, myName) = me()
        val gid = GroupStore.PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)
        store.put(ChatGroup(gid, name.trim().take(40), members.filterKeys { it != myId }.entries.take(Rich.MAX_MEMBERS - 1).associate { it.key to it.value }))
        send(gid, "👋 $myName created the group \"${name.trim().take(40)}\"")
        return gid
    }

    /** Leaves a group: the others are told, and the chat goes from this phone. */
    fun leaveGroup(gid: String) {
        val g = groups?.get(gid) ?: return
        scope.launch {
            val (myId, myName) = me()
            val now = System.currentTimeMillis()
            val wire = Rich("$myName left the group", group = gid, groupName = g.name, members = g.members, leave = true).encode()
            g.members.keys.filter { it != myId }.forEach { dao.insert(carrier(it, wire, "", now)) }
            groups.remove(gid)
            dao.deleteChat(gid)
            deliverAll()
        }
    }

    /** Reacts to a message with an emoji ("" takes our reaction back). */
    fun react(target: MessageEntity, emoji: String) {
        scope.launch {
            val (myId, _) = me()
            val now = System.currentTimeMillis()
            dao.get(target.id)?.let { dao.update(it.copy(reactions = Rich.withReaction(it.reactions, myId, emoji))) }
            if (target.peer == SkyBot.NODE_ID) return@launch
            val g = if (GroupStore.isGroup(target.peer)) groups?.get(target.peer) else null
            val wire = Rich(react = emoji, reactTo = target.id, group = g?.id, groupName = g?.name.orEmpty(), members = g?.members.orEmpty()).encode()
            val to = g?.members?.keys?.filter { it != myId } ?: listOf(target.peer)
            to.forEach { dao.insert(carrier(it, wire, "", now)) }
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
                val told = peer != SkyBot.NODE_ID && receiptFor(m)
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

    /** Read receipt for an incoming message: in a group it goes to whoever wrote it, for their copy. */
    private fun receiptFor(m: MessageEntity): Boolean =
        if (GroupStore.isGroup(m.peer)) m.sender.isNotBlank() && m.parent.isNotBlank() && mesh.sendReceipt(m.sender, m.parent, read = true)
        else mesh.sendReceipt(m.peer, m.id, read = true)

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
            // A group message is carried by its copies, one per member.
            if (GroupStore.isGroup(m.peer)) return@forEach
            // A reply carries what it answers.
            val wire = if (!m.hidden && m.replyTo.isNotBlank()) Rich.replyParts(m.replyTo)?.let { (id, n, q) -> Rich(m.text, replyId = id, replyName = n, replyText = q).encode() } ?: m.text else m.text
            when (val h = mesh.sendChat(m.peer, m.id, wire, m.createdAt, m.att.ifBlank { null })) {
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
            if (receiptFor(m)) dao.update(m.copy(readReceiptSent = true))
        }
    }

    private suspend fun receive(e: MeshEvent.MessageReceived) {
        val now = System.currentTimeMillis()
        val isNew = dao.markSeen(SeenId(e.messageId, now)) != -1L
        // Always answer with a receipt: if our first one was lost, the sender is still re-sending.
        mesh.sendReceipt(e.fromNodeId, e.messageId, read = false)
        if (!isNew) return // A copy we already have: discard it.
        Rich.decode(e.text)?.let { rich -> receiveRich(e, rich, now); return }
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

    /** A group message, a reply or a reaction. */
    private suspend fun receiveRich(e: MeshEvent.MessageReceived, rich: Rich, now: Long) {
        val (myId, _) = me()
        val gid = rich.group?.takeIf { GroupStore.isGroup(it) }
        // Keep the group's name and members up to date (whoever writes last knows best).
        if (gid != null && groups != null) {
            val known = groups.get(gid)
            if (rich.leave) {
                known?.let { groups.put(it.copy(members = it.members - e.fromNodeId)) }
            } else if (rich.members.isNotEmpty()) {
                groups.put(ChatGroup(gid, rich.groupName.ifBlank { known?.name ?: "Group" }, rich.members - myId, known?.createdAt ?: now))
            } else if (known == null) {
                groups.put(ChatGroup(gid, rich.groupName.ifBlank { "Group" }, mapOf(e.fromNodeId to ""), now))
            }
        }
        if (rich.react != null) {
            val target = rich.reactTo?.let { dao.get(it) } ?: return
            // Only someone in that chat can react to its messages.
            if (target.peer != e.fromNodeId && target.peer != gid) return
            dao.update(target.copy(reactions = Rich.withReaction(target.reactions, e.fromNodeId, rich.react)))
            return
        }
        val peer = gid ?: e.fromNodeId
        val senderName = rich.members[e.fromNodeId].orEmpty()
        if (rich.leave) {
            dao.insert(MessageEntity(id = e.messageId, peer = peer, fromMe = false, text = "🚪 ${senderName.ifBlank { "Someone" }} left the group", createdAt = e.sentAt,
                status = MessageStatus.READ, readAt = now, readReceiptSent = true, sender = e.fromNodeId, senderName = senderName))
            return
        }
        val open = openConversation == peer
        if (!open) onIncoming(peer, if (gid != null) "${senderName.ifBlank { "Someone" }}: ${rich.text}" else rich.text)
        record(AuditKind.MESSAGE, e.fromNodeId, "Message ${e.messageId} received from {name}" + (if (gid != null) " in group ${rich.groupName}" else "") + ": \"${rich.text.take(80)}\"")
        val readNow = open && mesh.sendReceipt(e.fromNodeId, e.messageId, read = true)
        dao.insert(MessageEntity(
            id = if (gid != null) rich.groupMsgId ?: e.messageId else e.messageId, peer = peer, fromMe = false, text = rich.text, createdAt = e.sentAt,
            status = if (open) MessageStatus.READ else MessageStatus.RECEIVED, readAt = if (open) now else null, readReceiptSent = readNow,
            history = event(now, if (e.viaInternet) "Received over the internet, through the BlueMob relay. End-to-end encrypted" else "Received over ${mesh.linkName(e.fromNodeId)}"),
            sender = if (gid != null) e.fromNodeId else "", senderName = senderName,
            replyTo = rich.replyId?.let { Rich.replyField(it, rich.replyName, rich.replyText) } ?: "",
            parent = if (gid != null) e.messageId else "",
        ))
    }

    /** A member's receipt for their copy of our group message: the group message shows the best of them. */
    private suspend fun updateGroupStatus(parentId: String, now: Long) {
        val visible = dao.get(parentId) ?: return
        val copies = dao.copiesOf(parentId)
        if (copies.isEmpty()) return
        val read = copies.count { it.status == MessageStatus.READ }
        val delivered = copies.count { it.status == MessageStatus.DELIVERED || it.status == MessageStatus.READ }
        val status = when { read == copies.size -> MessageStatus.READ; delivered > 0 -> MessageStatus.DELIVERED; else -> MessageStatus.SENT }
        val note = "Delivered to $delivered of ${copies.size}, read by $read"
        if (status != visible.status || !visible.history.endsWith("$note\n")) dao.update(visible.copy(status = status,
            deliveredAt = visible.deliveredAt ?: if (delivered > 0) now else null, readAt = if (status == MessageStatus.READ) now else visible.readAt,
            history = visible.history + event(now, note)))
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
        if (m.hidden && m.parent.isNotBlank()) updateGroupStatus(m.parent, now)
    }

    private suspend fun talkToSky(text: String, now: Long) {
        dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, true, text, now, MessageStatus.LOCAL))
        delay(350)
        _typing.update { it + SkyBot.NODE_ID }
        val started = System.currentTimeMillis()
        // The answer as it's being written: shown as one message that grows.
        val streamLock = Mutex()
        var shown: MessageEntity? = null
        var finished = false
        var lastShown = 0L
        val status = { if (openConversation == SkyBot.NODE_ID) MessageStatus.READ else MessageStatus.RECEIVED }
        val answer = runCatching {
            sky(text) { partial ->
                val t = System.currentTimeMillis()
                if (partial.isNotBlank() && t - lastShown >= 250) {
                    lastShown = t
                    scope.launch {
                        streamLock.withLock {
                            if (finished) return@withLock
                            val m = shown
                            if (m == null) {
                                val fresh = MessageEntity(newId(), SkyBot.NODE_ID, false, partial, System.currentTimeMillis(), status())
                                dao.insert(fresh); shown = fresh
                                _typing.update { it - SkyBot.NODE_ID }
                            } else dao.update(m.copy(text = partial).also { shown = it })
                        }
                    }
                }
            }
        }.getOrElse { SkyAnswer("Sorry, something went wrong. Please ask again.") }
        val actions = answer.actions.joinToString("\n") { "${it.label}|${it.target}" }
        streamLock.withLock {
            finished = true
            val m = shown
            if (m != null) {
                dao.update(m.copy(text = answer.text, actions = actions, status = if (openConversation == SkyBot.NODE_ID) MessageStatus.READ else m.status))
            } else {
                delay((SkyBot.typingDelayMs(answer.text) - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                dao.insert(MessageEntity(newId(), SkyBot.NODE_ID, false, answer.text, System.currentTimeMillis(), status(), actions = actions))
            }
        }
        _typing.update { it - SkyBot.NODE_ID }
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
