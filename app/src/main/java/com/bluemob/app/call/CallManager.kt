package com.bluemob.app.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class CallPhase { OUTGOING, INCOMING, ACTIVE, ENDED }

data class Call(
    val id: String,
    val peer: String,
    val name: String,
    val video: Boolean,
    val phase: CallPhase,
    val startedAt: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = video,
    val cameraOn: Boolean = video,
    val ended: String? = null,
)

/**
 * Voice and video calls with someone connected directly (Bluetooth or Wi-Fi, no internet). Set-up messages
 * (invite, accept, decline, end) are signed packets; the audio and video go straight over the link.
 *
 * Video is a stream of small JPEG frames: a few per second over Bluetooth, smoother over Wi-Fi. That's enough to
 * see someone's face or show an injury, which is what matters off the grid.
 */
class CallManager(
    context: Context,
    private val mesh: NearbyMeshTransport,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
    /** Someone is calling: the app opens the call screen, or shows a notification in the background. */
    private val onIncoming: (Call) -> Unit = {},
) {
    private val app = context.applicationContext
    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call.asStateFlow()

    private val _remoteFrame = MutableStateFlow<Bitmap?>(null)
    val remoteFrame: StateFlow<Bitmap?> = _remoteFrame.asStateFlow()
    private val _localFrame = MutableStateFlow<Bitmap?>(null)
    val localFrame: StateFlow<Bitmap?> = _localFrame.asStateFlow()

    private val voice = VoiceLink(app) { bytes -> _call.value?.let { mesh.sendMedia(it.peer, bytes) } }
    private var ringtone: Ringtone? = null
    private var ringJob: Job? = null
    private var timeoutJob: Job? = null
    private var linkWatch: Job? = null
    private var lastFrameSent = 0L
    @Volatile private var encoding = false

    init {
        scope.launch { mesh.events.collect { e -> if (e is MeshEvent.App && e.kind == KIND) onSignal(e) } }
        scope.launch {
            mesh.media.collect { m ->
                val c = _call.value ?: return@collect
                if (c.phase != CallPhase.ACTIVE || m.fromNodeId != c.peer) return@collect
                when (m.bytes[0]) {
                    NearbyMeshTransport.MEDIA_AUDIO -> voice.onPacket(m.bytes)
                    NearbyMeshTransport.MEDIA_VIDEO -> decodeFrame(m.bytes)
                }
            }
        }
    }

    fun hasMic() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun hasCamera() = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Calls someone. Returns why it can't, or null when it's ringing. */
    fun start(peer: String, name: String, video: Boolean): String? {
        _call.value?.takeIf { it.phase != CallPhase.ENDED }?.let { return "You're already in a call with ${it.name}" }
        if (!mesh.isConnected(peer)) return "Calls need $name to be nearby and connected directly. Messages still work through other phones."
        val c = Call("c-" + UUID.randomUUID().toString().take(10), peer, name, video, CallPhase.OUTGOING)
        if (!signal(c, "invite", JSONObject().put("video", video))) return "Couldn't reach $name"
        _call.value = c
        ringback()
        timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == c.id && _call.value?.phase == CallPhase.OUTGOING) { signal(c, "end"); finish(if (mesh.mayBeOld(c.peer)) "No answer. If ${c.name} has BlueMob 0.6 or older, they need to update for calls" else "No answer") } }
        audit.add(AuditKind.MESH, "${if (video) "Video" else "Voice"} call to $name")
        return null
    }

    fun accept() {
        val c = _call.value?.takeIf { it.phase == CallPhase.INCOMING } ?: return
        signal(c, "accept")
        begin(c)
    }

    fun decline() {
        val c = _call.value ?: return
        signal(c, if (c.phase == CallPhase.INCOMING) "decline" else "end")
        finish(if (c.phase == CallPhase.ACTIVE) "Call ended" else "Declined")
    }

    fun hangUp() = decline()

    fun toggleMute() = _call.update { it?.copy(muted = !it.muted) }.also { voice.muted = _call.value?.muted == true }
    fun toggleSpeaker() { _call.update { it?.copy(speaker = !it.speaker) }; voice.setSpeaker(_call.value?.speaker == true) }
    fun toggleCamera() { _call.update { it?.copy(cameraOn = !it.cameraOn) }; if (_call.value?.cameraOn == false) _localFrame.value = null }

    /** Forget a finished call, once the screen has shown how it ended. */
    fun clear() { if (_call.value?.phase == CallPhase.ENDED) _call.value = null }

    /** True when a new video frame is due (so the camera doesn't convert frames we'd throw away). */
    fun wantsFrame(): Boolean {
        val c = _call.value ?: return false
        if (c.phase != CallPhase.ACTIVE || !c.cameraOn || encoding) return false
        val fps = if (mesh.linkName(c.peer) == "Wi-Fi") FPS_WIFI else FPS_BLUETOOTH
        return SystemClock.elapsedRealtime() - lastFrameSent >= 1000 / fps
    }

    /**
     * A camera frame, already upright. Sent only as often as the link can take: about [FPS_WIFI] frames a second
     * over Wi-Fi and [FPS_BLUETOOTH] over Bluetooth. Called on the camera thread.
     */
    fun onCameraFrame(frame: Bitmap) {
        val c = _call.value ?: return
        if (c.phase != CallPhase.ACTIVE || !c.cameraOn || encoding) return
        val now = SystemClock.elapsedRealtime()
        val fps = if (mesh.linkName(c.peer) == "Wi-Fi") FPS_WIFI else FPS_BLUETOOTH
        if (now - lastFrameSent < 1000 / fps) return
        lastFrameSent = now
        encoding = true
        try {
            val small = scaleDown(frame, FRAME_SIZE)
            val jpeg = jpeg(small)
            _localFrame.value = small
            if (jpeg != null) mesh.sendMedia(c.peer, byteArrayOf(NearbyMeshTransport.MEDIA_VIDEO) + jpeg)
        } finally {
            encoding = false
        }
    }

    private fun onSignal(e: com.bluemob.app.mesh.MeshEvent.App) {
        val b = e.body
        val id = b.optString("cid").takeIf { it.startsWith("c-") && it.length <= 20 } ?: return
        val current = _call.value
        when (b.optString("a")) {
            "invite" -> {
                if (!e.direct) return
                if (current != null && current.phase != CallPhase.ENDED) {
                    mesh.sendApp(e.fromNodeId, KIND, JSONObject().put("cid", id).put("a", "busy"))
                    return
                }
                val c = Call(id, e.fromNodeId, e.name.ifBlank { "Someone" }, b.optBoolean("video"), CallPhase.INCOMING)
                _call.value = c
                ring()
                timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == id && _call.value?.phase == CallPhase.INCOMING) finish("Missed call") }
                audit.add(AuditKind.MESH, "${if (c.video) "Video" else "Voice"} call from ${c.name}")
                onIncoming(c)
            }
            "accept" -> if (current?.id == id && current.phase == CallPhase.OUTGOING && current.peer == e.fromNodeId) begin(current)
            "decline" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} can't talk right now")
            "busy" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} is on another call")
            "end" -> if (current?.id == id && current.peer == e.fromNodeId) finish(if (current.phase == CallPhase.INCOMING) "Missed call" else "Call ended")
        }
    }

    private fun begin(c: Call) {
        stopRinging()
        timeoutJob?.cancel()
        val active = c.copy(phase = CallPhase.ACTIVE, startedAt = System.currentTimeMillis())
        _call.value = active
        if (hasMic()) voice.start(active.speaker)
        voice.muted = active.muted || !hasMic()
        // The call needs the direct link: if it drops for more than a few seconds, end the call cleanly.
        linkWatch = scope.launch {
            var gone = 0
            while (_call.value?.id == c.id && _call.value?.phase == CallPhase.ACTIVE) {
                delay(1_000)
                gone = if (mesh.isConnected(c.peer)) 0 else gone + 1
                if (gone >= LINK_GRACE_S) finish("Lost the connection with ${c.name}")
            }
        }
    }

    private fun finish(reason: String) {
        val c = _call.value ?: return
        stopRinging()
        timeoutJob?.cancel()
        linkWatch?.cancel()
        voice.stop()
        if (c.phase == CallPhase.ACTIVE) {
            val secs = (System.currentTimeMillis() - c.startedAt) / 1000
            audit.add(AuditKind.MESH, "Call with ${c.name} ended after ${secs / 60} min ${secs % 60} s")
        }
        _call.value = c.copy(phase = CallPhase.ENDED, ended = reason)
        _remoteFrame.value = null
        _localFrame.value = null
        scope.launch { delay(2_500); if (_call.value?.id == c.id && _call.value?.phase == CallPhase.ENDED) _call.value = null }
    }

    private fun signal(c: Call, action: String, extra: JSONObject = JSONObject()) =
        mesh.sendApp(c.peer, KIND, extra.put("cid", c.id).put("a", action))

    private fun ring() {
        stopRinging()
        ringtone = runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
                if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }.getOrNull()
    }

    private fun ringback() {
        stopRinging()
        ringJob = scope.launch {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70) }.getOrNull() ?: return@launch
            try {
                while (true) { tone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1_500); delay(4_000) }
            } finally { tone.release() }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        ringJob?.cancel()
        ringJob = null
    }

    private fun decodeFrame(bytes: ByteArray) {
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeByteArray(bytes, 1, bytes.size - 1) }.getOrNull() }
            if (bmp != null && _call.value?.phase == CallPhase.ACTIVE) _remoteFrame.value = bmp
        }
    }

    companion object {
        const val KIND = "call"
        const val RING_TIMEOUT_MS = 45_000L
        const val LINK_GRACE_S = 8
        const val FPS_WIFI = 10
        const val FPS_BLUETOOTH = 2
        /** Longest side of a video frame, in pixels. */
        const val FRAME_SIZE = 320

        fun scaleDown(b: Bitmap, max: Int): Bitmap {
            val scale = max.toFloat() / maxOf(b.width, b.height)
            if (scale >= 1f) return b
            return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
        }

        fun rotate(b: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
            if (degrees == 0 && !mirror) return b
            val m = Matrix().apply { postRotate(degrees.toFloat()); if (mirror) postScale(-1f, 1f) }
            return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }

        /** JPEG small enough for one packet: lowers the quality until it fits. */
        fun jpeg(b: Bitmap): ByteArray? {
            for (q in intArrayOf(55, 40, 28, 18)) {
                val out = ByteArrayOutputStream()
                b.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() < NearbyMeshTransport.MAX_MEDIA - 64) return out.toByteArray()
            }
            return null
        }
    }
}
