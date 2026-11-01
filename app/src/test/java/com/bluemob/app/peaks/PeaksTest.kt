package com.bluemob.app.peaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeaksTest {
    @Test fun bearingsPointTheRightWay() {
        assertEquals(0.0, Peaks.bearingDeg(30.0, 78.0, 31.0, 78.0), 0.01)
        assertEquals(90.0, Peaks.bearingDeg(0.0, 78.0, 0.0, 79.0), 0.01)
        assertEquals(180.0, Peaks.bearingDeg(31.0, 78.0, 30.0, 78.0), 0.01)
    }

    @Test fun distanceOfOneDegreeOfLatitude() = assertEquals(111_195.0, Peaks.distanceM(30.0, 78.0, 31.0, 78.0), 50.0)

    @Test fun farPeaksSinkBelowTheHorizonWithTheEarthsCurve() {
        // 1,000 m higher at 10 km: well above the horizon. 100 m higher at 100 km: the curve hides it.
        assertTrue(Peaks.elevationDeg(1_000.0, 10_000.0) > 5)
        assertTrue(Peaks.elevationDeg(100.0, 100_000.0) < 0)
    }

    @Test fun aroundListsNearestFirstWithinRange() {
        val near = Peak("Near", 31.05, 78.18, 4_000); val far = Peak("Far", 31.5, 78.18, 6_000); val tooFar = Peak("Too far", 33.0, 78.18, 7_000)
        val seen = Peaks.around(listOf(far, tooFar, near), 31.0, 78.18, 3_000.0, 100_000.0)
        assertEquals(listOf("Near", "Far"), seen.map { it.peak.name })
        assertEquals(0.0, seen.first().bearingDeg, 0.5)
    }

    @Test fun readsOpenStreetMapAnswers() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":31.0094,"lon":78.5528,"tags":{"natural":"peak","name":"बंदरपूंछ","name:en":"Bandarpunch","ele":"6316"}},
            {"type":"node","id":2,"lat":31.1,"lon":78.6,"tags":{"natural":"peak","name":"No height"}},
            {"type":"node","id":3,"lat":31.2,"lon":78.7,"tags":{"natural":"peak","name":"Feet","ele":"13812 ft"}}]}"""
        val p = Peaks.parseOverpass(json)
        assertEquals(listOf("Bandarpunch", "Feet"), p.map { it.name })
        assertEquals(6316, p[0].eleM); assertEquals(4209, p[1].eleM)
    }

    @Test fun heightsInManyForms() {
        assertEquals(4210, Peaks.parseEle("4,210 m")); assertEquals(3048, Peaks.parseEle("10000 ft")); assertNull(Peaks.parseEle("high"))
    }

    @Test fun queryAsksForNamedPeaksAroundARoughPosition() {
        val q = Peaks.overpassQuery(31.02151, 78.17183, 150_000)
        assertTrue(q.contains("\"natural\"=\"peak\"") && q.contains("around:150000,31.02,78.17"))
    }

    @Test fun angleDiffGoesTheShortWay() { assertEquals(-20.0, Peaks.angleDiff(350.0, 10.0), 1e-9); assertEquals(20.0, Peaks.angleDiff(10.0, 350.0), 1e-9) }
}
