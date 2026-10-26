package com.bluemob.app.safety

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bluemob.app.BlueMobApp

/**
 * "I'll be back by 6 pm": if the user hasn't checked in by then, BlueMob asks once, waits [GRACE_MS], then counts down
 * and sends an SOS with their note and last position, to people nearby and their SOS contacts. A reminder comes
 * [REMIND_MS] before. Alarms wake BlueMob even when it's closed (they may arrive a few minutes late in deep sleep).
 */
class CheckInTimer(private val app: BlueMobApp) {
    val due get() = app.settings.checkInDue
    val note get() = app.settings.checkInNote

    fun set(dueAt: Long, note: String) {
        app.settings.setCheckIn(dueAt, note.trim().take(120))
        schedule()
        app.audit.add(com.bluemob.app.audit.AuditKind.APP, "Check-in timer set for " + time(dueAt) + if (note.isNotBlank()) ": \"${note.take(60)}\"" else "")
    }

    fun extend(ms: Long) {
        val base = maxOf(due.value, System.currentTimeMillis())
        app.settings.setCheckIn(base + ms, note.value)
        app.notifier.cancel(NOTE_ID)
        schedule()
    }

    /** "I'm back": the timer ends and nothing is sent. */
    fun done() {
        if (due.value == 0L) return
        app.settings.setCheckIn(0L, "")
        cancelAlarms()
        app.notifier.cancel(NOTE_ID)
        if (app.countdown.running) app.countdown.cancel()
        app.audit.add(com.bluemob.app.audit.AuditKind.APP, "Checked in: back safely")
    }

    /** After BlueMob starts again (e.g. the phone restarted), puts the alarms back. */
    fun restore() { if (due.value > 0L) schedule() }

    private fun schedule() {
        cancelAlarms()
        val d = due.value
        if (d == 0L) return
        val now = System.currentTimeMillis()
        if (d - REMIND_MS > now) alarm(KIND_REMIND, d - REMIND_MS)
        alarm(KIND_DUE, maxOf(d, now + 1_000))
        alarm(KIND_GRACE, maxOf(d, now) + GRACE_MS)
    }

    fun onAlarm(kind: String) {
        val d = due.value
        if (d == 0L) return
        when (kind) {
            KIND_REMIND -> app.notifier.checkIn(NOTE_ID, "⏰ Back by " + time(d) + "?", "Tap I'm back when you are, or add time.", urgent = false)
            KIND_DUE -> app.notifier.checkIn(NOTE_ID, "⏰ Are you back?", "It's past " + time(d) + ". If you don't answer in 10 minutes, BlueMob sends an SOS.", urgent = true)
            KIND_GRACE -> if (System.currentTimeMillis() >= d + GRACE_MS - 60_000) {
                app.notifier.cancel(NOTE_ID)
                app.countdown.start(30, "You didn't check in by " + time(d) + ".", "Didn't check in by " + time(d) + note.value.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                    "Missed check-in at " + time(d) + ": SOS sent")
                app.settings.setCheckIn(0L, "")
            }
        }
    }

    private fun alarm(kind: String, at: Long) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(kind)) }
    }

    private fun cancelAlarms() {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        listOf(KIND_REMIND, KIND_DUE, KIND_GRACE).forEach { runCatching { am.cancel(pending(it)) } }
    }

    private fun pending(kind: String) = PendingIntent.getBroadcast(app, kind.hashCode(),
        Intent(app, Alarm::class.java).putExtra(EXTRA_KIND, kind), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The timer's alarms. */
    class Alarm : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            (context.applicationContext as? BlueMobApp)?.checkIn?.onAlarm(intent.getStringExtra(EXTRA_KIND) ?: return)
        }
    }

    /** "I'm back" and "+30 min" on the notification. */
    class Action : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val t = (context.applicationContext as? BlueMobApp)?.checkIn ?: return
            when (intent.getStringExtra(EXTRA_KIND)) {
                ACTION_BACK -> t.done()
                ACTION_EXTEND -> t.extend(30 * 60_000L)
            }
        }
    }

    companion object {
        const val REMIND_MS = 15 * 60_000L
        const val GRACE_MS = 10 * 60_000L
        const val NOTE_ID = 7708
        const val EXTRA_KIND = "kind"
        const val KIND_REMIND = "remind"
        const val KIND_DUE = "due"
        const val KIND_GRACE = "grace"
        const val ACTION_BACK = "back"
        const val ACTION_EXTEND = "extend"
        fun time(at: Long): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
}
