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
    /** Something wrong on this side, e.g. the microphone isn't allowed. */
    val warning: String? = null,
    val theyMuted: Boolean = false,
    /** No voice from them for a few seconds while they aren't muted: the link is struggling. */
    val noAudio: Boolean = false,
    val outgoing: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Walkie-talkie mode: our mic is on only while we hold the talk button. */
    val ptt: Boolean = false,
    /** Holding the talk button. */
    val talking: Boolean = false,
    val theyPtt: Boolean = false,
    val theyTalking: Boolean = false,
    /** How we're linked, e.g. "Wi-Fi" or "Bluetooth". */
    val link: String = "",
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
    /** Saves each call to the call history. */
    private val log: suspend (com.bluemob.app.data.CallLogEntry) -> Unit = {},
    /** True when a BlueMob relay is set, so calls can go over the internet. */
    private val relaySet: () -> Boolean = { false },
) {
    private val app = context.applicationContext
    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call.asStateFlow()

    private val _remoteFrame = MutableStateFlow<Bitmap?>(null)
    val remoteFrame: StateFlow<Bitmap?> = _remoteFrame.asStateFlow()
    private val _localFrame = MutableStateFlow<Bitmap?>(null)
    val localFrame: StateFlow<Bitmap?> = _localFrame.asStateFlow()

    private val voice = VoiceLink(app) { bytes ->
        _call.value?.let { c ->
            // If the link has fallen behind, drop this bit of audio rather than let the delay keep growing.
            if (mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_AUDIO) < AUDIO_BACKLOG_MAX) mesh.sendMedia(c.peer, bytes)
        }
    }
    private var ringtone: Ringtone? = null
    private var ringJob: Job? = null
    private var timeoutJob: Job? = null
    private var linkWatch: Job? = null
    private var lastFrameSent = 0L
    @Volatile private var lastAudioAt = 0L
    @Volatile private var encoding = false

    init {
        scope.launch {
            mesh.events.collect { e ->
                if (e is MeshEvent.App && e.kind == KIND) onSignal(e)
                if (e is MeshEvent.Unreachable) _call.value?.takeIf { it.peer == e.nodeId && it.phase == CallPhase.OUTGOING && !mesh.isConnected(it.peer) }?.let {
                    finish("${it.name} isn't online right now. Try again later, or send a message: it waits for them.", "NO_ANSWER")
                }
            }
        }
        scope.launch {
            mesh.media.collect { m ->
                val c = _call.value ?: return@collect
                if (c.phase != CallPhase.ACTIVE || m.fromNodeId != c.peer) return@collect
                when (m.bytes[0]) {
                    NearbyMeshTransport.MEDIA_AUDIO -> { lastAudioAt = SystemClock.elapsedRealtime(); voice.onPacket(m.bytes) }
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
        if (!mesh.canReachLive(peer)) return when {
            !relaySet() -> "$name isn't nearby. Calls over the internet need the BlueMob relay: set it in You → Internet bridge."
            else -> "$name isn't nearby, and this phone isn't connected to the internet relay yet. Check mobile data or Wi-Fi and try again."
        }
        val c = Call("c-" + UUID.randomUUID().toString().take(10), peer, name, video, CallPhase.OUTGOING, outgoing = true, link = mesh.callLink(peer))
        if (!signal(c, "invite", JSONObject().put("video", video))) return "Couldn't reach $name"
        _call.value = c
        ringback()
        timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == c.id && _call.value?.phase == CallPhase.OUTGOING) { signal(c, "end"); finish(if (mesh.mayBeOld(c.peer)) "No answer. If ${c.name} has BlueMob 0.6 or older, they need to update for calls" else "No answer", "NO_ANSWER") } }
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
        finish(if (c.phase == CallPhase.ACTIVE) "Call ended" else if (c.outgoing) "Cancelled" else "Declined",
            if (c.phase == CallPhase.ACTIVE) null else if (c.outgoing) "CANCELLED" else "DECLINED")
    }

    fun hangUp() = decline()

    /** Walkie-talkie mode: clearer over weak links and no echo on speaker, because only one side talks at a time. */
    fun togglePtt() {
        _call.update { it?.copy(ptt = !it.ptt, talking = false) }
        val c = _call.value ?: return
        applyMic(c)
        signal(c, "ptt", JSONObject().put("on", c.ptt))
    }

    /** Holding (true) or releasing (false) the talk button. */
    fun talk(down: Boolean) {
        val c = _call.value?.takeIf { it.ptt && it.talking != down } ?: return
        _call.update { it?.copy(talking = down) }
        applyMic(_call.value ?: return)
        signal(c, "talk", JSONObject().put("on", down))
        runCatching { clicker?.startTone(if (down) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK, 80) }
    }

    /** A short click when the talk button goes down and up, like a radio. */
    private val clicker by lazy { runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 40) }.getOrNull() }

    private fun applyMic(c: Call) { voice.muted = c.muted || !hasMic() || (c.ptt && !c.talking) }

    fun toggleMute() {
        _call.update { it?.copy(muted = !it.muted) }
        val c = _call.value ?: return
        applyMic(c)
        // Tell them, so their phone doesn't think the link dropped.
        signal(c, "mute", JSONObject().put("on", c.muted))
    }
    fun toggleSpeaker() { _call.update { it?.copy(speaker = !it.speaker) }; voice.setSpeaker(_call.value?.speaker == true) }
    fun toggleCamera() { _call.update { it?.copy(cameraOn = !it.cameraOn) }; if (_call.value?.cameraOn == false) _localFrame.value = null }

    /** Forget a finished call, once the screen has shown how it ended. */
    fun clear() { if (_call.value?.phase == CallPhase.ENDED) _call.value = null }

    /** True when a new video frame is due (so the camera doesn't convert frames we'd throw away). */
    fun wantsFrame(): Boolean {
        val c = _call.value ?: return false
        if (c.phase != CallPhase.ACTIVE || !c.cameraOn || encoding) return false
        val fps = if (wifi(c)) FPS_WIFI else FPS_BLUETOOTH
        if (SystemClock.elapsedRealtime() - lastFrameSent < 1000 / fps) return false
        // Voice comes first: only send the next frame once the last one has gone and no audio is waiting.
        return mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_VIDEO) == 0 && mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_AUDIO) < AUDIO_BACKLOG_MAX / 2
    }

    /** Wi-Fi nearby, or the internet: room for bigger, more frequent video frames than Bluetooth. */
    private fun wifi(c: Call) = mesh.callLink(c.peer).let { it == "Wi-Fi" || it == "Internet" }

    /**
     * A camera frame, already upright. Sent only as often as the link can take: about [FPS_WIFI] frames a second
     * over Wi-Fi and [FPS_BLUETOOTH] over Bluetooth. Called on the camera thread.
     */
    fun onCameraFrame(frame: Bitmap) {
        val c = _call.value ?: return
        if (c.phase != CallPhase.ACTIVE || !c.cameraOn || encoding) return
        val now = SystemClock.elapsedRealtime()
        val fps = if (wifi(c)) FPS_WIFI else FPS_BLUETOOTH
        if (now - lastFrameSent < 1000 / fps) return
        lastFrameSent = now
        encoding = true
        try {
            val small = scaleDown(frame, if (wifi(c)) FRAME_SIZE else FRAME_SIZE_BLUETOOTH)
            val jpeg = jpeg(small, if (wifi(c)) 55 else 35)
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
                // Calls need a live link: straight to us nearby, or over the internet relay.
                if (!e.direct && !e.viaInternet) return
                if (current != null && current.phase != CallPhase.ENDED) {
                    mesh.sendApp(e.fromNodeId, KIND, JSONObject().put("cid", id).put("a", "busy"))
                    return
                }
                val c = Call(id, e.fromNodeId, e.name.ifBlank { "Someone" }, b.optBoolean("video"), CallPhase.INCOMING, link = if (e.direct) mesh.linkName(e.fromNodeId) else "Internet")
                _call.value = c
                ring()
                timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == id && _call.value?.phase == CallPhase.INCOMING) finish("Missed call") }
                audit.add(AuditKind.MESH, "${if (c.video) "Video" else "Voice"} call from ${c.name}")
                onIncoming(c)
            }
            "accept" -> if (current?.id == id && current.phase == CallPhase.OUTGOING && current.peer == e.fromNodeId) begin(current)
            "decline" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} can't talk right now", "DECLINED")
            "busy" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} is on another call", "BUSY")
            "ptt" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyPtt = b.optBoolean("on"), theyTalking = false, noAudio = false) }
            "talk" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyTalking = b.optBoolean("on"), noAudio = false) }
            "mute" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyMuted = b.optBoolean("on"), noAudio = false) }
            "end" -> if (current?.id == id && current.peer == e.fromNodeId) finish(if (current.phase == CallPhase.INCOMING) "Missed call" else "Call ended")
        }
    }

    private fun begin(c: Call) {
        stopRinging()
        timeoutJob?.cancel()
        val active = c.copy(phase = CallPhase.ACTIVE, startedAt = System.currentTimeMillis())
        _call.value = active
        // Always play their voice, even if we can't record ours.
        voice.start(active.speaker)
        voice.muted = active.muted || !hasMic()
        lastAudioAt = SystemClock.elapsedRealtime()
        if (!hasMic()) _call.update { it?.copy(warning = "Microphone not allowed, so ${c.name} can't hear you. Allow it in Settings → Apps → BlueMob → Permissions.") }
        else scope.launch {
            delay(1_500)
            if (_call.value?.id == c.id && _call.value?.phase == CallPhase.ACTIVE && !voice.micWorking)
                _call.update { it?.copy(warning = "The microphone is busy (another app may be using it), so ${c.name} can't hear you.") }
        }
        // The call needs the direct link: if it drops for more than a few seconds, end the call cleanly.
        linkWatch = scope.launch {
            var gone = 0
            while (_call.value?.id == c.id && _call.value?.phase == CallPhase.ACTIVE) {
                delay(1_000)
                // Nearby or over the internet; if they come into range mid-call, voice moves to the direct link by itself.
                gone = if (mesh.canReachLive(c.peer)) 0 else gone + 1
                if (gone >= LINK_GRACE_S) finish("Lost the connection with ${c.name}")
                val link = mesh.callLink(c.peer)
                if (gone == 0 && _call.value?.link != link) _call.update { it?.copy(link = link) }
                val now = _call.value
                // Quiet is expected when they've muted, or use walkie-talkie and aren't holding the button.
                val quiet = SystemClock.elapsedRealtime() - lastAudioAt > NO_AUDIO_MS && now?.theyMuted == false && !(now.theyPtt && !now.theyTalking)
                if (_call.value?.noAudio != quiet) _call.update { it?.copy(noAudio = quiet) }
            }
        }
    }

    private fun finish(reason: String, outcome: String? = null) {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        val now = System.currentTimeMillis()
        val result = outcome ?: when (c.phase) {
            CallPhase.ACTIVE -> "ANSWERED"
            CallPhase.INCOMING -> "MISSED"
            else -> if (reason.startsWith("Lost")) "FAILED" else "NO_ANSWER"
        }
        scope.launch {
            log(com.bluemob.app.data.CallLogEntry(c.id, c.peer, c.name, c.video, c.outgoing, result, if (c.phase == CallPhase.ACTIVE) c.startedAt else c.createdAt,
                if (c.phase == CallPhase.ACTIVE) (now - c.startedAt) / 1000 else 0))
        }
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
        const val NO_AUDIO_MS = 3_000L
        const val FPS_WIFI = 10
        const val FPS_BLUETOOTH = 2
        /** Longest side of a video frame, in pixels. */
        const val FRAME_SIZE = 320
        /** Smaller over Bluetooth (about 3–6 KB a frame), so video never crowds out the voice. */
        const val FRAME_SIZE_BLUETOOTH = 176
        /** About half a second of voice. */
        const val AUDIO_BACKLOG_MAX = 4_000

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
        fun jpeg(b: Bitmap, startQuality: Int = 55): ByteArray? {
            for (q in intArrayOf(startQuality, 40, 28, 18).filter { it <= startQuality }) {
                val out = ByteArrayOutputStream()
                b.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() < NearbyMeshTransport.MAX_MEDIA - 64) return out.toByteArray()
            }
            return null
        }
    }
}
