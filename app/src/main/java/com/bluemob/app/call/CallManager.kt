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
    /** How we're linked, e.g. "Wi-Fi", "Bluetooth", "Internet" or "Through Asha". */
    val link: String = "",
    /** Voice and video are end-to-end encrypted (both phones have BlueMob 0.12 or newer). */
    val e2e: Boolean = false,
    /** Ringing them, but their phone isn't online yet: we keep trying for a little while. */
    val waiting: Boolean = false,
    /** Their phone was asleep and the relay woke it: it rings there as BlueMob starts. */
    val waking: Boolean = false,
    /** Their picture arrives but their voice doesn't: something on the audio side, not the link. */
    val videoOnly: Boolean = false,
    /** Voice counters ("sent 210 · got 0 · played 0"), shown when voice has trouble, so it can be reported. */
    val voiceStats: String = "",
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
    /** The end-to-end key for a call with someone, or null if we don't have their public key. */
    private val cipherFor: (peer: String, callId: String) -> com.bluemob.app.mesh.CallCipher? = { _, _ -> null },
    /** Keeps the microphone (and camera) working while the screen is off or another app is open. */
    private val keepAlive: (active: Boolean, video: Boolean) -> Unit = { _, _ -> },
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
            if (mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_AUDIO) < AUDIO_BACKLOG_MAX) send(c, bytes)
        }
    }
    /** This call's end-to-end key, when both phones support it. */
    @Volatile private var cipher: com.bluemob.app.mesh.CallCipher? = null

    private fun send(c: Call, frame: ByteArray) {
        val sealed = if (c.e2e) cipher?.seal(frame) ?: return else frame
        mesh.sendMedia(c.peer, sealed)
    }
    private var ringtone: Ringtone? = null
    private var ringJob: Job? = null
    private var timeoutJob: Job? = null
    private var linkWatch: Job? = null
    private var retryJob: Job? = null
    private var lastFrameSent = 0L
    @Volatile private var lastAudioAt = 0L
    @Volatile private var lastVideoAt = 0L
    private var lastNomic = 0L
    @Volatile private var encoding = false

    init {
        scope.launch {
            mesh.events.collect { e ->
                if (e is MeshEvent.App && e.kind == KIND) onSignal(e)
                if (e is MeshEvent.Unreachable) _call.value?.takeIf { it.peer == e.nodeId && it.phase == CallPhase.OUTGOING && !mesh.isConnected(it.peer) }?.let { c ->
                    // Their phone may be reconnecting (switching networks, waking up): keep ringing for a while.
                    // Their phone was woken up (BlueMob closed): give it longer to start and connect.
                    if (e.waking && !c.waking) _call.update { it?.copy(waking = true) }
                    val grace = if (e.waking || c.waking) WAKING_GRACE_MS else UNREACHABLE_GRACE_MS
                    if (System.currentTimeMillis() - c.createdAt < grace) {
                        if (!c.waiting) _call.update { it?.copy(waiting = true) }
                        if (retryJob?.isActive != true) retryJob = scope.launch {
                            delay(RETRY_MS)
                            _call.value?.takeIf { it.id == c.id && it.phase == CallPhase.OUTGOING }?.let { signal(it, "invite", JSONObject().put("video", it.video).put("e2e", it.e2e)) }
                        }
                    } else finish("${c.name} isn't online right now. Try again later, or send a message: it waits for them.", "NO_ANSWER")
                }
            }
        }
        scope.launch {
            mesh.media.collect { m ->
                val c = _call.value ?: return@collect
                if (c.phase != CallPhase.ACTIVE || m.fromNodeId != c.peer) return@collect
                // An encrypted call takes only frames sealed with its key; anything else is dropped.
                val frame = if (c.e2e) cipher?.open(m.bytes, c.peer) ?: return@collect else m.bytes
                when (frame[0]) {
                    NearbyMeshTransport.MEDIA_AUDIO -> { lastAudioAt = SystemClock.elapsedRealtime(); voice.onPacket(frame) }
                    NearbyMeshTransport.MEDIA_VIDEO -> { lastVideoAt = SystemClock.elapsedRealtime(); decodeFrame(frame) }
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
            else -> "$name isn't nearby, and neither this phone nor anyone near you is connected to the internet. Check mobile data or Wi-Fi and try again."
        }
        val id = "c-" + UUID.randomUUID().toString().take(10)
        // Encrypt whenever we can; the other phone says in its answer whether it can too.
        val canEncrypt = cipherFor(peer, id) != null && mesh.featureGap(peer, "e2ecall") == null
        val c = Call(id, peer, name, video, CallPhase.OUTGOING, outgoing = true, link = mesh.callLink(peer), e2e = canEncrypt)
        if (!signal(c, "invite", JSONObject().put("video", video).put("e2e", canEncrypt))) return "Couldn't reach $name"
        _call.value = c
        ringback()
        timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == c.id && _call.value?.phase == CallPhase.OUTGOING) { signal(c, "end"); finish(if (mesh.mayBeOld(c.peer)) "No answer. If ${c.name} has BlueMob 0.6 or older, they need to update for calls" else "No answer", "NO_ANSWER") } }
        audit.add(AuditKind.MESH, "${if (video) "Video" else "Voice"} call to $name")
        return null
    }

    fun accept() {
        val c = _call.value?.takeIf { it.phase == CallPhase.INCOMING } ?: return
        signal(c, "accept", JSONObject().put("e2e", c.e2e))
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
        val fps = if (internet(c)) INTERNET_VIDEO[videoLevel].fps else if (wifi(c)) FPS_WIFI else FPS_BLUETOOTH
        if (SystemClock.elapsedRealtime() - lastFrameSent < 1000 / fps) return false
        adaptVideo(c)
        // Voice comes first: only send the next frame once the last one has gone and no audio is waiting.
        return mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_VIDEO) == 0 && mesh.mediaBacklog(c.peer, NearbyMeshTransport.MEDIA_AUDIO) < AUDIO_BACKLOG_MAX / 2
    }

    /** Wi-Fi nearby, or the internet: room for bigger, more frequent video frames than Bluetooth. */
    private fun wifi(c: Call) = when (mesh.pathTo(c.peer)) {
        is NearbyMeshTransport.Path.Direct -> mesh.linkName(c.peer) == "Wi-Fi"
        NearbyMeshTransport.Path.Live -> true
        else -> false // through other phones: keep it small
    }

    private fun internet(c: Call) = mesh.pathTo(c.peer) == NearbyMeshTransport.Path.Live

    /**
     * Over the internet, video quality follows the connection: sharper while frames go out as fast as we make them,
     * smaller as soon as they start to queue. 0 is the best of [INTERNET_VIDEO].
     */
    @Volatile private var videoLevel = 1
    private var clearSince = 0L

    private fun adaptVideo(c: Call) {
        if (!internet(c)) return
        val backlog = mesh.mediaBacklog(c.peer)
        val now = SystemClock.elapsedRealtime()
        when {
            backlog > INTERNET_BUSY -> { videoLevel = (videoLevel + 1).coerceAtMost(INTERNET_VIDEO.lastIndex); clearSince = now }
            backlog == 0 && now - clearSince > 3_000 -> { videoLevel = (videoLevel - 1).coerceAtLeast(0); clearSince = now }
            backlog > 0 -> clearSince = now
        }
    }

    /**
     * A camera frame, already upright. Sent only as often as the link can take: about [FPS_WIFI] frames a second
     * over Wi-Fi and [FPS_BLUETOOTH] over Bluetooth. Called on the camera thread.
     */
    fun onCameraFrame(frame: Bitmap) {
        val c = _call.value ?: return
        if (c.phase != CallPhase.ACTIVE || !c.cameraOn || encoding) return
        val now = SystemClock.elapsedRealtime()
        val net = internet(c)
        val q = INTERNET_VIDEO[videoLevel]
        val fps = if (net) q.fps else if (wifi(c)) FPS_WIFI else FPS_BLUETOOTH
        if (now - lastFrameSent < 1000 / fps) return
        lastFrameSent = now
        encoding = true
        try {
            val small = scaleDown(frame, if (net) q.size else if (wifi(c)) FRAME_SIZE else FRAME_SIZE_BLUETOOTH)
            val jpeg = jpeg(small, if (net) q.quality else if (wifi(c)) 55 else 35)
            _localFrame.value = small
            if (jpeg != null) send(c, byteArrayOf(NearbyMeshTransport.MEDIA_VIDEO) + jpeg)
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
                // Calls need a live link back: nearby, over the internet, or through the phones around us.
                if (!e.direct && !e.viaInternet && !mesh.canReachLive(e.fromNodeId)) return
                if (current?.id == id) return // the same invite again (they retried while we were reconnecting)
                if (current != null && current.phase != CallPhase.ENDED) {
                    mesh.sendApp(e.fromNodeId, KIND, JSONObject().put("cid", id).put("a", "busy"))
                    return
                }
                val e2e = b.optBoolean("e2e") && cipherFor(e.fromNodeId, id) != null
                val c = Call(id, e.fromNodeId, e.name.ifBlank { "Someone" }, b.optBoolean("video"), CallPhase.INCOMING,
                    link = if (e.direct) mesh.linkName(e.fromNodeId) else mesh.callLink(e.fromNodeId), e2e = e2e)
                _call.value = c
                ring()
                timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (_call.value?.id == id && _call.value?.phase == CallPhase.INCOMING) finish("Missed call") }
                audit.add(AuditKind.MESH, "${if (c.video) "Video" else "Voice"} call from ${c.name}")
                onIncoming(c)
            }
            // An older BlueMob doesn't answer "e2e": then the call isn't encrypted (it can only be nearby or over the relay).
            "accept" -> if (current?.id == id && current.phase == CallPhase.OUTGOING && current.peer == e.fromNodeId) begin(current.copy(e2e = current.e2e && b.optBoolean("e2e")))
            "decline" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} can't talk right now", "DECLINED")
            "busy" -> if (current?.id == id && current.peer == e.fromNodeId) finish("${current.name} is on another call", "BUSY")
            "ptt" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyPtt = b.optBoolean("on"), theyTalking = false, noAudio = false) }
            "talk" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyTalking = b.optBoolean("on"), noAudio = false) }
            "mute" -> if (current?.id == id && current.peer == e.fromNodeId) _call.update { it?.copy(theyMuted = b.optBoolean("on"), noAudio = false) }
            // They hear nothing from us: rebuild our microphone.
            "nomic" -> if (current?.id == id && current.peer == e.fromNodeId && current.phase == CallPhase.ACTIVE) voice.restartMic()
            "end" -> if (current?.id == id && current.peer == e.fromNodeId) finish(if (current.phase == CallPhase.INCOMING) "Missed call" else "Call ended")
        }
    }

    private fun begin(c: Call) {
        stopRinging()
        timeoutJob?.cancel()
        retryJob?.cancel()
        cipher = if (c.e2e) cipherFor(c.peer, c.id) else null
        val active = c.copy(phase = CallPhase.ACTIVE, startedAt = System.currentTimeMillis(), waiting = false, e2e = c.e2e && cipher != null)
        _call.value = active
        videoLevel = 1
        runCatching { keepAlive(true, active.video) }
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
                val t = SystemClock.elapsedRealtime()
                val quiet = t - lastAudioAt > NO_AUDIO_MS && now?.theyMuted == false && !(now.theyPtt && !now.theyTalking)
                // Their picture comes through but not their voice: the link is fine, their microphone isn't. Ask
                // their phone to restart it (at most every few seconds).
                val videoOnly = quiet && t - lastVideoAt < NO_AUDIO_MS
                if (quiet && gone == 0 && t - lastNomic > NOMIC_EVERY_MS) { lastNomic = t; signal(c, "nomic") }
                val st = voice.stats()
                val stats = "Voice: sent ${st.sent} · got ${st.received} · played ${st.played}" +
                    (if (st.micRestarts + st.speakerRestarts > 0) " · restarted mic ${st.micRestarts}, speaker ${st.speakerRestarts}" else "")
                if (now?.noAudio != quiet || now.videoOnly != videoOnly || now.voiceStats != stats) _call.update { it?.copy(noAudio = quiet, videoOnly = videoOnly, voiceStats = stats) }
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
        retryJob?.cancel()
        linkWatch?.cancel()
        voice.stop()
        cipher = null
        runCatching { keepAlive(false, false) }
        if (c.phase == CallPhase.ACTIVE) {
            val secs = (System.currentTimeMillis() - c.startedAt) / 1000
            val st = voice.stats()
            audit.add(AuditKind.MESH, "Call with ${c.name} ended after ${secs / 60} min ${secs % 60} s · voice sent ${st.sent}, got ${st.received}, played ${st.played}" +
                if (st.micRestarts + st.speakerRestarts > 0) ", mic restarted ${st.micRestarts}×, speaker ${st.speakerRestarts}×" else "")
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
        const val NOMIC_EVERY_MS = 6_000L
        const val FPS_WIFI = 10
        const val FPS_BLUETOOTH = 2
        /** Longest side of a video frame, in pixels. */
        const val FRAME_SIZE = 320
        /** Smaller over Bluetooth (about 3–6 KB a frame), so video never crowds out the voice. */
        const val FRAME_SIZE_BLUETOOTH = 176
        /** About half a second of voice. */
        const val AUDIO_BACKLOG_MAX = 4_000
        /** Keep ringing someone whose phone is offline this long, in case it's just reconnecting. */
        const val UNREACHABLE_GRACE_MS = 20_000L
        const val RETRY_MS = 4_000L
        /** When the relay woke their phone (BlueMob was closed), wait this long for it to start and connect. */
        const val WAKING_GRACE_MS = 45_000L

        /** Video over the internet, best first: picked by how fast frames actually leave the phone. */
        class VideoQuality(val size: Int, val quality: Int, val fps: Int)
        val INTERNET_VIDEO = listOf(VideoQuality(640, 70, 12), VideoQuality(480, 62, 12), VideoQuality(360, 55, 10), VideoQuality(240, 45, 6))
        /** Queued bytes that mean the internet link is behind: step video down. */
        const val INTERNET_BUSY = 40_000

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
            for (q in (listOf(startQuality) + listOf(40, 28, 18).filter { it < startQuality })) {
                val out = ByteArrayOutputStream()
                b.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() < NearbyMeshTransport.MAX_MEDIA - 64) return out.toByteArray()
            }
            return null
        }
    }
}
