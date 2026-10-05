package com.bluemob.app.mesh

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.crypto.KeyBook
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.UUID

/** How the router reaches phones in direct range. [NearbyMeshTransport] is the real one; tests wire routers together. */
interface Wire {
    fun neighbors(): List<String>
    fun send(nodeId: String, packet: JSONObject)
    fun linkName(nodeId: String): String
    fun nameOf(nodeId: String): String
}

/** The internet as one more way to reach people (the BlueMob relay). See [com.bluemob.app.bridge.BridgeClient]. */
interface InternetPath {
    fun up(): Boolean
    /** Queue a packet to upload to the relay. */
    fun enqueue(key: String, packet: String)
    /** Ask the relay for this ID's public key. */
    fun lookupKey(id: String)
}

/** A message or receipt this phone carries for others. [copies] is how many more phones it may hand copies to. */
data class CarriedItem(
    val key: String,
    val to: String,
    val origin: String,
    val packet: String,
    val copies: Int,
    val givenTo: Set<String>,
    val expiresAt: Long,
    val receivedAt: Long,
)

interface RelayStore {
    fun all(): List<CarriedItem>
    fun put(item: CarriedItem)
    fun remove(key: String)
}

class MemoryRelayStore : RelayStore {
    private val items = LinkedHashMap<String, CarriedItem>()
    override fun all() = items.values.toList()
    override fun put(item: CarriedItem) { items[item.key] = item }
    override fun remove(key: String) { items.remove(key) }
}

/**
 * Gets messages to anyone with a BlueMob ID, even far beyond radio range, by store-carry-forward:
 *
 * - If they're in range, the message goes straight to them.
 * - Otherwise it's handed to phones nearby, who carry it and hand it on when they meet others ("Binary Spray and
 *   Wait": a message starts with [COPIES] copies, each hand-off gives away half, and the last copy is only given to
 *   the recipient). That reaches people quickly without flooding the mesh.
 * - Messages are signed and end-to-end encrypted ([Crypto]); carriers can't read or change them.
 * - The recipient's delivery receipt travels back the same way, and tells carriers they can drop their copies.
 * - If we don't have someone's public key yet, we ask the mesh ("keyq"); any phone that knows it answers, and the
 *   answer proves itself because the key must hash to their ID.
 */
class MeshRouter(
    private val wire: Wire,
    private val keys: DeviceKeys,
    private val keyBook: KeyBook,
    private val store: RelayStore = MemoryRelayStore(),
    private val myName: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) : MessageLink {
    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    private class Outgoing(val key: String, val packet: String, var copies: Int, val givenTo: MutableSet<String> = mutableSetOf(), var directEpoch: Long = -1, var uploaded: Boolean = false)
    private val outgoing = mutableMapOf<String, Outgoing>()
    private val connectedEpoch = mutableMapOf<String, Long>()
    private var epoch = 0L
    private val seen = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > MAX_SEEN
    }
    private val askedForKey = mutableMapOf<String, Long>()

    val myId: String get() = keys.nodeId
    /** How many messages this phone is carrying for other people. */
    val carrying: Int get() = store.all().count { it.expiresAt > now() }

    override fun isConnected(nodeId: String) = nodeId in wire.neighbors()
    override fun linkName(nodeId: String) = if (isConnected(nodeId)) wire.linkName(nodeId) else "the mesh"

    override fun sendChat(toNodeId: String, messageId: String, text: String, sentAt: Long): Handoff {
        val out = outgoing[messageId] ?: run {
            val theirKey = keyBook.key(toNodeId) ?: run { askForKey(toNodeId); return Handoff.NeedsKey }
            val shared = Crypto.sharedKey(keys.keyPair.private, theirKey, keys.nodeId, toNodeId)
            val sealed = Crypto.seal(shared, JSONObject().put("text", text).toString().toByteArray(), messageId.toByteArray())
            val body = JSONObject().put("id", messageId).put("to", toNodeId).put("at", sentAt).put("x", sentAt + TTL_MS)
                .put("name", myName()).put("c", sealed)
            Outgoing("$RMSG:$messageId", Envelope.seal(RMSG, body, keys).toString(), COPIES).also { outgoing[messageId] = it }
        }
        return offer(out, toNodeId)
    }

    override fun sendReceipt(toNodeId: String, messageId: String, read: Boolean): Boolean {
        val t = now()
        val body = JSONObject().put("mid", messageId).put("to", toNodeId).put("k", if (read) "r" else "d").put("at", t).put("x", t + TTL_MS)
        val out = Outgoing("$RRCPT:$messageId${if (read) "r" else "d"}", Envelope.seal(RRCPT, body, keys).toString(), RECEIPT_COPIES)
        return offer(out, toNodeId) !is Handoff.Held
    }

    private fun offer(out: Outgoing, to: String): Handoff {
        val ns = wire.neighbors()
        if (to in ns) {
            val ep = connectedEpoch[to] ?: 0
            if (out.directEpoch == ep) return Handoff.Held // already sent on this connection
            out.directEpoch = ep
            wire.send(to, JSONObject(out.packet).put("c", 1))
            return Handoff.Direct(wire.linkName(to))
        }
        val given = mutableListOf<String>()
        for (n in ns) {
            if (out.copies <= 1) break
            if (n in out.givenTo) continue
            val half = out.copies / 2
            wire.send(n, JSONObject(out.packet).put("c", half))
            out.copies -= half
            out.givenTo += n
            given += n
        }
        val names = given.map(wire::nameOf).toMutableList()
        // Far away? If this phone has internet, the relay carries it too. Whichever arrives first wins.
        if (!out.uploaded && internet?.up() == true) {
            internet?.enqueue(out.key, JSONObject(out.packet).put("c", 1).toString())
            out.uploaded = true
            names += INTERNET_NAME
        }
        return if (names.isEmpty()) Handoff.Held else Handoff.Carried(names)
    }

    /** A phone came into range: hand it anything it should carry or receive. */
    fun onNeighborConnected(nodeId: String) {
        epoch++
        connectedEpoch[nodeId] = epoch
        expire()
        store.all().forEach { forward(it) }
        askedForKey.keys.toList().forEach { id -> if (!keyBook.knows(id)) askForKey(id, force = true) }
        _events.tryEmit(MeshEvent.PeerConnected(nodeId))
    }

    /** A routed packet arrived from a phone in range. */
    fun onPacket(fromNeighbor: String, json: JSONObject) {
        when (json.optString("t")) {
            RMSG, RRCPT -> onRouted(fromNeighbor, json)
            KEYQ -> onKeyQuestion(fromNeighbor, json)
            KEYA -> onKeyAnswer(fromNeighbor, json)
        }
    }

    private fun onRouted(fromNeighbor: String, json: JSONObject) {
        val o = Envelope.open(json) ?: return log("Dropped a message with a bad signature")
        val b = o.body
        val key = o.type + ":" + if (o.type == RMSG) b.optString("id") else b.optString("mid") + b.optString("k")
        val repeat = seen.put(key, true) != null
        if (b.optLong("x") < now()) return
        keyBook.add(o.publicB64)
        val to = b.optString("to")
        if (o.from == keys.nodeId) return
        // Repeats for us still go up: the message store discards the copy but answers with a receipt again,
        // in case our first receipt was lost.
        if (to == keys.nodeId) return deliver(o)
        if (repeat) return
        // A delivery receipt means the message arrived: carriers can stop carrying it.
        if (o.type == RRCPT && b.optString("k") == "d") store.remove("$RMSG:${b.optString("mid")}")
        val mine = store.all().filter { it.origin == o.from }
        if (mine.size >= MAX_PER_ORIGIN) return log("Not carrying more for ${o.from.take(4)}: limit reached")
        val all = store.all()
        if (all.size >= MAX_CARRIED) all.minByOrNull { it.receivedAt }?.let { store.remove(it.key) }
        val item = CarriedItem(key, to, o.from, JSONObject(json.toString()).put("h", o.hops + 1).toString(),
            json.optInt("c", 1).coerceIn(1, COPIES), setOf(fromNeighbor), b.optLong("x"), now())
        store.put(item)
        forward(item)
    }

    private fun forward(item: CarriedItem) {
        val ns = wire.neighbors()
        if (item.to in ns) {
            wire.send(item.to, JSONObject(item.packet).put("c", 1))
            store.remove(item.key)
            return
        }
        var copies = item.copies
        val given = item.givenTo.toMutableSet()
        for (n in ns) {
            if (copies <= 1) break
            if (n in given) continue
            val half = copies / 2
            wire.send(n, JSONObject(item.packet).put("c", half))
            copies -= half
            given += n
        }
        if (copies != item.copies) store.put(item.copy(copies = copies, givenTo = given))
        if (item.key !in uploadedCarried && internet?.up() == true) {
            internet?.enqueue(item.key, item.packet)
            uploadedCarried += item.key
        }
    }

    /** Set when this phone can reach the BlueMob relay. */
    var internet: InternetPath? = null
    private val uploadedCarried = mutableSetOf<String>()

    /** A packet downloaded from the relay. */
    fun onInternetPacket(json: JSONObject) = onPacket(INTERNET_NAME, json)

    /** The relay gave us a key we asked for. */
    fun onKeyFound(id: String) {
        if (askedForKey.remove(id) != null) _events.tryEmit(MeshEvent.KeyLearned(id))
    }

    /** Internet just became available: anything waiting can go now. */
    fun onInternetUp() { _events.tryEmit(MeshEvent.RouteAvailable) }

    private fun deliver(o: Envelope.Opened) {
        val b = o.body
        val viaInternet = o.raw.optInt("net") == 1
        val hops = o.hops + 1
        when (o.type) {
            RMSG -> {
                val theirKey = keyBook.key(o.from) ?: return
                val id = b.optString("id")
                val shared = Crypto.sharedKey(keys.keyPair.private, theirKey, keys.nodeId, o.from)
                val plain = Crypto.open(shared, b.optString("c"), id.toByteArray()) ?: return log("Couldn't decrypt a message from ${o.from.take(4)}")
                val text = runCatching { JSONObject(String(plain)).optString("text") }.getOrNull()?.take(MAX_TEXT) ?: return
                if (text.isEmpty() || id.isEmpty()) return
                _events.tryEmit(MeshEvent.MessageReceived(o.from, id, text, b.optLong("at"), hops, b.optString("name").take(24).ifBlank { null }, viaInternet))
            }
            RRCPT -> _events.tryEmit(MeshEvent.Receipt(o.from, b.optString("mid"), b.optString("k") == "r", hops, viaInternet))
        }
    }

    private fun askForKey(id: String, force: Boolean = false) {
        val last = askedForKey[id]
        if (!force && last != null && now() - last < 15_000) return
        askedForKey[id] = now()
        internet?.takeIf { it.up() }?.lookupKey(id)
        val q = JSONObject().put("t", KEYQ).put("q", id).put("r", UUID.randomUUID().toString().take(12)).put("h", 0)
        seen[KEYQ + q.optString("r")] = true
        wire.neighbors().forEach { wire.send(it, q) }
    }

    private fun onKeyQuestion(fromNeighbor: String, json: JSONObject) {
        val r = json.optString("r")
        if (r.isEmpty() || seen.put(KEYQ + r, true) != null) return
        val q = json.optString("q")
        val pk = if (q == keys.nodeId) keys.publicB64 else keyBook.b64(q)
        if (pk != null) {
            val a = JSONObject().put("t", KEYA).put("pk", pk).put("r", r).put("h", 0)
            seen[KEYA + r] = true
            wire.send(fromNeighbor, a)
            return
        }
        val h = json.optInt("h") + 1
        if (h < KEY_HOPS) wire.neighbors().filter { it != fromNeighbor }.forEach { wire.send(it, JSONObject(json.toString()).put("h", h)) }
    }

    private fun onKeyAnswer(fromNeighbor: String, json: JSONObject) {
        val r = json.optString("r")
        if (seen.put(KEYA + r, true) != null) return
        val id = keyBook.add(json.optString("pk")) ?: return
        if (askedForKey.remove(id) != null) {
            log("Learned the key for ${id.take(4)}")
            _events.tryEmit(MeshEvent.KeyLearned(id))
            return
        }
        // Not ours: pass it back toward whoever asked.
        val h = json.optInt("h") + 1
        if (h < KEY_HOPS) wire.neighbors().filter { it != fromNeighbor }.forEach { wire.send(it, JSONObject(json.toString()).put("h", h)) }
    }

    private fun expire() {
        val t = now()
        store.all().filter { it.expiresAt < t }.forEach { store.remove(it.key) }
    }

    companion object {
        const val INTERNET_NAME = "the internet"
        const val RMSG = "rmsg"
        const val RRCPT = "rrcpt"
        const val KEYQ = "keyq"
        const val KEYA = "keya"
        const val COPIES = 8
        const val RECEIPT_COPIES = 4
        const val TTL_MS = 3 * 24 * 3_600_000L
        const val MAX_CARRIED = 300
        const val MAX_PER_ORIGIN = 30
        const val MAX_SEEN = 5_000
        const val MAX_TEXT = 2_000
        const val KEY_HOPS = 6
    }
}
