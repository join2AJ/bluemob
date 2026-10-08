package com.bluemob.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SunMoonTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test fun delhiSunriseAndSunsetInMarch() {
        // New Delhi, 20 March 2025: sunrise about 06:24 IST (00:54 UTC), sunset about 18:32 IST (13:02 UTC).
        val t = SunMoon.times(utc(2025, 3, 20, 6), 28.61, 77.21)
        assertEquals(utc(2025, 3, 20, 0, 54).toDouble(), t.rise!!.toDouble(), 6 * 60_000.0)
        assertEquals(utc(2025, 3, 20, 13, 2).toDouble(), t.set!!.toDouble(), 6 * 60_000.0)
    }

    @Test fun sunIsInTheSouthAtNoonAndEastInTheMorning() {
        val noon = SunMoon.sun(utc(2025, 3, 20, 6, 50), 28.61, 77.21) // about local solar noon
        assertEquals(180.0, noon.azimuthDeg, 12.0)
        assertTrue(noon.altitudeDeg > 55)
        val morning = SunMoon.sun(utc(2025, 3, 20, 2), 28.61, 77.21)
        assertEquals(95.0, morning.azimuthDeg, 15.0)
    }

    @Test fun moonPhaseOfAKnownFullMoon() {
        // Full moon on 14 March 2025, 06:55 UTC.
        assertEquals(0.5, SunMoon.moonPhase(utc(2025, 3, 14, 7)), 0.03)
    }
}

class RetraceTest {
    @Test fun pointsBackAlongTheTrail() {
        val trail = (0..10).map { com.bluemob.app.contacts.GeoPoint(28.0 + it * 0.0001, 77.0, 5f, it * 1000L) } // about 11 m apart, heading north
        val me = trail[8]
        val p = com.bluemob.app.trail.TrailMath.retracePoint(trail, me)!!
        assertTrue("goes back south", p.lat < me.lat)
        assertTrue(com.bluemob.app.util.Geo.distanceM(p, me) >= 40.0)
        assertEquals(trail.first(), com.bluemob.app.trail.TrailMath.retracePoint(trail, trail[1]))
    }
}
