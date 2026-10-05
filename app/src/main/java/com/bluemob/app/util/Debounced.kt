package com.bluemob.app.util

import android.os.Handler
import android.os.Looper

/** Runs [action] once, [delayMs] after the last call: for saving state that changes many times a second. */
class Debounced(private val delayMs: Long = 1_000, private val action: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val run = Runnable(action)
    operator fun invoke() {
        handler.removeCallbacks(run)
        handler.postDelayed(run, delayMs)
    }
}
