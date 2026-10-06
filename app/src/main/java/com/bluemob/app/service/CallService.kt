package com.bluemob.app.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bluemob.app.BlueMobApp
import com.bluemob.app.R

/**
 * Runs during a call. Android silences the microphone of an app that isn't on screen unless a "microphone"
 * foreground service is running, so without this the other person hears nothing as soon as you lock the phone or
 * open another app. It also keeps the network going for calls over the internet.
 */
class CallService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BlueMobApp
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (granted(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video && granted(Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        val name = app.calls.call.value?.name ?: "BlueMob"
        val n = NotificationCompat.Builder(this, Notifier.CH_MESH)
            .setSmallIcon(R.drawable.ic_stat_bluemob)
            .setContentTitle("${if (video) "Video" else "Voice"} call with $name")
            .setContentText("Tap to go back to the call")
            .setOngoing(true).setSilent(true).setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(app.notifier.openCall())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val ok = runCatching { ServiceCompat.startForeground(this, Notifier.CALL_ID, n, type) }.isSuccess
        if (!ok) stopSelf()
        return START_NOT_STICKY
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_VIDEO = "video"

        fun update(context: Context, active: Boolean, video: Boolean) {
            if (active) runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java).putExtra(EXTRA_VIDEO, video)) }
            else runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
