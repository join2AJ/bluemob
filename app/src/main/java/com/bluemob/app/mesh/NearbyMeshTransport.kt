package com.bluemob.app.mesh

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bluemob.app.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Finds and connects to nearby BlueMob phones with Google Nearby Connections.
 *
 * Nearby picks the radio for us: Bluetooth / BLE for discovery and small messages, and it
 * upgrades the link to Wi-Fi (Direct or hotspot) when both phones support it. None of this
 * needs a SIM card or internet.
 *
 * We use [Strategy.P2P_CLUSTER], which allows every phone to connect to many others at once:
 * the shape we need for a mesh where messages hop A -> B -> C.
 */
class NearbyMeshTransport(context: Context, private val identity: Identity) {

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    /** Ping send times, keyed by ping ID, to measure round-trip time. */
    private val pendingPings = mutableMapOf<String, Long>()

    fun start() {
        if (_running.value) return
        _running.value = true
        val myInfo = EndpointInfo.encode(identity.nodeId, identity.displayName)
        log("Starting as \"${identity.displayName}\" (${identity.nodeId.take(6)})")

        scope.launch {
            try {
                client.startAdvertising(
                    myInfo, SERVICE_ID, connectionCallback,
                    AdvertisingOptions.Builder().setStrategy(STRATEGY).build(),
                ).await()
                log("Advertising: other phones can now see this one")
            } catch (e: Exception) {
                log("Advertising failed: ${describe(e)}")
            }
        }
        scope.launch {
            try {
                client.startDiscovery(
                    SERVICE_ID, discoveryCallback,
                    DiscoveryOptions.Builder().setStrategy(STRATEGY).build(),
                ).await()
                log("Scanning for nearby phones…")
            } catch (e: Exception) {
                log("Discovery failed: ${describe(e)}")
            }
        }
    }

    fun stop() {
        if (!_running.value) return
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _peers.value = emptyMap()
        pendingPings.clear()
        _running.value = false
        log("Stopped")
    }

    fun connect(endpointId: String) {
        val peer = _peers.value[endpointId] ?: return
        if (peer.state != PeerState.DISCOVERED) return
        setState(endpointId, PeerState.CONNECTING)
        log("Connecting to ${peer.name}…")
        scope.launch {
            try {
                client.requestConnection(
                    EndpointInfo.encode(identity.nodeId, identity.displayName),
                    endpointId,
                    connectionCallback,
                ).await()
            } catch (e: Exception) {
                val code = (e as? ApiException)?.statusCode
                // The other phone connected to us at the same moment: not an error.
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@launch
                log("Connection to ${peer.name} failed: ${describe(e)}")
                if (_peers.value[endpointId]?.state == PeerState.CONNECTING) {
                    setState(endpointId, PeerState.DISCOVERED)
                }
            }
        }
    }

    fun disconnect(endpointId: String) {
        val peer = _peers.value[endpointId] ?: return
        client.disconnectFromEndpoint(endpointId)
        _peers.update { it - endpointId }
        log("Disconnected from ${peer.name}")
    }

    /** Sends a ping and logs the round-trip time when the pong comes back. */
    fun ping(endpointId: String) {
        val id = SystemClock.elapsedRealtimeNanos().toString()
        pendingPings[id] = SystemClock.elapsedRealtime()
        send(endpointId, JSONObject().put("t", TYPE_PING).put("id", id))
    }

    private fun send(endpointId: String, json: JSONObject) {
        client.sendPayload(endpointId, Payload.fromBytes(json.toString().toByteArray()))
            .addOnFailureListener { log("Send failed: ${describe(it)}") }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (nodeId, name) = EndpointInfo.decode(info.endpointName) ?: return
            if (nodeId == identity.nodeId) return
            _peers.update { peers ->
                // Drop stale entries for the same phone under an old endpoint ID.
                peers.filterValues { it.nodeId != nodeId || it.state != PeerState.DISCOVERED } +
                    (endpointId to Peer(endpointId, nodeId, name, PeerState.DISCOVERED))
            }
            log("Found $name")
            autoConnect(endpointId, nodeId)
        }

        override fun onEndpointLost(endpointId: String) {
            val peer = _peers.value[endpointId] ?: return
            if (peer.state == PeerState.DISCOVERED) {
                _peers.update { it - endpointId }
                log("Lost sight of ${peer.name}")
            }
        }
    }

    /**
     * Connect to every phone we find, so a mesh forms without anyone tapping buttons.
     *
     * If both phones dial each other at the same moment, both attempts can fail. To avoid
     * that, the phone with the smaller node ID dials first; the other waits a few seconds
     * and only dials if nothing has happened (for example, if only it can see the other).
     */
    private fun autoConnect(endpointId: String, peerNodeId: String) {
        scope.launch {
            if (identity.nodeId > peerNodeId) delay(AUTO_CONNECT_BACKOFF_MS)
            if (_running.value && _peers.value[endpointId]?.state == PeerState.DISCOVERED) {
                connect(endpointId)
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (nodeId, name) = EndpointInfo.decode(info.endpointName) ?: run {
                client.rejectConnection(endpointId)
                return
            }
            _peers.update { it + (endpointId to Peer(endpointId, nodeId, name, PeerState.CONNECTING)) }
            // Phase 1 accepts everyone. Phase 5 adds identity keys and encryption.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peer = _peers.value[endpointId] ?: return
            if (result.status.isSuccess) {
                setState(endpointId, PeerState.CONNECTED)
                log("Connected to ${peer.name}")
            } else {
                setState(endpointId, PeerState.DISCOVERED)
                log("Could not connect to ${peer.name}: " +
                    ConnectionsStatusCodes.getStatusCodeString(result.status.statusCode))
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = _peers.value[endpointId] ?: return
            _peers.update { it - endpointId }
            log("${peer.name} disconnected")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val json = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return
            val name = _peers.value[endpointId]?.name ?: endpointId
            when (json.optString("t")) {
                TYPE_PING -> {
                    log("Ping from $name")
                    send(endpointId, JSONObject().put("t", TYPE_PONG).put("id", json.optString("id")))
                }
                TYPE_PONG -> {
                    val sentAt = pendingPings.remove(json.optString("id")) ?: return
                    log("Pong from $name: ${SystemClock.elapsedRealtime() - sentAt} ms round trip")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun setState(endpointId: String, state: PeerState) {
        _peers.update { peers ->
            val peer = peers[endpointId] ?: return@update peers
            peers + (endpointId to peer.copy(state = state))
        }
    }

    private fun log(text: String) {
        Log.i(TAG, text)
        _log.update { (listOf(LogLine(System.currentTimeMillis(), text)) + it).take(MAX_LOG_LINES) }
    }

    private fun describe(e: Exception): String = when (e) {
        is ApiException -> ConnectionsStatusCodes.getStatusCodeString(e.statusCode)
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val TAG = "BlueMobMesh"
        private const val SERVICE_ID = "com.bluemob.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val AUTO_CONNECT_BACKOFF_MS = 4_000L
        private const val MAX_LOG_LINES = 200
        private const val TYPE_PING = "ping"
        private const val TYPE_PONG = "pong"
    }
}
