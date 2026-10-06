package com.bluemob.app.mesh

import org.json.JSONObject
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A call frame on its way through other phones: `R | ttl | dest (8) | origin (8) | frame`. Phones in between read
 * only the header; the frame inside is end-to-end encrypted ([CallCipher]).
 */
object RelayFrame {
    const val MARK: Byte = 'R'.code.toByte()
    const val HEADER = 18
    const val TTL = 6

    class Parsed(val ttl: Int, val dest: String, val origin: String, val inner: ByteArray)

    fun wrap(dest: String, origin: String, inner: ByteArray, ttl: Int = TTL): ByteArray =
        byteArrayOf(MARK, ttl.toByte()) + idBytes(dest) + idBytes(origin) + inner

    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size <= HEADER || bytes[0] != MARK) return null
        return Parsed(bytes[1].toInt() and 0xFF, hex(bytes, 2), hex(bytes, 10), bytes.copyOfRange(HEADER, bytes.size))
    }

    /** The same frame with one hop used up, or null if it has gone far enough. */
    fun hop(bytes: ByteArray): ByteArray? {
        val ttl = bytes[1].toInt() and 0xFF
        if (ttl <= 1) return null
        return bytes.copyOf().also { it[1] = (ttl - 1).toByte() }
    }

    /** The kind of call frame inside ('A' voice or 'V' video), for deciding what to drop when a link is busy. */
    fun innerKind(bytes: ByteArray): Byte = when {
        bytes.size <= HEADER -> 0
        bytes[HEADER] == CallCipher.MARK && bytes.size > HEADER + 1 -> bytes[HEADER + 1]
        else -> bytes[HEADER]
    }

    fun idBytes(id: String) = ByteArray(8) { i -> id.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    private fun hex(b: ByteArray, from: Int) = (from until from + 8).joinToString("") { "%02x".format(b[it]) }
}

/**
 * Who can reach whom, so a call can go through other phones: a small distance-vector table. Every phone tells the
 * phones next to it which IDs it can reach and in how many hops ([advert]), and whether it (or someone it can reach)
 * has the internet relay. Each phone then knows the next hop towards anyone a few hops away, and towards the internet.
 */
class RouteTable(private val me: String, private val now: () -> Long = System::currentTimeMillis) {
    private class Advert(val routes: Map<String, Int>, val netHops: Int, val at: Long)
    private val adverts = mutableMapOf<String, Advert>()

    /** What [neighbor] last told us. Hop counts are from them. */
    @Synchronized fun update(neighbor: String, json: JSONObject) {
        val r = json.optJSONObject("r") ?: JSONObject()
        val routes = HashMap<String, Int>()
        r.keys().asSequence().take(MAX_ROUTES).forEach { id -> if (ID.matches(id)) routes[id] = r.optInt(id, MAX_HOPS).coerceIn(0, MAX_HOPS) }
        adverts[neighbor] = Advert(routes, json.optInt("net", -1), now())
    }

    @Synchronized fun remove(neighbor: String) { adverts.remove(neighbor) }

    /** Our own advert: us, our neighbours, and what they reach, plus how far we are from the internet. */
    @Synchronized fun advert(neighbors: Collection<String>, online: Boolean): JSONObject {
        val r = JSONObject()
        reach(neighbors).entries.sortedBy { it.value }.take(MAX_ROUTES).forEach { (id, h) -> r.put(id, h) }
        return JSONObject().put("t", TYPE).put("r", r).put("net", if (online) 0 else netHop(neighbors)?.let { it.second + 1 } ?: -1)
    }

    /** Everyone we can reach and in how many hops (neighbours are 1). */
    @Synchronized fun reach(neighbors: Collection<String>): Map<String, Int> {
        val best = HashMap<String, Int>()
        neighbors.forEach { best[it] = 1 }
        fresh(neighbors).forEach { (_, a) ->
            a.routes.forEach { (id, h) -> if (id != me && h + 1 <= MAX_HOPS && h + 1 < (best[id] ?: Int.MAX_VALUE)) best[id] = h + 1 }
        }
        return best
    }

    /** The neighbour to hand a frame for [dest] to, and how many hops it will take. Null if no one knows the way. */
    @Synchronized fun nextHop(dest: String, neighbors: Collection<String>): Pair<String, Int>? =
        if (dest in neighbors) dest to 1
        else fresh(neighbors).mapNotNull { (n, a) -> a.routes[dest]?.let { n to it + 1 } }.filter { it.second <= MAX_HOPS }.minByOrNull { it.second }

    /** The neighbour closest to the internet relay, and its distance (0 = that neighbour is online itself). */
    @Synchronized fun netHop(neighbors: Collection<String>): Pair<String, Int>? =
        fresh(neighbors).filter { it.second.netHops in 0 until MAX_HOPS }.minByOrNull { it.second.netHops }?.let { it.first to it.second.netHops }

    private fun fresh(neighbors: Collection<String>) =
        adverts.filter { (n, a) -> n in neighbors && now() - a.at < STALE_MS }.map { it.key to it.value }

    companion object {
        const val TYPE = "rt"
        const val MAX_HOPS = 5
        const val MAX_ROUTES = 60
        const val STALE_MS = 20_000L
        private val ID = Regex("^[0-9a-f]{16}$")
    }
}

/**
 * End-to-end encryption for one call's voice and video. Both phones derive the same key from their identity keys
 * (the same ECDH they use for messages) and the call ID, so nothing secret is sent and nobody in between (phones
 * relaying the call, or the internet relay) can listen or inject sound. Frame: `E | kind | counter (8) | AES-GCM`.
 * The nonce is the sender's ID prefix plus the counter, so the two directions never reuse one.
 */
class CallCipher(shared: ByteArray, callId: String, private val myId: String) {
    private val key = SecretKeySpec(Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(shared, "HmacSHA256")); doFinal("bluemob-call-v1|$callId".toByteArray()) }, "AES")
    private var counter = 0L

    @Synchronized fun seal(frame: ByteArray): ByteArray {
        val ctr = ++counter
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(myId, ctr))); updateAAD(byteArrayOf(MARK, frame[0])) }
        return byteArrayOf(MARK, frame[0]) + ByteBuffer.allocate(8).putLong(ctr).array() + c.doFinal(frame)
    }

    /** The original frame, or null if it wasn't sealed with this call's key by [senderId]. */
    fun open(bytes: ByteArray, senderId: String): ByteArray? {
        if (bytes.size < 2 + 8 + 16 || bytes[0] != MARK) return null
        val ctr = ByteBuffer.wrap(bytes, 2, 8).long
        return runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(senderId, ctr))); updateAAD(byteArrayOf(MARK, bytes[1])) }
                .doFinal(bytes, 10, bytes.size - 10)
        }.getOrNull()?.takeIf { it.isNotEmpty() && it[0] == bytes[1] }
    }

    private fun nonce(id: String, ctr: Long) = RelayFrame.idBytes(id).copyOf(4) + ByteBuffer.allocate(8).putLong(ctr).array()

    companion object {
        const val MARK: Byte = 'E'.code.toByte()
    }
}
