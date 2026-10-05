package com.bluemob.app.contacts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Someone we've met over the mesh, or added by their BlueMob ID. [lastSeen] is 0 for people we've never met in person. */
data class Contact(
    val nodeId: String,
    val name: String,
    val avatar: String?,
    val lastSeen: Long,
    val location: GeoPoint? = null,
)

data class GeoPoint(val lat: Double, val lon: Double, val accuracyM: Float, val time: Long)

/**
 * Remembers everyone this phone has met, with when we last saw them and where they last
 * said they were, so the dashboard can show "last seen 2 h ago" after they leave.
 */
class ContactsStore(context: Context) {
    private val prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)

    private val _contacts = MutableStateFlow(load())
    /** Every packet touches a contact, so writes are batched: at most one save a second. */
    private val saveSoon = com.bluemob.app.util.Debounced(1_000) { save() }
    val contacts: StateFlow<Map<String, Contact>> = _contacts.asStateFlow()

    fun upsert(nodeId: String, transform: (Contact?) -> Contact) {
        val updated = transform(_contacts.value[nodeId])
        _contacts.value = _contacts.value + (nodeId to updated)
        saveSoon()
    }

    fun touch(nodeId: String, name: String, now: Long = System.currentTimeMillis()) = upsert(nodeId) {
        it?.copy(name = name, lastSeen = now) ?: Contact(nodeId, name, null, now)
    }

    /** Adds someone by their BlueMob ID, before ever meeting them. Keeps what we already know about them. */
    fun addById(nodeId: String, name: String) = upsert(nodeId) { it ?: Contact(nodeId, name, null, 0) }

    fun forgetAll() {
        _contacts.value = emptyMap()
        save()
    }

    private fun load(): Map<String, Contact> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).associate { i ->
                val o = array.getJSONObject(i)
                val loc = o.optJSONObject("loc")?.let {
                    GeoPoint(it.getDouble("lat"), it.getDouble("lon"), it.optDouble("acc", 0.0).toFloat(), it.getLong("t"))
                }
                val c = Contact(
                    nodeId = o.getString("id"),
                    name = o.getString("name"),
                    avatar = o.optString("avatar").ifEmpty { null },
                    lastSeen = o.getLong("seen"),
                    location = loc,
                )
                c.nodeId to c
            }
        }.getOrDefault(emptyMap())
    }

    private fun save() {
        val array = JSONArray()
        _contacts.value.values.forEach { c ->
            array.put(JSONObject().apply {
                put("id", c.nodeId)
                put("name", c.name)
                put("avatar", c.avatar ?: "")
                put("seen", c.lastSeen)
                c.location?.let {
                    put("loc", JSONObject().put("lat", it.lat).put("lon", it.lon).put("acc", it.accuracyM.toDouble()).put("t", it.time))
                }
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "contacts_v1"
    }
}
