package com.bluemob.app.account

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The optional recovery email, kept by the BlueMob relay against this BlueMob ID (signed by this phone, so nobody else
 * can set or change it). Used later for account recovery and receipts; the relay never shows it to anyone.
 */
object RecoveryEmail {
    /** Sends [email] ("" removes it) to the relay. True once it's stored. */
    suspend fun upload(baseUrl: String, keys: DeviceKeys, email: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val at = System.currentTimeMillis()
            val e = Emails.clean(email)
            val sig = Crypto.encode(keys.sign(listOf("bluemob-email", e, at).joinToString("|").toByteArray()))
            val c = URL(baseUrl.trimEnd('/') + "/v1/email").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 60_000
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(JSONObject().put("pk", keys.publicB64).put("at", at).put("email", e).put("sig", sig).toString().toByteArray()) }
                c.responseCode == 200
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Brings the relay up to date with the profile, if it isn't already. */
    suspend fun sync(profile: ProfileStore, baseUrl: String?, keys: DeviceKeys) {
        val want = profile.profile.value.email.orEmpty()
        if (baseUrl.isNullOrBlank() || want == profile.syncedEmail.orEmpty()) return
        if (upload(baseUrl, keys, want)) profile.markEmailSynced(want)
    }
}
