package com.bluemob.app.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The direction the phone points, in degrees from magnetic north. Works offline: it's the phone's own sensor. */
class HeadingSensor(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val rotation = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = rotation != null

    private val _accuracy = kotlinx.coroutines.flow.MutableStateFlow(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    /** The sensor's own accuracy (SensorManager.SENSOR_STATUS_*): low means it needs a figure-8 to calibrate. */
    val accuracy: kotlinx.coroutines.flow.StateFlow<Int> = _accuracy

    /** Emits a smoothed heading while collected; nothing if the phone has no compass. */
    fun headings(): Flow<Float> = callbackFlow {
        val r = FloatArray(9)
        val o = FloatArray(3)
        var smoothed: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                val deg = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                // Low-pass filter that goes the short way round 0°/360°.
                val prev = smoothed
                smoothed = if (prev == null) deg else {
                    val diff = ((deg - prev + 540) % 360) - 180
                    ((prev + diff * 0.15f) + 360) % 360
                }
                trySend(smoothed!!)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) { _accuracy.value = accuracy }
        }
        if (rotation != null) sensors?.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors?.unregisterListener(listener) }
    }

    /**
     * Where the back camera points: (bearing from magnetic north, degrees above the horizon), for the phone held up
     * in front of you. Smoothed; nothing if the phone has no compass.
     */
    fun cameraPose(): Flow<Pair<Float, Float>> = callbackFlow {
        val r = FloatArray(9); val m = FloatArray(9); val o = FloatArray(3)
        var az: Float? = null; var pitch = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, m)
                SensorManager.getOrientation(m, o)
                val deg = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                val p = -Math.toDegrees(o[1].toDouble()).toFloat()
                val prev = az
                az = if (prev == null) deg else { val diff = ((deg - prev + 540) % 360) - 180; ((prev + diff * 0.12f) + 360) % 360 }
                pitch += (p - pitch) * 0.12f
                trySend(az!! to pitch)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) { _accuracy.value = accuracy }
        }
        if (rotation != null) sensors?.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors?.unregisterListener(listener) }
    }
}
