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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

    /** Who can reach whom through the phones around us: lets calls hop through friends, and reach the internet through one. */
    val routes = RouteTable(identity.nodeId)

    /** Gets chat messages and receipts to anyone, through other phones if needed. The message store talks to this. */
    val router = MeshRouter(this, identity.keys, keyBook, relayStore, myName = { identity.displayName.value }, log = ::log)

    /** Extra packets to send to a phone that just connected (e.g. audit witness notes). Set by the app. */
    var onConnectedPackets: (String) -> List<JSONObject> = { emptyList() }

    /** Packet types handed to the app as [MeshEvent.Extra] instead of being handled here. */
    var extraTypes: Set<String> = emptySet()

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

    /** Whether advertising / discovery are actually running. Both stop when Bluetooth is switched off. */
    private var advertising = false
    private var discovering = false
    private var advertisingPending = false
    private var discoveryPending = false
    private var lastDiscoveryStart = 0L
    private var watchdog: Job? = null
    private var restartJob: Job? = null

    /** False while a radio the user hasn't allowed us to switch on is off: then the mesh waits instead of retrying. */
    var radiosAllowed: () -> Boolean = { true }

    /** Use Wi-Fi for faster links. When false, Nearby is told not to touch Wi-Fi (Bluetooth only). */
    private var useWifi = true

    private val _paused = MutableStateFlow(false)
    /** The mesh is on but waiting for Bluetooth, which the user switched off. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun start(useWifi: Boolean = true) {
        if (_running.value) return
        this.useWifi = useWifi
        _running.value = true
        log("Starting as \"${identity.displayName.value}\" (${identity.nodeId.take(6)})")
        beginAdvertising()
        beginDiscovery()
        // Advertising and discovery quietly die when a radio goes off, and a failed connection is never retried
        // by Nearby itself. This keeps both alive and retries until the phones find each other again.
        watchdog?.cancel()
        watchdog = scope.launch {
            var ticks = 0L
            while (isActive) {
                delay(HEARTBEAT_MS)
                if (!_running.value) continue
                heartbeat()
                if (ticks % 2 == 0L) shareRoutes()
                if (++ticks % (WATCHDOG_MS / HEARTBEAT_MS) == 0L) checkHealth()
            }
        }
    }

    fun stop() {
        if (!_running.value) return
        _paused.value = false
        watchdog?.cancel()
        restartJob?.cancel()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        advertising = false
        discovering = false
        dropAllPeers()
        _running.value = false
        log("Stopped")
    }

    /**
     * Starts advertising and discovery from scratch, e.g. after Bluetooth or Wi-Fi was switched off and on.
     * Nearby doesn't recover from that by itself: the phone stays invisible and finds no one.
     */
    fun restart(reason: String) {
        if (!_running.value) return
        log("Restarting the mesh: $reason")
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        advertising = false
        discovering = false
        dropAllPeers()
        beginAdvertising()
        beginDiscovery()
    }

    /** Called when Bluetooth or Wi-Fi changes. Waits for the radio to settle, then restarts the mesh. */
    fun onRadiosChanged(bluetoothOn: Boolean) {
        if (!_running.value) return
        restartJob?.cancel()
        restartJob = scope.launch {
            if (!bluetoothOn) {
                // Links over Bluetooth are gone; Nearby doesn't always say so. Wi-Fi links may survive, so only
                // mark the radios as needing a restart and let the watchdog retry until Bluetooth is back.
                advertising = false
                discovering = false
                log("Bluetooth is off: nearby phones can't find this one until it's back on")
                return@launch
            }
            delay(RADIO_SETTLE_MS)
            restart("a radio was switched back on")
        }
    }

    private fun dropAllPeers() {
        val now = System.currentTimeMillis()
        _peers.value.values.filter { it.state == PeerState.CONNECTED }
            .forEach { contacts.touch(it.nodeId, it.name, now) }
        _peers.value = emptyMap()
        authTokens.clear()
        pendingPings.clear()
    }

    private fun beginAdvertising() {
        if (advertising || advertisingPending || !mayUseRadios()) return
        advertisingPending = true
        scope.launch {
            try {
                client.startAdvertising(
                    myEndpointName(), SERVICE_ID, connectionCallback,
                    AdvertisingOptions.Builder().setStrategy(STRATEGY)
                        .setConnectionType(if (useWifi) com.google.android.gms.nearby.connection.ConnectionType.BALANCED else com.google.android.gms.nearby.connection.ConnectionType.NON_DISRUPTIVE)
                        .setDisruptiveUpgrade(useWifi).build(),
                ).await()
                advertising = true
                log("Advertising: other phones can now see this one")
            } catch (e: Exception) {
                val code = (e as? ApiException)?.statusCode
                advertising = code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING
                if (!advertising) log("Advertising failed: ${describe(e)}. Trying again soon")
            } finally {
                advertisingPending = false
            }
        }
    }

    private fun beginDiscovery() {
        if (discovering || discoveryPending || !mayUseRadios()) return
        discoveryPending = true
        lastDiscoveryStart = SystemClock.elapsedRealtime()
        scope.launch {
            try {
                client.startDiscovery(
                    SERVICE_ID, discoveryCallback,
                    DiscoveryOptions.Builder().setStrategy(STRATEGY).build(),
                ).await()
                discovering = true
                log("Scanning for nearby phones…")
            } catch (e: Exception) {
                val code = (e as? ApiException)?.statusCode
                discovering = code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING
                if (!discovering) log("Discovery failed: ${describe(e)}. Trying again soon")
            } finally {
                discoveryPending = false
            }
        }
    }

    /**
     * Stops and starts discovery. Nearby reports each phone once per discovery session, so a phone that
     * disconnected (walked away, Bluetooth toggled) is often never "found" again until discovery restarts.
     */
    private fun refreshDiscovery() {
        if (!_running.value || discoveryPending) return
        client.stopDiscovery()
        discovering = false
        _peers.update { peers -> peers.filterValues { it.state != PeerState.DISCOVERED } }
        beginDiscovery()
    }

    /** When we last heard anything from each connected phone. */
    private val lastHeard = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Nearby can take a minute or two to notice a phone has gone (Bluetooth switched off, walked away). So we
     * ping quiet links, and treat a phone that hasn't answered anything for [PEER_TIMEOUT_MS] as gone. Uses the
     * plain ping every BlueMob version answers.
     */
    private fun heartbeat() {
        val now = SystemClock.elapsedRealtime()
        _peers.value.values.filter { it.state == PeerState.CONNECTED }.forEach { p ->
            val heard = lastHeard[p.endpointId] ?: now.also { lastHeard[p.endpointId] = it }
            when {
                now - heard > PEER_TIMEOUT_MS -> dropSilent(p)
                now - heard > HEARTBEAT_MS -> sendTo(p.endpointId, JSONObject().put("t", TYPE_PING).put("id", HEARTBEAT + now))
            }
        }
    }

    /**
     * Tells the phones around us who we can reach (for calls through friends), and tells the internet relay which
     * phones it can reach through us, so this phone becomes the whole group's way to the world.
     */
    private fun shareRoutes() {
        val ns = connectedNodes()
        val online = live?.connected?.value == true
        if (ns.isNotEmpty()) {
            val advert = routes.advert(ns, online)
            verifiedEndpoints().forEach { sendTo(it, advert) }
        }
        if (online) live?.carry(routes.reach(ns).keys)
    }

    private fun verifiedEndpoints() = _peers.value.values.filter { it.state == PeerState.CONNECTED && it.verified }.map { it.endpointId }

    private fun dropSilent(p: Peer) {
        routes.remove(p.nodeId)
        client.disconnectFromEndpoint(p.endpointId)
        _peers.update { it - p.endpointId }
        authTokens.remove(p.endpointId)
        lastHeard.remove(p.endpointId)
        contacts.touch(p.nodeId, p.name)
        log("${p.name} stopped answering (out of range or Bluetooth off)")
        scope.launch { delay(RADIO_SETTLE_MS); refreshDiscovery() }
    }

    /**
     * Starting advertising or discovery makes Android's Nearby service switch Bluetooth on. If the user switched it off
     * (and hasn't allowed BlueMob to turn it on), we wait for them instead of quietly turning it back on.
     */
    private fun mayUseRadios(): Boolean {
        val ok = radiosAllowed()
        if (!ok && !_paused.value) log("Paused: Bluetooth is off. The mesh continues when you turn it on")
        _paused.value = !ok
        return ok
    }

    private fun checkHealth() {
        if (!advertising) beginAdvertising()
        if (!discovering) { beginDiscovery(); return }
        // Found but never connected (both dialled at once, or the attempt failed): try again.
        _peers.value.values.filter { it.state == PeerState.DISCOVERED }.forEach { autoConnect(it.endpointId, it.nodeId) }
        val alone = _peers.value.values.none { it.state == PeerState.CONNECTED }
        val since = SystemClock.elapsedRealtime() - lastDiscoveryStart
        if ((alone && since > LONELY_REFRESH_MS) || since > REFRESH_MS) refreshDiscovery()
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

    /** Sends a signed rating to everyone connected; each phone passes it on (up to 5 hops). */
    fun broadcastRate(json: JSONObject, seenKey: String) {
        seenSos += TYPE_RATE + seenKey
        connectedEndpoints().forEach { sendTo(it, json) }
    }

    /**
     * Sends a signed packet to one person: straight to them when connected, otherwise to everyone connected, who
     * pass it on (up to [SOS_MAX_HOPS] hops). Returns false if no one at all is connected.
     */
    fun sendApp(to: String, kind: String, body: JSONObject = JSONObject()): Boolean {
        val id = "a-" + java.util.UUID.randomUUID().toString().take(13)
        val b = JSONObject(body.toString()).put("id", id).put("to", to).put("k", kind)
            .put("name", identity.displayName.value).put("at", System.currentTimeMillis())
        val json = Envelope.seal(TYPE_APP, b, identity.keys)
        seenSos += TYPE_APP + id
        connectedEndpointFor(to)?.let { sendTo(it, json); return true }
        val targets = connectedEndpoints()
        targets.forEach { sendTo(it, json) }
        // Not nearby: also try the internet. The same packet may arrive both ways; the ID stops doubles.
        val online = live?.sendText(to, json.toString()) == true
        return targets.isNotEmpty() || online
    }

    /** The internet link for calls with people far away. Set by the app; null when there's no relay. */
    var live: com.bluemob.app.bridge.LiveLink? = null
        set(value) {
            field = value
            value?.onText = { from, data ->
                val json = runCatching { JSONObject(data) }.getOrNull()
                when (json?.optString("t")) {
                    TYPE_APP -> scope.launch { handleApp(null, json, viaInternetFrom = from) }
                    // A chat message or receipt sent live: for us, or for a phone near us that we carry.
                    MeshRouter.RMSG, MeshRouter.RRCPT -> scope.launch { router.onInternetPacket(json.put("net", 1)) }
                }
            }
            value?.onBinary = { from, bytes ->
                when {
                    bytes.isEmpty() || bytes.size > MAX_MEDIA + RelayFrame.HEADER -> {}
                    bytes[0] == RelayFrame.MARK -> scope.launch { relayFrame(null, bytes) }
                    bytes.size <= MAX_MEDIA && isCallFrame(bytes[0]) -> _media.tryEmit(Media(from, bytes))
                }
            }
            value?.onOffline = { to, waking -> _events.tryEmit(MeshEvent.Unreachable(to, waking)) }
        }

    /** How a call's audio and video get to someone right now. */
    sealed interface Path {
        /** Connected to them nearby. */
        data class Direct(val endpointId: String) : Path
        /** Over the internet relay, from this phone. */
        data object Live : Path
        /** Through phones around us: [hop] is the next one, [hops] the whole way. */
        data class Relay(val endpointId: String, val hop: String, val hops: Int) : Path
        /** To the internet through a phone near us that has it. */
        data class NetVia(val endpointId: String, val hop: String) : Path
    }

    fun pathTo(nodeId: String): Path? {
        connectedEndpointFor(nodeId)?.let { return Path.Direct(it) }
        val ns = connectedNodes()
        val mesh = routes.nextHop(nodeId, ns)?.let { (hop, hops) -> connectedEndpointFor(hop)?.let { Path.Relay(it, hop, hops) } }
        val online = live?.connected?.value == true
        // With our own internet, prefer it unless they're only a couple of hops away and not online themselves.
        if (online && (mesh == null || mesh.hops > 2 || live?.presence?.value?.reachable(nodeId) == true)) return Path.Live
        if (mesh != null) return mesh
        if (online) return Path.Live
        return routes.netHop(ns)?.let { (hop, _) -> connectedEndpointFor(hop)?.let { Path.NetVia(it, hop) } }
    }

    /** True when a call can reach [nodeId]: nearby, through phones around us, or over the internet (ours or a friend's). */
    fun canReachLive(nodeId: String) = pathTo(nodeId) != null

    /** "Wi-Fi" / "Bluetooth" when they're nearby, "Internet", or the friend the call goes through. */
    fun callLink(nodeId: String) = when (val p = pathTo(nodeId)) {
        is Path.Direct -> linkName(nodeId)
        is Path.Relay -> "Through ${nameOf(p.hop)}" + if (p.hops > 2) " (${p.hops} hops)" else ""
        is Path.NetVia -> "Internet through ${nameOf(p.hop)}"
        Path.Live -> live?.presence?.value?.via?.get(nodeId)?.let { "Internet, then through ${nameOf(it)}" } ?: "Internet"
        null -> "Internet"
    }

    /** True if [nodeId] is online over the internet (signed in to the relay, or carried by a phone near them). */
    fun onlineOverInternet(nodeId: String) = live?.presence?.value?.reachable(nodeId) == true

    private fun isCallFrame(b: Byte) = b == MEDIA_AUDIO || b == MEDIA_VIDEO || b == CallCipher.MARK

    /** The kind of call frame ('A' or 'V'), looking inside encrypted and relay frames. */
    private fun kindOf(bytes: ByteArray): Byte = when (bytes.firstOrNull()) {
        CallCipher.MARK -> bytes.getOrElse(1) { 0 }
        RelayFrame.MARK -> RelayFrame.innerKind(bytes)
        else -> bytes.firstOrNull() ?: 0
    }

    /**
     * A call frame passing through: for us, or on its way to someone else. Only end-to-end encrypted frames are
     * accepted from a relay, so a phone in between can't put words in anyone's mouth.
     */
    private fun relayFrame(fromNode: String?, bytes: ByteArray) {
        val f = RelayFrame.parse(bytes) ?: return
        if (f.dest == identity.nodeId) {
            if (f.inner.size <= MAX_MEDIA && f.inner[0] == CallCipher.MARK) _media.tryEmit(Media(f.origin, f.inner))
            return
        }
        if (f.origin == identity.nodeId) return
        val next = RelayFrame.hop(bytes) ?: return
        val video = kindOf(bytes) == MEDIA_VIDEO
        connectedEndpointFor(f.dest)?.let { sendRelayed(it, next, video); return }
        val ns = connectedNodes()
        routes.nextHop(f.dest, ns)?.takeIf { it.first != fromNode }?.let { (hop, _) -> connectedEndpointFor(hop)?.let { sendRelayed(it, next, video); return } }
        if (live?.connected?.value == true) {
            if (!video || (live?.backlog() ?: 0) < RELAY_VIDEO_BACKLOG) live?.sendBinary(f.dest, next)
            return
        }
        routes.netHop(ns)?.takeIf { it.first != fromNode }?.let { (hop, _) -> connectedEndpointFor(hop)?.let { sendRelayed(it, next, video) } }
    }

    /** Passes on someone else's call frame, dropping video (never voice first) if that link is already busy. */
    private fun sendRelayed(endpointId: String, frame: ByteArray, video: Boolean) {
        val busy = endpointBacklog(endpointId)
        if (busy > (if (video) RELAY_VIDEO_BACKLOG else RELAY_AUDIO_BACKLOG)) return
        sendTracked(endpointId, frame)
    }

    private fun sendTracked(endpointId: String, bytes: ByteArray) {
        val payload = Payload.fromBytes(bytes)
        inFlight[payload.id] = InFlight(endpointId, bytes.size, kindOf(bytes), SystemClock.elapsedRealtime())
        client.sendPayload(endpointId, payload).addOnFailureListener { inFlight.remove(payload.id) }
    }

    private fun handleApp(fromEndpoint: String?, json: JSONObject, viaInternetFrom: String? = null) {
        val o = Envelope.open(json) ?: return
        val to = o.body.optString("to")
        // Over the internet it may come from the sender, or from a phone with internet passing it on for them (a gateway);
        // either way the sender's signature has been checked.
        if (!seenSos.add(TYPE_APP + o.body.optString("id"))) return
        if (to == identity.nodeId) {
            if (o.from == identity.nodeId) return
            keyBook.add(o.publicB64)
            val direct = fromEndpoint != null && _peers.value[fromEndpoint]?.nodeId == o.from
            _events.tryEmit(MeshEvent.App(o.from, o.body.optString("name").take(Identity.MAX_NAME_LENGTH), o.body.optString("k"), o.body, o.hops + 1, direct,
                viaInternet = viaInternetFrom != null))
            return
        }
        val hops = o.hops + 1
        if (hops >= SOS_MAX_HOPS) return
        val forward = JSONObject(json.toString()).put("h", hops)
        val direct = connectedEndpointFor(to)
        if (direct != null) { sendTo(direct, forward); return }
        connectedEndpoints().filter { it != fromEndpoint }.forEach { sendTo(it, forward) }
        // We have internet and they aren't around us: this phone is the group's way out.
        if (viaInternetFrom == null && routes.nextHop(to, connectedNodes()) == null) live?.takeIf { it.connected.value }?.sendText(to, forward.toString())
    }

    private val _media = MutableSharedFlow<Media>(extraBufferCapacity = 32, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    /** Live call audio and video frames from directly connected phones. */
    val media: SharedFlow<Media> = _media.asSharedFlow()

    /** A chunk of call audio or a video frame. The first byte says which ([Media.AUDIO] or [Media.VIDEO]). */
    class Media(val fromNodeId: String, val bytes: ByteArray)

    /**
     * Sends call audio or video: straight to them nearby, over the internet, or through the phones around us
     * ([pathTo]). Frames that go through other phones must already be end-to-end encrypted. False if there's no way.
     */
    fun sendMedia(nodeId: String, bytes: ByteArray): Boolean = when (val p = pathTo(nodeId)) {
        is Path.Direct -> { sendTracked(p.endpointId, bytes); true }
        Path.Live -> live?.sendBinary(nodeId, bytes) == true
        is Path.Relay -> bytes[0] == CallCipher.MARK && run { sendTracked(p.endpointId, RelayFrame.wrap(nodeId, identity.nodeId, bytes)); true }
        is Path.NetVia -> bytes[0] == CallCipher.MARK && run { sendTracked(p.endpointId, RelayFrame.wrap(nodeId, identity.nodeId, bytes)); true }
        null -> false
    }

    /** Progress and results of file transfers (photos, documents, voice notes). */
    sealed interface FileEvent {
        val nodeId: String
        val transferId: Long
        data class Progress(override val nodeId: String, override val transferId: Long, val done: Long, val total: Long, val outgoing: Boolean) : FileEvent
        data class Arrived(override val nodeId: String, override val transferId: Long, val file: Payload.File) : FileEvent
        data class Sent(override val nodeId: String, override val transferId: Long) : FileEvent
        data class Failed(override val nodeId: String, override val transferId: Long, val outgoing: Boolean) : FileEvent
    }

    private val _fileEvents = MutableSharedFlow<FileEvent>(extraBufferCapacity = 64, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val fileEvents: SharedFlow<FileEvent> = _fileEvents.asSharedFlow()
    private val incomingFiles = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, Payload>>()
    private val outgoingFiles = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /**
     * Sends a file (already encrypted by the caller) straight to a connected phone. [announce] gets the transfer ID
     * first, so the other phone can tell which message the file belongs to. Returns the ID, or null if not connected.
     */
    fun sendFile(nodeId: String, file: java.io.File, announce: (Long) -> Unit): Long? {
        val endpointId = connectedEndpointFor(nodeId) ?: return null
        val payload = runCatching { Payload.fromFile(file) }.getOrNull() ?: return null
        announce(payload.id)
        outgoingFiles[payload.id] = nodeId
        client.sendPayload(endpointId, payload).addOnFailureListener {
            outgoingFiles.remove(payload.id)
            _fileEvents.tryEmit(FileEvent.Failed(nodeId, payload.id, outgoing = true))
        }
        return payload.id
    }

    private class InFlight(val endpointId: String, val size: Int, val kind: Byte, val at: Long)
    /** Call media handed to Nearby but not yet confirmed sent, so calls can tell when the link is falling behind. */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Long, InFlight>()

    /**
     * Bytes of call media ([kind] = audio or video, or all) still queued for [nodeId]. Over Bluetooth this grows
     * quickly if we send more than the link carries; calls use it to hold back video and drop stale audio.
     */
    fun mediaBacklog(nodeId: String, kind: Byte? = null): Int {
        // Over the internet we can't tell audio from video in the queue. Video waits for an empty queue; voice only
        // counts as behind once more than about one video frame is waiting.
        val endpointId = when (val p = pathTo(nodeId)) {
            is Path.Direct -> p.endpointId
            is Path.Relay -> p.endpointId
            is Path.NetVia -> p.endpointId
            else -> return (live?.backlog() ?: 0L).toInt().let { if (kind == MEDIA_AUDIO) (it - 32_000).coerceAtLeast(0) else it }
        }
        return endpointBacklog(endpointId, kind)
    }

    private fun endpointBacklog(endpointId: String, kind: Byte? = null): Int {
        val now = SystemClock.elapsedRealtime()
        var total = 0
        val it = inFlight.entries.iterator()
        while (it.hasNext()) {
            val f = it.next().value
            // Nearby normally reports each payload; forget any it never did.
            if (now - f.at > MEDIA_STALE_MS) { it.remove(); continue }
            if (f.endpointId == endpointId && (kind == null || f.kind == kind)) total += f.size
        }
        return total
    }

    private val _otherVersions = MutableStateFlow<Map<String, OtherVersion>>(emptyMap())
    /** BlueMob phones nearby on a protocol we can't link with (very old or much newer versions). */
    val otherVersions: StateFlow<Map<String, OtherVersion>> = _otherVersions.asStateFlow()

    /**
     * Null if [nodeId]'s BlueMob can do [cap], or we can't tell. Otherwise their version, in words, for a
     * message like "Asha has BlueMob 0.9-lite: ask them to update". Messages, SOS and lost mode work with every version.
     */
    fun featureGap(nodeId: String, cap: String): String? {
        val peer = _peers.value.values.firstOrNull { it.nodeId == nodeId && it.state == PeerState.CONNECTED && it.verified } ?: return null
        // 0.7 has games, calls and ringing but didn't list its features; 0.6 and older have none of them.
        val caps = peer.caps ?: return null
        return if (cap in caps) null else "BlueMob ${peer.app ?: "(older)"}"
    }

    /** True when [nodeId] is connected but didn't list its features: BlueMob 0.7 or older. */
    fun mayBeOld(nodeId: String): Boolean =
        _peers.value.values.any { it.nodeId == nodeId && it.state == PeerState.CONNECTED && it.verified && it.caps == null }

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
        .also { j -> s.bloodGroup?.let { j.put("blood", it) }; s.age?.let { j.put("age", it) } }

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
        .put("app", com.bluemob.app.BuildConfig.VERSION_NAME)
        .put("caps", org.json.JSONArray(CAPS.toList()))
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
            val (nodeId, name) = EndpointInfo.decode(info.endpointName) ?: run {
                // Another BlueMob version we can't link with: say so, rather than ignoring them.
                EndpointInfo.version(info.endpointName)?.takeIf { it.first != PROTOCOL }?.let { (proto, who) ->
                    _otherVersions.update { it + (endpointId to OtherVersion(who.take(Identity.MAX_NAME_LENGTH), proto, System.currentTimeMillis())) }
                    log("Found $who with ${if (proto < PROTOCOL) "an older" else "a newer"} BlueMob that can't link with this one")
                }
                return
            }
            if (nodeId == identity.nodeId) return
            // Found again after discovery restarted, but we're already linked: keep the live link as it is.
            if (_peers.value.values.any { it.nodeId == nodeId && it.state != PeerState.DISCOVERED }) return
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
            _otherVersions.update { it - endpointId }
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
                lastHeard[endpointId] = SystemClock.elapsedRealtime()
                setState(endpointId, PeerState.CONNECTED)
                contacts.touch(peer.nodeId, peer.name)
                log("Connected to ${peer.name}")
                sendTo(endpointId, helloJson(endpointId))
                myLocation?.let { sendTo(endpointId, locationJson(it)) }
                activeSos?.let { sendTo(endpointId, it) }
                activeLost?.let { sendTo(endpointId, it) }
                onConnectedPackets(peer.nodeId).forEach { sendTo(endpointId, it) }
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
            routes.remove(peer.nodeId)
            _peers.update { it - endpointId }
            authTokens.remove(endpointId)
            lastHeard.remove(endpointId)
            contacts.touch(peer.nodeId, peer.name)
            log("${peer.name} disconnected")
            // So they're found again as soon as they're back in range.
            scope.launch { delay(RADIO_SETTLE_MS); refreshDiscovery() }
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
            lastHeard[endpointId] = SystemClock.elapsedRealtime()
            if (payload.type == Payload.Type.FILE) {
                // Only from phones that proved their ID; the file is matched to a message when it finishes.
                if (_peers.value[endpointId]?.verified == true) incomingFiles[payload.id] = endpointId to payload
                else client.cancelPayload(payload.id)
                return
            }
            val bytes = payload.asBytes() ?: return
            if (bytes.isNotEmpty() && (isCallFrame(bytes[0]) || bytes[0] == RelayFrame.MARK)) {
                // Call media only from a phone that proved its ID, and only within the size of a frame.
                val peer = _peers.value[endpointId]?.takeIf { it.verified } ?: return
                if (bytes[0] == RelayFrame.MARK) { if (bytes.size <= MAX_MEDIA) relayFrame(peer.nodeId, bytes) }
                else if (bytes.size <= MAX_MEDIA) _media.tryEmit(Media(peer.nodeId, bytes))
                return
            }
            if (bytes.size > MAX_PAYLOAD) return log("Ignored an oversized packet")
            val json = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return
            val peer = _peers.value[endpointId] ?: return
            contacts.touch(peer.nodeId, peer.name)
            when (json.optString("t")) {
                TYPE_HELLO -> {
                    if (!verifyHello(endpointId, peer, json)) return
                    val name = json.optString("name").replace("|", " ").trim().take(Identity.MAX_NAME_LENGTH).ifBlank { peer.name }
                    val avatar = json.optString("avatar").take(8).ifBlank { null }
                    val caps = json.optJSONArray("caps")?.let { a -> (0 until minOf(a.length(), 64)).map { a.optString(it).take(16) }.toSet() }
                    val app = json.optString("app").take(16).ifBlank { null }
                    _peers.update { it + (endpointId to peer.copy(name = name, verified = true, caps = caps, app = app)) }
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
                RouteTable.TYPE -> if (peer.verified) routes.update(peer.nodeId, json)
                TYPE_RATE -> relaySigned(endpointId, json) { o -> listOf(o.body.optString("subject"), o.body.optString("kind"), o.body.optString("ctx"), o.body.optLong("at")).joinToString("|") }
                    ?.let { _events.tryEmit(MeshEvent.Extra(peer.nodeId, TYPE_RATE, json)) }
                in extraTypes -> _events.tryEmit(MeshEvent.Extra(peer.nodeId, json.optString("t"), json))
                TYPE_SOS -> handleSos(endpointId, json)
                TYPE_LOST -> handleLost(endpointId, json)
                TYPE_ROOM -> handleRoom(endpointId, json)
                TYPE_APP -> handleApp(endpointId, json)
                TYPE_PING -> {
                    if (!json.optString("id").startsWith(HEARTBEAT)) log("Ping from ${peer.name}")
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

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val pid = update.payloadId
            val done = update.status != PayloadTransferUpdate.Status.IN_PROGRESS
            if (done) inFlight.remove(pid)
            incomingFiles[pid]?.let { (ep, p) ->
                val node = _peers.value[ep]?.nodeId ?: return@let
                when {
                    update.totalBytes > FILE_MAX_BYTES -> { client.cancelPayload(pid); incomingFiles.remove(pid); _fileEvents.tryEmit(FileEvent.Failed(node, pid, false)) }
                    update.status == PayloadTransferUpdate.Status.SUCCESS -> { incomingFiles.remove(pid); p.asFile()?.let { _fileEvents.tryEmit(FileEvent.Arrived(node, pid, it)) } }
                    done -> { incomingFiles.remove(pid); _fileEvents.tryEmit(FileEvent.Failed(node, pid, false)) }
                    else -> _fileEvents.tryEmit(FileEvent.Progress(node, pid, update.bytesTransferred, update.totalBytes, false))
                }
            }
            outgoingFiles[pid]?.let { node ->
                when {
                    update.status == PayloadTransferUpdate.Status.SUCCESS -> { outgoingFiles.remove(pid); _fileEvents.tryEmit(FileEvent.Sent(node, pid)) }
                    done -> { outgoingFiles.remove(pid); _fileEvents.tryEmit(FileEvent.Failed(node, pid, true)) }
                    else -> _fileEvents.tryEmit(FileEvent.Progress(node, pid, update.bytesTransferred, update.totalBytes, true))
                }
            }
        }
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
            bloodGroup = b.optString("blood").takeIf { it in com.bluemob.app.account.BloodGroups.ALL },
            age = b.optInt("age", -1).takeIf { it in 1..120 },
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
        private const val WATCHDOG_MS = 16_000L
        private const val HEARTBEAT_MS = 4_000L
        /** No packet at all from a connected phone for this long: it's gone. */
        private const val PEER_TIMEOUT_MS = 15_000L
        private const val HEARTBEAT = "hb-"
        private const val MEDIA_STALE_MS = 3_000L
        /** Someone else's call through us: drop their video above this much queued, and their voice above the second. */
        private const val RELAY_VIDEO_BACKLOG = 24_000
        private const val RELAY_AUDIO_BACKLOG = 6_000
        /** Largest file accepted: the 25 MB attachment limit plus encryption overhead. */
        private const val FILE_MAX_BYTES = 25L * 1024 * 1024 + 1024
        private const val RADIO_SETTLE_MS = 2_000L
        /** Restart discovery this often while no one is connected… */
        private const val LONELY_REFRESH_MS = 40_000L
        /** …and now and then anyway, to find newcomers Nearby missed. */
        private const val REFRESH_MS = 5 * 60_000L
        private const val MAX_LOG_LINES = 200
        private const val TYPE_HELLO = "hello"
        private const val TYPE_LOC = "loc"
        /** Bumped when packets change in ways older versions can't read. Matches the endpoint prefix. */
        const val PROTOCOL = 2
        /**
         * What this version can do, sent in every hello. Older phones ignore packet types they don't know, so new
         * features are added as new capabilities and new types, never by changing what existing packets mean.
         */
        val CAPS = setOf("msg", "rcpt", "sos", "lost", "room", "rate", "audit", "app", "game", "games2", "call", "ring", "file", "ptt", "relay", "e2ecall")
        private const val MAX_PAYLOAD = 32 * 1024
        private const val MAX_NOTE = 200
        private const val TYPE_SOS = "sos"
        private const val TYPE_LOST = "lost"
        private const val TYPE_ROOM = "room"
        private const val TYPE_APP = "app"
        const val MEDIA_AUDIO: Byte = 'A'.code.toByte()
        const val MEDIA_VIDEO: Byte = 'V'.code.toByte()
        /** Nearby's limit for one BYTES payload. */
        const val MAX_MEDIA = 32 * 1024
        const val TYPE_RATE = "rate"
        const val SOS_MAX_HOPS = 5
        private const val TYPE_PING = "ping"
        private const val TYPE_PONG = "pong"
    }
}
