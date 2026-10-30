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

    @Test fun captainSumsUpTheTeamRollCallOnce() {
        val team = ChatGroup("g-t", "Team 1", mapOf(me to "Arjun"), 0, trip = true, owner = me, parent = "g-1", captain = ravi)
        val roll = RollCall("m9", now - 4 * 60_000, mapOf(me to Reach.Direct(5)))
        assertTrue(TripWatch.Act.Summary("g-t", "m9") in TripWatch.due(listOf(team), mapOf("g-t" to roll), ravi, now, emptySet()))
        assertTrue(TripWatch.due(listOf(team), mapOf("g-t" to roll), ravi, now, emptySet(), summarized = setOf("m9")).none { it is TripWatch.Act.Summary })
    }

    @Test fun checksInNearACheckpointOnce() {
        val cp = Stop("cp", 0, "Bridge", checkpoint = true, lat = 30.0, lon = 78.0, radiusM = 150)
        val trip = g.copy(autoMin = 0, itinerary = listOf(cp))
        val near = com.bluemob.app.contacts.GeoPoint(30.0005, 78.0, 10f, now)
        val far = com.bluemob.app.contacts.GeoPoint(30.01, 78.0, 10f, now)
        assertEquals(listOf(TripWatch.Act.CheckIn("g-1", "cp")), TripWatch.due(listOf(trip), emptyMap(), ravi, now, emptySet(), here = near))
        assertTrue(TripWatch.due(listOf(trip), emptyMap(), ravi, now, emptySet(), here = far).isEmpty())
        assertTrue(TripWatch.due(listOf(trip.copy(checkins = mapOf("cp" to mapOf(ravi to 1L)))), emptyMap(), ravi, now, emptySet(), here = near).isEmpty())
        // An old position doesn't count.
        assertTrue(TripWatch.due(listOf(trip), emptyMap(), ravi, now, emptySet(), here = near.copy(time = now - 10 * 60_000)).isEmpty())
    }

    @Test fun itinerarySurvivesTheTrip() {
        val stops = listOf(Stop("a", 5, "Camp", "note", checkpoint = true, lat = 30.1, lon = 78.2, radiusM = 500), Stop("b", 1, "Start"))
        assertEquals(stops.sortedBy { it.at }, Stop.fromJson(Stop.toJson(stops)))
        assertEquals("a", Stop.next(stops.sortedBy { it.at }, 3)!!.id)
    }

    @Test fun closedGroupsAreDeleted30DaysLater() {
        val now = 100 * 86_400_000L
        val closed = g.copy(autoMin = 0, closedAt = now - TripMeta.KEEP_CLOSED_MS)
        assertEquals(listOf(TripWatch.Act.Purge("g-1")), TripWatch.due(listOf(closed), emptyMap(), ravi, now, emptySet()))
        assertTrue(TripWatch.due(listOf(closed.copy(closedAt = now - TripMeta.KEEP_CLOSED_MS + 60_000)), emptyMap(), ravi, now, emptySet()).isEmpty())
        // An end date with nobody closing it: closes a day later, deleted 30 days after that.
        val ended = g.copy(autoMin = 0, expiresAt = now - TripMeta.GRACE_MS - TripMeta.KEEP_CLOSED_MS)
        assertEquals(listOf(TripWatch.Act.Purge("g-1")), TripWatch.due(listOf(ended), emptyMap(), ravi, now, emptySet()))
    }
}
