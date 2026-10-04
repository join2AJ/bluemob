package com.bluemob.app.mesh

import kotlinx.coroutines.flow.SharedFlow

/** The part of the mesh the message store needs. [NearbyMeshTransport] is the real one; tests use a fake. */
interface MessageLink {
    val events: SharedFlow<MeshEvent>
    fun isConnected(nodeId: String): Boolean
    fun linkName(nodeId: String): String
    fun sendChat(toNodeId: String, messageId: String, text: String, sentAt: Long): Boolean
    fun sendReceipt(toNodeId: String, messageId: String, read: Boolean): Boolean
}
