package com.bluemob.app.sos

import android.content.SharedPreferences
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.mesh.SosSignal
import com.bluemob.app.settings.AppSettings
import com.bluemob.app.settings.SosContact
import com.bluemob.app.settings.SosContactState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * SOS contacts, inside the app (no SMS gateway). Between 1 and [MAX] people; each one is asked and accepts in their own
 * BlueMob, and an accepted contact is "verified". When you send an SOS, every contact gets a full-screen alert in
 * BlueMob: straight away when they're online or nearby, otherwise as soon as their phone next connects (the alert also
 * travels as a chat message, which the relay keeps until delivered). "I'm safe" reaches them the same way.
 *
 * Contacts are found by BlueMob ID, or by mobile number through the relay's directory, which only ever holds a
 * fingerprint (hash) of each verified number, never the number itself.
 */
class SosCircle(
    private val settings: AppSettings,
    private val mesh: NearbyMeshTransport,
    private val keys: DeviceKeys,
    private val myName: () -> String,
    private val myPhone: () -> String?,
    private val relay: () -> String?,
    private val sendChat: (peer: String, text: String) -> Unit,
    private val sos: SosManager,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    /** Someone asked us to be their SOS contact: the app shows it (and notifies in the background). */
    private val onRequest: (Request) -> Unit = {},
) {
    data class Request(val from: String, val name: String, val at: Long = System.currentTimeMillis())

    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    /** People asking us to be their SOS contact, waiting for an answer. */
    val requests: StateFlow<List<Request>> = _requests.asStateFlow()

    /** People whose SOS we agreed to receive (we're their SOS contact). */
    private val _guarding = MutableStateFlow(prefs.getStringSet(GUARDING, emptySet()).orEmpty())
    val guarding: StateFlow<Set<String>> = _guarding.asStateFlow()

    init {
        scope.launch { mesh.events.collect { e -> if (e is MeshEvent.App && e.kind == KIND) onPacket(e) } }
    }

    val contacts get() = settings.sosContacts

    /**
     * Adds a contact by BlueMob ID ([nodeId]) or by mobile number (looked up on the relay). Returns a message for the
     * user, or null when it's done.
     */
    suspend fun add(name: String, phone: String, nodeId: String?): String? {
        if (contacts.value.size >= MAX) return "You can have up to $MAX SOS contacts. Remove one first."
        val id = nodeId ?: phone.takeIf { it.isNotBlank() }?.let { lookup(it) }
        if (id != null && contacts.value.any { it.nodeId == id }) return "$name is already one of your SOS contacts."
        if (id == keys.nodeId) return "That's you. Add someone else."
        val c = settings.addSosContact(name, phone, id, if (id != null) SosContactState.ASKED else SosContactState.NOT_ON_BLUEMOB)
        if (id != null) ask(c) else return "$name isn't on BlueMob yet. Ask them to install it and sign up with this number, then add them again. Until then you can text them from your phone."
        return null
    }

    fun remove(id: String) {
        settings.sosContacts.value.firstOrNull { it.id == id }?.nodeId?.let { mesh.sendApp(it, KIND, JSONObject().put("a", "remove")) }
        settings.removeSosContact(id)
    }

    /** Asks again (e.g. they haven't answered). */
    fun ask(c: SosContact) {
        val to = c.nodeId ?: return
        mesh.sendApp(to, KIND, JSONObject().put("a", "ask"))
        sendChat(to, "🛟 I've added you as my SOS contact on BlueMob. If I'm ever in trouble, you'll get an alert here. Please open BlueMob and accept.")
    }

    fun answer(r: Request, yes: Boolean) {
        _requests.update { list -> list.filterNot { it.from == r.from } }
        if (yes) setGuarding(_guarding.value + r.from)
        mesh.sendApp(r.from, KIND, JSONObject().put("a", if (yes) "yes" else "no"))
        if (yes) sendChat(r.from, "✅ I accepted. I'm your SOS contact on BlueMob now.")
    }

    /** Our SOS: every contact on BlueMob gets an alert, now or when they next connect. */
    fun alert(s: SosSignal) {
        val body = JSONObject().put("a", "sos").put("id", s.id).put("note", s.note.take(200)).put("at", s.at)
            .put("lat", s.lat ?: JSONObject.NULL).put("lon", s.lon ?: JSONObject.NULL).put("acc", s.pos?.uncertaintyM ?: -1.0)
            .put("bat", s.battery ?: -1).put("blood", s.bloodGroup ?: "").put("age", s.age ?: -1)
        val where = if (s.lat != null && s.lon != null) "\nWhere: https://maps.google.com/?q=%.5f,%.5f".format(s.lat, s.lon) + (s.pos?.let { " (±${it.uncertaintyM.toInt()} m)" } ?: "") else "\nPosition not known."
        val text = "🆘 SOS from ${myName()}" + (if (s.note.isNotBlank()) ": ${s.note}" else "") + where +
            (s.battery?.let { "\nBattery $it%" } ?: "") + (s.bloodGroup?.let { "\nBlood group $it" } ?: "")
        contacts.value.mapNotNull { it.nodeId }.forEach { to ->
            mesh.sendApp(to, KIND, JSONObject(body.toString()))
            sendChat(to, text)
        }
    }

    fun safe(s: SosSignal) {
        contacts.value.mapNotNull { it.nodeId }.forEach { to ->
            mesh.sendApp(to, KIND, JSONObject().put("a", "safe").put("id", s.id))
            sendChat(to, "✅ I'm safe now. Thank you.")
        }
    }

    private fun onPacket(e: MeshEvent.App) {
        val b = e.body
        when (b.optString("a")) {
            "ask" -> if (e.fromNodeId !in _guarding.value && _requests.value.none { it.from == e.fromNodeId }) {
                val r = Request(e.fromNodeId, e.name.ifBlank { "Someone" })
                _requests.update { it + r }
                onRequest(r)
            } else if (e.fromNodeId in _guarding.value) mesh.sendApp(e.fromNodeId, KIND, JSONObject().put("a", "yes"))
            "yes", "no" -> contacts.value.firstOrNull { it.nodeId == e.fromNodeId }?.let { c ->
                settings.updateSosContact(c.id) { it.copy(state = if (b.optString("a") == "yes") SosContactState.ACCEPTED else SosContactState.DECLINED) }
            }
            "remove" -> { setGuarding(_guarding.value - e.fromNodeId); _requests.update { l -> l.filterNot { it.from == e.fromNodeId } } }
            // Only from people we agreed to look out for (or who are our own contacts): nobody else can set off an alarm.
            "sos", "safe" -> if (e.fromNodeId in _guarding.value || contacts.value.any { it.nodeId == e.fromNodeId }) {
                val lat = b.optDouble("lat").takeUnless { it.isNaN() }
                val lon = b.optDouble("lon").takeUnless { it.isNaN() }
                sos.receive(SosSignal(
                    id = b.optString("id").take(40), fromNodeId = e.fromNodeId, name = e.name.ifBlank { "Your contact" }, note = b.optString("note").take(200),
                    lat = lat, lon = lon, battery = b.optInt("bat", -1).takeIf { it in 0..100 }, at = b.optLong("at"), hops = e.hops,
                    cancelled = b.optString("a") == "safe",
                    pos = if (lat != null && lon != null) com.bluemob.app.trail.PositionEstimate(lat, lon, true, lat, lon, b.optLong("at"), b.optDouble("acc", 50.0).toFloat().coerceAtLeast(5f), null, null, null,
                        b.optDouble("acc", 50.0).coerceAtLeast(5.0), b.optLong("at")) else null,
                    bloodGroup = b.optString("blood").takeIf { it in com.bluemob.app.account.BloodGroups.ALL }, age = b.optInt("age", -1).takeIf { it in 1..120 },
                ))
            }
        }
    }

    private fun setGuarding(ids: Set<String>) { _guarding.value = ids; prefs.edit().putStringSet(GUARDING, ids).apply() }

    // ---- the relay's directory of verified numbers (fingerprints only) ----

    /** Registers our verified number's fingerprint, so people who add us by number find us. Once per number. */
    suspend fun registerNumber() = withContext(Dispatchers.IO) {
        val phone = myPhone() ?: return@withContext
        val base = relay()?.trimEnd('/') ?: return@withContext
        val h = fingerprint(phone)
        if (prefs.getString(REGISTERED, null) == h) return@withContext
        runCatching {
            val at = System.currentTimeMillis()
            val sig = Crypto.encode(keys.sign(listOf("bluemob-phone", h, at).joinToString("|").toByteArray()))
            val c = URL("$base/v1/phone").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 60_000
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(JSONObject().put("pk", keys.publicB64).put("at", at).put("h", h).put("sig", sig).toString().toByteArray()) }
                if (c.responseCode == 200) prefs.edit().putString(REGISTERED, h).apply()
            } finally { c.disconnect() }
        }
    }

    /** The BlueMob ID registered for a mobile number, if any. */
    suspend fun lookup(phone: String): String? = withContext(Dispatchers.IO) {
        val base = relay()?.trimEnd('/') ?: return@withContext null
        runCatching {
            val h = fingerprint(phone)
            val at = System.currentTimeMillis()
            val sig = Crypto.encode(keys.sign(listOf("bluemob-phone-get", h, at).joinToString("|").toByteArray()))
            val c = URL("$base/v1/phone?h=$h&id=${keys.nodeId}&at=$at&sig=${URLEncoder.encode(sig, "UTF-8")}").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20_000; c.readTimeout = 60_000
                if (c.responseCode != 200) return@runCatching null
                JSONObject(c.inputStream.bufferedReader().readText()).optString("id").takeIf { it.length == 16 }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    companion object {
        const val KIND = "sosc"
        const val MAX = 4
        private const val GUARDING = "guarding"
        private const val REGISTERED = "phone_registered"

        /** The same number written any way (+91 98765 43210, 098765…) gives the same fingerprint. */
        fun fingerprint(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            val e164 = when {
                phone.trim().startsWith("+") -> "+$digits"
                digits.length == 10 -> "+91$digits"
                digits.length == 11 && digits.startsWith("0") -> "+91" + digits.drop(1)
                else -> "+$digits"
            }
            return Crypto.sha256("bluemob-phone-v1|$e164".toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
