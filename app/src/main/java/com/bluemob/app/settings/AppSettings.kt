package com.bluemob.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class SignalMode(val label: String, val emoji: String, val tip: String) {
    SCREEN("Screen", "📱", "Flashes the whole screen white"),
    TORCH("Flashlight", "🔦", "Blinks the camera flash. Brightest, best at night"),
    SOUND("Sound", "📢", "Loud whistle-pitch beeps. Carries in fog and forest"),
    ALL("All", "🚨", "Screen, flashlight and sound together"),
}

/** A place the user saved, to walk back to with the compass. */
data class Spot(val id: String, val name: String, val lat: Double, val lon: Double, val time: Long)

/** Small preferences that aren't part of the identity: SOS signal, saved spots, saved guides. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _signal = MutableStateFlow(
        runCatching { SignalMode.valueOf(prefs.getString("signal", null) ?: "") }.getOrDefault(SignalMode.SCREEN)
    )
    val signalDefault: StateFlow<SignalMode> = _signal.asStateFlow()

    fun setSignalDefault(mode: SignalMode) {
        prefs.edit().putString("signal", mode.name).apply()
        _signal.value = mode
    }

    private val _spots = MutableStateFlow(loadSpots())
    val spots: StateFlow<List<Spot>> = _spots.asStateFlow()

    fun addSpot(spot: Spot) {
        _spots.value = _spots.value + spot
        saveSpots()
    }

    fun removeSpot(id: String) {
        _spots.value = _spots.value.filterNot { it.id == id }
        saveSpots()
    }

    private val _bookmarks = MutableStateFlow(prefs.getStringSet("bookmarks", emptySet())!!.toSet())
    val bookmarks: StateFlow<Set<String>> = _bookmarks.asStateFlow()

    fun toggleBookmark(articleId: String) {
        _bookmarks.value = if (articleId in _bookmarks.value) _bookmarks.value - articleId else _bookmarks.value + articleId
        prefs.edit().putStringSet("bookmarks", _bookmarks.value).apply()
    }

    private fun loadSpots(): List<Spot> = runCatching {
        val a = JSONArray(prefs.getString("spots", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Spot(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getLong("t"))
        }
    }.getOrDefault(emptyList())

    private fun saveSpots() {
        val a = JSONArray()
        _spots.value.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("t", it.time)) }
        prefs.edit().putString("spots", a.toString()).apply()
    }
}
