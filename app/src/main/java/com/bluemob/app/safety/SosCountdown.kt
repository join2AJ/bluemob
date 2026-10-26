package com.bluemob.app.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.bluemob.app.BlueMobApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An SOS that sends itself unless cancelled: used when the phone notices trouble on its own (power button pressed
 * 5 times, a hard fall, a missed check-in). It vibrates every second and shows a notification with a big Cancel.
 */
class SosCountdown(private val app: BlueMobApp) {
    private var job: Job? = null
    val running: Boolean get() = job?.isActive == true

    /** Counts down [seconds], then sends an SOS with [note]. [why] explains it on the notification. */
    fun start(seconds: Int, why: String, note: String, audit: String) {
        if (running || app.sos.mine.value != null) return
        job = app.appScope.launch {
            for (left in seconds downTo 1) {
                buzz(if (left <= 5) 500 else 250)
                app.notifier.sosCountdown(left, why)
                delay(1_000)
            }
            app.notifier.cancelSosCountdown()
            app.sos.send(note)
            app.audit.add(com.bluemob.app.audit.AuditKind.APP, audit)
            buzz(1_200)
            app.notifier.note("🆘 SOS sent", "People nearby and your SOS contacts are being alerted. Open BlueMob to see who is coming, or to say you're safe.",
                com.bluemob.app.ui.nav.Route.Sos.key)
        }
    }

    fun cancel() {
        job?.cancel(); job = null
        app.notifier.cancelSosCountdown()
    }

    private fun buzz(ms: Long) = runCatching {
        val v = app.getSystemService(Vibrator::class.java) ?: return@runCatching
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    /** The "Cancel" button on the countdown notification. */
    class Cancel : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            (context.applicationContext as? BlueMobApp)?.countdown?.cancel()
        }
    }
}
