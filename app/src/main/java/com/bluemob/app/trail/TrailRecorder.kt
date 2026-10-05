package com.bluemob.app.trail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.compass.HeadingSensor
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.TrailDao
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.location.LocationTracker
import com.bluemob.app.settings.AppSettings
import com.bluemob.app.util.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Records the user's trail while they turn it on (it's opt-in, it keeps GPS on and uses battery).
 *
 * GPS fixes go straight onto the trail. When GPS drops out, the step counter and the compass carry the position
 * forward (dead reckoning), and those points are marked as estimates.
 */
class TrailRecorder(
    context: Context,
    private val location: LocationTracker,
    private val heading: HeadingSensor,
    private val dao: TrailDao,
    private val settings: AppSettings,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
    /** Whether GPS must stay on for "Share my location" after the trail stops. */
    private val sharing: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(SensorManager::class.java)
    private val stepSensor = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val enabled: StateFlow<Boolean> = settings.trailOn
    val points: StateFlow<List<TrailPoint>> = dao.observeRecent(MAX_POINTS).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _estimate = MutableStateFlow<PositionEstimate?>(null)
    /** Best guess of where we are, while the trail is on. */
    val estimate: StateFlow<PositionEstimate?> = _estimate.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val currentHeading: StateFlow<Float?> = _heading.asStateFlow()

    val stepCounterAvailable: Boolean get() = stepSensor != null
    fun hasStepPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private val reckoner = DeadReckoner()
    private var jobs = emptyList<Job>()
    private var lastSaved: TrailPoint? = null
    private var stepBase: Float? = null

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val total = e.values[0]
            val base = stepBase
            stepBase = total
            if (base != null && total >= base) reckoner.onSteps((total - base).toInt(), _heading.value)
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
    }

    init {
        scope.launch { settings.trailOn.collect { on -> if (on) startRecording() else stopRecording() } }
    }

    fun setEnabled(on: Boolean) {
        if (on == settings.trailOn.value) return
        settings.setTrailOn(on)
        audit.add(AuditKind.POSITION, if (on) "Trail recording turned on" else "Trail recording turned off")
    }

    fun clear() {
        scope.launch { dao.clear() }
        lastSaved = null
        audit.add(AuditKind.POSITION, "Trail cleared")
    }

    /** Called when the step permission was just granted, so steps start counting without a restart. */
    fun onStepPermission() {
        if (settings.trailOn.value) registerSteps()
    }

    private fun startRecording() {
        if (jobs.isNotEmpty()) return
        location.hold()
        (location.location.value ?: location.lastKnown())?.let { reckoner.onFix(it) }
        registerSteps()
        jobs = listOf(
            scope.launch { heading.headings().collect { _heading.value = it } },
            scope.launch {
                var lastFixTime = -1L
                location.location.collect { p ->
                    if (p == null || p.time == lastFixTime) return@collect
                    lastFixTime = p.time
                    reckoner.onFix(p)
                    save(p, estimated = false)
                }
            },
            scope.launch {
                while (true) {
                    val e = reckoner.estimate(System.currentTimeMillis(), _heading.value)
                    _estimate.value = e
                    if (e != null && !e.gps) save(GeoPoint(e.lat, e.lon, e.uncertaintyM.toFloat(), e.at), estimated = true)
                    delay(5_000)
                }
            },
        )
    }

    private fun stopRecording() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        sensors?.unregisterListener(stepListener)
        stepBase = null
        _estimate.value = null
        location.release(keepForSharing = sharing())
    }

    private fun registerSteps() {
        if (stepSensor == null || !hasStepPermission()) return
        sensors?.unregisterListener(stepListener)
        stepBase = null
        sensors?.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** Adds a point if we've moved enough (or enough time passed) since the last one, so the trail stays small. */
    private suspend fun save(p: GeoPoint, estimated: Boolean) {
        val last = lastSaved ?: points.value.lastOrNull()
        if (last != null) {
            val d = Geo.distanceM(GeoPoint(last.lat, last.lon, 0f, 0), p)
            if (estimated) { if (d < 20) return } else if (d < 10 && p.time - last.time < 3 * 60_000) return
        }
        val point = TrailPoint(time = p.time.takeIf { it > 0 } ?: System.currentTimeMillis(), lat = p.lat, lon = p.lon, accuracyM = p.accuracyM, estimated = estimated)
        lastSaved = point
        dao.insert(point)
    }

    /** A one-off estimate when the trail is off: just the last known fix, with honest uncertainty. */
    fun snapshot(): PositionEstimate? = _estimate.value ?: (location.location.value ?: location.lastKnown())?.let {
        DeadReckoner().apply { onFix(it) }.estimate(System.currentTimeMillis(), _heading.value)
    }

    private companion object {
        const val MAX_POINTS = 3000
    }
}
