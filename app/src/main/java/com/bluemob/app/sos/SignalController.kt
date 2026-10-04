package com.bluemob.app.sos

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Drives the physical SOS signals: the camera flashlight and a loud whistle-pitch tone.
 * The screen flash is drawn by the UI. Failures (no flash, camera busy) are ignored: the other signals still work.
 */
class SignalController(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        cameras?.cameraIdList?.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    val hasTorch: Boolean get() = torchId != null

    fun torch(on: Boolean) {
        val id = torchId ?: return
        runCatching { cameras?.setTorchMode(id, on) }
    }

    /** Plays a ~2.8 kHz tone, close to a rescue whistle, for [ms] milliseconds. */
    fun beep(ms: Int) {
        runCatching {
            val rate = 22_050
            val n = rate * ms / 1000
            val samples = ShortArray(n) { i ->
                // Short fade in/out avoids clicks.
                val env = minOf(1.0, i / 200.0, (n - i) / 200.0)
                (sin(2 * PI * TONE_HZ * i / rate) * Short.MAX_VALUE * 0.8 * env).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
            track.write(samples, 0, n)
            track.setNotificationMarkerPosition(n)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        }
    }

    fun stop() = torch(false)

    companion object {
        private const val TONE_HZ = 2800.0
        private const val DOT = 300

        /** ··· ––– ··· as (on, milliseconds) steps: dots 1 unit, dashes 3, gaps 1 / 3 / 7. */
        val SOS_PATTERN: List<Pair<Boolean, Int>> = buildList {
            fun letter(unit: Int, last: Int) {
                repeat(3) { i -> add(true to unit); add(false to if (i < 2) DOT else last) }
            }
            letter(DOT, DOT * 3)
            letter(DOT * 3, DOT * 3)
            letter(DOT, DOT * 7)
        }
    }
}
