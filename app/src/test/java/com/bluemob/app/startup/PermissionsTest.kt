package com.bluemob.app.startup

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every feature's Android permission is declared. A missing INTERNET permission once crashed 0.11.1 on start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsTest {
    @Test fun featuresHaveTheirPermissions() {
        val app = RuntimeEnvironment.getApplication()
        val declared = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf(
            "android.permission.INTERNET",              // relay: messages, internet calls, guide downloads
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE", "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_FINE_LOCATION",  // GPS, trail, base camp
            "android.permission.RECORD_AUDIO",          // calls, voice notes
            "android.permission.CAMERA",                // video calls
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
        ).forEach { assertTrue("$it is missing from the manifest", it in declared) }
    }
}
