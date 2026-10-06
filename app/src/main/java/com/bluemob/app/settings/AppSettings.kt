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
data class Spot(val id: String, val name: String, val lat: Double, val lon: Double, val time: Long) {
    val isBaseCamp: Boolean get() = id == BASE_CAMP_ID

    companion object {
        const val BASE_CAMP_ID = "base"
    }
}

/** Someone to text when the user sends an SOS. Stored only on this phone. */
data class SosContact(val id: String, val name: String, val phone: String)

/** Small preferences that aren't part of the identity: SOS signal, saved spots, saved guides. */
class AppSettings(context: Context) {
    private val prefs = com.bluemob.app.crypto.SecurePrefs.open(context, "settings")

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

    /** Base camp is a spot with a fixed ID, so setting it again moves it. */
    fun setBaseCamp(lat: Double, lon: Double) {
        _spots.value = listOf(Spot(Spot.BASE_CAMP_ID, "Base camp", lat, lon, System.currentTimeMillis())) + _spots.value.filterNot { it.isBaseCamp }
        saveSpots()
    }

    private val _background = MutableStateFlow(prefs.getBoolean("background", false))
    /** Keep the mesh running with the screen off (a foreground service with a notification). Off until the user turns it on. */
    val background: StateFlow<Boolean> = _background.asStateFlow()

    fun setBackground(on: Boolean) {
        prefs.edit().putBoolean("background", on).apply()
        _background.value = on
    }

    private val _meshAtStart = MutableStateFlow(prefs.getBoolean("mesh_at_start", false))
    /** Start the mesh when BlueMob opens. Off until the user chooses it. */
    val meshAtStart: StateFlow<Boolean> = _meshAtStart.asStateFlow()
    fun setMeshAtStart(on: Boolean) { prefs.edit().putBoolean("mesh_at_start", on).apply(); _meshAtStart.value = on }

    private fun policy(key: String) = runCatching { RadioPolicy.valueOf(prefs.getString(key, null) ?: "") }.getOrDefault(RadioPolicy.ASK)
    private val _bluetoothPolicy = MutableStateFlow(policy("policy_bluetooth"))
    /** Whether the mesh may switch Bluetooth on (through Android's Nearby service) without asking first. */
    val bluetoothPolicy: StateFlow<RadioPolicy> = _bluetoothPolicy.asStateFlow()
    fun setBluetoothPolicy(p: RadioPolicy) { prefs.edit().putString("policy_bluetooth", p.name).apply(); _bluetoothPolicy.value = p }

    private val _wifiPolicy = MutableStateFlow(policy("policy_wifi"))
    /** Whether the mesh may use (and switch on) Wi-Fi for faster links: ask, allow, or never (Bluetooth only). */
    val wifiPolicy: StateFlow<RadioPolicy> = _wifiPolicy.asStateFlow()
    fun setWifiPolicy(p: RadioPolicy) { prefs.edit().putString("policy_wifi", p.name).apply(); _wifiPolicy.value = p }

    /** The relay built into this version of the app. Phones use it automatically. */
    val defaultBridgeUrl: String = com.bluemob.app.BuildConfig.DEFAULT_RELAY_URL.trimEnd('/')

    private val _bridgeUrl = MutableStateFlow(prefs.getString("bridge_url", null) ?: defaultBridgeUrl)
    /** The BlueMob relay this phone uses when it has internet: the built-in one, unless the user picked another. */
    val bridgeUrl: StateFlow<String> = _bridgeUrl.asStateFlow()

    /** Uses another relay. Blank (or the built-in address) goes back to the built-in relay. */
    fun setBridgeUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty() || clean == defaultBridgeUrl) prefs.edit().remove("bridge_url").apply() else prefs.edit().putString("bridge_url", clean).apply()
        _bridgeUrl.value = clean.ifEmpty { defaultBridgeUrl }
    }

    val bridgeCursor: Long get() = prefs.getLong("bridge_cursor", 0)
    fun setBridgeCursor(v: Long) = prefs.edit().putLong("bridge_cursor", v).apply()

    private val _trailOn = MutableStateFlow(prefs.getBoolean("trail", false))
    /** Opt-in: recording the trail keeps GPS on. */
    val trailOn: StateFlow<Boolean> = _trailOn.asStateFlow()

    fun setTrailOn(on: Boolean) {
        prefs.edit().putBoolean("trail", on).apply()
        _trailOn.value = on
    }

    private val _sosContacts = MutableStateFlow(loadContacts())
    val sosContacts: StateFlow<List<SosContact>> = _sosContacts.asStateFlow()

    fun addSosContact(name: String, phone: String) {
        _sosContacts.value = _sosContacts.value + SosContact("c" + System.currentTimeMillis(), name.trim(), phone.trim())
        saveContacts()
    }

    fun removeSosContact(id: String) {
        _sosContacts.value = _sosContacts.value.filterNot { it.id == id }
        saveContacts()
    }

    private fun loadContacts(): List<SosContact> = runCatching {
        val a = JSONArray(prefs.getString("sosContacts", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { SosContact(it.getString("id"), it.getString("name"), it.getString("phone")) } }
    }.getOrDefault(emptyList())

    private fun saveContacts() {
        val a = JSONArray()
        _sosContacts.value.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)) }
        prefs.edit().putString("sosContacts", a.toString()).apply()
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

/** What BlueMob may switch on by itself. Everything starts at [ASK]. */
enum class RadioPolicy { ASK, ALLOW, NEVER }
