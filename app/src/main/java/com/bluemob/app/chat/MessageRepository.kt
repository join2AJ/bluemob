package com.bluemob.app.chat

import com.bluemob.app.bot.SkyAnswer
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageDao
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.data.SeenId
import com.bluemob.app.mesh.MeshEvent
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
            dao.insert(
                MessageEntity(
                    id = newId(), peer = peer, fromMe = true, text = clean, createdAt = now, status = MessageStatus.PENDING,
                    directState = if (inRange) PathState.TRYING else PathState.WAITING,
                    internetState = PathState.UNAVAILABLE,
                    history = event(now, "Written on your phone") +
                        event(now, if (inRange) "In range. Sending directly" else "Not in range. Waiting on your phone until they are"),
                )
            )
            deliver(peer)
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

    fun clearAll() = scope.launch { dao.clear() }

    private suspend fun handle(event: MeshEvent) {
        when (event) {
            is MeshEvent.PeerConnected -> deliver(event.nodeId)
            is MeshEvent.MessageReceived -> receive(event)
            is MeshEvent.Receipt -> receipt(event)
            else -> Unit
        }
    }

    /** Sends everything still waiting for [peer], plus read receipts we owe them. */
    private suspend fun deliver(peer: String) = deliveryLock.withLock {
        if (!mesh.isConnected(peer)) return@withLock
        val link = mesh.linkName(peer)
        val now = System.currentTimeMillis()
        dao.unacknowledged(peer).forEach { m ->
            if (mesh.sendChat(peer, m.id, m.text, m.createdAt)) {
                val note = if (m.attempts == 0) "Sent over $link" else "Sent again over $link (attempt ${m.attempts + 1}): no receipt came back last time"
                dao.update(m.copy(status = MessageStatus.SENT, directState = PathState.TRYING, attempts = m.attempts + 1, history = m.history + event(now, note)))
            }
        }
        dao.readReceiptsOwed(peer).forEach { m ->
            if (mesh.sendReceipt(peer, m.id, read = true)) dao.update(m.copy(readReceiptSent = true))
        }
    }

    private suspend fun receive(e: MeshEvent.MessageReceived) {
        val now = System.currentTimeMillis()
        val isNew = dao.markSeen(SeenId(e.messageId, now)) != -1L
        // Always answer with a receipt: if our first one was lost, the sender is still re-sending.
        mesh.sendReceipt(e.fromNodeId, e.messageId, read = false)
        if (!isNew) return // A copy we already have: discard it.
        val open = openConversation == e.fromNodeId
        val readNow = open && mesh.sendReceipt(e.fromNodeId, e.messageId, read = true)
        dao.insert(
            MessageEntity(
                id = e.messageId, peer = e.fromNodeId, fromMe = false, text = e.text, createdAt = e.sentAt,
                status = if (open) MessageStatus.READ else MessageStatus.RECEIVED,
                readAt = if (open) now else null, readReceiptSent = readNow,
                history = event(now, "Received over ${mesh.linkName(e.fromNodeId)}"),
            )
        )
    }

    private suspend fun receipt(e: MeshEvent.Receipt) {
        val m = dao.get(e.messageId) ?: return
        if (!m.fromMe || m.peer != e.fromNodeId) return
        val now = System.currentTimeMillis()
        val link = m.deliveredVia ?: mesh.linkName(e.fromNodeId)
        val delivered = m.copy(
            deliveredAt = m.deliveredAt ?: now, deliveredVia = link, directState = PathState.DELIVERED,
            internetState = if (m.internetState == PathState.WAITING) PathState.CANCELLED else m.internetState,
        )
        when {
            e.read && m.status != MessageStatus.READ ->
                dao.update(delivered.copy(status = MessageStatus.READ, readAt = now,
                    history = delivered.history + (if (m.deliveredAt == null) event(now, "Delivered over $link") else "") + event(now, "Read receipt came back over $link")))
            !e.read && (m.status == MessageStatus.SENT || m.status == MessageStatus.PENDING) ->
                dao.update(delivered.copy(status = MessageStatus.DELIVERED,
                    history = delivered.history + event(now, "Delivered over $link. Delivery receipt came back")))
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
        fun newId(): String = "m-" + UUID.randomUUID().toString().replace("-", "").take(20)
        fun event(at: Long, text: String) = "$at|$text\n"

        /** Parses [MessageEntity.history] into (time, text) pairs. */
        fun historyOf(m: MessageEntity): List<Pair<Long, String>> = m.history.lines().filter { it.isNotBlank() }.mapNotNull {
            val i = it.indexOf('|')
            if (i < 0) null else it.substring(0, i).toLongOrNull()?.let { t -> t to it.substring(i + 1) }
        }
    }
}
