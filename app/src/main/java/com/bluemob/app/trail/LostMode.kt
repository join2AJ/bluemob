package com.bluemob.app.trail

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.LostSignal
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.util.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * "I'm lost": turns the trail on and shares our best position estimate with everyone nearby every minute
 * (and whenever it moves 25 m), so they can find us even when our GPS has dropped out. Phones pass it on,
 * like an SOS.
 */
class LostMode(
    private val mesh: NearbyMeshTransport,
    private val identity: Identity,
    private val trail: TrailRecorder,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
) {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _lastShared = MutableStateFlow<LostSignal?>(null)
    val lastShared: StateFlow<LostSignal?> = _lastShared.asStateFlow()

    private val _received = MutableStateFlow<Map<String, LostSignal>>(emptyMap())
    /** People nearby who are lost, by node ID, with their latest position estimate. */
    val received: StateFlow<Map<String, LostSignal>> = _received.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            mesh.events.collect { e ->
                if (e !is MeshEvent.LostReceived) return@collect
                val s = e.lost
                val before = _received.value[s.fromNodeId]
                if (s.ended) {
                    _received.update { it - s.fromNodeId }
                    audit.add(AuditKind.POSITION, "${s.name} is no longer lost")
                } else if (before == null || s.at > before.at) {
                    _received.update { it + (s.fromNodeId to s) }
                    if (before == null) audit.add(AuditKind.POSITION, "${s.name} is lost. ${s.pos?.describe() ?: "No position yet."}")
                }
            }
        }
    }

    fun start() {
        if (_on.value) return
        _on.value = true
        trail.setEnabled(true)
        audit.add(AuditKind.POSITION, "Lost mode on: sharing position with everyone nearby")
        job = scope.launch {
            var lastAudit = 0L
            var lastLat = Double.NaN
            var lastLon = Double.NaN
            var lastSent = 0L
            while (true) {
                val pos = trail.estimate.value ?: trail.snapshot()
                val now = System.currentTimeMillis()
                val moved = pos != null && (lastLat.isNaN() || Geo.distanceM(
                    com.bluemob.app.contacts.GeoPoint(lastLat, lastLon, 0f, 0), com.bluemob.app.contacts.GeoPoint(pos.lat, pos.lon, 0f, 0),
                ) >= 25)
                if (moved || now - lastSent >= 60_000) {
                    val signal = LostSignal("lost-" + UUID.randomUUID().toString().take(12), identity.nodeId, identity.displayName.value, pos, now, 0)
                    mesh.broadcastLost(signal)
                    _lastShared.value = signal
                    lastSent = now
                    if (pos != null) { lastLat = pos.lat; lastLon = pos.lon }
                    if (pos != null && (moved || now - lastAudit >= 5 * 60_000)) {
                        audit.add(AuditKind.POSITION, "Shared position: ${pos.describe(now)}")
                        lastAudit = now
                    }
                }
                delay(5_000)
            }
        }
    }

    fun stop() {
        if (!_on.value) return
        job?.cancel()
        job = null
        _on.value = false
        mesh.broadcastLost(LostSignal("lost-" + UUID.randomUUID().toString().take(12), identity.nodeId, identity.displayName.value, null, System.currentTimeMillis(), 0, ended = true))
        _lastShared.value = null
        audit.add(AuditKind.POSITION, "Lost mode off: found my way")
    }
}
