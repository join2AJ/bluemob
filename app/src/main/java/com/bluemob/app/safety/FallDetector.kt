package com.bluemob.app.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bluemob.app.BlueMobApp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Spots a fall in the accelerometer's readings (m/s², gravity included):
 * 1. **free fall**: almost no acceleration for at least [minFallMs];
 * 2. **impact** within a second after: a jolt above [impact];
 * 3. **stillness**: after the bounce settles, [stillMs] of the phone lying still (close to plain gravity).
 * Someone who gets up, picks the phone up, or walks on breaks step 3, so normal drops of a phone on a table that's
 * then picked up don't count. Pure logic, so it's tested without a phone.
 */
class FallLogic(
    private val freeFall: Float = 3.5f,
    private val minFallMs: Long = 60,
    private val impact: Float = 24f,
    private val settleMs: Long = 1_500,
    private val stillMs: Long = 10_000,
    private val stillTolerance: Float = 1.5f,
) {
    private enum class State { IDLE, FALLING, IMPACT_WAIT, STILL }
    private var state = State.IDLE
    private var since = 0L

    /** One reading at [t] ms of total acceleration [m]. True when a fall is confirmed. */
    fun sample(t: Long, m: Float): Boolean {
        when (state) {
            State.IDLE -> if (m < freeFall) { state = State.FALLING; since = t }
            State.FALLING -> if (m >= freeFall) {
                if (t - since >= minFallMs) { state = State.IMPACT_WAIT; since = t; return sample(t, m) } else state = State.IDLE
            }
            State.IMPACT_WAIT -> when {
                m > impact -> { state = State.STILL; since = t }
                t - since > 1_000 -> state = State.IDLE
            }
            State.STILL -> {
                val after = t - since
                if (after > settleMs && abs(m - SensorManager.GRAVITY_EARTH) > stillTolerance) state = State.IDLE
                else if (after >= settleMs + stillMs) { state = State.IDLE; return true }
            }
        }
        return false
    }
}

/** Listens to the accelerometer while fall detection is on, and starts a 30-second SOS countdown after a fall. */
class FallDetector(private val app: BlueMobApp) : SensorEventListener {
    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val logic = FallLogic()
    private var on = false

    val available: Boolean get() = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun listen(enable: Boolean) {
        if (enable == on) return
        val s = sensors ?: return
        if (enable) s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        else s.unregisterListener(this)
        on = enable
    }

    override fun onSensorChanged(e: SensorEvent) {
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        if (logic.sample(System.currentTimeMillis(), sqrt(x * x + y * y + z * z)))
            app.countdown.start(COUNTDOWN_S, "BlueMob noticed a hard fall and no movement since.", "Possible fall: a hard fall and no movement after it",
                "Fall detected: no answer for 30 seconds, SOS sent")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val COUNTDOWN_S = 30
    }
}
