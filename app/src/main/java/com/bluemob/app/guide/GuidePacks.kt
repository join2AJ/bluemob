package com.bluemob.app.guide

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A downloadable set of guides for one kind of trip, e.g. "Mountains & snow". */
data class PackInfo(val id: String, val version: Int, val emoji: String, val title: String, val about: String, val articles: Int, val bytes: Long)

/**
 * Extra survival guides people download when they have internet, for the trip they're planning: mountains, monsoon,
 * heat, wildlife… Once downloaded they work offline like the built-in guides, and Sky answers from them too.
 * They come from the BlueMob relay and are kept on the phone (encrypted, like everything else).
 */
object GuidePacks {
    @Volatile var installedArticles: List<Article> = emptyList()
        private set

    private val _installed = MutableStateFlow<List<PackInfo>>(emptyList())
    val installed: StateFlow<List<PackInfo>> = _installed.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        reload()
    }

    private fun reload() {
        val p = prefs ?: return
        val packs = p.all.keys.filter { it.startsWith("pack:") }.mapNotNull { k -> p.getString(k, null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() } }
        installedArticles = packs.flatMap { it.second }
        _installed.value = packs.map { it.first }.sortedBy { it.title }
    }

    /** What the relay offers. Null if it couldn't be reached. */
    suspend fun available(relay: String): List<PackInfo>? = withContext(Dispatchers.IO) {
        get("$relay/v1/guides")?.let { body ->
            val a = JSONObject(body).optJSONArray("packs") ?: JSONArray()
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { j -> info(j, j.optInt("articles"), j.optLong("bytes")) } }
        }
    }

    /** Downloads (or updates) a pack. Returns an error to show, or null when it's installed. */
    suspend fun download(relay: String, id: String): String? = withContext(Dispatchers.IO) {
        val body = get("$relay/v1/guides/$id") ?: return@withContext "Couldn't download it. Check the internet connection and try again."
        val parsed = runCatching { parse(JSONObject(body)) }.getOrNull() ?: return@withContext "That pack couldn't be read."
        if (parsed.second.isEmpty()) return@withContext "That pack is empty."
        prefs?.edit()?.putString("pack:$id", body)?.apply()
        reload()
        null
    }

    fun remove(id: String) { prefs?.edit()?.remove("pack:$id")?.apply(); reload() }

    private fun info(j: JSONObject, articles: Int, bytes: Long) = PackInfo(j.getString("id"), j.optInt("version", 1), j.optString("emoji", "📘"),
        j.getString("title"), j.optString("about"), articles, bytes)

    /** Reads a pack. Article IDs get the pack's prefix so they never clash with built-in ones. */
    fun parse(j: JSONObject): Pair<PackInfo, List<Article>> {
        val a = j.getJSONArray("articles")
        val articles = (0 until a.length()).mapNotNull { i ->
            val x = a.getJSONObject(i)
            runCatching {
                Article(
                    id = j.getString("id") + "-" + x.getString("id"),
                    category = runCatching { GuideCategory.valueOf(x.getString("category")) }.getOrDefault(GuideCategory.BASICS),
                    title = x.getString("title").take(80), minutes = x.optInt("minutes", 2).coerceIn(1, 30), intro = x.optString("intro").take(400),
                    steps = x.getJSONArray("steps").let { s -> (0 until minOf(s.length(), 20)).map { s.getString(it).take(500) } },
                    avoid = x.optJSONArray("avoid")?.let { s -> (0 until minOf(s.length(), 10)).map { s.getString(it).take(400) } }.orEmpty(),
                )
            }.getOrNull()
        }
        return info(j, articles.size, j.toString().length.toLong()) to articles
    }

    private fun get(url: String): String? = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText().takeIf { it.length < 512 * 1024 } else null } finally { c.disconnect() }
    }.getOrNull()
}
