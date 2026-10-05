package com.bluemob.app.trail

import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailMathTest {
    private val start = GeoPoint(30.0869, 78.2676, 5f, 0)

    /** Walks [steps] legs of [legM] metres, turning [turnPerLeg] degrees (positive = right) after each. */
    private fun walk(steps: Int, legM: Double, turnPerLeg: Double, startBearing: Double = 0.0): List<GeoPoint> {
        val out = mutableListOf(start)
        var bearing = startBearing
        repeat(steps) { i ->
            val last = out.last()
            val (lat, lon) = Geo.offset(last.lat, last.lon, bearing, legM)
            out += GeoPoint(lat, lon, 5f, (i + 1) * 10_000L)
            bearing += turnPerLeg
        }
        return out
    }

    @Test fun straightLineIsStraight() {
        val s = TrailMath.analyse(walk(25, 20.0, 0.0, 45.0))
        assertEquals(Course.STRAIGHT, s.course)
        assertEquals(45.0, s.bearingDeg!!, 1.0)
        assertTrue(s.straightness > 0.98)
    }

    @Test fun gentleRightBendIsCurvingRight() = assertEquals(Course.CURVING_RIGHT, TrailMath.analyse(walk(20, 20.0, 6.0)).course)

    @Test fun leftBendIsCurvingLeft() = assertEquals(Course.CURVING_LEFT, TrailMath.analyse(walk(20, 20.0, -6.0)).course)

    @Test fun fullLoopIsCircling() = assertEquals(Course.CIRCLING, TrailMath.analyse(walk(24, 20.0, 15.0)).course)

    @Test fun zigzagIsWeaving() {
        val pts = mutableListOf(start)
        repeat(20) { i ->
            val (lat, lon) = Geo.offset(pts.last().lat, pts.last().lon, if (i % 2 == 0) 50.0 else 310.0, 20.0)
            pts += GeoPoint(lat, lon, 5f, i * 1000L)
        }
        assertEquals(Course.WEAVING, TrailMath.analyse(pts).course)
    }

    @Test fun shortTrailAsksToKeepWalking() = assertEquals(Course.TOO_SHORT, TrailMath.analyse(walk(2, 20.0, 0.0)).course)

    @Test fun gpsJitterWhileStandingStillIsNotAWalk() {
        val jitter = (0 until 50).map { i -> GeoPoint(start.lat + (i % 3) * 0.00003, start.lon + (i % 2) * 0.00003, 8f, i * 1000L) }
        assertEquals(Course.TOO_SHORT, TrailMath.analyse(jitter).course)
    }

    @Test fun offsetMovesTheRightDistanceAndDirection() {
        val (lat, lon) = Geo.offset(start.lat, start.lon, 120.0, 500.0)
        val p = GeoPoint(lat, lon, 0f, 0)
        assertEquals(500.0, Geo.distanceM(start, p), 1.0)
        assertEquals(120.0, Geo.bearingDeg(start, p), 0.5)
    }

    @Test fun freshFixIsGps() {
        val r = DeadReckoner().apply { onFix(start.copy(time = 1_000_000)) }
        val e = r.estimate(1_030_000, 90f)!!
        assertTrue(e.gps)
        assertEquals(start.lat, e.lat, 1e-9)
        assertTrue(e.describe(1_030_000).startsWith("GPS"))
    }

    @Test fun stepsCarryThePositionWhenGpsDropsOut() {
        val r = DeadReckoner(strideM = 0.75).apply { onFix(start.copy(time = 0)) }
        r.onSteps(400, 90f) // 300 m east
        val e = r.estimate(10 * 60_000L, 90f)!!
        assertFalse(e.gps)
        assertEquals(300.0, e.walkedM!!, 0.01)
        assertEquals(90.0, e.travelBearingDeg!!, 0.5)
        assertEquals(300.0, Geo.distanceM(start, GeoPoint(e.lat, e.lon, 0f, 0)), 2.0)
        assertEquals(5 + 10 + 45.0, e.uncertaintyM, 0.01)
        val text = e.describe(10 * 60_000L)
        assertTrue(text, text.contains("No GPS now") && text.contains("walked about 300 m heading 90° E"))
    }

    @Test fun walkingThereAndBackEndsNearTheFix() {
        val r = DeadReckoner().apply { onFix(start) }
        r.onSteps(200, 0f)
        r.onSteps(200, 180f)
        val e = r.estimate(5 * 60_000L, 180f)!!
        assertEquals(300.0, e.walkedM!!, 0.01)
        assertTrue(e.movedM < 1.0)
    }

    @Test fun withoutAStepCounterUncertaintyGrowsWithTime() {
        val r = DeadReckoner().apply { onFix(start.copy(time = 0)) }
        val e = r.estimate(20 * 60_000L, null)!!
        assertNull(e.walkedM)
        assertEquals(5 + 20 * 60 * DeadReckoner.WALKING_MPS, e.uncertaintyM, 0.01)
    }
}
