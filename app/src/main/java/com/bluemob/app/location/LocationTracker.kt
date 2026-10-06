package com.bluemob.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bluemob.app.contacts.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks this phone's position using the GPS chip, which works with no SIM or internet.
 * Only runs while the user has "Share my location" turned on.
 */
class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    private var active = false

    private val listener = LocationListener { loc: Location ->
        val current = _location.value
        // Ignore a fix that is older or much less accurate than the one we already have.
        if (current != null && loc.time < current.time) return@LocationListener
        if (current != null && loc.accuracy > current.accuracyM * 3 && loc.time - current.time < 60_000) {
            return@LocationListener
        }
        _location.value = GeoPoint(loc.latitude, loc.longitude, loc.accuracy, loc.time)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (active || manager == null || !hasPermission()) return
        active = true
        register()
    }

    @SuppressLint("MissingPermission")
    private fun register() {
        val m = manager ?: return
        val fast = boosts > 0
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!m.allProviders.contains(provider)) continue
            runCatching { m.getLastKnownLocation(provider) }.getOrNull()?.let(listener::onLocationChanged)
            runCatching {
                m.requestLocationUpdates(provider, if (fast) FAST_INTERVAL_MS else UPDATE_INTERVAL_MS, if (fast) 0f else UPDATE_DISTANCE_M, listener, Looper.getMainLooper())
            }
        }
    }

    /**
     * Fast, continuous GPS while someone may be searching for us (lost mode): a fix every couple of seconds
     * instead of every 15 s. Uses more battery, so it's only on while needed. Calls nest.
     */
    private var boosts = 0
    fun boost(on: Boolean) {
        val before = boosts > 0
        boosts = (boosts + if (on) 1 else -1).coerceAtLeast(0)
        if (on) hold() else release(keepForSharing = false)
        if (active && before != (boosts > 0)) { manager?.removeUpdates(listener); register() }
    }

    /** The phone's last known position, without starting GPS. Used for SOS and the compass. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): GeoPoint? {
        if (manager == null || !hasPermission()) return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { manager.allProviders.contains(it) }
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude, it.longitude, it.accuracy, it.time) }
    }

    /** Screens such as the compass can hold GPS on while visible, even when not sharing. */
    private var holds = 0
    fun hold() { holds++; start() }
    fun release(keepForSharing: Boolean) { holds = (holds - 1).coerceAtLeast(0); if (holds == 0 && !keepForSharing && !sharing) stop() }

    /** True while the user shares their position: GPS then stays on when screens let go of it. */
    private var sharing = false
    fun setSharing(on: Boolean) { sharing = on; if (on) start() else if (holds == 0) stop() }

    fun stop() {
        if (!active) return
        manager?.removeUpdates(listener)
        active = false
        _location.value = null
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 15_000L
        const val FAST_INTERVAL_MS = 2_000L
        const val UPDATE_DISTANCE_M = 5f
    }
}
