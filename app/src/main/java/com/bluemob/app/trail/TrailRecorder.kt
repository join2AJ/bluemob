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
import kotlinx.coroutines.flow.flatMapLatest
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
    private val trips: com.bluemob.app.data.TripDao,
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
    /** The trip being recorded now. */
    val currentTrip: StateFlow<String?> = settings.currentTrip
    /** Points of the trip being recorded (empty when the trail is off). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val points: StateFlow<List<TrailPoint>> = settings.currentTrip
        .flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.observeTrip(id) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    /** Every trip on this phone, newest first. */
    val allTrips: StateFlow<List<com.bluemob.app.data.Trip>> = trips.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

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

    /** Turning the trail on starts a new trip; turning it off ends it. Past trips stay on the phone. */
    fun setEnabled(on: Boolean, tripName: String? = null) {
        if (on == settings.trailOn.value) return
        scope.launch {
            if (on) beginTrip(tripName) else endTrip()
            settings.setTrailOn(on)
        }
        audit.add(AuditKind.POSITION, if (on) "Trail recording turned on" else "Trail recording turned off")
    }

    /** Ends the trip being recorded and starts another (e.g. a new day of the hike). */
    fun startNewTrip(name: String?) = scope.launch {
        endTrip()
        beginTrip(name)
        if (!settings.trailOn.value) settings.setTrailOn(true)
    }

    private suspend fun beginTrip(name: String?) {
        val now = System.currentTimeMillis()
        val id = "t-" + java.util.UUID.randomUUID().toString().take(12)
        val label = name?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            ?: ("Trip · " + java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)))
        trips.put(com.bluemob.app.data.Trip(id, label, now))
        settings.setCurrentTrip(id)
        lastSaved = null
        audit.add(AuditKind.POSITION, "Trip started: $label")
    }

    private suspend fun endTrip() {
        val id = settings.currentTrip.value ?: return
        trips.get(id)?.let { t -> trips.put(t.copy(endedAt = System.currentTimeMillis())) }
        settings.setCurrentTrip(null)
    }

    /** Deletes the trip being recorded (its points), keeping the recording going as a fresh trip. */
    fun clear() {
        scope.launch {
            settings.currentTrip.value?.let { dao.deleteTrip(it); trips.delete(it) }
            settings.setCurrentTrip(null)
            if (settings.trailOn.value) beginTrip(null)
        }
        lastSaved = null
        audit.add(AuditKind.POSITION, "Current trip's trail cleared")
    }

    suspend fun renameTrip(id: String, name: String) { trips.get(id)?.let { trips.put(it.copy(name = name.trim().take(40).ifEmpty { it.name })) } }
    suspend fun deleteTrip(id: String) {
        if (id == settings.currentTrip.value) { clear(); return }
        dao.deleteTrip(id); trips.delete(id)
        audit.add(AuditKind.POSITION, "A past trip was deleted")
    }
    suspend fun pointsOf(id: String) = dao.pointsOf(id)

    /** Called when the step permission was just granted, so steps start counting without a restart. */
    fun onStepPermission() {
        if (settings.trailOn.value) registerSteps()
    }

    private fun startRecording() {
        if (jobs.isNotEmpty()) return
        // Recording since before trips existed (or after a restart mid-trip without one): give it a trip.
        if (settings.currentTrip.value == null) scope.launch { beginTrip(null) }
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
            // Detailed enough to replay the trip and tell walking from riding: every 5 m, or every minute when still.
            if (estimated) { if (d < 20) return } else if (d < 5 && p.time - last.time < 60_000) return
        }
        val tripId = settings.currentTrip.value ?: return
        val point = TrailPoint(time = p.time.takeIf { it > 0 } ?: System.currentTimeMillis(), lat = p.lat, lon = p.lon, accuracyM = p.accuracyM, estimated = estimated, tripId = tripId,
            speedMps = if (estimated) null else location.speed.value, altitudeM = if (estimated) null else location.altitude.value)
        val step = last?.takeIf { it.tripId == tripId }?.let { Geo.distanceM(GeoPoint(it.lat, it.lon, 0f, 0), p) } ?: 0.0
        lastSaved = point
        dao.insert(point)
        trips.get(tripId)?.let { t -> trips.put(t.copy(distanceM = t.distanceM + step, points = t.points + 1)) }
    }

    /** A one-off estimate when the trail is off: just the last known fix, with honest uncertainty. */
    fun snapshot(): PositionEstimate? = _estimate.value ?: (location.location.value ?: location.lastKnown())?.let {
        DeadReckoner().apply { onFix(it) }.estimate(System.currentTimeMillis(), _heading.value)
    }

    private companion object {
        const val MAX_POINTS = 3000
    }
}
