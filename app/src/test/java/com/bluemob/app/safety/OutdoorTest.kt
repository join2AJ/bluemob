package com.bluemob.app.safety

import com.bluemob.app.chat.Rich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdoorTest {
    private val h = 3_600_000L
    private val now = 100 * 24 * h

    @Test fun fallingPressureAtTheSamePlaceWarns() {
        val s = listOf(PressureTrend.Sample(now - 3 * h, 1010f, 500.0), PressureTrend.Sample(now - h, 1007f, 500.0), PressureTrend.Sample(now, 1005f, 500.0))
        val t = PressureTrend.tendency(s, now)!!
        assertEquals(-5.3, t, 0.2) // the 5 hPa drop at 500 m is about 5.3 at sea level
        assertEquals(PressureTrend.Level.FALLING_FAST, PressureTrend.level(t))
    }

    @Test fun climbingIsNotBadWeather() {
        // 300 m up in 2 hours: raw pressure falls about 33 hPa, but at sea level it's the same.
        val p0 = 1013.0
        fun at(alt: Double) = (p0 * Math.pow(1 - alt / 44_330.0, 5.255)).toFloat()
        val s = listOf(PressureTrend.Sample(now - 2 * h, at(2_000.0), 2_000.0), PressureTrend.Sample(now, at(2_300.0), 2_300.0))
        assertEquals(PressureTrend.Level.STEADY, PressureTrend.level(PressureTrend.tendency(s, now)))
    }

    @Test fun noAltitudeOrTooShortMeansNoTrend() {
        assertNull(PressureTrend.tendency(listOf(PressureTrend.Sample(now - 3 * h, 1010f, null), PressureTrend.Sample(now, 1000f, null)), now))
        assertNull(PressureTrend.tendency(listOf(PressureTrend.Sample(now - 30 * 60_000L, 1010f, 0.0), PressureTrend.Sample(now, 1000f, 0.0)), now))
    }

    @Test fun readingsSurviveSaving() {
        val s = listOf(PressureTrend.Sample(now, 1001.5f, 320.0), PressureTrend.Sample(now + 1, 1001f, null))
        assertEquals(s, PressureTrend.fromJson(PressureTrend.toJson(s)))
    }

    @Test fun turnBackWhenTheWalkTakesAlmostAllTheLightLeft() {
        // 6 km back is 1 h 30 min of walking; with 1 h 50 min of light left, that leaves less than half an hour spare.
        assertTrue(Daylight.plan(now + 110 * 60_000L, now, 6_000.0)!!.turnBack)
        assertFalse(Daylight.plan(now + 4 * h, now, 6_000.0)!!.turnBack)
        assertNull(Daylight.plan(now - 1, now, 6_000.0))
        assertEquals(2 * h, Daylight.walkMs(4_000.0, 600.0))
    }

    @Test fun pinsTravelInGroupMessages() {
        val f = Rich.pinField("water", "Spring | left", 30.123456, 78.5)
        assertEquals(Triple("water", "Spring   left", 30.123456 to 78.5), Rich.pinParts(f))
        assertEquals(f, Rich.decode(Rich(group = "g-1", pin = f).encode())!!.pin)
        assertNull(Rich.pinParts("water|x|95|10"))
    }
}
