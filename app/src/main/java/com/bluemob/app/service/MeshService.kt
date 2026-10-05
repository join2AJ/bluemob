package com.bluemob.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bluemob.app.BlueMobApp
import com.bluemob.app.mesh.PeerState
import com.bluemob.app.permissions.MeshPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps BlueMob's mesh running when the app is in the background or the screen is off, so an SOS or a message
 * still reaches this phone, and messages it carries for others keep moving. Android requires a visible
 * notification for this; it's the quiet "BlueMob is listening for people nearby" one.
 */
class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = application as BlueMobApp
        // Android 14 refuses this kind of service without Bluetooth permission (e.g. if it was revoked): stop quietly.
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifier.ONGOING_ID, app.notifier.ongoing(0, app.mesh.router.carrying),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        }.isSuccess
        if (!started) { stopSelf(); return }
        scope.launch {
            app.mesh.peers.collect { peers ->
                val connected = peers.values.count { it.state == PeerState.CONNECTED }
                app.notifier.updateOngoing(connected, app.mesh.router.carrying)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BlueMobApp
        // After Android restarts the service (e.g. it was stopped for memory), bring the mesh back.
        if (!app.mesh.running.value && MeshPermissions.allGranted(this)) app.mesh.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java)) }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }
}
