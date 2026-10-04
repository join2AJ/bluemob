package com.bluemob.app.chat

import com.bluemob.app.bot.SkyBot
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class MessageStatus { SENDING, SENT, DELIVERED, FAILED }

data class ChatMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val time: Long,
    val status: MessageStatus = MessageStatus.SENT,
)

data class Conversation(
    val nodeId: String,
    val messages: List<ChatMessage> = emptyList(),
    val unread: Int = 0,
    val typing: Boolean = false,
)

/**
 * Chats with nearby phones and with the Sky practice bot.
 *
 * Messages are kept in memory for now; saving them on the phone comes with the chat phase.
 */
class ChatRepository(
    private val mesh: NearbyMeshTransport,
    private val scope: CoroutineScope,
) {
    private val _conversations = MutableStateFlow(
        mapOf(SkyBot.NODE_ID to Conversation(SkyBot.NODE_ID, SkyBot.greeting.map { incoming(it) }, unread = 1))
    )
    val conversations: StateFlow<Map<String, Conversation>> = _conversations.asStateFlow()

    /** The chat currently on screen, which should not collect unread badges. */
    var openConversation: String? = null

    init {
        scope.launch {
            mesh.events.collect { event ->
                when (event) {
                    is MeshEvent.ChatReceived -> addIncoming(event.fromNodeId, event.messageId, event.text, event.sentAt)
                    is MeshEvent.ChatDelivered -> setStatus(event.toNodeId, event.messageId, MessageStatus.DELIVERED)
                    is MeshEvent.PingResult -> Unit
                }
            }
        }
    }

    fun send(nodeId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val message = ChatMessage(UUID.randomUUID().toString(), true, clean, System.currentTimeMillis(), MessageStatus.SENDING)
        edit(nodeId) { it.copy(messages = it.messages + message) }
        if (nodeId == SkyBot.NODE_ID) talkToSky(message) else sendToPeer(nodeId, message)
    }

    fun markRead(nodeId: String) = edit(nodeId) { it.copy(unread = 0) }

    private fun sendToPeer(nodeId: String, message: ChatMessage) {
        val sent = mesh.sendChat(nodeId, message.id, message.text, message.time)
        setStatus(nodeId, message.id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
    }

    /** Plays out a real-feeling exchange: sent, delivered, typing…, reply. */
    private fun talkToSky(message: ChatMessage) {
        scope.launch {
            delay(350)
            setStatus(SkyBot.NODE_ID, message.id, MessageStatus.SENT)
            delay(450)
            setStatus(SkyBot.NODE_ID, message.id, MessageStatus.DELIVERED)
            val reply = SkyBot.reply(message.text)
            delay(400)
            edit(SkyBot.NODE_ID) { it.copy(typing = true) }
            delay(SkyBot.typingDelayMs(reply))
            edit(SkyBot.NODE_ID) { it.copy(typing = false) }
            addIncoming(SkyBot.NODE_ID, UUID.randomUUID().toString(), reply, System.currentTimeMillis())
        }
    }

    private fun addIncoming(nodeId: String, id: String, text: String, time: Long) {
        edit(nodeId) { c ->
            if (c.messages.any { it.id == id }) return@edit c
            c.copy(
                messages = c.messages + ChatMessage(id, false, text, time),
                unread = if (openConversation == nodeId) 0 else c.unread + 1,
            )
        }
    }

    private fun setStatus(nodeId: String, id: String, status: MessageStatus) = edit(nodeId) { c ->
        c.copy(messages = c.messages.map { if (it.id == id) it.copy(status = status) else it })
    }

    private fun edit(nodeId: String, transform: (Conversation) -> Conversation) {
        _conversations.update { all -> all + (nodeId to transform(all[nodeId] ?: Conversation(nodeId))) }
    }

    private fun incoming(text: String) = ChatMessage(UUID.randomUUID().toString(), false, text, System.currentTimeMillis())
}
