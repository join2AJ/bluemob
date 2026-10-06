package com.bluemob.app.service

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
        nm.createNotificationChannel(NotificationChannel(CH_RESCUE, "Rescue groups", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Who's coming to help, and messages in rescue groups"
        })
    }

    fun ongoing(connected: Int, carrying: Int): Notification = NotificationCompat.Builder(context, CH_MESH)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(if (connected == 0) "BlueMob is listening for people nearby" else "Connected to $connected ${if (connected == 1) "person" else "people"} nearby")
        .setContentText("SOS and messages reach you with the screen off" + if (carrying > 0) " · carrying $carrying for others" else "")
        .setOngoing(true).setSilent(true).setShowWhen(false)
        .setContentIntent(open(null, 1))
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun updateOngoing(connected: Int, carrying: Int) = post(ONGOING_ID, ongoing(connected, carrying))

    fun sos(s: SosSignal) = post(s.id.hashCode(), NotificationCompat.Builder(context, CH_SOS)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle("🆘 ${s.name} needs help")
        .setContentText(s.note.ifBlank { "Tap to see where they are" })
        .setStyle(NotificationCompat.BigTextStyle().bigText((if (s.note.isNotBlank()) "“${s.note}”\n" else "") + (s.pos?.describe() ?: "")))
        .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
        .setColor(0xFFD94F55.toInt()).setAutoCancel(true)
        .setContentIntent(open(null, 2))
        // On phones that allow it, the SOS alert opens over the lock screen, like an incoming call.
        .setFullScreenIntent(open(null, 3), true)
        .build())

    fun message(fromId: String, name: String, text: String) = post(fromId.hashCode(), NotificationCompat.Builder(context, CH_MSG)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(name).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setCategory(NotificationCompat.CATEGORY_MESSAGE).setAutoCancel(true)
        .setContentIntent(open("chat:$fromId", fromId.hashCode()))
        .build())

    fun rating(text: String) = post(4242, NotificationCompat.Builder(context, CH_MSG)
        .setSmallIcon(R.drawable.ic_stat_bluemob).setContentTitle("Your rating").setContentText(text).setAutoCancel(true)
        .setContentIntent(open("person:me", 4242)).build())

    fun rescue(room: String, text: String) = post(room.hashCode() + 7, NotificationCompat.Builder(context, CH_RESCUE)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle("Rescue group").setContentText(text)
        .setColor(0xFFD94F55.toInt()).setAutoCancel(true)
        .setContentIntent(open("rescue:$room", room.hashCode() + 7))
        .build())

    /** Anything else worth knowing right away: someone ringing to find us, a game invite, a missed call. */
    fun note(title: String, text: String, route: String? = null, id: Int = title.hashCode() + 11) = post(id, NotificationCompat.Builder(context, CH_RESCUE)
        .setSmallIcon(R.drawable.ic_stat_bluemob)
        .setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(0xFF2F6F62.toInt()).setAutoCancel(true)
        .setContentIntent(open(route, id))
        .build())

    private fun post(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { manager.notify(id, n) }
    }

    private fun open(route: String?, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply { route?.let { putExtra(EXTRA_ROUTE, it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ONGOING_ID = 1001
        const val EXTRA_ROUTE = "route"
        private const val CH_MESH = "mesh"
        private const val CH_SOS = "sos"
        private const val CH_MSG = "messages"
        private const val CH_RESCUE = "rescue"
    }
}
