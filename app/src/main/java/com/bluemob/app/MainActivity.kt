package com.bluemob.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.bluemob.app.permissions.MeshPermissions
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.BlueMobRoot
import com.bluemob.app.ui.SystemStatus
import com.bluemob.app.ui.theme.BlueMobTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val systemStatus = mutableStateOf(SystemStatus(permissionsGranted = false, locationServicesOff = false))

    private val meshPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
            // Get going straight away once the user says yes.
            if (systemStatus.value.permissionsGranted) viewModel.startMesh()
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                viewModel.setShareLocation(true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueMobTheme {
                BlueMobRoot(
                    vm = viewModel,
                    system = systemStatus.value,
                    onRequestPermissions = { meshPermissionLauncher.launch(MeshPermissions.required) },
                    onOpenLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onEnableLocationSharing = {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        systemStatus.value = SystemStatus(
            permissionsGranted = MeshPermissions.allGranted(this),
            locationServicesOff = MeshPermissions.needsLocationServices(this),
        )
    }
}
