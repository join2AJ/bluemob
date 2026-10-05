package com.bluemob.app.rescue

import com.bluemob.app.data.RescueMessage
import com.bluemob.app.trail.PositionEstimate
import com.bluemob.app.util.Geo

enum class HelperStatus(val label: String) { COMING("On the way"), ARRIVED("Arrived"), LEFT("Left") }

/** Someone who tapped "I'm coming". [pos] is their latest shared position. */
data class Helper(
    val nodeId: String,
    val name: String,
    val status: HelperStatus,
    val pos: PositionEstimate?,
    val updatedAt: Long,
    val joinedAt: Long,
)

/**
 * The group around one SOS: the person who needs help, everyone who said they're coming, and their chat.
 * Anyone who heard the SOS can see it; tapping "I'm coming" adds you to it.
 */
data class RescueRoom(
    val id: String,
    val victimId: String,
    val victimName: String,
    val note: String,
    val battery: Int?,
    val startedAt: Long,
    /** The person's latest known position: from their SOS, then from their updates. */
    val victimPos: PositionEstimate?,
    val ended: Boolean,
    val helpers: List<Helper>,
    /** What shows in the chat: messages and joined / arrived / left / ended lines. */
    val chat: List<RescueMessage>,
    /** True on the phone of the person who sent the SOS. */
    val mine: Boolean,
    val myStatus: HelperStatus?,
) {
    val coming: List<Helper> get() = helpers.filter { it.status != HelperStatus.LEFT }
    val iAmIn: Boolean get() = mine || (myStatus != null && myStatus != HelperStatus.LEFT)

    /** Straight-line distance from a helper to the person, if both positions are known. */
    fun distanceM(h: Helper): Double? {
        val a = h.pos ?: return null
        val b = victimPos ?: return null
        return Geo.distanceM(com.bluemob.app.contacts.GeoPoint(a.lat, a.lon, 0f, 0), com.bluemob.app.contacts.GeoPoint(b.lat, b.lon, 0f, 0))
    }

    companion object {
        const val OPEN = "OPEN"
        const val JOIN = "JOIN"
        const val TEXT = "TEXT"
        const val POS = "POS"
        const val ARRIVED = "ARRIVED"
        const val LEAVE = "LEAVE"
        const val ENDED = "ENDED"
        private val CHAT_KINDS = setOf(JOIN, TEXT, ARRIVED, LEAVE, ENDED)

        /** Walking time over rough ground, at about 1.1 m/s. */
        fun walkMinutes(m: Double): Int = (m / 1.1 / 60).toInt().coerceAtLeast(1)

        /** Builds every room from the stored messages. Rooms whose SOS we haven't heard yet stay hidden. */
        fun build(all: List<RescueMessage>, myId: String, decode: (String?) -> PositionEstimate?): List<RescueRoom> =
            all.groupBy { it.room }.mapNotNull { (id, list) -> buildOne(id, list.sortedBy { it.at }, myId, decode) }.sortedByDescending { it.startedAt }

        private fun buildOne(id: String, list: List<RescueMessage>, myId: String, decode: (String?) -> PositionEstimate?): RescueRoom? {
            val open = list.firstOrNull { it.kind == OPEN } ?: return null
            val victim = open.fromNodeId
            val helpers = list.filter { it.fromNodeId != victim }.groupBy { it.fromNodeId }.mapNotNull { (node, msgs) ->
                val join = msgs.firstOrNull { it.kind == JOIN } ?: return@mapNotNull null
                val status = when (msgs.lastOrNull { it.kind in setOf(JOIN, ARRIVED, LEAVE) }?.kind) {
                    ARRIVED -> HelperStatus.ARRIVED
                    LEAVE -> HelperStatus.LEFT
                    else -> HelperStatus.COMING
                }
                val withPos = msgs.lastOrNull { it.pos != null }
                Helper(node, msgs.last().fromName, status, decode(withPos?.pos), msgs.maxOf { it.at }, join.at)
            }.sortedBy { it.joinedAt }
            val victimPos = list.lastOrNull { it.fromNodeId == victim && it.pos != null && it.kind != OPEN }?.let { decode(it.pos) } ?: decode(open.pos)
            return RescueRoom(
                id = id, victimId = victim, victimName = open.fromName, note = open.text, battery = list.lastOrNull { it.fromNodeId == victim && it.battery != null }?.battery,
                startedAt = open.at, victimPos = victimPos, ended = list.any { it.kind == ENDED }, helpers = helpers,
                chat = list.filter { it.kind in CHAT_KINDS }, mine = victim == myId, myStatus = helpers.firstOrNull { it.nodeId == myId }?.status,
            )
        }
    }
}
