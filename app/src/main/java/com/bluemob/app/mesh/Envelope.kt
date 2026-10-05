package com.bluemob.app.mesh

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import org.json.JSONObject

/**
 * A signed packet: `{t, b, pk, s, h}`. [b] is the body as a JSON string and must contain "from", the sender's ID.
 * The signature covers the type and the body, so nothing a relaying phone can change matters, except the hop count.
 */
object Envelope {
    data class Opened(val type: String, val body: JSONObject, val from: String, val publicB64: String, val hops: Int, val raw: JSONObject)

    fun seal(type: String, body: JSONObject, keys: DeviceKeys): JSONObject {
        val b = body.put("from", keys.nodeId).toString()
        return JSONObject().put("t", type).put("b", b).put("pk", keys.publicB64)
            .put("s", Crypto.encode(keys.sign("$type\n$b".toByteArray()))).put("h", 0)
    }

    /** Checks the signature and that the key belongs to the sender's ID. Null if anything is off. */
    fun open(json: JSONObject): Opened? {
        val type = json.optString("t")
        val b = json.optString("b")
        val pk = json.optString("pk")
        val sig = Crypto.decode(json.optString("s")) ?: return null
        val pkBytes = Crypto.decode(pk) ?: return null
        val key = Crypto.publicKey(pkBytes) ?: return null
        if (type.isEmpty() || b.isEmpty() || b.length > MAX_BODY) return null
        val body = runCatching { JSONObject(b) }.getOrNull() ?: return null
        val from = body.optString("from")
        if (from != Crypto.idFor(pkBytes)) return null
        if (!Crypto.verify(key, "$type\n$b".toByteArray(), sig)) return null
        return Opened(type, body, from, pk, json.optInt("h", 0), json)
    }

    const val MAX_BODY = 16_000
}
