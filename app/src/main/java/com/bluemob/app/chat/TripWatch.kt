package com.bluemob.app.chat

/**
 * What the group admin's (or a team captain's) phone should do for its trip groups right now, checked every minute:
 * send the automatic roll call when it's due, sound an alert when a phone didn't answer the last roll call, and ask
 * the admin whether to close the group once the trip has ended.
 */
object TripWatch {
    /** A phone that hasn't answered a roll call this long counts as unreachable. */
    const val UNREACHABLE_AFTER_MS = 3 * 60_000L
    /** A checkpoint is overdue this long after its planned time (and stops being checked 12 hours later). */
    const val OVERDUE_MS = 30 * 60_000L
    /** Buddies out of touch this long: each is told; after twice as long, the group (and so its admin) is told. */
    const val BUDDY_APART_MS = 20 * 60_000L

    sealed interface Act {
        data class RollCall(val gid: String) : Act
        /** [ids] didn't answer roll call [rollId]. */
        data class Unreachable(val gid: String, val rollId: String, val ids: List<String>) : Act
        data class AskToClose(val gid: String) : Act
        /** A captain's team roll call is done: sum it up in the team chat for the admin. */
        data class Summary(val gid: String, val rollId: String) : Act
        /** The trip ended long enough ago: erase its check-ins from this phone (the group and chat stay). */
        data class ForgetCheckIns(val gid: String) : Act
        /** [ids] haven't checked in at checkpoint [stopId], [OVERDUE_MS] after its planned time. Key: once per stop. */
        data class Overdue(val gid: String, val stopId: String, val ids: List<String>) : Act {
            val key get() = "overdue:$gid:$stopId"
        }
        /** Our buddy hasn't been in touch for [minutes]; [tellGroup] once it's been long enough to tell the admin. */
        data class BuddyApart(val gid: String, val buddy: String, val minutes: Long, val since: Long, val tellGroup: Boolean) : Act {
            val key get() = "buddy:$gid:$buddy:$since:${if (tellGroup) "group" else "me"}"
        }
        /** We're at checkpoint [stopId] (by location). */
        data class CheckIn(val gid: String, val stopId: String) : Act
        /** Time for today's altitude check (the admin turned it on). Key: once a day. */
        data class AltitudeDue(val gid: String, val day: Long) : Act { val key get() = "ams:$gid:$day" }
        /** A member's altitude check says moderate or worse: tell the admin or captain once. */
        data class AltitudeAlert(val gid: String, val member: String, val check: AmsCheck) : Act { val key get() = "amsalert:$gid:$member:${check.at}" }
        /** The trip ended: erase the health cards and altitude checks others shared with this phone. */
        data class ForgetCare(val gid: String) : Act
    }

    /** The day number and hour of [t] on this phone's clock. */
    fun localDay(t: Long): Long { val off = java.util.TimeZone.getDefault().getOffset(t); return Math.floorDiv(t + off, 86_400_000L) }
    fun localHour(t: Long): Int { val off = java.util.TimeZone.getDefault().getOffset(t); return (Math.floorMod(t + off, 86_400_000L) / 3_600_000L).toInt() }

    /** [alerted]: roll calls we already sounded an alert for. */
    fun due(groups: Collection<ChatGroup>, rolls: Map<String, RollCall>, me: String, now: Long, alerted: Set<String>,
        summarized: Set<String> = emptySet(), here: com.bluemob.app.contacts.GeoPoint? = null,
        /** When we were last in touch with someone (nearby, a message, online); 0 if never. */
        lastContact: (String) -> Long = { 0L }): List<Act> = buildList {
        groups.filter { it.trip && it.checkins.isNotEmpty() }.forEach { g ->
            g.closedTime(now).takeIf { it > 0 && now - it >= TripMeta.CHECKINS_KEPT_MS }?.let { add(Act.ForgetCheckIns(g.id)) }
        }
        groups.filter { it.trip && it.isClosed(now) && (it.health.isNotEmpty() || it.amsChecks.isNotEmpty()) }.forEach { add(Act.ForgetCare(it.id)) }
        groups.filter { it.trip && !it.isClosed(now) }.forEach { g ->
            val roll = rolls[g.id]
            // The daily altitude check, from 7 in the morning, once a day.
            if (g.amsDaily && localHour(now) >= 7 && localDay(g.myAmsAt) != localDay(now)) {
                val act = Act.AltitudeDue(g.id, localDay(now))
                if (act.key !in alerted) add(act)
            }
            if (g.canManage(me)) g.amsChecks.forEach { (id, c) ->
                if (id != me && c.level >= AltitudeCheck.Level.MODERATE && now - c.at < 12 * 3_600_000L) {
                    val act = Act.AltitudeAlert(g.id, id, c)
                    if (act.key !in alerted) add(act)
                }
            }
            if (g.canManage(me)) {
                if (g.autoMin > 0 && g.members.isNotEmpty() && now - (roll?.at ?: 0) >= g.autoMin * 60_000L) add(Act.RollCall(g.id))
                if (roll != null && roll.messageId !in alerted && now - roll.at >= UNREACHABLE_AFTER_MS) {
                    val missing = roll.members.filterValues { it == Reach.Waiting }.keys.filter { it in g.members }
                    if (missing.isNotEmpty()) add(Act.Unreachable(g.id, roll.messageId, missing))
                }
            }
            if (g.isOwner(me) && !g.isTeam && g.expiresAt in 1..now && !g.askedToClose) add(Act.AskToClose(g.id))
            // Overdue at a checkpoint: for the admin or captain, once per checkpoint, up to 12 hours after it was due.
            if (g.canManage(me)) g.itinerary.filter { it.checkpoint && now - it.at in OVERDUE_MS..(12 * 3_600_000L) }.forEach { s ->
                val missing = g.members.keys.filter { g.checkins[s.id]?.containsKey(it) != true }
                val act = Act.Overdue(g.id, s.id, missing)
                if (missing.isNotEmpty() && act.key !in alerted) add(act)
            }
            // My buddy: out of touch for a while (we must have been in touch since the trip began).
            g.buddies[me]?.takeIf { it in g.members }?.let { buddy ->
                val last = lastContact(buddy)
                if (last >= g.createdAt && now - last >= BUDDY_APART_MS) {
                    val tell = now - last >= 2 * BUDDY_APART_MS
                    val act = Act.BuddyApart(g.id, buddy, (now - last) / 60_000, last, tell)
                    if (act.key !in alerted) add(act)
                }
            }
            if (g.isTeam && g.captain == me && !g.isOwner(me) && roll != null && roll.messageId !in summarized && now - roll.at >= UNREACHABLE_AFTER_MS)
                add(Act.Summary(g.id, roll.messageId))
            // Checkpoints with a place: a fresh, accurate enough position inside the circle checks us in.
            if (here != null && now - here.time < 5 * 60_000 && here.accuracyM <= 100) g.itinerary.filter { it.checkpoint && it.hasPlace }.forEach { s ->
                if (g.checkins[s.id]?.containsKey(me) != true &&
                    com.bluemob.app.util.Geo.distanceM(here, com.bluemob.app.contacts.GeoPoint(s.lat!!, s.lon!!, 0f, now)) <= s.radiusM)
                    add(Act.CheckIn(g.id, s.id))
            }
        }
    }
}
