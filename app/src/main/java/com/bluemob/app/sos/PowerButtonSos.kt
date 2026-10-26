package com.bluemob.app.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.bluemob.app.BlueMobApp

/**
 * Five quick presses: a press of the power button turns the screen off or on, so 5 changes within [WINDOW_MS] count.
 * Pure logic, so it can be tested without a phone.
 */
class PressCounter(private val presses: Int = PRESSES, private val windowMs: Long = WINDOW_MS) {
    private val times = ArrayDeque<Long>()

    /** A screen change at [now]. True when it completes the pattern (and starts counting afresh). */
    fun press(now: Long): Boolean {
        times.addLast(now)
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        if (times.size >= presses) { times.clear(); return true }
        return false
    }

    companion object {
        const val PRESSES = 5
        const val WINDOW_MS = 4_000L
    }
}

/**
 * "Press the power button 5 times" sends an SOS, even with the phone locked in a pocket. It runs while BlueMob's
 * background service does (Background on), and only if the user switched it on.
 *
 * After the presses the phone vibrates and counts down [COUNTDOWN_S] seconds with a "Cancel" button, so a pocket
 * accident can be stopped; then the SOS goes out as from the SOS button.
 */
class PowerButtonSos(private val app: BlueMobApp) {
    private val counter = PressCounter()
    private var registered = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!app.countdown.running && counter.press(System.currentTimeMillis()))
                app.countdown.start(COUNTDOWN_S, "You pressed the power button 5 times.", "Sent with the power button",
                    "SOS sent by pressing the power button 5 times")
        }
    }

    /** Listens while [on]: called by the background service as the setting changes. */
    fun listen(on: Boolean) {
        if (on == registered) return
        if (on) {
            val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
            ContextCompat.registerReceiver(app, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else runCatching { app.unregisterReceiver(screen) }
        registered = on
    }

    companion object {
        const val COUNTDOWN_S = 5
    }
}
