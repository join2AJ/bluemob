package com.bluemob.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCareTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun lakeLouiseLevels() {
        assertEquals(AltitudeCheck.Level.WELL, AltitudeCheck.level(AltitudeCheck.Answers(0, 2, 2, 2)))   // no headache: not AMS
        assertEquals(AltitudeCheck.Level.WELL, AltitudeCheck.level(AltitudeCheck.Answers(1, 1, 0, 0)))
        assertEquals(AltitudeCheck.Level.MILD, AltitudeCheck.level(AltitudeCheck.Answers(1, 1, 1, 0)))
        assertEquals(AltitudeCheck.Level.MODERATE, AltitudeCheck.level(AltitudeCheck.Answers(2, 2, 2, 0)))
        assertEquals(AltitudeCheck.Level.SEVERE, AltitudeCheck.level(AltitudeCheck.Answers(3, 3, 2, 2)))
        assertEquals(AltitudeCheck.Level.SEVERE, AltitudeCheck.level(AltitudeCheck.Answers(0, 0, 0, 0, danger = true)))
    }

    @Test fun checkTravelsAndKeepsItsLevel() {
        val c = AmsCheck(7, 2, false, now, 3_800)
        val back = AmsCheck.decode(c.encode())!!
        assertEquals(c, back)
        assertEquals(AltitudeCheck.Level.MODERATE, back.level)
        assertNull(AmsCheck.decode("nonsense"))
        val r = Rich(group = "g-1", ams = c, health = "Blood group O+").encode()
        val d = Rich.decode(r)!!
        assertEquals(c, d.ams); assertEquals("Blood group O+", d.health); assertTrue(d.isControl)
    }

    @Test fun groupKeepsCareFieldsAcrossSaves() {
        val h = listOf(Headcount(now, listOf("a", "b"), listOf("c")))
        assertEquals(h, Headcount.fromJson(Headcount.toJson(h)))
        val m = mapOf("a" to AmsCheck(4, 1, false, now))
        assertEquals(m, AmsCheck.mapFromJson(AmsCheck.mapToJson(m)))
        assertTrue(TripMeta.fromJson(TripMeta(owner = "o", amsDaily = true).toJson()).amsDaily)
    }

    private fun trip(vararg extra: (ChatGroup) -> ChatGroup) = extra.fold(ChatGroup("g-1", "Trek", mapOf("b" to "Bala", "c" to "Chitra"), now - day, trip = true, owner = "a")) { g, f -> f(g) }

    @Test fun altitudeReminderOncePerDayFromSevenAm() {
        val morning = TripWatch.localDay(now) * day - java.util.TimeZone.getDefault().getOffset(now) + 8 * 3_600_000L
        val g = trip({ it.copy(amsDaily = true) })
        val due = TripWatch.due(listOf(g), emptyMap(), "b", morning, emptySet()).filterIsInstance<TripWatch.Act.AltitudeDue>()
        assertEquals(1, due.size)
        assertTrue(TripWatch.due(listOf(g), emptyMap(), "b", morning, setOf(due.single().key)).none { it is TripWatch.Act.AltitudeDue })
        assertTrue(TripWatch.due(listOf(g.copy(myAmsAt = morning - 60_000)), emptyMap(), "b", morning, emptySet()).none { it is TripWatch.Act.AltitudeDue })
        assertTrue(TripWatch.due(listOf(g.copy(amsDaily = false)), emptyMap(), "b", morning, emptySet()).none { it is TripWatch.Act.AltitudeDue })
    }

    @Test fun adminHearsAboutModerateAltitudeSickness() {
        val g = trip({ it.copy(amsChecks = mapOf("b" to AmsCheck(7, 2, false, now - 60_000), "c" to AmsCheck(2, 1, false, now - 60_000))) })
        val alerts = TripWatch.due(listOf(g), emptyMap(), "a", now, emptySet()).filterIsInstance<TripWatch.Act.AltitudeAlert>()
        assertEquals(listOf("b"), alerts.map { it.member })
    }

    @Test fun healthCardsAreErasedWhenTheTripEnds() {
        val open = trip({ it.copy(health = mapOf("b" to "O+")) })
        assertTrue(TripWatch.due(listOf(open), emptyMap(), "a", now, emptySet()).none { it is TripWatch.Act.ForgetCare })
        val ended = open.copy(closedAt = now - 1)
        assertEquals(listOf(TripWatch.Act.ForgetCare("g-1")), TripWatch.due(listOf(ended), emptyMap(), "a", now, emptySet()).filterIsInstance<TripWatch.Act.ForgetCare>())
    }

    @Test fun reportHasThePlanAndHeadcountsButNoHealth() {
        val g = trip({ it.copy(health = mapOf("b" to "Allergic to penicillin"), headcounts = listOf(Headcount(now, listOf("a", "b"), listOf("c")))) })
        val names = mapOf("a" to "Asha", "b" to "Bala", "c" to "Chitra")
        val text = TripReport.text(TripReport.build(g, emptyList(), null, "a", { names[it] ?: "?" }, now) { "t$it" })
        assertTrue(text.contains("2 of 3 here · missing: Chitra"))
        assertTrue(text.contains("Name: Trek"))
        assertFalse(text.contains("penicillin"))
    }
}
