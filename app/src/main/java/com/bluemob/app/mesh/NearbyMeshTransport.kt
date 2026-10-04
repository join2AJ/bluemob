package com.bluemob.app.mesh

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bluemob.app.contacts.Contact
import com.bluemob.app.contacts.ContactsStore
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 *
 * Messages are small JSON objects with a type field `t`:
 * - `hello`: profile (name, avatar), sent right after connecting
 * - `loc`: the sender's GPS position, only if they chose to share it
 * - `msg`: a chat message with a unique ID (it may be sent again until a receipt comes back)
 * - `rcpt`: a delivery (`k = d`) or read (`k = r`) receipt for a message ID
 * - `sos`: an SOS, passed on by every phone that hears it (up to [SOS_MAX_HOPS] hops)
 * - `ping` / `pong`: round-trip time test
 */
class NearbyMeshTransport(
    context: Context,
    private val identity: Identity,
    private val contacts: ContactsStore,
) : MessageLink {

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    /** Ping send times, keyed by ping ID, to measure round-trip time. */
    private val pendingPings = mutableMapOf<String, Long>()

    /** SOS IDs already seen, so a passed-on SOS isn't shown or passed on twice. */
    private val seenSos = mutableSetOf<String>()

    /** SOS we're currently sending: re-sent to every phone that connects until it's cancelled. */
    private var activeSos: JSONObject? = null

    /** Our latest position, if the user shares it. Sent to everyone we connect to. */
    private var myLocation: GeoPoint? = null

    fun start() {
        if (_running.value) return
        _running.value = true
        log("Starting as \"${identity.displayName.value}\" (${identity.nodeId.take(6)})")

        scope.launch {
            try {
                client.startAdvertising(
                    myEndpointName(), SERVICE_ID, connectionCallback,
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
        val now = System.currentTimeMillis()
        _peers.value.values.filter { it.state == PeerState.CONNECTED }
            .forEach { contacts.touch(it.nodeId, it.name, now) }
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
                client.requestConnection(myEndpointName(), endpointId, connectionCallback).await()
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
        contacts.touch(peer.nodeId, peer.name)
        log("Disconnected from ${peer.name}")
    }

    /** Sends a ping and reports the round-trip time when the pong comes back. Returns false if not connected. */
    fun ping(nodeId: String): Boolean {
        val endpointId = connectedEndpointFor(nodeId) ?: return false
        val id = SystemClock.elapsedRealtimeNanos().toString()
        pendingPings[id] = SystemClock.elapsedRealtime()
        send(endpointId, JSONObject().put("t", TYPE_PING).put("id", id))
        return true
    }

    /** Sends a chat message to a directly connected phone. Returns false if they are not connected. */
    override fun sendChat(toNodeId: String, messageId: String, text: String, sentAt: Long): Boolean {
        val endpointId = connectedEndpointFor(toNodeId) ?: return false
        send(endpointId, JSONObject().put("t", TYPE_MSG).put("id", messageId).put("text", text).put("at", sentAt))
        return true
    }

    /** Sends a delivery or read receipt for a message. Returns false if they are not connected. */
    override fun sendReceipt(toNodeId: String, messageId: String, read: Boolean): Boolean {
        val endpointId = connectedEndpointFor(toNodeId) ?: return false
        send(endpointId, JSONObject().put("t", TYPE_RCPT).put("id", messageId).put("k", if (read) "r" else "d"))
        return true
    }

    /** Broadcasts our SOS to everyone connected now, and to everyone who connects later, until cancelled. */
    fun broadcastSos(sos: SosSignal): Int {
        val json = sosJson(sos)
        seenSos += sos.id + sos.cancelled
        activeSos = if (sos.cancelled) null else json
        val targets = connectedEndpoints()
        targets.forEach { send(it, json) }
        log((if (sos.cancelled) "Sent \"I'm safe\" to " else "SOS sent to ") + "${targets.size} phones")
        return targets.size
    }

    /** Human words for the link to someone, e.g. "Bluetooth" or "Wi-Fi". */
    override fun linkName(nodeId: String): String {
        val peer = _peers.value.values.firstOrNull { it.nodeId == nodeId && it.state == PeerState.CONNECTED }
        return when (peer?.quality) {
            LinkQuality.HIGH -> "Wi-Fi"
            LinkQuality.MEDIUM -> "Wi-Fi or Bluetooth"
            else -> "Bluetooth"
        }
    }

    /** Node IDs of everyone connected right now. */
    fun connectedNodes(): List<String> =
        _peers.value.values.filter { it.state == PeerState.CONNECTED }.map { it.nodeId }

    private fun sosJson(s: SosSignal) = JSONObject()
        .put("t", TYPE_SOS).put("id", s.id).put("from", s.fromNodeId).put("name", s.name).put("note", s.note)
        .put("lat", s.lat ?: JSONObject.NULL).put("lon", s.lon ?: JSONObject.NULL).put("bat", s.battery ?: -1)
        .put("at", s.at).put("hops", s.hops).put("cancel", s.cancelled)

    /** Shares our position with everyone connected; pass null to stop sharing. */
    fun updateMyLocation(location: GeoPoint?) {
        myLocation = location
        if (location == null) return
        connectedEndpoints().forEach { send(it, locationJson(location)) }
    }

    /** Re-sends our profile after the user changes their name or avatar. */
    fun broadcastProfile() = connectedEndpoints().forEach { send(it, helloJson()) }

    override fun isConnected(nodeId: String): Boolean = connectedEndpointFor(nodeId) != null

    private fun connectedEndpointFor(nodeId: String): String? =
        _peers.value.values.firstOrNull { it.nodeId == nodeId && it.state == PeerState.CONNECTED }?.endpointId

    private fun connectedEndpoints() =
        _peers.value.values.filter { it.state == PeerState.CONNECTED }.map { it.endpointId }

    private fun myEndpointName() = EndpointInfo.encode(identity.nodeId, identity.displayName.value)

    private fun helloJson() = JSONObject()
        .put("t", TYPE_HELLO)
        .put("name", identity.displayName.value)
        .put("avatar", identity.avatar.value)

    private fun locationJson(l: GeoPoint) = JSONObject()
        .put("t", TYPE_LOC).put("lat", l.lat).put("lon", l.lon).put("acc", l.accuracyM.toDouble()).put("at", l.time)

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
            contacts.touch(nodeId, name)
            log("Found $name")
            autoConnect(endpointId, nodeId)
        }

        override fun onEndpointLost(endpointId: String) {
            val peer = _peers.value[endpointId] ?: return
            if (peer.state == PeerState.DISCOVERED) {
                _peers.update { it - endpointId }
                contacts.touch(peer.nodeId, peer.name)
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
            // Everyone is accepted for now. Phase 5 adds identity keys and encryption.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peer = _peers.value[endpointId] ?: return
            if (result.status.isSuccess) {
                setState(endpointId, PeerState.CONNECTED)
                contacts.touch(peer.nodeId, peer.name)
                log("Connected to ${peer.name}")
                send(endpointId, helloJson())
                myLocation?.let { send(endpointId, locationJson(it)) }
                activeSos?.let { send(endpointId, it) }
                _events.tryEmit(MeshEvent.PeerConnected(peer.nodeId))
            } else {
                setState(endpointId, PeerState.DISCOVERED)
                log("Could not connect to ${peer.name}: " +
                    ConnectionsStatusCodes.getStatusCodeString(result.status.statusCode))
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = _peers.value[endpointId] ?: return
            _peers.update { it - endpointId }
            contacts.touch(peer.nodeId, peer.name)
            log("${peer.name} disconnected")
        }

        override fun onBandwidthChanged(endpointId: String, info: BandwidthInfo) {
            val quality = when (info.quality) {
                BandwidthInfo.Quality.HIGH -> LinkQuality.HIGH
                BandwidthInfo.Quality.MEDIUM -> LinkQuality.MEDIUM
                else -> LinkQuality.LOW
            }
            _peers.update { peers ->
                val peer = peers[endpointId] ?: return@update peers
                peers + (endpointId to peer.copy(quality = quality))
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val json = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return
            val peer = _peers.value[endpointId] ?: return
            contacts.touch(peer.nodeId, peer.name)
            when (json.optString("t")) {
                TYPE_HELLO -> {
                    val name = json.optString("name").ifBlank { peer.name }
                    val avatar = json.optString("avatar").ifBlank { null }
                    _peers.update { it + (endpointId to peer.copy(name = name)) }
                    contacts.upsert(peer.nodeId) { c ->
                        c?.copy(name = name, avatar = avatar, lastSeen = System.currentTimeMillis())
                            ?: Contact(peer.nodeId, name, avatar, System.currentTimeMillis())
                    }
                }
                TYPE_LOC -> {
                    val loc = GeoPoint(
                        json.optDouble("lat"), json.optDouble("lon"),
                        json.optDouble("acc", 0.0).toFloat(), json.optLong("at", System.currentTimeMillis()),
                    )
                    if (loc.lat.isNaN() || loc.lon.isNaN()) return
                    contacts.upsert(peer.nodeId) { c ->
                        (c ?: Contact(peer.nodeId, peer.name, null, System.currentTimeMillis())).copy(location = loc)
                    }
                }
                TYPE_MSG -> {
                    val id = json.optString("id")
                    val text = json.optString("text")
                    if (id.isEmpty() || text.isEmpty()) return
                    // The message store decides whether this is new, and sends the receipt.
                    _events.tryEmit(MeshEvent.MessageReceived(peer.nodeId, id, text, json.optLong("at", System.currentTimeMillis())))
                }
                TYPE_RCPT -> {
                    val id = json.optString("id")
                    if (id.isNotEmpty()) _events.tryEmit(MeshEvent.Receipt(peer.nodeId, id, json.optString("k") == "r"))
                }
                TYPE_SOS -> handleSos(endpointId, json)
                TYPE_PING -> {
                    log("Ping from ${peer.name}")
                    send(endpointId, JSONObject().put("t", TYPE_PONG).put("id", json.optString("id")))
                }
                TYPE_PONG -> {
                    val sentAt = pendingPings.remove(json.optString("id")) ?: return
                    val rtt = SystemClock.elapsedRealtime() - sentAt
                    log("Pong from ${peer.name}: $rtt ms round trip")
                    _events.tryEmit(MeshEvent.PingResult(peer.nodeId, rtt))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    /** Show an SOS once, then pass it on to everyone else we're connected to. */
    private fun handleSos(fromEndpoint: String, json: JSONObject) {
        val id = json.optString("id")
        if (id.isEmpty() || !seenSos.add(id + json.optBoolean("cancel"))) return
        val hops = json.optInt("hops", 0) + 1
        val sos = SosSignal(
            id = id, fromNodeId = json.optString("from"), name = json.optString("name"), note = json.optString("note"),
            lat = json.optDouble("lat").takeUnless { it.isNaN() }, lon = json.optDouble("lon").takeUnless { it.isNaN() },
            battery = json.optInt("bat", -1).takeIf { it >= 0 }, at = json.optLong("at"), hops = hops,
            cancelled = json.optBoolean("cancel"),
        )
        if (sos.fromNodeId == identity.nodeId) return
        log("SOS from ${sos.name} (${if (hops == 1) "direct" else "passed on $hops times"})")
        _events.tryEmit(MeshEvent.SosReceived(sos))
        if (hops < SOS_MAX_HOPS) {
            val forward = JSONObject(json.toString()).put("hops", hops)
            connectedEndpoints().filter { it != fromEndpoint }.forEach { send(it, forward) }
        }
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
        private const val TYPE_HELLO = "hello"
        private const val TYPE_LOC = "loc"
        private const val TYPE_MSG = "msg"
        private const val TYPE_RCPT = "rcpt"
        private const val TYPE_SOS = "sos"
        const val SOS_MAX_HOPS = 5
        private const val TYPE_PING = "ping"
        private const val TYPE_PONG = "pong"
    }
}
