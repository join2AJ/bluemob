package com.bluemob.app.mesh

enum class PeerState { DISCOVERED, CONNECTING, CONNECTED }

/** How good the radio link is, as reported by Nearby Connections. */
enum class LinkQuality { LOW, MEDIUM, HIGH }

/**
 * A phone within direct radio range.
 *
 * [endpointId] is the short-lived ID Nearby Connections assigns to this link;
 * [nodeId] is the peer's permanent BlueMob identity.
 */
data class Peer(
    val endpointId: String,
    val nodeId: String,
    val name: String,
    val state: PeerState,
    val quality: LinkQuality? = null,
)

data class LogLine(val timeMillis: Long, val text: String)

/** Things that arrive over the mesh that other parts of the app care about. */
sealed interface MeshEvent {
    data class ChatReceived(val fromNodeId: String, val messageId: String, val text: String, val sentAt: Long) : MeshEvent
    data class ChatDelivered(val toNodeId: String, val messageId: String) : MeshEvent
    data class PingResult(val nodeId: String, val roundTripMs: Long) : MeshEvent
}
