package com.bluemob.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.bluemob.app.mesh.NearbyMeshTransport
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Two-way voice over a direct phone-to-phone link: 8 kHz μ-law frames of [FRAME_MS] ms, each sent as one small
 * packet `A | seq(2) | μ-law samples`. Playback keeps at most about half a second queued, dropping the oldest
 * audio rather than letting the delay grow when the link stutters.
 */
class VoiceLink(context: Context, private val send: (ByteArray) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    /**
     * One call's audio. Each call gets its own, so threads from a call that just ended can never keep the
     * microphone or eat the next call's audio, and sequence numbers start fresh.
     */
    private class Session {
        @Volatile var running = true
        @Volatile var restartMic = false
        val queue = ArrayBlockingQueue<ShortArray>(QUEUE_MAX)
        var lastSeq = -1
    }

    @Volatile private var session: Session? = null
    @Volatile var muted = false
    private var recorder: Thread? = null
    private var player: Thread? = null
    private var oldMode = AudioManager.MODE_NORMAL

    /** True while the microphone is actually recording; false if Android refused it. */
    @Volatile var micWorking = false
        private set

    /** For the call screen's details and for problem reports: where voice stops, if it does. */
    data class Stats(val sent: Int, val received: Int, val played: Int, val micRestarts: Int, val speakerRestarts: Int)
    @Volatile private var sent = 0
    @Volatile private var received = 0
    @Volatile private var played = 0
    @Volatile private var micRestarts = 0
    @Volatile private var speakerRestarts = 0
    fun stats() = Stats(sent, received, played, micRestarts, speakerRestarts)

    private var focus: android.media.AudioFocusRequest? = null

    @SuppressLint("MissingPermission")
    @Synchronized fun start(speaker: Boolean) {
        // A session left over from an earlier call (it should never happen) would keep the microphone: replace it.
        if (session != null) stop()
        val s = Session()
        session = s
        sent = 0; received = 0; played = 0; micRestarts = 0; speakerRestarts = 0
        oldMode = audio.mode
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        // Some phones keep a call's microphone and speaker quiet unless the app holds audio focus.
        if (Build.VERSION.SDK_INT >= 26) runCatching {
            focus = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }.build().also { audio.requestAudioFocus(it) }
        }
        setSpeaker(speaker)
        recorder = Thread({ record(s) }, "bluemob-mic").apply { start() }
        player = Thread({ play(s) }, "bluemob-speaker").apply { start() }
    }

    @Synchronized fun stop() {
        val s = session ?: return
        session = null
        s.running = false
        recorder?.interrupt(); player?.interrupt()
        // Let the microphone and speaker go before a new call can ask for them.
        runCatching { recorder?.join(700) }
        runCatching { player?.join(400) }
        recorder = null; player = null
        s.queue.clear()
        micWorking = false
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else @Suppress("DEPRECATION") { audio.isSpeakerphoneOn = false }
            audio.mode = oldMode
        }
        if (Build.VERSION.SDK_INT >= 26) focus?.let { f -> runCatching { audio.abandonAudioFocusRequest(f) } }
        focus = null
    }

    /** The other phone hears nothing from us: start the microphone again from scratch. */
    fun restartMic() { session?.restartMic = true }

    fun setSpeaker(on: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                if (on) audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { audio.setCommunicationDevice(it) }
                else audio.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = on
            }
        }
    }

    /** A voice packet from the other phone. */
    fun onPacket(bytes: ByteArray) {
        val s = session ?: return
        if (bytes.size < 4 || bytes[0] != NearbyMeshTransport.MEDIA_AUDIO) return
        val seq = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        if (!SeqWindow.accept(s.lastSeq, seq)) return
        s.lastSeq = seq
        val pcm = MuLaw.decode(bytes, 3)
        received++
        while (s.queue.size >= QUEUE_KEEP) s.queue.poll()
        s.queue.offer(pcm)
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Voice-call input first (with the phone's own echo cancelling); plain microphone if a phone refuses it.
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)) {
            val rec = runCatching { AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 4)) }.getOrNull()
            if (rec?.state == AudioRecord.STATE_INITIALIZED) return rec
            rec?.release()
        }
        return null
    }

    /**
     * Records and sends our voice. If Android takes the microphone away mid-call (a route change, another app, the
     * phone silencing it), the recorder reports an error or gives only silence: then it's rebuilt, instead of the
     * call carrying on without our voice.
     */
    private fun record(s: Session) {
        val pcm = ShortArray(FRAME)
        var seq = 0
        while (s.running) {
            val rec = openRecorder() ?: run {
                Log.w(TAG, "Microphone unavailable"); micWorking = false
                if (!sleep(s, 1_000)) return
                micRestarts++
                null
            } ?: continue
            val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            var silentFrames = 0
            try {
                rec.startRecording()
                micWorking = rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
                if (!micWorking) { if (!sleep(s, 500)) return; micRestarts++; continue }
                while (s.running && !s.restartMic) {
                    var got = 0
                    var failed = false
                    while (got < FRAME && s.running) {
                        val n = rec.read(pcm, got, FRAME - got)
                        if (n < 0) { failed = true; break }
                        if (n == 0) break
                        got += n
                    }
                    if (failed) { Log.w(TAG, "Microphone dropped by Android: restarting it"); break }
                    if (got < FRAME) continue
                    // Exact digital silence for a few seconds means the phone muted our capture, not a quiet room.
                    silentFrames = if (pcm.all { it.toInt() == 0 }) silentFrames + 1 else 0
                    if (silentFrames >= SILENT_RESTART && !muted) { Log.w(TAG, "Microphone gives only silence: restarting it"); break }
                    if (muted) continue
                    val packet = ByteArray(3 + FRAME)
                    packet[0] = NearbyMeshTransport.MEDIA_AUDIO
                    packet[1] = (seq shr 8).toByte(); packet[2] = seq.toByte()
                    MuLaw.encode(pcm, FRAME, packet, 3)
                    seq = (seq + 1) and 0xFFFF
                    send(packet)
                    sent++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Recording stopped", e)
            } finally {
                runCatching { rec.stop() }
                rec.release(); aec?.release(); ns?.release()
            }
            s.restartMic = false
            if (s.running) { micRestarts++; if (!sleep(s, 200)) return }
        }
    }

    private fun openTrack(): AudioTrack? {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(min, FRAME * 2 * 2))
                .build()
        }.getOrNull()?.takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    }

    /** Plays their voice. If Android drops the speaker (a route change, Bluetooth headset), it's rebuilt. */
    private fun play(s: Session) {
        while (s.running) {
            val track = openTrack() ?: run { if (!sleep(s, 1_000)) return; speakerRestarts++; null } ?: continue
            try {
                track.play()
                while (s.running) {
                    val pcm = s.queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    val n = track.write(pcm, 0, pcm.size)
                    if (n < 0) { Log.w(TAG, "Speaker dropped by Android ($n): restarting it"); break }
                    played++
                }
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                Log.w(TAG, "Playback stopped", e)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
            if (s.running) { speakerRestarts++; if (!sleep(s, 200)) return }
        }
    }

    /** Waits, unless the call ends meanwhile. False if it ended. */
    private fun sleep(s: Session, ms: Long): Boolean = try { Thread.sleep(ms); s.running } catch (_: InterruptedException) { false }

    /** Which audio frames to play: in order, skipping late or repeated ones. Sequence numbers wrap at 65536. */
    object SeqWindow {
        fun accept(last: Int, seq: Int): Boolean = last < 0 || ((seq - last) and 0xFFFF).let { it != 0 && it < 0x8000 }
    }

    companion object {
        private const val TAG = "BlueMobCall"
        const val RATE = 8_000
        const val FRAME_MS = 60
        const val FRAME = RATE * FRAME_MS / 1000
        /** About half a second of audio at most; more and we drop the oldest to keep the delay down. */
        private const val QUEUE_KEEP = 8
        private const val QUEUE_MAX = 16
        /** About 3 seconds of exact silence. */
        private const val SILENT_RESTART = 50
    }
}
