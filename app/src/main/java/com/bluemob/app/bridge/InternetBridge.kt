package com.bluemob.app.bridge

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.settings.AppSettings
import com.bluemob.app.util.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the internet bridge is doing, for the status screen. */
data class BridgeStatus(
    val configured: Boolean = false,
    val online: Boolean = false,
    val lastSync: Long? = null,
    val lastError: String? = null,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val queued: Int = 0,
)

/**
 * Keeps this phone in touch with the BlueMob relay whenever it has internet: every 20 seconds (sooner when something
 * is waiting) it uploads messages it's sending or carrying, and downloads messages for itself and the phones around it.
 * That's how two people who met over Bluetooth can keep talking from 1,000 km apart, and how a phone with signal
 * becomes a bridge for everyone near it.
 */
class InternetBridge(
    private val settings: AppSettings,
    identity: Identity,
    keyBook: KeyBook,
    private val mesh: NearbyMeshTransport,
    private val connectivity: Connectivity,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
) {
    val client = BridgeClient({ settings.bridgeUrl.value.takeIf { it.isNotBlank() } }, identity.keys, keyBook,
        cursors = mutableMapOf<String, Long>().apply { settings.bridgeCursor.takeIf { it > 0 }?.let { put(identity.nodeId, it) } })
    private val me = identity.nodeId

    private val _status = MutableStateFlow(BridgeStatus())
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    init {
        client.online = { connectivity.online.value }
        mesh.router.internet = client
        scope.launch {
            var wasUp = false
            connectivity.online.collect { on ->
                val up = on && client.configured
                if (up && !wasUp) mesh.router.onInternetUp()
                wasUp = up
                _status.value = _status.value.copy(online = on, configured = client.configured)
            }
        }
        scope.launch(Dispatchers.IO) {
            var announced = false
            while (true) {
                if (client.up()) {
                    val neighbors = withContext(Dispatchers.Main) { mesh.connectedNodes() }
                    val r = client.sync(mesh.router, neighbors) { block -> withContext(Dispatchers.Main) { block() } }
                    client.cursors[me]?.let { settings.setBridgeCursor(it) }
                    _status.value = _status.value.copy(
                        configured = true, online = true, lastSync = if (r.ok) System.currentTimeMillis() else _status.value.lastSync,
                        lastError = r.error, uploaded = _status.value.uploaded + r.uploaded, downloaded = _status.value.downloaded + r.downloaded, queued = client.queued,
                    )
                    if (r.ok && !announced) { audit.add(AuditKind.MESH, "Connected to the BlueMob relay: this phone is a bridge to the internet"); announced = true }
                    if (r.uploaded + r.downloaded > 0) audit.add(AuditKind.MESH, "Relay: sent ${r.uploaded}, received ${r.downloaded} over the internet")
                } else {
                    _status.value = _status.value.copy(configured = client.configured, online = connectivity.online.value, queued = client.queued)
                }
                delay(if (client.queued > 0) 5_000 else 20_000)
            }
        }
    }

    /** Called after the user changes the relay address. */
    fun reconfigure() {
        _status.value = _status.value.copy(configured = client.configured, lastError = null)
        if (client.up()) mesh.router.onInternetUp()
    }
}
