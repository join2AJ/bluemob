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

/** One stretch between two trail points: when, how fast, and the mode that speed suggests. */
data class Leg(val from: TrailPoint, val to: TrailPoint, val metres: Double, val seconds: Double, val kmh: Double) {
    val mode get() = TravelMode.of(kmh)
}

data class TripAnalysis(
    val legs: List<Leg>,
    val distanceM: Double,
    val movingSeconds: Double,
    val maxKmh: Double,
    val avgMovingKmh: Double,
    /** Seconds spent in each mode (stops included). */
    val timeByMode: Map<TravelMode, Double>,
    /** The mode most of the moving time was in. */
    val likelyMode: TravelMode?,
    val climbM: Double,
) {
    companion object {
        /**
         * Speeds from the GPS where it gave one, otherwise from distance over time. A 3-point median smooths GPS jumps,
         * so one bad fix doesn't turn a walk into a car ride.
         */
        fun of(points: List<TrailPoint>): TripAnalysis {
            val raw = points.zipWithNext().mapNotNull { (a, b) ->
                val s = (b.time - a.time) / 1000.0
                if (s <= 0) return@mapNotNull null
                val m = Geo.distanceM(GeoPoint(a.lat, a.lon, 0f, 0), GeoPoint(b.lat, b.lon, 0f, 0))
                val gps = b.speedMps?.takeIf { it >= 0 }?.toDouble()
                Leg(a, b, m, s, (gps ?: (m / s)) * 3.6)
            }
            val legs = raw.mapIndexed { i, l ->
                val near = raw.subList((i - 1).coerceAtLeast(0), (i + 2).coerceAtMost(raw.size)).map { it.kmh }.sorted()
                l.copy(kmh = near[near.size / 2])
            }
            val moving = legs.filter { it.mode != TravelMode.STILL }
            val movingS = moving.sumOf { it.seconds }
            val byMode = legs.groupBy { it.mode }.mapValues { (_, l) -> l.sumOf { it.seconds } }
            var climb = 0.0
            points.mapNotNull { it.altitudeM }.zipWithNext { a, b -> if (b - a > 2) climb += b - a }
            return TripAnalysis(
                legs = legs, distanceM = legs.sumOf { it.metres }, movingSeconds = movingS,
                maxKmh = legs.maxOfOrNull { it.kmh } ?: 0.0,
                avgMovingKmh = if (movingS > 0) moving.sumOf { it.metres } / movingS * 3.6 else 0.0,
                timeByMode = byMode,
                likelyMode = byMode.filterKeys { it != TravelMode.STILL }.maxByOrNull { it.value }?.key,
                climbM = climb,
            )
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
