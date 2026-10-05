package com.bluemob.app.trail

import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.util.Geo
import com.bluemob.app.util.TimeText
import com.bluemob.app.util.cardinal
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where we think this phone is. With fresh GPS that's simply the fix. Without it, it's the last fix plus the
 * steps walked since then in the direction the compass showed (dead reckoning), with a growing margin of error.
 */
data class PositionEstimate(
    val lat: Double,
    val lon: Double,
    /** True when [lat]/[lon] is a fresh GPS fix. */
    val gps: Boolean,
    val fixLat: Double,
    val fixLon: Double,
    val fixAt: Long,
    val fixAccuracyM: Float,
    /** Metres walked since the fix, from the step counter. Null when the phone can't count steps. */
    val walkedM: Double?,
    /** Overall direction travelled since the fix (0 = north). */
    val travelBearingDeg: Double?,
    /** Which way the phone points now. */
    val headingDeg: Float?,
    /** How far off the estimate could be. */
    val uncertaintyM: Double,
    val at: Long,
) {
    /** Straight-line distance from the fix to the estimate. */
    val movedM: Double get() = Geo.distanceM(GeoPoint(fixLat, fixLon, 0f, 0), GeoPoint(lat, lon, 0f, 0))

    /** One or two plain sentences for people who are trying to find this person. */
    fun describe(now: Long = System.currentTimeMillis()): String = if (gps) {
        "GPS ${TimeText.ago(fixAt, now)}, accurate to ${fixAccuracyM.roundToInt()} m: ${Geo.formatLatLon(lat, lon)}." +
            (headingDeg?.let { " Facing ${it.roundToInt()}° ${cardinal(it.toDouble())}." } ?: "")
    } else {
        val last = "No GPS now. Last fix ${TimeText.ago(fixAt, now)} at ${Geo.formatLatLon(fixLat, fixLon)}"
        val since = when {
            walkedM != null && walkedM >= 5 && travelBearingDeg != null ->
                ", then walked about ${Geo.formatDistance(walkedM)} heading ${travelBearingDeg.roundToInt()}° ${cardinal(travelBearingDeg)}" +
                    (if (movedM >= 5) " (${Geo.formatDistance(movedM)} from the fix in a straight line)" else "")
            walkedM != null -> ", and has barely moved since"
            else -> ""
        }
        val facing = headingDeg?.let { " Facing ${it.roundToInt()}° ${cardinal(it.toDouble())}." } ?: ""
        "$last$since.$facing Estimated position ${Geo.formatLatLon(lat, lon)}, within about ${Geo.formatDistance(uncertaintyM)}."
    }
}

/** Keeps a running estimate from GPS fixes and steps. Pure logic, so it can be tested without a phone. */
class DeadReckoner(private val strideM: Double = 0.75) {
    private var fix: GeoPoint? = null
    private var east = 0.0
    private var north = 0.0
    private var walked = 0.0
    var countsSteps = false
        private set

    fun onFix(p: GeoPoint) {
        fix = p
        east = 0.0; north = 0.0; walked = 0.0
    }

    /** [steps] new steps taken while the phone pointed at [headingDeg]. */
    fun onSteps(steps: Int, headingDeg: Float?) {
        countsSteps = true
        if (steps <= 0 || fix == null) return
        val d = steps * strideM
        walked += d
        if (headingDeg != null) {
            val h = Math.toRadians(headingDeg.toDouble())
            east += d * sin(h)
            north += d * cos(h)
        }
    }

    fun estimate(now: Long, headingDeg: Float?): PositionEstimate? {
        val f = fix ?: return null
        val age = now - f.time
        val fresh = age < GPS_FRESH_MS && walked < 25
        val moved = sqrt(east * east + north * north)
        val bearing = if (moved >= 1) (Math.toDegrees(atan2(east, north)) + 360) % 360 else null
        val (lat, lon) = if (fresh || bearing == null) f.lat to f.lon else Geo.offset(f.lat, f.lon, bearing, moved)
        val uncertainty = when {
            fresh -> f.accuracyM.toDouble()
            countsSteps -> f.accuracyM + 10 + walked * 0.15
            // No step counter: they could have walked anywhere at an easy pace since the fix.
            else -> (f.accuracyM + age / 1000.0 * WALKING_MPS).coerceAtMost(20_000.0)
        }
        return PositionEstimate(
            lat, lon, fresh, f.lat, f.lon, f.time, f.accuracyM, if (countsSteps) walked else null,
            bearing, headingDeg, uncertainty, now,
        )
    }

    companion object {
        const val GPS_FRESH_MS = 120_000L
        const val WALKING_MPS = 1.2
    }
}

enum class Course(val title: String, val tip: String) {
    TOO_SHORT("Keep walking", "Walk about 60 m and BlueMob will tell you whether you're keeping a straight line."),
    STRAIGHT("Walking straight", "Your recent path is a straight line."),
    CURVING_LEFT("Curving to the left", "Your path bends left. Pick a landmark ahead in the direction you want and walk to it."),
    CURVING_RIGHT("Curving to the right", "Your path bends right. Pick a landmark ahead in the direction you want and walk to it."),
    WEAVING("Weaving", "Your path zigzags. That's normal on rough ground; check the overall direction below."),
    CIRCLING("You may be walking in a circle", "Stop. Lost people often loop back. Stay put, or follow the compass back to a saved spot."),
}

data class TrailStats(
    /** Length of the recent path looked at. */
    val pathM: Double,
    /** Straight-line distance between its ends. */
    val netM: Double,
    /** How much the direction changed between the first and second half: positive = right, negative = left. */
    val turnDeg: Double,
    /** Overall direction of the recent path. */
    val bearingDeg: Double?,
    val course: Course,
) {
    val straightness: Double get() = if (pathM > 0) netM / pathM else 0.0
}

object TrailMath {
    /** Looks at about the last [windowM] metres of the trail and decides whether the user walks straight. */
    fun analyse(points: List<GeoPoint>, windowM: Double = 400.0, minStepM: Double = 15.0): TrailStats {
        // Thin out GPS jitter: keep points at least minStepM apart, newest first.
        val kept = mutableListOf<GeoPoint>()
        var path = 0.0
        for (p in points.asReversed()) {
            val last = kept.lastOrNull()
            if (last == null) { kept += p; continue }
            val d = Geo.distanceM(last, p)
            if (d < minStepM) continue
            kept += p
            path += d
            if (path >= windowM) break
        }
        val pts = kept.asReversed()
        if (pts.size < 3 || path < 60) return TrailStats(path, if (pts.size > 1) Geo.distanceM(pts.first(), pts.last()) else 0.0, 0.0, null, Course.TOO_SHORT)
        // Total turning catches loops; comparing the first half's direction with the second half's tells a real bend
        // from a zigzag (whose turns cancel out).
        val totalTurn = pts.zipWithNext { a, b -> Geo.bearingDeg(a, b) }.zipWithNext { a, b -> ((b - a + 540) % 360) - 180 }.sum()
        val mid = pts[pts.size / 2]
        val turn = ((Geo.bearingDeg(mid, pts.last()) - Geo.bearingDeg(pts.first(), mid) + 540) % 360) - 180
        val net = Geo.distanceM(pts.first(), pts.last())
        val straight = net / path
        val course = when {
            abs(totalTurn) >= 300 || (straight < 0.35 && path > 200) -> Course.CIRCLING
            straight >= 0.85 && abs(turn) < 30 -> Course.STRAIGHT
            turn >= 30 -> Course.CURVING_RIGHT
            turn <= -30 -> Course.CURVING_LEFT
            else -> Course.WEAVING
        }
        return TrailStats(path, net, turn, Geo.bearingDeg(pts.first(), pts.last()), course)
    }
}
