package com.bluemob.app.trail

import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.util.Geo

/** How someone was probably moving, judged by speed. */
enum class TravelMode(val label: String, val emoji: String, val maxKmh: Double) {
    STILL("Stopped", "⏸", 1.0),
    FOOT("On foot", "🚶", 7.5),
    CYCLE("Cycle", "🚲", 25.0),
    BIKE("Motorbike", "🏍", 60.0),
    CAR("Car or bus", "🚗", Double.MAX_VALUE);

    companion object {
        fun of(kmh: Double): TravelMode = entries.first { kmh <= it.maxKmh }
    }
}

/** Speed bands for colouring the trail and chart, so the speed can be read at a glance. */
enum class SpeedBand(val label: String, val maxKmh: Double) {
    STOPPED("Stopped", 1.0),
    UNDER_20("Under 20 km/h", 20.0),
    FROM_20("20–40 km/h", 40.0),
    FROM_40("40–60 km/h", 60.0),
    FROM_60("60–80 km/h", 80.0),
    OVER_80("Over 80 km/h", Double.MAX_VALUE);

    companion object {
        fun of(kmh: Double): SpeedBand = entries.first { kmh <= it.maxKmh }
    }
}

/**
 * One stretch between two trail points: when, how fast, and how they were probably travelling. [gap] marks a stretch
 * whose speed can't be trusted (a GPS jump: faster than any vehicle), left out of the speeds and modes.
 */
data class Leg(val from: TrailPoint, val to: TrailPoint, val metres: Double, val seconds: Double, val kmh: Double,
               val mode: TravelMode = TravelMode.of(kmh), val gap: Boolean = false) {
    val band get() = SpeedBand.of(kmh)
}

data class TripAnalysis(
    val legs: List<Leg>,
    val distanceM: Double,
    val movingSeconds: Double,
    val maxKmh: Double,
    val avgMovingKmh: Double,
    /** Seconds spent in each mode (stops included). */
    val timeByMode: Map<TravelMode, Double>,
    /** The mode most of the moving time was in (or what the user said it was). */
    val likelyMode: TravelMode?,
    val climbM: Double,
    /** Seconds in each speed band (stops included). */
    val timeBySpeed: Map<SpeedBand, Double> = emptyMap(),
    /** True when the user said how they travelled. */
    val corrected: Boolean = false,
) {
    companion object {
        /**
         * Speeds from the GPS where it gave one, otherwise from distance over time. A 3-point median smooths GPS jumps,
         * so one bad fix doesn't turn a walk into a car ride.
         */
        /** Faster than this is a GPS jump, not travel (no road vehicle; planes aren't tracked). */
        const val IMPOSSIBLE_KMH = 250.0

        /** [override]: how the user says they travelled; every moving stretch takes that mode. */
        fun of(points: List<TrailPoint>, override: TravelMode? = null): TripAnalysis {
            val raw = points.zipWithNext().mapNotNull { (a, b) ->
                val s = (b.time - a.time) / 1000.0
                if (s <= 0) return@mapNotNull null
                val m = Geo.distanceM(GeoPoint(a.lat, a.lon, 0f, 0), GeoPoint(b.lat, b.lon, 0f, 0))
                val gps = b.speedMps?.takeIf { it >= 0 }?.toDouble()
                val kmh = (gps ?: (m / s)) * 3.6
                if (kmh > IMPOSSIBLE_KMH) Leg(a, b, m, s, 0.0, TravelMode.STILL, gap = true) else Leg(a, b, m, s, kmh)
            }
            val good = raw.filter { !it.gap }
            val smoothed = modes(good.mapIndexed { i, l ->
                val near = good.subList((i - 1).coerceAtLeast(0), (i + 2).coerceAtMost(good.size)).map { it.kmh }.sorted()
                l.copy(kmh = near[near.size / 2])
            }).let { list -> if (override == null) list else list.map { if (it.kmh > TravelMode.STILL.maxKmh) it.copy(mode = override) else it } }
            // Back in time order, with the GPS jumps where they were (shown, but not counted).
            val byStart = smoothed.associateBy { it.from.time }
            val legs = raw.map { if (it.gap) it else byStart[it.from.time] ?: it }
            val counted = legs.filter { !it.gap }
            val moving = counted.filter { it.mode != TravelMode.STILL }
            val movingS = moving.sumOf { it.seconds }
            val byMode = counted.groupBy { it.mode }.mapValues { (_, l) -> l.sumOf { it.seconds } }
            var climb = 0.0
            points.mapNotNull { it.altitudeM }.zipWithNext { a, b -> if (b - a > 2) climb += b - a }
            return TripAnalysis(
                legs = legs, distanceM = counted.sumOf { it.metres }, movingSeconds = movingS,
                maxKmh = counted.maxOfOrNull { it.kmh } ?: 0.0,
                avgMovingKmh = if (movingS > 0) moving.sumOf { it.metres } / movingS * 3.6 else 0.0,
                timeByMode = byMode,
                likelyMode = override ?: byMode.filterKeys { it != TravelMode.STILL }.maxByOrNull { it.value }?.key,
                climbM = climb,
                timeBySpeed = counted.groupBy { it.band }.mapValues { (_, l) -> l.sumOf { it.seconds } },
                corrected = override != null,
            )
        }

        /** Slower than this for [CHANGE_S] or more is a stop where someone could change vehicle. */
        private const val CHANGE_S = 180.0
        /** A ride shorter than this (in fast time) is more likely GPS noise than a vehicle. */
        private const val MIN_RIDE_S = 60.0

        /**
         * One vehicle per ride. Nobody switches from a car to a cycle while moving: a car slows at a junction, a cycle
         * speeds downhill, but the vehicle only changes where they stopped or walked for a few minutes. So the trip is
         * cut at those stops, and each ride in between gets one vehicle, from the speed it kept up most of the time
         * (85th percentile, so one fast stretch or one slow junction doesn't decide it). Pauses inside a ride (traffic
         * lights) show as stopped.
         */
        fun modes(legs: List<Leg>): List<Leg> {
            if (legs.isEmpty()) return legs
            val slow = legs.map { it.kmh <= TravelMode.FOOT.maxKmh }
            // Mark the legs that belong to a change point: a long slow run, or slow time at the start or end.
            val isBreak = BooleanArray(legs.size)
            var i = 0
            while (i < legs.size) {
                if (!slow[i]) { i++; continue }
                var j = i
                var secs = 0.0
                while (j < legs.size && slow[j]) { secs += legs[j].seconds; j++ }
                if (secs >= CHANGE_S || i == 0 || j == legs.size) for (k in i until j) isBreak[k] = true
                i = j
            }
            val out = legs.toMutableList()
            i = 0
            while (i < legs.size) {
                if (isBreak[i]) { out[i] = legs[i].copy(mode = TravelMode.of(legs[i].kmh)); i++; continue }
                var j = i
                while (j < legs.size && !isBreak[j]) j++
                val ride = legs.subList(i, j)
                val fastS = ride.filter { it.kmh > TravelMode.FOOT.maxKmh }.sumOf { it.seconds }
                val vehicle = if (fastS < MIN_RIDE_S) null else {
                    val moving = ride.filter { it.kmh > TravelMode.STILL.maxKmh }.sortedBy { it.kmh }
                    val total = moving.sumOf { it.seconds }
                    var acc = 0.0
                    val p85 = moving.firstOrNull { acc += it.seconds; acc >= total * 0.85 }?.kmh ?: moving.last().kmh
                    TravelMode.of(p85).takeIf { it > TravelMode.FOOT } ?: TravelMode.CYCLE
                }
                for (k in i until j) {
                    val l = legs[k]
                    out[k] = l.copy(mode = when {
                        vehicle == null -> TravelMode.of(l.kmh)
                        l.kmh <= TravelMode.STILL.maxKmh -> TravelMode.STILL
                        else -> vehicle
                    })
                }
                i = j
            }
            return out
        }

        /** Where on the trail we are at [time]: between two points, interpolated. */
        fun positionAt(points: List<TrailPoint>, time: Long): Pair<Double, Double>? {
            if (points.isEmpty()) return null
            if (time <= points.first().time) return points.first().lat to points.first().lon
            val i = points.indexOfFirst { it.time >= time }
            if (i < 0) return points.last().lat to points.last().lon
            val a = points[i - 1]; val b = points[i]
            val f = if (b.time == a.time) 1.0 else (time - a.time).toDouble() / (b.time - a.time)
            return (a.lat + (b.lat - a.lat) * f) to (a.lon + (b.lon - a.lon) * f)
        }
    }
}
