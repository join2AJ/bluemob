package com.bluemob.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this phone currently has working internet. A phone that does can later act as
 * the bridge (gateway) that carries the mesh's messages to the outside world.
 */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(checkNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }

    private fun checkNow(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
