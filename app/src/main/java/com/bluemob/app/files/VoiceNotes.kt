package com.bluemob.app.files

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hold-to-record voice notes (AAC, 32 kbit/s mono: about 240 KB a minute) and playback. A recorded clip is clear
 * even when a live call over a weak link wouldn't be, and it can wait to be delivered.
 */
class VoiceNotes(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    private val _recordingMs = MutableStateFlow<Long?>(null)
    /** How long the current recording is, or null when not recording. */
    val recordingMs: StateFlow<Long?> = _recordingMs.asStateFlow()
    private var ticker: Job? = null

    fun start(): Boolean {
        if (recorder != null) return true
        val out = File(app.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_MS.toInt())
            r.setOutputFile(out.path)
            r.prepare()
            r.start()
            recorder = r; file = out; startedAt = SystemClock.elapsedRealtime()
            ticker = scope.launch { while (isActive) { _recordingMs.value = SystemClock.elapsedRealtime() - startedAt; delay(200) } }
            true
        }.getOrElse { out.delete(); false }
    }

    /** Stops and returns the clip and its length, or null if it was too short or failed. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val length = SystemClock.elapsedRealtime() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release(); recorder = null
        ticker?.cancel(); _recordingMs.value = null
        val f = file; file = null
        if (!ok || f == null || length < MIN_MS) { f?.delete(); return null }
        return f to length
    }

    fun cancel() { stop()?.first?.delete() }

    // ---- Playback ----
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<Pair<String, Float>?>(null)
    /** The voice note playing (its file ID) and how far through it is, 0..1. */
    val playing: StateFlow<Pair<String, Float>?> = _playing.asStateFlow()
    private var progressJob: Job? = null

    fun play(fid: String, file: File) {
        stopPlaying()
        val p = runCatching { MediaPlayer().apply { setDataSource(file.path); prepare(); start() } }.getOrNull() ?: return
        player = p
        p.setOnCompletionListener { stopPlaying(); file.delete() }
        progressJob = scope.launch {
            while (isActive && player === p) {
                _playing.value = fid to (runCatching { p.currentPosition.toFloat() / p.duration.coerceAtLeast(1) }.getOrDefault(0f))
                delay(100)
            }
        }
    }

    fun stopPlaying() {
        progressJob?.cancel()
        runCatching { player?.stop() }
        player?.release(); player = null
        _playing.value = null
    }

    companion object {
        const val MIN_MS = 700L
        const val MAX_MS = 5 * 60_000L
    }
}
