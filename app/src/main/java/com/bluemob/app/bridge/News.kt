package com.bluemob.app.bridge

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A message from the BlueMob team to everyone: an alert, an update, a tip. */
data class NewsItem(val id: String, val at: Long, val title: String, val text: String, val link: String = "", val urgent: Boolean = false,
                    /** Sent to this person only, not to everyone. */
                    val personal: Boolean = false)

/**
 * News from the BlueMob team, posted on the relay. Fetched when BlueMob connects, every few hours, and right away when
 * the relay says there's something new. Kept on the phone so it can be read offline.
 */
class News(private val prefs: SharedPreferences, private val relay: () -> String?, private val onNew: (NewsItem) -> Unit,
           /** "&id=…&at=…&sig=…": proves this phone's BlueMob ID, so messages meant only for it come too. */
           private val auth: () -> String = { "" }) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<NewsItem>> = _items.asStateFlow()

    private val _seenAt = MutableStateFlow(prefs.getLong(SEEN, 0L))
    /** When the news list was last opened: newer items count as unread. */
    val seenAt: StateFlow<Long> = _seenAt.asStateFlow()

    fun markSeen() { val now = System.currentTimeMillis(); _seenAt.value = now; prefs.edit().putLong(SEEN, now).apply() }

    private fun load(): List<NewsItem> = runCatching {
        val a = JSONArray(prefs.getString(ITEMS, "[]"))
        (0 until a.length()).map { i -> parse(a.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun parse(o: JSONObject) = NewsItem(o.getString("id"), o.getLong("at"), o.optString("title"), o.optString("text"), o.optString("link"), o.optBoolean("urgent"), o.optBoolean("personal"))

    /** Fetches anything new. New items from the last week are announced with a notification. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val base = relay()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return@withContext
        val since = _items.value.maxOfOrNull { it.at } ?: (System.currentTimeMillis() - 30 * 86_400_000L)
        val body = runCatching {
            val c = URL("$base/v1/announcements?since=$since" + auth()).openConnection() as HttpURLConnection
            try { c.connectTimeout = 15_000; c.readTimeout = 20_000; if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
        }.getOrNull() ?: return@withContext
        val fresh = runCatching { JSONObject(body).getJSONArray("items").let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
            .filter { n -> _items.value.none { it.id == n.id } }
        if (fresh.isEmpty()) return@withContext
        val all = (fresh + _items.value).sortedByDescending { it.at }.take(50)
        _items.value = all
        prefs.edit().putString(ITEMS, JSONArray(all.map { JSONObject().put("id", it.id).put("at", it.at).put("title", it.title).put("text", it.text).put("link", it.link).put("urgent", it.urgent).put("personal", it.personal) }).toString()).apply()
        val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
        fresh.filter { it.at > weekAgo }.take(3).forEach(onNew)
    }

    private companion object {
        const val ITEMS = "items"
        const val SEEN = "seen_at"
    }
}
