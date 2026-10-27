package com.bluemob.app.chat

/**
 * What the group admin's (or a team captain's) phone should do for its trip groups right now, checked every minute:
 * send the automatic roll call when it's due, sound an alert when a phone didn't answer the last roll call, and ask
 * the admin whether to close the group once the trip has ended.
 */
object TripWatch {
    /** A phone that hasn't answered a roll call this long counts as unreachable. */
    const val UNREACHABLE_AFTER_MS = 3 * 60_000L

    sealed interface Act {
        data class RollCall(val gid: String) : Act
        /** [ids] didn't answer roll call [rollId]. */
        data class Unreachable(val gid: String, val rollId: String, val ids: List<String>) : Act
        data class AskToClose(val gid: String) : Act
    }

    /** [alerted]: roll calls we already sounded an alert for. */
    fun due(groups: Collection<ChatGroup>, rolls: Map<String, RollCall>, me: String, now: Long, alerted: Set<String>): List<Act> = buildList {
        groups.filter { it.trip && !it.isClosed(now) }.forEach { g ->
            val roll = rolls[g.id]
            if (g.canManage(me)) {
                if (g.autoMin > 0 && g.members.isNotEmpty() && now - (roll?.at ?: 0) >= g.autoMin * 60_000L) add(Act.RollCall(g.id))
                if (roll != null && roll.messageId !in alerted && now - roll.at >= UNREACHABLE_AFTER_MS) {
                    val missing = roll.members.filterValues { it == Reach.Waiting }.keys.filter { it in g.members }
                    if (missing.isNotEmpty()) add(Act.Unreachable(g.id, roll.messageId, missing))
                }
            }
            if (g.isOwner(me) && !g.isTeam && g.expiresAt in 1..now && !g.askedToClose) add(Act.AskToClose(g.id))
        }
    }
}
