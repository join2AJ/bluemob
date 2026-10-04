package com.bluemob.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** The runtime permissions Nearby Connections needs, which differ by Android version. */
object MeshPermissions {

    val required: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun allGranted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * On Android 12 and below, Bluetooth/Wi-Fi scanning silently finds nothing unless the
     * location switch in quick settings is on (no GPS fix or internet is needed).
     */
    fun needsLocationServices(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(lm)
    }
}
