package com.bluemob.app.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemob.app.BlueMobApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Sign-up, the account screen and the app lock: profile, consent, PIN, recovery code, your data and deleting it. */
class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val identity = blueMob.identity
    private val settings = blueMob.settings

    val profile = blueMob.profile.profile
    /** Sign-up finished: the number is verified, the details saved, and the app opens. */
    fun completeSignup(r: com.bluemob.app.ui.account.SignupResult) {
        identity.setDisplayName(r.name); identity.setAvatar(r.avatar)
        blueMob.profile.setVerifiedPhone(r.phone)
        blueMob.profile.setMedical(r.dob, r.sex, r.bloodGroup, r.allergies, r.conditions)
        recordConsent(r.parentalConsent)
        blueMob.profile.setEmail(r.email)
        syncEmail()
        identity.setOnboardingDone(true)
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Signed up: mobile number verified (${r.phone.take(5)}…)")
    }
    fun setMedical(dob: String?, sex: String?, blood: String?, allergies: String?, conditions: String?) = blueMob.profile.setMedical(dob, sex, blood, allergies, conditions)
    fun setDetails(age: Int?, blood: String?) = blueMob.profile.setDetails(age, blood)
    /** Optional recovery email: saved on the phone, and on the relay as soon as we're online. */
    fun setEmail(email: String?) { blueMob.profile.setEmail(email); syncEmail() }
    private fun syncEmail() = viewModelScope.launch { blueMob.syncEmail() }
    fun clearPin() { lock.clearPin(); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "PIN lock turned off") }

    private val lock = blueMob.lock
    val pinSet = lock.pinSet
    val locked = lock.locked
    val biometric = lock.biometric
    val lockAfterMs = lock.lockAfterMs
    val recoverySaved = lock.recoverySaved
    fun setPin(pin: String) { val first = !lock.hasPin; lock.setPin(pin); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, if (first) "Account PIN created" else "Account PIN changed") }
    fun checkPin(pin: String) = lock.check(pin).also { if (it is com.bluemob.app.account.PinResult.Wait) blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Several wrong PINs: login paused") }
    fun unlockedByBiometric() = lock.unlockedByBiometric()
    fun setBiometric(on: Boolean) = lock.setBiometric(on)
    fun setLockAfter(ms: Long) = lock.setLockAfter(ms)
    fun lockNow() = lock.lockNow()
    fun recoveryCode(): String = identity.recoveryCode()
    fun setRecoverySaved() { lock.setRecoverySaved(); blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Recovery code saved") }
    /** Restores an ID from a recovery code. Returns an error to show, or null when the app should restart. */
    fun restore(code: String): String? {
        val id = identity.restore(code) ?: return "That code isn't right. Check each group of 4, especially 0/O and 1/I."
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "BlueMob ID restored from a recovery code: BM ${com.bluemob.app.util.formatId(id)}")
        return null
    }

    /** Signed up before the consent step existed: asked once to agree. */
    fun needsConsent(): Boolean = identity.onboardingDone.value && blueMob.profile.consentAt == 0L
    /** Records that the user agreed to the privacy policy (and, for under-18s, that a parent or guardian agreed). */
    fun recordConsent(parental: Boolean) {
        blueMob.profile.setConsent(System.currentTimeMillis(), parental)
        blueMob.audit.add(com.bluemob.app.audit.AuditKind.APP, "Agreed to the privacy policy" + if (parental) " (with a parent's or guardian's consent)" else "")
    }

    /**
     * "Download my data" (DPDP / GDPR right of access): everything BlueMob keeps about the user on this phone, as one
     * readable JSON file to save or share. Keys and the PIN are left out.
     */
    suspend fun exportMyData(): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        fun t(ms: Long) = iso.format(java.util.Date(ms))
        val p = blueMob.profile.profile.value
        val j = org.json.JSONObject()
        j.put("exported", t(System.currentTimeMillis())).put("app", "BlueMob " + com.bluemob.app.BuildConfig.VERSION_NAME)
        j.put("you", org.json.JSONObject().put("name", identity.displayName.value).put("bluemobId", identity.nodeId).put("phone", p.phone ?: "")
            .put("email", p.email ?: "").put("dateOfBirth", p.dob ?: "").put("age", p.age ?: org.json.JSONObject.NULL).put("sex", p.sex ?: "")
            .put("bloodGroup", p.bloodGroup ?: "").put("allergies", p.allergies ?: "").put("medicalConditions", p.conditions ?: ""))
        j.put("sosContacts", org.json.JSONArray(settings.sosContacts.value.map { org.json.JSONObject().put("name", it.name).put("phone", it.phone).put("state", it.state.label) }))
        j.put("peopleMet", org.json.JSONArray(blueMob.contacts.contacts.value.values.map { org.json.JSONObject().put("bluemobId", it.nodeId).put("name", it.name).put("lastSeen", if (it.lastSeen > 0) t(it.lastSeen) else "") }))
        j.put("blocked", org.json.JSONArray(settings.blocked.value.toList()))
        j.put("messages", org.json.JSONArray(blueMob.database.messages().all().filter { !it.hidden }.sortedBy { it.createdAt }.map { m ->
            org.json.JSONObject().put("chat", m.peer).put("fromMe", m.fromMe).put("text", m.text).put("time", t(m.createdAt)).put("status", m.status.name)
        }))
        j.put("calls", org.json.JSONArray(blueMob.callLog.all().map { c ->
            org.json.JSONObject().put("with", c.name).put("bluemobId", c.peer).put("video", c.video).put("outgoing", c.outgoing).put("outcome", c.outcome).put("time", t(c.startedAt)).put("seconds", c.durationS)
        }))
        j.put("trips", org.json.JSONArray(blueMob.trail.allTrips.value.map { tr -> org.json.JSONObject().put("name", tr.name).put("start", t(tr.startedAt)).put("end", tr.endedAt?.let { t(it) } ?: "").put("metres", tr.distanceM.toInt()) }))
        j.put("guidesRead", org.json.JSONObject(com.bluemob.app.guide.GuidePacks.read.value.mapValues { t(it.value) }))
        j.put("auditTrail", org.json.JSONArray(blueMob.audit.entries.first().asReversed().map { a -> org.json.JSONObject().put("time", t(a.time)).put("kind", a.kind).put("text", a.text) }))
        val dir = java.io.File(getApplication<android.app.Application>().cacheDir, "open").apply { mkdirs() }
        dir.resolve("bluemob-my-data.json").apply { writeText(j.toString(2)) }
    }

    /**
     * Deletes the account: the relay forgets this BlueMob ID (key, number, email, backups, waiting messages), then
     * everything on this phone is wiped and BlueMob closes. With [phoneOnly], skips the relay (e.g. no internet).
     */
    suspend fun deleteAccount(phoneOnly: Boolean = false): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!phoneOnly) {
            val base = settings.bridgeUrl.value.trimEnd('/')
            val err = runCatching {
                val at = System.currentTimeMillis()
                val keys = identity.keys
                val sig = com.bluemob.app.crypto.Crypto.encode(keys.sign("bluemob-delete|$at".toByteArray()))
                val c = java.net.URL("$base/v1/account/delete").openConnection() as java.net.HttpURLConnection
                try {
                    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 30_000
                    c.setRequestProperty("content-type", "application/json")
                    c.outputStream.use { it.write(org.json.JSONObject().put("pk", keys.publicB64).put("at", at).put("sig", sig).toString().toByteArray()) }
                    if (c.responseCode == 200) null else "The BlueMob server said no (${c.responseCode})."
                } finally { c.disconnect() }
            }.getOrElse { "No internet, so the BlueMob server couldn't be told." }
            if (err != null) return@withContext err
        }
        val app = getApplication<android.app.Application>()
        app.getSystemService(android.app.ActivityManager::class.java)?.clearApplicationUserData()
        null
    }
}
