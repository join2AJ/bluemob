package com.bluemob.app.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.pow

/** The phone's air-pressure sensor (many phones have one): one reading at a time, so it costs almost no battery. */
class Barometer(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    val available: Boolean get() = sensor != null

    /** One reading in hPa, or null (no sensor, or no answer within 5 s). */
    suspend fun read(): Float? {
        val s = sensor ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sensors?.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0])
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sensors?.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensors?.unregisterListener(l) }
            }
        }
    }
}

/**
 * Weather from air pressure. Climbing also lowers pressure (about 12 hPa per 100 m), so readings are turned into
 * what they'd be at sea level using the GPS altitude, and only readings with a known altitude count. A fall of 3 hPa
 * or more in 3 hours usually means bad weather within hours; 6 or more, a storm.
 */
object PressureTrend {
    data class Sample(val at: Long, val hPa: Float, val altM: Double?)

    enum class Level(val label: String, val advice: String) {
        STEADY("Steady", "No sign of a change in the weather."),
        RISING("Rising", "Weather settling or improving."),
        FALLING("Falling", "Weather may get worse later today. Keep an eye on the sky."),
        FALLING_FAST("Falling fast", "Bad weather likely within a few hours. Plan shelter, or head down early."),
        STORM("Storm coming", "A storm is likely soon. Get off ridges and summits, and find shelter now."),
    }

    const val WINDOW_MS = 3 * 3_600_000L
    const val KEEP_MS = 12 * 3_600_000L

    /** Pressure at sea level for a reading at [altM] metres. */
    fun seaLevel(hPa: Float, altM: Double): Double = hPa / (1 - altM / 44_330.0).pow(5.255)

    /**
     * Change over (about) the last 3 hours, in hPa, scaled to 3 hours; null without two readings with an altitude at
     * least an hour apart. Readings where we climbed or dropped more than 400 m are skipped (GPS height is too rough).
     */
    fun tendency(samples: List<Sample>, now: Long): Double? {
        val usable = samples.filter { it.altM != null && now - it.at <= WINDOW_MS + 30 * 60_000L }.sortedBy { it.at }
        val last = usable.lastOrNull() ?: return null
        val first = usable.firstOrNull { last.at - it.at >= 3_600_000L && abs(last.altM!! - it.altM!!) <= 400 } ?: return null
        val change = seaLevel(last.hPa, last.altM!!) - seaLevel(first.hPa, first.altM!!)
        return change * WINDOW_MS / (last.at - first.at)
    }

    fun level(t: Double?): Level = when {
        t == null -> Level.STEADY
        t <= -6 -> Level.STORM
        t <= -3 -> Level.FALLING_FAST
        t <= -1.5 -> Level.FALLING
        t >= 1.5 -> Level.RISING
        else -> Level.STEADY
    }

    fun toJson(l: List<Sample>) = JSONArray().apply { l.forEach { put(JSONArray().put(it.at).put(it.hPa.toDouble()).put(it.altM ?: org.json.JSONObject.NULL)) } }.toString()
    fun fromJson(s: String?): List<Sample> = runCatching {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).map { i -> a.getJSONArray(i).let { p -> Sample(p.getLong(0), p.getDouble(1).toFloat(), if (p.isNull(2)) null else p.getDouble(2)) } }
    }.getOrDefault(emptyList())
}

/**
 * Daylight against the walk back: how long until sunset, how long to walk to base camp (about 4 km/h on the flat,
 * plus an hour per 600 m of climb), and whether it's time to turn back (with half an hour to spare).
 */
object Daylight {
    data class Plan(val lightLeftMs: Long, val walkMs: Long?, val turnBack: Boolean)

    fun walkMs(distM: Double, climbM: Double = 0.0): Long = ((distM / 4_000.0 + maxOf(climbM, 0.0) / 600.0) * 3_600_000L).toLong()

    fun plan(sunsetAt: Long?, now: Long, distToBaseM: Double?, climbM: Double = 0.0): Plan? {
        val set = sunsetAt ?: return null
        val left = set - now
        if (left <= 0) return null
        val walk = distToBaseM?.let { walkMs(it, climbM) }
        return Plan(left, walk, walk != null && distToBaseM > 300 && walk + 30 * 60_000L >= left)
    }
}
