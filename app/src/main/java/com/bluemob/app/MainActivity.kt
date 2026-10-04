package com.bluemob.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.bluemob.app.permissions.MeshPermissions
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = SystemActions(
            requestMeshPermissions = { meshPermissionLauncher.launch(MeshPermissions.required) },
            openLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            enableLocationSharing = { shareAfterGrant = true; requestLocation() },
            requestLocation = { shareAfterGrant = false; requestLocation() },
            openBatterySaver = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), fallback = Intent(Settings.ACTION_SETTINGS)) },
            askKeepRunning = ::askKeepRunning,
        )
        setContent {
            BlueMobTheme {
                BlueMobRoot(vm = viewModel, system = systemStatus.value, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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
