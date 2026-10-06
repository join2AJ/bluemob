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

    @SuppressLint("MissingPermission")
    fun start(speaker: Boolean) {
        if (session != null) return
        val s = Session()
        session = s
        oldMode = audio.mode
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        setSpeaker(speaker)
        recorder = Thread({ record(s) }, "bluemob-mic").apply { start() }
        player = Thread({ play(s) }, "bluemob-speaker").apply { start() }
    }

    fun stop() {
        val s = session ?: return
        session = null
        s.running = false
        recorder?.interrupt(); player?.interrupt()
        // Let the microphone go before a new call can ask for it.
        runCatching { recorder?.join(500) }
        recorder = null; player = null
        s.queue.clear()
        micWorking = false
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else @Suppress("DEPRECATION") { audio.isSpeakerphoneOn = false }
            audio.mode = oldMode
        }
    }

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
        while (s.queue.size >= QUEUE_KEEP) s.queue.poll()
        s.queue.offer(pcm)
    }

    @SuppressLint("MissingPermission")
    private fun record(s: Session) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 4))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: run { Log.w(TAG, "Microphone unavailable"); micWorking = false; return }
        val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val pcm = ShortArray(FRAME)
        var seq = 0
        try {
            rec.startRecording()
            micWorking = rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
            while (s.running) {
                var got = 0
                while (got < FRAME && s.running) {
                    val n = rec.read(pcm, got, FRAME - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < FRAME || muted) continue
                val packet = ByteArray(3 + FRAME)
                packet[0] = NearbyMeshTransport.MEDIA_AUDIO
                packet[1] = (seq shr 8).toByte(); packet[2] = seq.toByte()
                MuLaw.encode(pcm, FRAME, packet, 3)
                seq = (seq + 1) and 0xFFFF
                send(packet)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recording stopped", e)
        } finally {
            runCatching { rec.stop() }
            rec.release(); aec?.release(); ns?.release()
        }
    }

    private fun play(s: Session) {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(min, FRAME * 2 * 2))
                .build()
        }.getOrNull() ?: return
        try {
            track.play()
            while (s.running) {
                val pcm = s.queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                track.write(pcm, 0, pcm.size)
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            Log.w(TAG, "Playback stopped", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

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
    }
}
