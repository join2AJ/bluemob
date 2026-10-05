package com.bluemob.app.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which radios are on. [internet] is real, working internet (it comes from [com.bluemob.app.util.Connectivity]). */
data class RadioState(
    val bluetooth: Boolean,
    val wifi: Boolean,
    val location: Boolean,
    val airplane: Boolean,
    val hasBluetooth: Boolean = true,
    val hasGps: Boolean = true,
)

/** One radio, with what BlueMob uses it for. */
enum class Radio(val title: String, val why: String) {
    BLUETOOTH("Bluetooth", "Finds people nearby and carries messages and SOS. BlueMob can't reach anyone without it."),
    WIFI("Wi-Fi", "Faster links with people nearby (no Wi-Fi network or internet needed). It can stay on in airplane mode."),
    LOCATION("Location (GPS)", "Your position for the compass, trail, SOS and lost mode. GPS needs no internet. Android 12 and older also need it to find phones nearby."),
    INTERNET("Internet", "Optional. When this phone has internet it can act as a bridge for people nearby. Off saves battery."),
}

/** Watches Bluetooth, Wi-Fi, location and airplane mode, and updates as the user switches them. */
class Radios(context: Context) {
    private val app = context.applicationContext
    private val bt: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val lm = app.getSystemService(LocationManager::class.java)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<RadioState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Also called when the app comes back to the front, in case a broadcast was missed. */
    fun refresh() { _state.value = read() }

    private fun read() = RadioState(
        bluetooth = runCatching { bt?.isEnabled == true }.getOrDefault(false),
        wifi = wifi?.isWifiEnabled == true,
        location = lm != null && LocationManagerCompat.isLocationEnabled(lm),
        airplane = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
        hasBluetooth = bt != null,
        hasGps = lm?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true,
    )
}
