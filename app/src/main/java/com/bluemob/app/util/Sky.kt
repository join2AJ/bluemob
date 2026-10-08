package com.bluemob.app.util

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is, when it rises and sets, and the moon's phase: all worked out on the phone, no internet. */
object SunMoon {
    data class SunPosition(val azimuthDeg: Double, val altitudeDeg: Double)
    /** Today's sunrise and sunset (null when the sun doesn't rise or set, near the poles). */
    data class SunTimes(val rise: Long?, val set: Long?)

    private const val RAD = PI / 180
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val OBLIQUITY = RAD * 23.4397

    private fun toDays(ms: Long) = ms / DAY_MS - 0.5 + J1970 - J2000
    private fun fromJulian(j: Double) = ((j + 0.5 - J1970) * DAY_MS).toLong()
    private fun meanAnomaly(d: Double) = RAD * (357.5291 + 0.98560028 * d)
    private fun eclipticLongitude(m: Double): Double {
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        return m + c + RAD * 102.9372 + PI
    }
    private fun declination(l: Double) = asin(sin(OBLIQUITY) * sin(l))
    private fun rightAscension(l: Double) = atan2(sin(l) * cos(OBLIQUITY), cos(l))
    private fun siderealTime(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw

    /** The sun's compass direction (0 = north, clockwise) and height above the horizon. */
    fun sun(atMs: Long, lat: Double, lon: Double): SunPosition {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(atMs)
        val l = eclipticLongitude(meanAnomaly(d))
        val dec = declination(l)
        val h = siderealTime(d, lw) - rightAscension(l)
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        // atan2 gives the angle from south, westward: turn it into a compass bearing.
        return SunPosition(((az / RAD) + 180 + 360) % 360, alt / RAD)
    }

    /** Sunrise and sunset for the day that [atMs] falls in (by the sun at that place, not the phone's time zone). */
    fun times(atMs: Long, lat: Double, lon: Double): SunTimes {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(atMs)
        val n = Math.round(d - 0.0009 - lw / (2 * PI)).toDouble()
        val ds = 0.0009 + lw / (2 * PI) + n
        val m = meanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l)
        val jNoon = J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
        val cosW = (sin(RAD * -0.833) - sin(phi) * sin(dec)) / (cos(phi) * cos(dec))
        if (cosW < -1 || cosW > 1) return SunTimes(null, null)
        val w = acos(cosW)
        val jSet = J2000 + 0.0009 + (w + lw) / (2 * PI) + n + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
        val jRise = jNoon - (jSet - jNoon)
        return SunTimes(fromJulian(jRise), fromJulian(jSet))
    }

    /** 0 = new moon, 0.5 = full, back to 1. */
    fun moonPhase(atMs: Long): Double {
        val known = 947_182_440_000L // a new moon: 6 Jan 2000, 18:14 UTC
        val synodic = 29.530588853 * DAY_MS
        val p = ((atMs - known) % synodic) / synodic
        return if (p < 0) p + 1 else p
    }

    fun moonWords(phase: Double): Pair<String, String> = when {
        phase < 0.03 || phase > 0.97 -> "🌑" to "New moon: dark night"
        phase < 0.22 -> "🌒" to "Waxing crescent: little light, sets early"
        phase < 0.28 -> "🌓" to "First quarter: light until about midnight"
        phase < 0.47 -> "🌔" to "Waxing gibbous: bright evening"
        phase < 0.53 -> "🌕" to "Full moon: bright all night"
        phase < 0.72 -> "🌖" to "Waning gibbous: rises late, bright after"
        phase < 0.78 -> "🌗" to "Last quarter: light after midnight"
        else -> "🌘" to "Waning crescent: dark evening"
    }

    /** "1 h 20 min" */
    fun span(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }

    @Suppress("unused") private fun frac(x: Double) = x - floor(x)
}
