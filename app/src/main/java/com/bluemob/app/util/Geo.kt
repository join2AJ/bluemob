package com.bluemob.app.util

import com.bluemob.app.contacts.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass bearing from [a] to [b] in degrees, 0 = north. */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** The point [distanceM] metres from ([lat], [lon]) towards [bearingDeg]. */
    fun offset(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val d = distanceM / EARTH_RADIUS_M
        val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 = kotlin.math.asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return Math.toDegrees(lat2) to ((Math.toDegrees(lon2) + 540) % 360 - 180)
    }

    /** "30.0869 N, 78.2676 E" */
    fun formatLatLon(lat: Double, lon: Double): String =
        "%.4f %s, %.4f %s".format(kotlin.math.abs(lat), if (lat >= 0) "N" else "S", kotlin.math.abs(lon), if (lon >= 0) "E" else "W")

    fun formatDistance(m: Double): String = when {
        m < 10 -> "a few m"
        m < 1000 -> "${(m / 5).roundToInt() * 5} m"
        m < 10_000 -> "%.1f km".format(m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }
}

object TimeText {
    fun ago(then: Long, now: Long = System.currentTimeMillis()): String {
        val s = (now - then).coerceAtLeast(0) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            s < 7 * 86_400 -> "${s / 86_400} d ago"
            else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(then))
        }
    }
}

/** "3F9A1C2B7D4E8A01" → "3F9A 1C2B 7D4E 8A01". */
fun formatId(id: String): String = id.uppercase().chunked(4).joinToString(" ")

/** Short form shown next to a name when two people share it: "#3F9A". */
fun shortId(id: String): String = "#" + id.take(4).uppercase()

/** 0° → "N", 90° → "E" … */
fun cardinal(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 / 45.0).let { Math.round(it).toInt() % 8 }]
