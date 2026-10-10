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
 * Keeps BlueMob running when it's closed or the screen is off ("Reachable when closed"): the internet link stays
 * signed in, so calls and messages ring through, and the mesh (when on) keeps finding people nearby and carrying
 * messages for others. Android requires a visible notification for this; it's the quiet ongoing one.
 */
class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = application as BlueMobApp
        // Android 14+: "remote messaging" (calls and messages over the internet) needs no extra permission; the
        // "connected device" type (the Bluetooth mesh) is added only when its permissions are granted.
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
                (if (MeshPermissions.allGranted(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else -> 0
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifier.ONGOING_ID, app.notifier.ongoing(0, app.mesh.router.carrying, app.mesh.running.value), type)
        }.isSuccess
        if (!started) { stopSelf(); return }
        scope.launch {
            kotlinx.coroutines.flow.combine(app.mesh.peers, app.mesh.running) { peers, on -> peers to on }.collect { (peers, on) ->
                val connected = peers.values.count { it.state == PeerState.CONNECTED }
                app.notifier.updateOngoing(connected, app.mesh.router.carrying, on)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BlueMobApp
        // After Android restarts the service (e.g. it was stopped for memory, or after a reboot), bring the mesh back
        // if the user has it start by itself.
        if (intent?.getBooleanExtra(EXTRA_FROM_APP, false) != true && app.settings.meshAtStart.value &&
            !app.mesh.running.value && MeshPermissions.allGranted(this) && app.mesh.radiosAllowed())
            app.mesh.start(useWifi = app.radios.state.value.wifi || app.settings.wifiPolicy.value == com.bluemob.app.settings.RadioPolicy.ALLOW)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Phone restarted or BlueMob updated: start again, so calls and messages keep reaching this phone. */
    class Restart : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext as? BlueMobApp ?: return
            if (app.startupError == null && app.settings.background.value) runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FROM_APP = "from_app"
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).putExtra(EXTRA_FROM_APP, true)) }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }
}
