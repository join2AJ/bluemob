package com.bluemob.app.mesh

import com.bluemob.app.trail.PositionEstimate

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
    /** A chat message. The same [messageId] may arrive more than once; the receiver keeps the first. */
    data class MessageReceived(val fromNodeId: String, val messageId: String, val text: String, val sentAt: Long) : MeshEvent
    /** A receipt for one of our messages: [read] is false for "delivered", true for "read". */
    data class Receipt(val fromNodeId: String, val messageId: String, val read: Boolean) : MeshEvent
    /** A link to [nodeId] just opened: anything waiting for them can go now. */
    data class PeerConnected(val nodeId: String) : MeshEvent
    data class PingResult(val nodeId: String, val roundTripMs: Long) : MeshEvent
    data class SosReceived(val sos: SosSignal) : MeshEvent
    data class LostReceived(val lost: LostSignal) : MeshEvent
    data class RoomReceived(val msg: RoomPayload) : MeshEvent
}

/**
 * One message in an SOS rescue group. [room] is the SOS ID. [kind] is JOIN, TEXT, POS, ARRIVED, LEAVE or ENDED.
 * [pos] is the sender's position, when they share it (JOIN, POS, ARRIVED).
 */
data class RoomPayload(
    val id: String,
    val room: String,
    val fromNodeId: String,
    val fromName: String,
    val kind: String,
    val text: String,
    val at: Long,
    val pos: PositionEstimate?,
    val hops: Int = 0,
)

/** An SOS as it travels the mesh. [hops] counts how many phones passed it on. */
data class SosSignal(
    val id: String,
    val fromNodeId: String,
    val name: String,
    val note: String,
    val lat: Double?,
    val lon: Double?,
    val battery: Int?,
    val at: Long,
    val hops: Int,
    val cancelled: Boolean = false,
    /** GPS, or last fix plus steps and direction since, so helpers can pinpoint the sender. */
    val pos: PositionEstimate? = null,
)

/** "I'm lost": the sender's latest position estimate, passed on like an SOS. [ended] when they found their way. */
data class LostSignal(
    val id: String,
    val fromNodeId: String,
    val name: String,
    val pos: PositionEstimate?,
    val at: Long,
    val hops: Int,
    val ended: Boolean = false,
)
