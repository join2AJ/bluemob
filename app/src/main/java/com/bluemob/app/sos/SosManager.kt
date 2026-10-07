package com.bluemob.app.sos

import android.content.Context
import android.os.BatteryManager
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.mesh.SosSignal
import com.bluemob.app.trail.TrailRecorder
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
    private val trail: TrailRecorder,
    private val signals: SignalController,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
    /** Age and blood group to send with our SOS. */
    private val medical: () -> Pair<Int?, String?> = { null to null },
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
            mesh.events.collect { e -> if (e is MeshEvent.SosReceived) receive(e.sos) }
        }
    }

    /** An SOS (or "I'm safe") from someone: over the mesh, or from someone whose SOS contact we are. */
    fun receive(s: SosSignal) {
        if (s.cancelled) {
            if (_received.value.containsKey(s.fromNodeId)) audit.add(AuditKind.SOS, "${s.name} is safe now (SOS ended)")
            _received.update { it - s.fromNodeId }
            if (_alert.value?.fromNodeId == s.fromNodeId) _alert.value = null
            return
        }
        _received.update { it + (s.fromNodeId to s) }
        if (alerted.add(s.id)) {
            audit.add(AuditKind.SOS, "SOS received from ${s.name}" + (if (s.note.isNotBlank()) ": \"${s.note}\"" else "") +
                " (${if (s.hops <= 1) "direct" else "passed on by ${s.hops - 1} phones"}). " + (s.pos?.describe() ?: "No position."))
            _alert.value = s
            scope.launch { repeat(3) { signals.beep(220); delay(320) } }
        }
    }

    /** Our SOS reached the people who should hear it, wherever they are: set by the app (SOS contacts). */
    var onSent: (SosSignal) -> Unit = {}
    var onSafe: (SosSignal) -> Unit = {}

    fun batteryPct(): Int? = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

    /** Sends an SOS. Returns how many phones it reached right now. */
    fun send(note: String): Int {
        val here = trail.snapshot()
        val sos = SosSignal(
            id = "sos-" + UUID.randomUUID().toString().take(12), fromNodeId = identity.nodeId, name = identity.displayName.value,
            note = note.trim(), lat = here?.lat, lon = here?.lon, battery = batteryPct(), at = System.currentTimeMillis(), hops = 0, pos = here,
            age = medical().first, bloodGroup = medical().second,
        )
        _mine.value = sos
        val reached = mesh.broadcastSos(sos)
        runCatching { onSent(sos) }
        audit.add(AuditKind.SOS, "SOS sent to $reached phones nearby" + (if (sos.note.isNotBlank()) ": \"${sos.note}\"" else "") +
            ". " + (here?.describe() ?: "No position known."))
        return reached
    }

    /** "I'm safe": tells everyone and stops re-sending. */
    fun cancel() {
        val sos = _mine.value ?: return
        mesh.broadcastSos(sos.copy(cancelled = true, at = System.currentTimeMillis()))
        runCatching { onSafe(sos) }
        _mine.value = null
        audit.add(AuditKind.SOS, "\"I'm safe\": SOS ended")
    }

    fun dismissAlert() { _alert.value = null }

    /** Shows the alert as it would look if someone nearby sent an SOS. Nothing is sent. */
    fun preview(name: String) {
        val now = System.currentTimeMillis()
        val (lat, lon) = com.bluemob.app.util.Geo.offset(30.0869, 78.2676, 215.0, 330.0)
        val pos = com.bluemob.app.trail.PositionEstimate(lat, lon, false, 30.0869, 78.2676, now - 18 * 60_000, 8f, 340.0, 215.0, 230f, 61.0, now)
        _alert.value = SosSignal(PREVIEW_ID, "preview", name, "Twisted my ankle near the stream. Can't walk", lat, lon, 23, now, 2, pos = pos, bloodGroup = "B+", age = 34)
    }

    companion object {
        const val PREVIEW_ID = "preview"
    }
}
