package com.bluemob.app.util

import com.bluemob.app.contacts.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon, 5f, 0)

    @Test
    fun distanceOfOneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distanceM(p(0.0, 0.0), p(1.0, 0.0)), 50.0)
    }

    @Test
    fun bearingEastIs90() {
        assertEquals(90.0, Geo.bearingDeg(p(0.0, 0.0), p(0.0, 1.0)), 0.1)
    }

    @Test
    fun formatsDistances() {
        assertEquals("40 m", Geo.formatDistance(42.0))
        assertEquals("1.8 km", Geo.formatDistance(1_800.0))
    }
}
