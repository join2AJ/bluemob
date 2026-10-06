package com.bluemob.app.bridge

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A live link to the BlueMob relay over the internet, used for calls with people who aren't nearby.
 *
 * While this phone has internet and a relay is set, it stays signed in (with its device key, so no one else can use
 * its ID) and reconnects by itself. Call set-up travels as the app's own signed packets; voice and video as
 * `[8-byte ID | data]` frames. The relay only passes bytes between two signed-in phones and stores nothing.
 */
class LiveLink(
    private val relayUrl: () -> String?,
    private val keys: DeviceKeys,
    private val scope: CoroutineScope,
    private val online: () -> Boolean,
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).connectTimeout(20, TimeUnit.SECONDS)
        .dns(SafeDns).build(),
) {
    private val _connected = MutableStateFlow(false)
    /** Signed in to the relay right now. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** A signed packet from [from] (call set-up, games…). Called on OkHttp's thread. */
    var onText: (from: String, data: String) -> Unit = { _, _ -> }
    /** Call audio or video from [from]. Called on OkHttp's thread. */
    var onBinary: (from: String, bytes: ByteArray) -> Unit = { _, _ -> }
    /** The relay says [to] isn't signed in ([waking]: it sent their phone a wake-up, so they may be on soon). */
    var onOffline: (to: String, waking: Boolean) -> Unit = { _, _ -> }

    /** Firebase token, sent to the relay so it can wake this phone when it isn't connected. */
    @Volatile private var pushToken: String? = null
    fun setPushToken(token: String) {
        pushToken = token
        ws?.takeIf { _connected.value }?.send(JSONObject().put("t", "push").put("token", token).toString())
    }
    /** The relay stored a message for us (or a phone we carry): fetch it now instead of at the next sync. */
    var onPoke: () -> Unit = {}

    /** Who was online at the last [askPresence]: signed in themselves, or reachable through a phone near them. */
    data class Presence(val online: Set<String> = emptySet(), val via: Map<String, String> = emptyMap(), val at: Long = 0) {
        fun reachable(id: String) = id in online || id in via
    }
    private val _presence = MutableStateFlow(Presence())
    val presence: StateFlow<Presence> = _presence.asStateFlow()
    private var lastVia: Set<String> = emptySet()
    private var lastViaAt = 0L

    @Volatile private var ws: WebSocket? = null
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val url = relayUrl()?.trim()?.trimEnd('/')
                if (url.isNullOrEmpty() || !online()) { if (ws != null) reconnect(); delay(5_000); continue }
                if (!_connected.value) {
                    if (ws == null) open(url)
                    var waited = 0
                    while (!_connected.value && ws != null && waited < 10_000) { delay(250); waited += 250 }
                    if (!_connected.value) {
                        ws?.cancel(); ws = null
                        delay(backoff); backoff = (backoff * 2).coerceAtMost(60_000)
                        continue
                    }
                    backoff = 2_000L
                }
                // Signed in: check again every second (a dropped link clears [connected] and we reconnect).
                delay(1_000)
            }
        }
    }

    fun stop() { loop?.cancel(); loop = null; ws?.close(1000, "bye"); ws = null; _connected.value = false }

    /** Reconnect now, e.g. after the relay address changed. */
    fun reconnect() { ws?.close(1000, "reconnect"); ws = null; _connected.value = false }

    private fun open(base: String) {
        val wsUrl = base.replaceFirst(Regex("^http"), "ws") + "/v1/live"
        val request = runCatching { Request.Builder().url(wsUrl).build() }.getOrNull() ?: return
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (m.optString("t")) {
                    "challenge" -> {
                        val sig = Crypto.encode(keys.sign("bluemob-live|${m.optString("n")}".toByteArray()))
                        webSocket.send(JSONObject().put("t", "auth").put("pk", keys.publicB64).put("sig", sig).toString())
                    }
                    "ok" -> if (m.optString("id") == keys.nodeId) {
                        lastVia = emptySet(); _connected.value = true
                        pushToken?.let { webSocket.send(JSONObject().put("t", "push").put("token", it).toString()) }
                    }
                    "poke" -> onPoke()
                    "presence" -> {
                        val on = m.optJSONArray("online")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { ID.matches(it) }.toSet() }.orEmpty()
                        val via = m.optJSONObject("via")?.let { v -> v.keys().asSequence().filter { ID.matches(it) }.associateWith { v.optString(it) } }.orEmpty()
                        _presence.value = Presence(on, via, System.currentTimeMillis())
                    }
                    "msg" -> m.optString("from").takeIf { ID.matches(it) }?.let { onText(it, m.optString("data")) }
                    "offline" -> onOffline(m.optString("to"), m.optBoolean("waking"))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size < 9) return
                val all = bytes.toByteArray()
                onBinary(all.copyOfRange(0, 8).joinToString("") { "%02x".format(it) }, all.copyOfRange(8, all.size))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = gone(webSocket)
        })
    }

    private fun gone(socket: WebSocket) {
        if (ws === socket) { ws = null; _connected.value = false }
    }

    /** Sends a signed packet to [to]. False if not signed in. */
    fun sendText(to: String, data: String): Boolean {
        val s = ws?.takeIf { _connected.value } ?: return false
        return s.send(JSONObject().put("t", "send").put("to", to).put("data", data).toString())
    }

    /** Sends call audio or video to [to]. False if not signed in. */
    fun sendBinary(to: String, bytes: ByteArray): Boolean {
        val s = ws?.takeIf { _connected.value } ?: return false
        val id = runCatching { ByteArray(8) { i -> to.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull() ?: return false
        return s.send((id + bytes).toByteString())
    }

    /** Asks who of [ids] is online; the answer updates [presence]. */
    fun askPresence(ids: Collection<String>): Boolean {
        val s = ws?.takeIf { _connected.value } ?: return false
        if (ids.isEmpty()) return false
        return s.send(JSONObject().put("t", "presence").put("ids", org.json.JSONArray(ids.take(50))).toString())
    }

    /**
     * Tells the relay which phones near us (with no internet of their own) we can pass things to. Sent when the list
     * changes, and every minute anyway.
     */
    fun carry(ids: Collection<String>) {
        val s = ws?.takeIf { _connected.value } ?: return
        val set = ids.filter { ID.matches(it) }.take(50).toSet()
        if (set == lastVia && System.currentTimeMillis() - lastViaAt < 60_000) return
        if (s.send(JSONObject().put("t", "via").put("ids", org.json.JSONArray(set.toList())).toString())) { lastVia = set; lastViaAt = System.currentTimeMillis() }
    }

    /** Bytes queued to the relay but not sent yet: calls hold back video and drop stale voice when this grows. */
    fun backlog(): Long = ws?.queueSize() ?: 0

    private companion object {
        val ID = Regex("^[0-9a-f]{16}$")
    }

    /**
     * Android throws SecurityException (not an IOException) when network access is refused, e.g. by a firewall app or
     * a "no internet" profile. OkHttp would let that crash its thread; report it as a normal failed lookup instead.
     */
    private object SafeDns : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> = try {
            okhttp3.Dns.SYSTEM.lookup(hostname)
        } catch (e: SecurityException) {
            throw java.net.UnknownHostException("Network access refused: ${e.message}")
        }
    }
}
