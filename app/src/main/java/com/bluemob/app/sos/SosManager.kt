package com.bluemob.app.sos

import android.content.Context
import android.os.BatteryManager
import com.bluemob.app.identity.Identity
import com.bluemob.app.location.LocationTracker
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.mesh.SosSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Our own SOS, and SOS calls from others.
 *
 * Sending: the SOS goes to every connected phone right away, and to every phone that connects later,
 * until "I'm safe". Each phone that hears it passes it on (see [NearbyMeshTransport]).
 * Receiving: the newest SOS opens a full-screen alert, with a short alarm.
 */
class SosManager(
    context: Context,
    private val mesh: NearbyMeshTransport,
    private val identity: Identity,
    private val location: LocationTracker,
    private val signals: SignalController,
    scope: CoroutineScope,
) {
    private val battery = context.getSystemService(BatteryManager::class.java)

    private val _mine = MutableStateFlow<SosSignal?>(null)
    val mine: StateFlow<SosSignal?> = _mine.asStateFlow()

    private val _received = MutableStateFlow<Map<String, SosSignal>>(emptyMap())
    /** Active SOS calls from others, by sender. */
    val received: StateFlow<Map<String, SosSignal>> = _received.asStateFlow()

    private val _alert = MutableStateFlow<SosSignal?>(null)
    /** The SOS to show full screen, until dismissed. */
    val alert: StateFlow<SosSignal?> = _alert.asStateFlow()

    private val alerted = mutableSetOf<String>()

    init {
        scope.launch {
            mesh.events.collect { e ->
                if (e !is MeshEvent.SosReceived) return@collect
                val s = e.sos
                if (s.cancelled) {
                    _received.update { it - s.fromNodeId }
                    if (_alert.value?.fromNodeId == s.fromNodeId) _alert.value = null
                    return@collect
                }
                _received.update { it + (s.fromNodeId to s) }
                if (alerted.add(s.id)) {
                    _alert.value = s
                    repeat(3) { signals.beep(220); delay(320) }
                }
            }
        }
    }

    fun batteryPct(): Int? = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

    /** Sends an SOS. Returns how many phones it reached right now. */
    fun send(note: String): Int {
        val here = location.location.value ?: location.lastKnown()
        val sos = SosSignal(
            id = "sos-" + UUID.randomUUID().toString().take(12), fromNodeId = identity.nodeId, name = identity.displayName.value,
            note = note.trim(), lat = here?.lat, lon = here?.lon, battery = batteryPct(), at = System.currentTimeMillis(), hops = 0,
        )
        _mine.value = sos
        return mesh.broadcastSos(sos)
    }

    /** "I'm safe": tells everyone and stops re-sending. */
    fun cancel() {
        val sos = _mine.value ?: return
        mesh.broadcastSos(sos.copy(cancelled = true, at = System.currentTimeMillis()))
        _mine.value = null
    }

    fun dismissAlert() { _alert.value = null }
}
