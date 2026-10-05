package com.bluemob.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.bluemob.app.permissions.MeshPermissions
import com.bluemob.app.system.Radio
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.BlueMobRoot
import com.bluemob.app.ui.SystemActions
import com.bluemob.app.ui.SystemStatus
import com.bluemob.app.ui.theme.BlueMobTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val systemStatus = mutableStateOf(SystemStatus(false, false, false, false))

    private val meshPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
            if (systemStatus.value.permissionsGranted) viewModel.startMesh()
        }

    private var shareAfterGrant = false
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            refreshStatus()
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted && shareAfterGrant) viewModel.setShareLocation(true)
            shareAfterGrant = false
        }

    private val stepPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.onStepPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = SystemActions(
            requestMeshPermissions = { meshPermissionLauncher.launch(MeshPermissions.required + notificationPermission()) },
            openLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            enableLocationSharing = { shareAfterGrant = true; requestLocation() },
            requestLocation = { shareAfterGrant = false; requestLocation() },
            openBatterySaver = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), fallback = Intent(Settings.ACTION_SETTINGS)) },
            askKeepRunning = ::askKeepRunning,
            switchRadio = ::switchRadio,
            shareId = {
                val text = "Message me on BlueMob, even with no signal: BM ${com.bluemob.app.util.formatId(viewModel.nodeId)}"
                runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your BlueMob ID")) }
            },
            textSos = ::textSos,
            requestSteps = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            },
        )
        handleRoute(intent)
        // Show over the lock screen only while an SOS alert is up, never for chats.
        lifecycleScope.launch {
            viewModel.sosAlert.collect { alert ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(alert != null); setTurnScreenOn(alert != null) }
            }
        }
        setContent {
            BlueMobTheme {
                BlueMobRoot(vm = viewModel, system = systemStatus.value, actions = actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRoute(intent)
    }

    /** A notification asked to open a chat or rescue group. */
    private fun handleRoute(intent: Intent?) {
        intent?.getStringExtra(com.bluemob.app.service.Notifier.EXTRA_ROUTE)?.let { viewModel.pendingRoute.value = it }
        intent?.removeExtra(com.bluemob.app.service.Notifier.EXTRA_ROUTE)
    }

    private fun notificationPermission(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    override fun onResume() {
        super.onResume()
        refreshStatus()
        viewModel.refreshRadios()
    }

    /**
     * Android doesn't let apps switch radios on their own (since Android 10), so each switch opens the closest
     * system panel or screen, and the user comes straight back.
     */
    @SuppressLint("MissingPermission")
    private fun switchRadio(radio: Radio, on: Boolean) {
        val settings = Intent(Settings.ACTION_SETTINGS)
        when (radio) {
            Radio.BLUETOOTH -> when {
                !on -> open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), settings)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                    meshPermissionLauncher.launch(MeshPermissions.required)
                else -> open(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            Radio.WIFI -> open(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS), settings)
            Radio.LOCATION -> open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), settings)
            Radio.INTERNET -> open(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS), settings,
            )
        }
    }

    /** Opens the SMS app with the SOS written in, addressed to [numbers]. The user taps send. */
    private fun textSos(numbers: List<String>, body: String) {
        val to = numbers.joinToString(";") { it.filter { ch -> ch.isDigit() || ch == '+' } }
        open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", body),
            fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$to")).putExtra("sms_body", body))
    }

    private fun requestLocation() {
        if (hasLocation()) { if (shareAfterGrant) viewModel.setShareLocation(true); shareAfterGrant = false; return }
        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /** Asks Android to let BlueMob keep running while Battery Saver is on, so messages and SOS still arrive. */
    @SuppressLint("BatteryLife")
    private fun askKeepRunning() {
        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun open(intent: Intent, fallback: Intent) {
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(fallback) } }
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        systemStatus.value = SystemStatus(
            permissionsGranted = MeshPermissions.allGranted(this),
            locationServicesOff = MeshPermissions.needsLocationServices(this),
            keepsRunning = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true,
            locationPermission = hasLocation(),
        )
    }
}
