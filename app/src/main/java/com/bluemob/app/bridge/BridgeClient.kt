package com.bluemob.app.bridge

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.crypto.KeyBook
import com.bluemob.app.mesh.InternetPath
import com.bluemob.app.mesh.MeshRouter
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP, so tests can swap it. Returns (status, body), or null if the network failed. */
interface Http {
    fun post(url: String, body: String): Pair<Int, String>?
    fun get(url: String): Pair<Int, String>?
}

object UrlHttp : Http {
    private fun call(url: String, method: String, body: String?): Pair<Int, String>? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        // Generous: a free-tier relay can take up to a minute to wake up after being idle.
        c.connectTimeout = 20_000
        c.readTimeout = 70_000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        c.disconnect()
        code to text
    }.getOrNull()

    override fun post(url: String, body: String) = call(url, "POST", body)
    override fun get(url: String) = call(url, "GET", null)
}

/** What one sync did, for the status screen. */
data class SyncResult(val ok: Boolean, val uploaded: Int = 0, val downloaded: Int = 0, val keysFound: Int = 0, val error: String? = null)

/**
 * Talks to the BlueMob relay (server/relay.js). One [sync]: upload queued packets (our messages and ones we carry),
 * look up keys we're missing, and download packets for this phone and the phones around it, which the router then
 * delivers or hands on over the mesh. The relay only ever sees signed, end-to-end encrypted packets.
 */
class BridgeClient(
    private val baseUrl: () -> String?,
    private val keys: DeviceKeys,
    private val keyBook: KeyBook,
    private val http: Http = UrlHttp,
    /** How far we've read, per ID. Our own is saved between launches; neighbours' start from the beginning. */
    val cursors: MutableMap<String, Long> = mutableMapOf(),
    private val now: () -> Long = System::currentTimeMillis,
) : InternetPath {
    /** False while the phone has no internet. */
    var online: () -> Boolean = { true }
    private val queue = LinkedHashMap<String, String>()
    private val wantedKeys = LinkedHashSet<String>()
    private val lock = Any()

    val configured: Boolean get() = !baseUrl().isNullOrBlank()
    override fun up(): Boolean = configured && online()
    override fun enqueue(key: String, packet: String) { synchronized(lock) { queue[key] = packet } }
    override fun lookupKey(id: String) { synchronized(lock) { wantedKeys += id } }
    val queued: Int get() = synchronized(lock) { queue.size }

    /** People whose ratings we want from the relay (people nearby, chats, SOS senders). */
    var ratingSubjects: () -> List<String> = { emptyList() }
    /** Ratings downloaded from the relay. Called through onRouter's thread. */
    var onRatings: (List<JSONObject>) -> Unit = {}
    private val ratingsFetchedAt = mutableMapOf<String, Long>()

    /**
     * One round trip. Network calls happen on the caller's thread; anything that touches the router goes through
     * [onRouter], so the app can run it on the main thread.
     */
    suspend fun sync(router: MeshRouter, neighbors: List<String>, onRouter: suspend (() -> Unit) -> Unit = { it() }): SyncResult {
        val base = baseUrl()?.trimEnd('/') ?: return SyncResult(false, error = "No relay set up")
        // 1. Upload.
        val batch = synchronized(lock) { queue.entries.take(100).map { it.key to it.value } }
        var uploaded = 0
        if (batch.isNotEmpty()) {
            val body = JSONObject().put("packets", JSONArray(batch.map { JSONObject(it.second) })).toString()
            val r = http.post("$base/v1/push", body) ?: return SyncResult(false, error = "Can't reach the relay")
            if (r.first != 200) return SyncResult(false, error = "Relay said ${r.first}")
            synchronized(lock) { batch.forEach { queue.remove(it.first) } }
            uploaded = batch.size
        }
        // 2. Keys we're missing.
        val ids = synchronized(lock) { wantedKeys.toList() }
        var found = 0
        for (id in ids) {
            val r = http.get("$base/v1/key?id=$id") ?: break
            if (r.first == 200) {
                val pk = runCatching { JSONObject(r.second).getString("pk") }.getOrNull()
                if (pk != null && keyBook.add(pk) == id) {
                    found++
                    synchronized(lock) { wantedKeys.remove(id) }
                    onRouter { router.onKeyFound(id) }
                }
            }
        }
        // 3. Ratings about the people around us, at most every 10 minutes each.
        val due = ratingSubjects().distinct().filter { now() - (ratingsFetchedAt[it] ?: 0) > 10 * 60_000 }.take(10)
        for (id in due) {
            val rr = http.get("$base/v1/ratings?subject=$id") ?: break
            ratingsFetchedAt[id] = now()
            if (rr.first == 200) {
                val arr = runCatching { JSONObject(rr.second).getJSONArray("ratings") }.getOrNull() ?: continue
                val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                if (list.isNotEmpty()) onRouter { onRatings(list) }
            }
        }
        // 4. Download for us and the phones around us. Pulling also registers our key, so people can find us by ID.
        val pullIds = (listOf(keys.nodeId) + neighbors).distinct().take(50)
        val at = now()
        val sig = Crypto.encode(keys.sign("bluemob-pull|$at|${pullIds.joinToString(",")}".toByteArray()))
        val since = JSONObject().apply { pullIds.forEach { id -> cursors[id]?.let { put(id, it) } } }
        val req = JSONObject().put("ids", JSONArray(pullIds)).put("since", since)
            .put("proof", JSONObject().put("pk", keys.publicB64).put("at", at).put("sig", sig)).toString()
        val r = http.post("$base/v1/pull", req) ?: return SyncResult(false, uploaded, error = "Can't reach the relay")
        if (r.first != 200) return SyncResult(false, uploaded, error = "Relay said ${r.first}")
        val res = JSONObject(r.second)
        val packets = res.optJSONArray("packets") ?: JSONArray()
        onRouter { for (i in 0 until packets.length()) router.onInternetPacket(packets.getJSONObject(i)) }
        res.optJSONObject("next")?.let { n -> n.keys().forEach { id -> cursors[id] = n.optLong(id) } }
        return SyncResult(true, uploaded, packets.length(), found)
    }
}
