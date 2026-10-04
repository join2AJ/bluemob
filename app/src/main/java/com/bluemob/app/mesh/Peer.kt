package com.bluemob.app.mesh

enum class PeerState { DISCOVERED, CONNECTING, CONNECTED }

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
)

data class LogLine(val timeMillis: Long, val text: String)
