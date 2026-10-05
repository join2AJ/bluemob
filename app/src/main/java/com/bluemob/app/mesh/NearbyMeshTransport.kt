package com.bluemob.app.mesh

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bluemob.app.contacts.Contact
import com.bluemob.app.contacts.ContactsStore
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.identity.Identity
import com.bluemob.app.trail.PosCodec.parsePos
import com.bluemob.app.trail.PosCodec.posJson
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
 * - `lost`: a lost person's position estimate, passed on the same way
 * - `room`: a message in an SOS rescue group (join, chat, position, arrived, left, ended), passed on the same way
 * - `ping` / `pong`: round-trip time test
 */
class NearbyMeshTransport(
    context: Context,
    private val identity: Identity,
    private val contacts: ContactsStore,
    private val keyBook: KeyBook,
    relayStore: RelayStore = MemoryRelayStore(),
) : Wire {

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    /** Gets chat messages and receipts to anyone, through other phones if needed. The message store talks to this. */
    val router = MeshRouter(this, identity.keys, keyBook, relayStore, myName = { identity.displayName.value }, log = ::log)

    /** Nearby's per-connection token: the other phone signs it in its hello to prove it owns its ID. */
    private val authTokens = mutableMapOf<String, ByteArray>()

    /** Ping send times, keyed by ping ID, to measure round-trip time. */
    private val pendingPings = mutableMapOf<String, Long>()

    /** IDs of SOS, lost and rescue-group packets already seen, so they aren't shown or passed on twice. Bounded. */
    private val seenSos: MutableSet<String> = java.util.Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 5_000
    })

    /** SOS we're currently sending: re-sent to every phone that connects until it's cancelled. */
    private var activeSos: JSONObject? = null

    /** Our latest "I'm lost" position, re-sent to every phone that connects until lost mode ends. */
    private var activeLost: JSONObject? = null

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
        sendTo(endpointId, JSONObject().put("t", TYPE_PING).put("id", id))
        return true
    }

    /** Broadcasts our SOS to everyone connected now, and to everyone who connects later, until cancelled. */
    fun broadcastSos(sos: SosSignal): Int {
        val json = Envelope.seal(TYPE_SOS, sosBody(sos), identity.keys)
        seenSos += TYPE_SOS + sos.id + sos.cancelled
        activeSos = if (sos.cancelled) null else json
        val targets = connectedEndpoints()
        targets.forEach { sendTo(it, json) }
        log((if (sos.cancelled) "Sent \"I'm safe\" to " else "SOS sent to ") + "${targets.size} phones")
        return targets.size
    }

    /** Shares a lost-mode position with everyone connected, and with everyone who connects later. */
    fun broadcastLost(lost: LostSignal): Int {
        val body = JSONObject().put("id", lost.id).put("name", lost.name).put("at", lost.at).put("end", lost.ended)
        lost.pos?.let { body.put("pos", posJson(it)) }
        val json = Envelope.seal(TYPE_LOST, body, identity.keys)
        seenSos += TYPE_LOST + lost.id
        activeLost = if (lost.ended) null else json
        val targets = connectedEndpoints()
        targets.forEach { sendTo(it, json) }
        return targets.size
    }

    /** Sends a rescue-group message to everyone connected; each phone passes it on. */
    fun broadcastRoom(m: RoomPayload): Int {
        val json = roomJson(m) ?: return 0
        seenSos += TYPE_ROOM + m.id
        val targets = connectedEndpoints()
        targets.forEach { sendTo(it, json) }
        return targets.size
    }

    /** Sends a rescue-group message to one phone, e.g. one that just connected and may have missed it. */
    fun sendRoom(nodeId: String, m: RoomPayload): Boolean {
        val endpointId = connectedEndpointFor(nodeId) ?: return false
        sendTo(endpointId, roomJson(m) ?: return false)
        return true
    }

    /** Our own group messages are signed by us. Ones we pass on for others keep their original signature. */
    private val roomPackets = object : LinkedHashMap<String, JSONObject>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?) = size > 500
    }

    private fun roomJson(m: RoomPayload): JSONObject? = roomPackets[m.id]?.let { JSONObject(it.toString()).put("h", 0) } ?: run {
        // Someone else's message we no longer hold signed (e.g. after a restart): we can't re-sign it as them.
        if (m.fromNodeId != identity.nodeId) return null
        val body = JSONObject().put("id", m.id).put("room", m.room).put("name", m.fromName).put("k", m.kind).put("text", m.text).put("at", m.at)
            .also { j -> m.pos?.let { j.put("pos", posJson(it)) } }
        Envelope.seal(TYPE_ROOM, body, identity.keys).also { if (m.fromNodeId == identity.nodeId) roomPackets[m.id] = it }
    }

    /** Human words for the link to someone, e.g. "Bluetooth" or "Wi-Fi". */
    override fun neighbors(): List<String> = connectedNodes()
    override fun send(nodeId: String, packet: JSONObject) { connectedEndpointFor(nodeId)?.let { sendTo(it, packet) } }
    override fun nameOf(nodeId: String): String = contacts.contacts.value[nodeId]?.name ?: "someone"

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

    private fun sosBody(s: SosSignal) = JSONObject()
        .put("id", s.id).put("name", s.name).put("note", s.note)
        .put("lat", s.lat ?: JSONObject.NULL).put("lon", s.lon ?: JSONObject.NULL).put("bat", s.battery ?: -1)
        .put("at", s.at).put("cancel", s.cancelled)
        .also { j -> s.pos?.let { j.put("pos", posJson(it)) } }

    /** Shares our position with everyone connected; pass null to stop sharing. */
    fun updateMyLocation(location: GeoPoint?) {
        myLocation = location
        if (location == null) return
        connectedEndpoints().forEach { sendTo(it, locationJson(location)) }
    }

    /** Re-sends our profile after the user changes their name or avatar. */
    fun broadcastProfile() = connectedEndpoints().forEach { sendTo(it, helloJson()) }

    fun isConnected(nodeId: String): Boolean = connectedEndpointFor(nodeId) != null

    private fun connectedEndpointFor(nodeId: String): String? =
        _peers.value.values.firstOrNull { it.nodeId == nodeId && it.state == PeerState.CONNECTED }?.endpointId

    private fun connectedEndpoints() =
        _peers.value.values.filter { it.state == PeerState.CONNECTED }.map { it.endpointId }

    private fun myEndpointName() = EndpointInfo.encode(identity.nodeId, identity.displayName.value)

    /** Our profile, our public key, and a signature over this connection's token: proof we own our ID. */
    private fun helloJson(endpointId: String? = null) = JSONObject()
        .put("t", TYPE_HELLO).put("v", PROTOCOL)
        .put("name", identity.displayName.value)
        .put("avatar", identity.avatar.value)
        .put("pk", identity.keys.publicB64)
        .also { j -> endpointId?.let { authTokens[it] }?.let { j.put("auth", Crypto.encode(identity.keys.sign(it))) } }

    private fun locationJson(l: GeoPoint) = JSONObject()
        .put("t", TYPE_LOC).put("lat", l.lat).put("lon", l.lon).put("acc", l.accuracyM.toDouble()).put("at", l.time)

    private fun sendTo(endpointId: String, json: JSONObject) {
        if (!_peers.value.containsKey(endpointId)) return
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
            // Both phones see the same token for this connection. Each signs it in its hello, proving it owns the ID
            // it advertised; a phone that can't is disconnected. (Nearby also encrypts the link itself.)
            info.rawAuthenticationToken?.let { authTokens[endpointId] = it }
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peer = _peers.value[endpointId] ?: return
            if (result.status.isSuccess) {
                setState(endpointId, PeerState.CONNECTED)
                contacts.touch(peer.nodeId, peer.name)
                log("Connected to ${peer.name}")
                sendTo(endpointId, helloJson(endpointId))
                myLocation?.let { sendTo(endpointId, locationJson(it)) }
                activeSos?.let { sendTo(endpointId, it) }
                activeLost?.let { sendTo(endpointId, it) }
                router.onNeighborConnected(peer.nodeId)
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
            authTokens.remove(endpointId)
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
            if (bytes.size > MAX_PAYLOAD) return log("Ignored an oversized packet")
            val json = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return
            val peer = _peers.value[endpointId] ?: return
            contacts.touch(peer.nodeId, peer.name)
            when (json.optString("t")) {
                TYPE_HELLO -> {
                    if (!verifyHello(endpointId, peer, json)) return
                    val name = json.optString("name").replace("|", " ").trim().take(Identity.MAX_NAME_LENGTH).ifBlank { peer.name }
                    val avatar = json.optString("avatar").take(8).ifBlank { null }
                    _peers.update { it + (endpointId to peer.copy(name = name, verified = true)) }
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
                MeshRouter.RMSG, MeshRouter.RRCPT, MeshRouter.KEYQ, MeshRouter.KEYA -> router.onPacket(peer.nodeId, json)
                TYPE_SOS -> handleSos(endpointId, json)
                TYPE_LOST -> handleLost(endpointId, json)
                TYPE_ROOM -> handleRoom(endpointId, json)
                TYPE_PING -> {
                    log("Ping from ${peer.name}")
                    sendTo(endpointId, JSONObject().put("t", TYPE_PONG).put("id", json.optString("id")))
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

    /** Checks the hello: the key must match the advertised ID, and it must sign this connection's token. */
    private fun verifyHello(endpointId: String, peer: Peer, json: JSONObject): Boolean {
        val pk = json.optString("pk")
        val bytes = Crypto.decode(pk)
        val key = bytes?.let(Crypto::publicKey)
        val token = authTokens[endpointId]
        val sig = Crypto.decode(json.optString("auth"))
        val ok = key != null && Crypto.idFor(bytes) == peer.nodeId &&
            (token == null || (sig != null && Crypto.verify(key, token, sig)))
        if (!ok) {
            log("Couldn't verify ${peer.name}: their ID doesn't match their key. Disconnected")
            disconnect(endpointId)
            return false
        }
        keyBook.add(pk)
        return true
    }

    /** Opens a signed SOS / lost / group packet once, and passes it on to everyone else we're connected to. */
    private fun relaySigned(fromEndpoint: String, json: JSONObject, seenKey: (Envelope.Opened) -> String): Envelope.Opened? {
        val o = Envelope.open(json) ?: run { log("Dropped a packet with a bad signature"); return null }
        if (!seenSos.add(o.type + seenKey(o))) return null
        keyBook.add(o.publicB64)
        val hops = o.hops + 1
        if (hops < SOS_MAX_HOPS) {
            val forward = JSONObject(json.toString()).put("h", hops)
            connectedEndpoints().filter { it != fromEndpoint }.forEach { sendTo(it, forward) }
        }
        return if (o.from == identity.nodeId) null else o
    }

    private fun handleSos(fromEndpoint: String, json: JSONObject) {
        val o = relaySigned(fromEndpoint, json) { it.body.optString("id") + it.body.optBoolean("cancel") } ?: return
        val b = o.body
        val sos = SosSignal(
            id = b.optString("id"), fromNodeId = o.from, name = b.optString("name").take(Identity.MAX_NAME_LENGTH), note = b.optString("note").take(MAX_NOTE),
            lat = b.optDouble("lat").takeUnless { it.isNaN() }, lon = b.optDouble("lon").takeUnless { it.isNaN() },
            battery = b.optInt("bat", -1).takeIf { it in 0..100 }, at = b.optLong("at"), hops = o.hops + 1,
            cancelled = b.optBoolean("cancel"), pos = parsePos(b.optJSONObject("pos")),
        )
        if (sos.id.isEmpty()) return
        log("SOS from ${sos.name} (${if (sos.hops == 1) "direct" else "passed on ${sos.hops} times"}), signature checked")
        _events.tryEmit(MeshEvent.SosReceived(sos))
    }

    private fun handleLost(fromEndpoint: String, json: JSONObject) {
        val o = relaySigned(fromEndpoint, json) { it.body.optString("id") } ?: return
        val b = o.body
        _events.tryEmit(MeshEvent.LostReceived(LostSignal(b.optString("id"), o.from, b.optString("name").take(Identity.MAX_NAME_LENGTH),
            parsePos(b.optJSONObject("pos")), b.optLong("at"), o.hops + 1, b.optBoolean("end"))))
    }

    private fun handleRoom(fromEndpoint: String, json: JSONObject) {
        val o = relaySigned(fromEndpoint, json) { it.body.optString("id") } ?: return
        val b = o.body
        val id = b.optString("id")
        val room = b.optString("room")
        if (id.isEmpty() || room.isEmpty()) return
        roomPackets[id] = JSONObject(json.toString())
        _events.tryEmit(MeshEvent.RoomReceived(RoomPayload(id, room, o.from, b.optString("name").take(Identity.MAX_NAME_LENGTH), b.optString("k"),
            b.optString("text").take(MAX_NOTE * 2), b.optLong("at"), parsePos(b.optJSONObject("pos")), o.hops + 1)))
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
        /** Bumped when packets change in ways older versions can't read. Matches the endpoint prefix. */
        const val PROTOCOL = 2
        private const val MAX_PAYLOAD = 32 * 1024
        private const val MAX_NOTE = 200
        private const val TYPE_SOS = "sos"
        private const val TYPE_LOST = "lost"
        private const val TYPE_ROOM = "room"
        const val SOS_MAX_HOPS = 5
        private const val TYPE_PING = "ping"
        private const val TYPE_PONG = "pong"
    }
}
