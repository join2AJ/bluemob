package com.bluemob.app.audit

import android.content.SharedPreferences
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.data.AuditEntry
import com.bluemob.app.mesh.Envelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A phone that holds a copy of our newest audit entry, and what it said the last time it handed it back. */
data class Witness(val nodeId: String, val name: String, val seq: Long, val matches: Boolean, val at: Long)

/**
 * Outside proof for the audit trail. On every connection we give the other phone a signed note of our newest entry
 * (seq + hash), and it hands back the note it kept from last time. Our signature proves the note is ours; if our
 * trail no longer contains that exact entry, someone changed it. We also keep the notes other phones give us.
 */
class AuditWitness(private val keys: DeviceKeys, private val prefs: SharedPreferences, private val nameOf: (String) -> String) {
    private val _witnesses = MutableStateFlow(load())
    val witnesses: StateFlow<List<Witness>> = _witnesses.asStateFlow()

    /** The signed note of our newest entry, to give a phone that just connected. */
    fun headNote(last: AuditEntry?): JSONObject? = last?.let {
        Envelope.seal(TYPE_HEAD, JSONObject().put("seq", it.seq).put("hash", it.hash).put("at", System.currentTimeMillis()), keys)
    }

    /** The note [nodeId] gave us last time, to hand back to them. */
    fun echoFor(nodeId: String): JSONObject? = prefs.getString("held:$nodeId", null)?.let { JSONObject(it).put("t", TYPE_ECHO) }

    /** Another phone's note: keep it (only if it's genuinely theirs). */
    fun onHead(fromNodeId: String, json: JSONObject) {
        val o = Envelope.open(json) ?: return
        if (o.from != fromNodeId) return
        prefs.edit().putString("held:$fromNodeId", JSONObject(json.toString()).put("t", TYPE_HEAD).toString()).apply()
    }

    /** Our own note, handed back by [fromNodeId]: check it against our trail. */
    fun onEcho(fromNodeId: String, json: JSONObject, entries: List<AuditEntry>) {
        val o = Envelope.open(JSONObject(json.toString()).put("t", TYPE_HEAD)) ?: return
        if (o.from != keys.nodeId) return
        val seq = o.body.optLong("seq")
        val ok = AuditChain.matches(entries, seq, o.body.optString("hash"))
        val w = Witness(fromNodeId, nameOf(fromNodeId), seq, ok, System.currentTimeMillis())
        _witnesses.value = (listOf(w) + _witnesses.value.filter { it.nodeId != fromNodeId }).take(50)
        save()
    }

    private fun load(): List<Witness> = runCatching {
        val a = org.json.JSONArray(prefs.getString("witnesses", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Witness(it.getString("id"), it.getString("name"), it.getLong("seq"), it.getBoolean("ok"), it.getLong("at")) } }
    }.getOrDefault(emptyList())

    private fun save() {
        val a = org.json.JSONArray()
        _witnesses.value.forEach { a.put(JSONObject().put("id", it.nodeId).put("name", it.name).put("seq", it.seq).put("ok", it.matches).put("at", it.at)) }
        prefs.edit().putString("witnesses", a.toString()).apply()
    }

    companion object {
        const val TYPE_HEAD = "auditw"
        const val TYPE_ECHO = "auditx"
    }
}
