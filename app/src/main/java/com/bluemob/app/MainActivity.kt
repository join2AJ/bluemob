package com.bluemob.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.bluemob.app.permissions.MeshPermissions
import com.bluemob.app.ui.HomeScreen
import com.bluemob.app.ui.MeshViewModel
import com.bluemob.app.ui.PermissionStatus
import com.bluemob.app.ui.theme.BlueMobTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MeshViewModel by viewModels()
    private val permissionStatus = mutableStateOf(PermissionStatus(granted = false, locationOff = false))

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueMobTheme {
                HomeScreen(
                    viewModel = viewModel,
                    permissions = permissionStatus.value,
                    onRequestPermissions = { permissionLauncher.launch(MeshPermissions.required) },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        )
                    },
                    onOpenLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        permissionStatus.value = PermissionStatus(
            granted = MeshPermissions.allGranted(this),
            locationOff = MeshPermissions.needsLocationServices(this),
        )
    }
}
