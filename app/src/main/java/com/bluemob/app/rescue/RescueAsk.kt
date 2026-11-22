package com.bluemob.app.rescue

/** When to ask whether a rescue is over, so a group never stays open for weeks without anyone noticing. */
object RescueAsk {
    const val MINE_AFTER_MS = 12 * 3_600_000L
    const val HELPER_AFTER_MS = 24 * 3_600_000L
    const val AGAIN_MS = 24 * 3_600_000L

    enum class Ask { NONE, STILL_NEED_HELP, IS_IT_OVER }

    /** How it ended, as helpers report it to the group. */
    val OUTCOMES = listOf("Found safe", "Found, getting care", "Not found", "False alarm")

    fun ask(room: RescueRoom, now: Long, snoozedUntil: Long?): Ask = when {
        room.ended || (snoozedUntil != null && snoozedUntil > now) -> Ask.NONE
        room.mine && now - room.startedAt >= MINE_AFTER_MS -> Ask.STILL_NEED_HELP
        !room.mine && now - room.startedAt >= HELPER_AFTER_MS -> Ask.IS_IT_OVER
        else -> Ask.NONE
    }

    /** "3 days", "14 hours": how long it has been open. */
    fun openFor(room: RescueRoom, now: Long): String {
        val h = ((now - room.startedAt) / 3_600_000L).coerceAtLeast(1)
        return if (h >= 48) "${h / 24} days" else "$h hours"
    }
}
