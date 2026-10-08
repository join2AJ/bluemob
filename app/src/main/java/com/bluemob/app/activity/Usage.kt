package com.bluemob.app.activity

import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** The parts of BlueMob that time is counted for. */
enum class AppArea(val label: String, val emoji: String) {
    NEARBY("Nearby", "📡"), CHATS("Chats", "💬"), CALLS("Calls", "📞"), COMPASS("Compass", "🧭"), GUIDE("Guide", "📖"),
    SKY("Sky", "✨"), SOS("SOS & rescue", "🆘"), GAMES("Games", "🎮"), YOU("You", "🙂"), SETTINGS("Settings", "⚙️"),
}

/**
 * Time spent in BlueMob, per part of the app, per day. Counted only while BlueMob is on screen, and kept on this
 * phone only (encrypted settings), for the activity dashboard.
 */
object Usage {
    private var prefs: SharedPreferences? = null
    private var area: AppArea? = null
    private var since = 0L
    private var visible = false

    private val _days = MutableStateFlow<Map<String, Map<AppArea, Long>>>(emptyMap())
    /** Seconds per area, by day ("yyyy-MM-dd"). */
    val days: StateFlow<Map<String, Map<AppArea, Long>>> = _days.asStateFlow()

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        _days.value = prefs.all.keys.filter { it.startsWith(KEY) }.associate { k ->
            val o = runCatching { JSONObject(prefs.getString(k, "{}")!!) }.getOrDefault(JSONObject())
            k.removePrefix(KEY) to AppArea.entries.associateWith { o.optLong(it.name) }.filterValues { it > 0 }
        }
    }

    /** The screen now showing. */
    @Synchronized fun enter(a: AppArea) {
        if (a == area) return
        flush()
        area = a
    }

    /** BlueMob came on screen (true) or went to the background (false). */
    @Synchronized fun visible(on: Boolean) {
        if (on == visible) return
        flush()
        visible = on
    }

    /** Adds the time since the last change to the current area. */
    @Synchronized fun flush(now: Long = SystemClock.elapsedRealtime(), wall: Long = System.currentTimeMillis()) {
        val a = area
        val s = (now - since) / 1000
        since = now
        if (!visible || a == null || s <= 0 || s > 6 * 3600) return
        val day = com.bluemob.app.guide.Streak.dayKey(wall)
        val today = (_days.value[day] ?: emptyMap()).toMutableMap()
        today[a] = (today[a] ?: 0) + s
        _days.value = _days.value + (day to today)
        prefs?.edit()?.putString(KEY + day, JSONObject(today.mapKeys { it.key.name } as Map<*, *>).toString())?.apply()
    }

    /** Seconds per area over the last [days] days (null = all). */
    fun total(days: Int?, now: Long = System.currentTimeMillis()): Map<AppArea, Long> {
        val keys = if (days == null) _days.value.keys else (0 until days).map { com.bluemob.app.guide.Streak.dayKey(now - it * 86_400_000L) }.toSet()
        val out = mutableMapOf<AppArea, Long>()
        _days.value.filterKeys { it in keys }.values.forEach { m -> m.forEach { (a, s) -> out[a] = (out[a] ?: 0) + s } }
        return out
    }

    /** Which area a screen route belongs to. */
    fun areaOf(route: String?, tab: String, inCall: Boolean): AppArea = when {
        inCall -> AppArea.CALLS
        route == null -> when (tab) {
            "NEARBY" -> AppArea.NEARBY; "CHATS" -> AppArea.CHATS; "COMPASS" -> AppArea.COMPASS; "GUIDE" -> AppArea.GUIDE; else -> AppArea.YOU
        }
        route == "chat:sky" -> AppArea.SKY
        route.startsWith("chat:") || route.startsWith("info:") || route.startsWith("media:") || route.startsWith("photo:") || route.startsWith("group") || route == "newchat" || route == "newgroup" -> AppArea.CHATS
        route.startsWith("article:") || route.startsWith("quiz:") || route == "guide-packs" -> AppArea.GUIDE
        route == "sos" || route == "signal" || route.startsWith("rescue") || route == "sos-contacts" -> AppArea.SOS
        route == "games" || route.startsWith("game:") || route.startsWith("match:") -> AppArea.GAMES
        route == "trips" -> AppArea.COMPASS
        route.startsWith("person:") || route == "activity" || route == "badges" -> AppArea.YOU
        else -> AppArea.SETTINGS
    }

    fun words(sec: Long): String = when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60} min"
        else -> "${sec / 3600} h ${sec % 3600 / 60} min"
    }

    private const val KEY = "use:"
}
