package com.bluemob.app.mesh

import kotlinx.coroutines.flow.SharedFlow

/** What happened when a message was offered to the mesh. */
sealed interface Handoff {
    /** Sent straight to them over [link]. */
    data class Direct(val link: String) : Handoff
    /** Given to these phones nearby to carry toward them. */
    data class Carried(val via: List<String>) : Handoff
    /** No one new to give it to right now: it waits on this phone. */
    data object Held : Handoff
    /** We don't have their key yet, so it can't be encrypted. BlueMob is asking phones nearby for it. */
    data object NeedsKey : Handoff
}

/** The part of the mesh the message store needs. [MeshRouter] is the real one; tests use a fake. */
interface MessageLink {
    val events: SharedFlow<MeshEvent>
    fun isConnected(nodeId: String): Boolean
    fun linkName(nodeId: String): String
    /** [att] is an attachment's details (JSON), sent inside the encrypted message; older versions see only [text]. */
    fun sendChat(toNodeId: String, messageId: String, text: String, sentAt: Long, att: String? = null): Handoff
    /** True if the receipt is on its way (to them, or with a phone carrying it). */
    fun sendReceipt(toNodeId: String, messageId: String, read: Boolean): Boolean
}
