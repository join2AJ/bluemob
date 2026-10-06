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
    /** True once the phone proved it owns its ID (signed hello). */
    val verified: Boolean = false,
    /** Features their BlueMob has (from its hello). Null for versions before 0.7, which didn't say. */
    val caps: Set<String>? = null,
    /** Their BlueMob version, e.g. "0.8.0", if they said. */
    val app: String? = null,
)

/** A BlueMob phone nearby that speaks a different protocol, so the two can't link. */
data class OtherVersion(val name: String, val protocol: Int, val seenAt: Long)

data class LogLine(val timeMillis: Long, val text: String)

/** Things that arrive over the mesh that other parts of the app care about. */
sealed interface MeshEvent {
    /** A chat message. The same [messageId] may arrive more than once; the receiver keeps the first. */
    data class MessageReceived(
        val fromNodeId: String, val messageId: String, val text: String, val sentAt: Long,
        /** 1 = straight from them; more = passed on by other phones. */
        val hops: Int = 1,
        /** The sender's name, as they wrote it. Lets us show people we've never met. */
        val name: String? = null,
        /** Arrived through the BlueMob relay over the internet. */
        val viaInternet: Boolean = false,
        /** An attachment's details (JSON), when the message carries a photo, document or voice note. */
        val att: String? = null,
    ) : MeshEvent
    /** A receipt for one of our messages: [read] is false for "delivered", true for "read". */
    data class Receipt(val fromNodeId: String, val messageId: String, val read: Boolean, val hops: Int = 1, val viaInternet: Boolean = false) : MeshEvent
    /** A new way to reach people opened up (e.g. internet came back): retry what's waiting. */
    data object RouteAvailable : MeshEvent
    /** A link to [nodeId] just opened: anything waiting for them can go now. */
    data class PeerConnected(val nodeId: String) : MeshEvent
    data class PingResult(val nodeId: String, val roundTripMs: Long) : MeshEvent
    data class SosReceived(val sos: SosSignal) : MeshEvent
    data class LostReceived(val lost: LostSignal) : MeshEvent
    data class RoomReceived(val msg: RoomPayload) : MeshEvent
    /** We just learned [nodeId]'s public key, so messages waiting for it can go. */
    data class KeyLearned(val nodeId: String) : MeshEvent
    /**
     * A signed packet addressed to us ([kind]: game moves, call set-up, "ring my phone"). [direct] when the sender
     * is connected straight to this phone (calls need that).
     */
    data class App(val fromNodeId: String, val name: String, val kind: String, val body: org.json.JSONObject, val hops: Int, val direct: Boolean,
        /** Came over the internet, through the relay's live link. */
        val viaInternet: Boolean = false) : MeshEvent
    /** The relay says [nodeId] isn't online right now. */
    data class Unreachable(val nodeId: String) : MeshEvent
    /** A packet type handled outside the transport (audit witness notes, ratings). */
    data class Extra(val fromNodeId: String, val type: String, val json: org.json.JSONObject) : MeshEvent
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
    /** From the sender's profile, so helpers can tell medics (0.9+; older versions leave them out). */
    val bloodGroup: String? = null,
    val age: Int? = null,
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
