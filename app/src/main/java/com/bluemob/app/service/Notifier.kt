package com.bluemob.app.service

import com.bluemob.app.i18n.Lang
import com.bluemob.app.ui.nav.Route
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bluemob.app.MainActivity
import com.bluemob.app.R
import com.bluemob.app.mesh.SosSignal

/** BlueMob's notifications: the quiet "mesh is on" one, SOS alerts, messages, and rescue-group updates. */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Do not disturb: messages and other notes arrive silently. SOS alerts and rescue groups always sound. */
    var quiet: () -> Boolean = { false }
    /** Full silence: every notification goes out silently, whatever its kind (see AppSettings.fullSilence). */
    var silent: () -> Boolean = { false }
    private fun ch(normal: String) = if (quiet()) CH_QUIET else normal

    init {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESH, "Mesh running", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while BlueMob listens for people nearby in the background"
        })
        nm.createNotificationChannel(NotificationChannel(CH_SOS, "SOS from people nearby", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Someone near you needs help"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300, 600, 900, 200, 900, 200, 900, 600, 300, 200, 300, 200, 300)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_NEWS, "News from BlueMob", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Alerts, updates and tips from the BlueMob team"
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUIET, "During do not disturb", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Messages, missed calls and invites while BlueMob's do not disturb is on: no sound or vibration"
            setSound(null, null); enableVibration(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when someone calls you, even with BlueMob closed"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        // Default importance, but silent: Android 13+ hides "silent" (low) notifications from the lock screen, which
        // is the one place this card has to show. The first version used a low channel; it's replaced.
        // High (but silent) for the same reason: some phones (Motorola, Android 15+) only put high-priority
        // notifications on the lock screen by default.
        listOf("emergency_card", "emergency_card_v2").forEach { old -> runCatching { nm.deleteNotificationChannel(old) } }
        nm.createNotificationChannel(NotificationChannel(CH_CARD, "Emergency info on the lock screen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Your blood group and emergency contacts, readable without unlocking, if you turn it on"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_RESCUE, "Rescue groups", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Who's coming to help, and messages in rescue groups"
        })
    }

    fun ongoing(connected: Int, carrying: Int, meshOn: Boolean = true): Notification = NotificationCompat.Builder(context, CH_MESH)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t(when {
            !meshOn -> "BlueMob is on: calls and messages can reach you"
            connected == 0 -> "BlueMob is listening for people nearby"
            else -> "Connected to $connected ${if (connected == 1) "person" else "people"} nearby"
        }))
        .setContentText(t((if (meshOn) "Calls, messages and SOS reach you with BlueMob closed" else "Over the internet. Turn on the mesh to reach people nearby too") +
            if (carrying > 0) " · carrying $carrying for others" else ""))
        .setOngoing(true).setSilent(true).setShowWhen(false)
        .setContentIntent(open(null, 1))
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun updateOngoing(connected: Int, carrying: Int, meshOn: Boolean = true) = post(ONGOING_ID, ongoing(connected, carrying, meshOn))

    fun sos(s: SosSignal) = post(s.id.hashCode(), NotificationCompat.Builder(context, CH_SOS)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t("🆘 ${s.name} needs help"))
        .setContentText(t(s.note.ifBlank { "Tap to see where they are" }))
        .setStyle(NotificationCompat.BigTextStyle().bigText(t((if (s.note.isNotBlank()) "“${s.note}”\n" else "") + (s.pos?.describe() ?: ""))))
        .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
        .setColor(0xFFD94F55.toInt()).setAutoCancel(true)
        .setContentIntent(open(null, 2))
        // On phones that allow it, the SOS alert opens over the lock screen, like an incoming call.
        .setFullScreenIntent(open(null, 3), true)
        .addAction(0, "I'm coming", open(Route.SosComing(s.id).key, 4 + s.id.hashCode()))
        .addAction(0, "Open map", mapFor(s) ?: open(null, 5 + s.id.hashCode()))
        .build())

    /** "Ended" for an SOS whose alert is still showing. */
    fun sosEnded(s: SosSignal) = cancel(s.id.hashCode())

    /** Where they are, in the phone's maps app (works offline with downloaded maps), or null if we don't know. */
    private fun mapFor(s: SosSignal): PendingIntent? {
        val lat = s.lat ?: return null
        val lon = s.lon ?: return null
        val uri = android.net.Uri.parse("geo:%.6f,%.6f?q=%.6f,%.6f(%s)".format(java.util.Locale.US, lat, lon, lat, lon, android.net.Uri.encode("SOS: " + s.name)))
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) == null) return null
        return PendingIntent.getActivity(context, 6 + s.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun message(fromId: String, name: String, text: String) = post(fromId.hashCode(), NotificationCompat.Builder(context, ch(CH_MSG))
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        // People's names and words stay as they wrote them.
        .setContentTitle(name).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setCategory(NotificationCompat.CATEGORY_MESSAGE).setAutoCancel(true)
        .setContentIntent(open(Route.Chat(fromId).key, fromId.hashCode()))
        .build())

    /** News from the BlueMob team. Urgent alerts (a flood warning) sound even during do not disturb. */
    fun news(id: String, title: String, text: String, urgent: Boolean) = post(id.hashCode() + 31, NotificationCompat.Builder(context, if (urgent) CH_RESCUE else ch(CH_NEWS))
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t(title)).setContentText(t(text))
        .setStyle(NotificationCompat.BigTextStyle().bigText(t(text)))
        .setAutoCancel(true).setContentIntent(open(Route.News.key, id.hashCode() + 31))
        .build())

    fun rating(text: String) = post(4242, NotificationCompat.Builder(context, ch(CH_MSG))
        .setSmallIcon(R.drawable.ic_stat_bluemob).setContentTitle(t("Your rating")).setContentText(t(text)).setAutoCancel(true)
        .setContentIntent(open(Route.Person(ME).key, 4242)).build())

    fun rescue(room: String, text: String) = post(room.hashCode() + 7, NotificationCompat.Builder(context, CH_RESCUE)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t("Rescue group")).setContentText(t(text))
        .setColor(0xFFD94F55.toInt()).setAutoCancel(true)
        .setContentIntent(open(Route.Rescue(room).key, room.hashCode() + 7))
        .build())

    /** Anything else worth knowing right away: someone ringing to find us, a game invite, a missed call. */
    fun note(title: String, text: String, route: String? = null, id: Int = title.hashCode() + 11) = post(id, NotificationCompat.Builder(context, ch(CH_RESCUE))
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t(title)).setContentText(t(text))
        .setStyle(NotificationCompat.BigTextStyle().bigText(t(text)))
        .setColor(0xFF2F6F62.toInt()).setAutoCancel(true)
        .setContentIntent(open(route, id))
        .build())

    /** For a trip group's admin or captain: a phone didn't answer the roll call, or the trip has ended. Rings. */
    fun tripAlert(gid: String, title: String, text: String) = post(gid.hashCode() + 19, NotificationCompat.Builder(context, CH_RESCUE)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
        .setColor(0xFFF59E0B.toInt()).setAutoCancel(true)
        .setContentIntent(open(Route.Group(gid).key, gid.hashCode() + 19))
        .build())

    /** Counting down to an SOS the phone started by itself, with a big "Cancel". [why] says what happened. */
    fun sosCountdown(secondsLeft: Int, why: String) {
        val cancel = PendingIntent.getBroadcast(context, 77, android.content.Intent(context, com.bluemob.app.safety.SosCountdown.Cancel::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(POWER_SOS_ID, NotificationCompat.Builder(context, CH_SOS)
            .setSmallIcon(R.drawable.ic_stat_bluemob)
            .setContentTitle(Lang.tr("🆘 Sending SOS in {} s").replace("{}", "$secondsLeft"))
            .setContentText(Lang.tr(why) + " " + Lang.tr("Tap Cancel if you're OK."))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true).setColor(0xFFD94F55.toInt())
            .addAction(0, Lang.tr("Cancel"), cancel)
            .build())
    }

    /** The check-in timer asking "Are you back?", with I'm back and +30 min. */
    fun checkIn(id: Int, title: String, text: String, urgent: Boolean) {
        fun action(kind: String, code: Int) = PendingIntent.getBroadcast(context, code,
            android.content.Intent(context, com.bluemob.app.safety.CheckInTimer.Action::class.java).putExtra(com.bluemob.app.safety.CheckInTimer.EXTRA_KIND, kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(id, NotificationCompat.Builder(context, if (urgent) CH_SOS else CH_RESCUE)
            .setSmallIcon(R.drawable.ic_stat_bluemob)
            .setContentTitle(Lang.tr(title)).setContentText(Lang.tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(Lang.tr(text)))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setColor(0xFFF59E0B.toInt()).setOngoing(urgent)
            .addAction(0, Lang.tr("I'm back"), action(com.bluemob.app.safety.CheckInTimer.ACTION_BACK, 81))
            .addAction(0, Lang.tr("+30 min"), action(com.bluemob.app.safety.CheckInTimer.ACTION_EXTEND, 82))
            .setContentIntent(open(Route.CheckIn.key, 83))
            .build())
    }

    fun cancelSosCountdown() = runCatching { manager.cancel(POWER_SOS_ID) }

    /** Notification text in the app's language. */
    private fun t(s: CharSequence?): String = Lang.tr(s?.toString() ?: "")

    private fun post(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Full silence: anything that would sound is moved to the quiet channel (the ongoing one and the card are silent anyway).
        val out = if (silent() && n.channelId != CH_MESH && n.channelId != CH_CARD && n.channelId != CH_QUIET)
            runCatching { Notification.Builder.recoverBuilder(context, n).setChannelId(CH_QUIET).build() }.getOrDefault(n) else n
        runCatching { manager.notify(id, out) }
    }

    /**
     * Someone is calling while BlueMob was closed: rings and opens over the lock screen like a phone call. Opening it
     * starts BlueMob, which connects and picks up the call (the caller keeps ringing meanwhile).
     */
    fun incomingCall(name: String, video: Boolean) = post(INCOMING_CALL_ID, NotificationCompat.Builder(context, CH_CALLS)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t("$name is calling"))
        .setContentText(t("${if (video) "Video" else "Voice"} call on BlueMob · tap to answer"))
        .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true).setTimeoutAfter(45_000)
        .setContentIntent(open(Route.Call.key, 10))
        .setFullScreenIntent(open(Route.Call.key, 11), true)
        .build())

    /** The emergency card: readable on the lock screen, so whoever finds you knows who to call. */
    fun emergencyCard(title: String, text: String) = post(EMERGENCY_CARD_ID, NotificationCompat.Builder(context, CH_CARD)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(t(title)).setContentText(t(text))
        .setStyle(NotificationCompat.BigTextStyle().bigText(t(text)))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setSilent(true).setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_STATUS).setOnlyAlertOnce(true)
        .setColor(0xFFD94F55.toInt())
        .setContentIntent(open(Route.Account.key, 12))
        .build())

    fun cancel(id: Int) { runCatching { manager.cancel(id) } }

    /** Opens BlueMob on the call that's going on. */
    fun openCall(): PendingIntent = open(Route.Call.key, 9)

    private fun open(route: String?, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply { route?.let { putExtra(EXTRA_ROUTE, it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ONGOING_ID = 1001
        const val EMERGENCY_CARD_ID = 1009
        const val CH_CARD = "emergency_card_v3"
        const val EXTRA_ROUTE = "route"
        private const val POWER_SOS_ID = 7707
        /** Stands for this phone's own ID in a notification's route; the app swaps in the real one. */
        const val ME = "me"
        const val CH_MESH = "mesh"
        const val CALL_ID = 1002
        /** Same ID as the in-app incoming-call note, so one replaces the other. */
        const val INCOMING_CALL_ID = 7_007
        private const val CH_CALLS = "calls"
        private const val CH_SOS = "sos"
        private const val CH_MSG = "messages"
        private const val CH_QUIET = "quiet"
        private const val CH_NEWS = "news"
        private const val CH_RESCUE = "rescue"
    }
}
