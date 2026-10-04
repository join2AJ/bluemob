package com.bluemob.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bluemob.app.BlueMobApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MeshViewModel(app: Application) : AndroidViewModel(app) {
    private val identity = (app as BlueMobApp).identity
    private val mesh = (app as BlueMobApp).mesh

    val nodeId: String = identity.nodeId
    val running = mesh.running
    val peers = mesh.peers
    val log = mesh.log

    private val _name = MutableStateFlow(identity.displayName)
    val name: StateFlow<String> = _name.asStateFlow()

    fun setName(value: String) {
        identity.displayName = value
        _name.value = identity.displayName
    }

    fun start() = mesh.start()
    fun stop() = mesh.stop()
    fun connect(endpointId: String) = mesh.connect(endpointId)
    fun disconnect(endpointId: String) = mesh.disconnect(endpointId)
    fun ping(endpointId: String) = mesh.ping(endpointId)
}
