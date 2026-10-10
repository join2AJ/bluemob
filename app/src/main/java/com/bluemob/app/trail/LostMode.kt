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
 * "I'm lost": turns the trail and fast GPS on and shares our best position estimate with everyone nearby
 * right away, then every [SHARE_EVERY_MS] (and whenever it moves [SHARE_MOVE_M]), so they can find us even
 * when our GPS has dropped out. Phones pass it on, like an SOS.
 *
 * Someone searching can also "ring" a lost (or SOS) phone: it sounds a loud whistle and flashes its light for
 * [RING_MS], which finds people in the dark, in a crowd or in thick forest when GPS can't.
 */
class LostMode(
    private val mesh: NearbyMeshTransport,
    private val identity: Identity,
    private val trail: TrailRecorder,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
    private val location: com.bluemob.app.location.LocationTracker? = null,
    private val signals: com.bluemob.app.sos.SignalController? = null,
    /** True while our own SOS is active: our phone can be rung then too. */
    private val sosActive: () -> Boolean = { false },
    /** Tells the user something (a toast while BlueMob is open, a notification otherwise). */
    private val say: (String) -> Unit = {},
    /** Whether helpers may make this phone sound, flash or light up during an SOS (on by default). */
    private val allowed: () -> Boolean = { true },
) {
    private val _screenFlash = MutableStateFlow(0L)
    /** Until when the screen should flash (a helper asked), or 0. The app shows a full-screen flashing light. */
    val screenFlash: StateFlow<Long> = _screenFlash.asStateFlow()
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
                if (e is MeshEvent.App) onApp(e)
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
        location?.boost(true)
        audit.add(AuditKind.POSITION, "Lost mode on: sharing position with everyone nearby")
        job = scope.launch {
            var lastAudit = 0L
            var lastLat = Double.NaN
            var lastLon = Double.NaN
            var lastSent = 0L
            var hadGps = false
            while (true) {
                val pos = trail.estimate.value ?: trail.snapshot()
                val now = System.currentTimeMillis()
                val moved = pos != null && (lastLat.isNaN() || Geo.distanceM(
                    com.bluemob.app.contacts.GeoPoint(lastLat, lastLon, 0f, 0), com.bluemob.app.contacts.GeoPoint(pos.lat, pos.lon, 0f, 0),
                ) >= SHARE_MOVE_M)
                // GPS just got its first fix (or came back): share it at once, it's much better than an estimate.
                val gpsNow = pos?.gps == true
                val gpsBack = gpsNow && !hadGps
                hadGps = gpsNow
                if (moved || gpsBack || now - lastSent >= SHARE_EVERY_MS) {
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
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        if (!_on.value) return
        job?.cancel()
        job = null
        _on.value = false
        location?.boost(false)
        stopRinging()
        mesh.broadcastLost(LostSignal("lost-" + UUID.randomUUID().toString().take(12), identity.nodeId, identity.displayName.value, null, System.currentTimeMillis(), 0, ended = true))
        _lastShared.value = null
        audit.add(AuditKind.POSITION, "Lost mode off: found my way")
    }

    /** Asks a lost or SOS person's phone to whistle and flash so we can find them. False if no one is in range. */
    fun ring(nodeId: String, what: String = ALL): Boolean {
        val sent = mesh.sendApp(nodeId, RING, org.json.JSONObject().put("what", what))
        if (sent) {
            audit.add(AuditKind.POSITION, "Asked ${mesh.nameOf(nodeId)}'s phone to ring and flash")
            awaitingReply += nodeId
            scope.launch {
                delay(RING_REPLY_WAIT_MS)
                if (awaitingReply.remove(nodeId)) say("No reply from ${mesh.nameOf(nodeId)}'s phone yet." +
                    if (mesh.mayBeOld(nodeId)) " If they have BlueMob 0.6 or older, ringing needs an update." else " They may be out of range: try again closer.")
            }
        }
        return sent
    }

    private var ringJob: Job? = null
    private val awaitingReply = mutableSetOf<String>()
    private var lastRing = 0L

    private fun onApp(e: MeshEvent.App) {
        when (e.kind) {
            RING -> {
                val inNeed = _on.value || sosActive()
                val canRing = inNeed && allowed()
                mesh.sendApp(e.fromNodeId, RING_REPLY, org.json.JSONObject().put("ok", canRing).put("why", if (!inNeed) "not-in-need" else if (!canRing) "off" else ""))
                if (!canRing) return
                val what = e.body.optString("what").ifBlank { ALL }
                val now = System.currentTimeMillis()
                if (now - lastRing < RING_GAP_MS) return
                lastRing = now
                val sound = what == ALL || what == SOUND
                val flash = what == ALL || what == FLASH
                val screen = what == ALL || what == SCREEN
                audit.add(AuditKind.POSITION, "${e.name} signalled this phone to find me (" + listOfNotNull("sound".takeIf { sound }, "light".takeIf { flash }, "screen".takeIf { screen }).joinToString() + ")")
                say("${e.name} is close and looking for you. Shout, wave and stay where you are.")
                if (screen) _screenFlash.value = System.currentTimeMillis() + RING_MS
                ringJob?.cancel()
                ringJob = scope.launch {
                    try {
                        val end = System.currentTimeMillis() + RING_MS
                        var on = false
                        while (System.currentTimeMillis() < end) {
                            on = !on
                            if (flash) signals?.torch(on)
                            if (on && sound) signals?.beep(450)
                            delay(500)
                        }
                    } finally { signals?.torch(false); _screenFlash.value = 0L }
                }
            }
            RING_REPLY -> if (awaitingReply.remove(e.fromNodeId)) say(
                if (e.body.optBoolean("ok")) "🔔 ${e.name}'s phone is signalling now. Listen and look around."
                else if (e.body.optString("why") == "off") "${e.name} has turned off signals from helpers. Call out and look for them."
                else "${e.name} isn't in lost mode or SOS, so their phone won't ring. Message them instead."
            )
        }
    }

    fun stopRinging() { ringJob?.cancel(); ringJob = null; signals?.torch(false); _screenFlash.value = 0L }

    companion object {
        const val RING = "ring"
        const val RING_REPLY = "ring-reply"
        const val ALL = "all"
        const val SOUND = "sound"
        const val FLASH = "flash"
        const val SCREEN = "screen"
        const val SHARE_EVERY_MS = 15_000L
        const val SHARE_MOVE_M = 10.0
        const val TICK_MS = 3_000L
        const val RING_MS = 20_000L
        const val RING_GAP_MS = 8_000L
        const val RING_REPLY_WAIT_MS = 15_000L
    }
}
