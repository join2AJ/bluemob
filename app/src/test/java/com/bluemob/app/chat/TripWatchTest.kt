package com.bluemob.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripWatchTest {
    private val me = "3f9a1c2b7d4e8a01"
    private val ravi = "0123456789abcdef"
    private val now = 10_000_000L
    private val g = ChatGroup("g-1", "Group 1", mapOf(ravi to "Ravi"), 0, trip = true, owner = me, autoMin = 10)

    @Test fun rollCallWhenDueAndAlertForPhonesThatDidNotAnswer() {
        assertEquals(listOf(TripWatch.Act.RollCall("g-1")), TripWatch.due(listOf(g), emptyMap(), me, now, emptySet()))
        val recent = RollCall("m1", now - 4 * 60_000, mapOf(ravi to Reach.Waiting))
        assertEquals(listOf(TripWatch.Act.Unreachable("g-1", "m1", listOf(ravi))), TripWatch.due(listOf(g), mapOf("g-1" to recent), me, now, emptySet()))
        // Once per roll call; answered phones don't count; too early to tell after a minute.
        assertTrue(TripWatch.due(listOf(g), mapOf("g-1" to recent), me, now, setOf("m1")).isEmpty())
        assertTrue(TripWatch.due(listOf(g), mapOf("g-1" to recent.copy(members = mapOf(ravi to Reach.Direct(9)))), me, now, emptySet()).isEmpty())
        assertTrue(TripWatch.due(listOf(g), mapOf("g-1" to recent.copy(at = now - 60_000)), me, now, emptySet()).isEmpty())
    }

    @Test fun onlyTheAdminOrCaptainActsAndNotOnClosedGroups() {
        assertTrue(TripWatch.due(listOf(g), emptyMap(), ravi, now, emptySet()).isEmpty())
        assertEquals(1, TripWatch.due(listOf(g.copy(captain = ravi)), emptyMap(), ravi, now, emptySet()).size)
        assertTrue(TripWatch.due(listOf(g.copy(closedAt = 5)), emptyMap(), me, now, emptySet()).isEmpty())
    }

    @Test fun asksTheAdminOnceWhenTheTripEnds() {
        val ended = g.copy(autoMin = 0, expiresAt = now - 1)
        assertEquals(listOf(TripWatch.Act.AskToClose("g-1")), TripWatch.due(listOf(ended), emptyMap(), me, now, emptySet()))
        assertTrue(TripWatch.due(listOf(ended.copy(askedToClose = true)), emptyMap(), me, now, emptySet()).isEmpty())
        assertTrue(TripWatch.due(listOf(ended.copy(expiresAt = now + 1)), emptyMap(), me, now, emptySet()).isEmpty())
    }
}
