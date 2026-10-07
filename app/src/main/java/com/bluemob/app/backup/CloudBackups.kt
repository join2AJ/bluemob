package com.bluemob.app.backup

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One backup kept in BlueMob Cloud. */
data class CloudItem(val name: String, val at: Long, val size: Long)

/**
 * BlueMob Cloud: backups kept on the BlueMob relay. The file is exactly the one saved on the phone: compressed and
 * encrypted with the backup password, which never leaves the phone, so the relay can't read it. Only this BlueMob ID
 * can list or download its backups; on a new phone, restore the ID with the recovery code first, then the backup.
 */
class CloudBackups(private val baseUrl: () -> String?, private val keys: () -> DeviceKeys, private val now: () -> Long = System::currentTimeMillis) {

    /** "kept" (a disk that survives restarts), "temporary" (test server: may be lost), "off", or null if unreachable. */
    fun serverMode(): String? = runCatching {
        val base = baseUrl()?.trimEnd('/') ?: return null
        get("$base/health")?.let { JSONObject(it).optString("cloudBackups", "off") }
    }.getOrNull()

    /** Uploads a backup file. Null when stored, otherwise why not. */
    fun upload(file: File): String? = runCatching {
        val base = baseUrl()?.trimEnd('/') ?: return "No relay set up"
        val bytes = file.readBytes()
        val at = now()
        val hash = Crypto.sha256(bytes).joinToString("") { "%02x".format(it) }
        val k = keys()
        val c = URL("$base/v1/backup").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"; c.connectTimeout = 20_000; c.readTimeout = 180_000; c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("content-type", "application/octet-stream")
            c.setRequestProperty("x-at", at.toString()); c.setRequestProperty("x-pk", k.publicB64)
            c.setRequestProperty("x-sig", Crypto.encode(k.sign(listOf("bluemob-backup", hash, at).joinToString("|").toByteArray())))
            c.outputStream.use { it.write(bytes) }
            when (c.responseCode) { 200 -> null; 413 -> "Too big for BlueMob Cloud (100 MB at most)"; 404 -> "This relay doesn't keep cloud backups"; else -> "The relay said ${c.responseCode}" }
        } finally { c.disconnect() }
    }.getOrElse { "Couldn't reach BlueMob Cloud. Check the internet." }

    /** Our backups, newest first. Null if the relay couldn't be reached. */
    fun list(): List<CloudItem>? = runCatching {
        val body = get(signed("list", "")) ?: return null
        val a = JSONObject(body).optJSONArray("backups") ?: return emptyList()
        (0 until a.length()).map { a.getJSONObject(it) }.map { CloudItem(it.getString("name"), it.getLong("at"), it.optLong("size")) }
    }.getOrNull()

    /** Downloads one of our backups into [dest]. */
    fun download(name: String, dest: File): Boolean = runCatching {
        val c = URL(signed(name, "/$name")).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000; c.readTimeout = 180_000
            if (c.responseCode != 200) return false
            c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
            true
        } finally { c.disconnect() }
    }.getOrDefault(false)

    private fun signed(name: String, suffix: String): String {
        val base = baseUrl()?.trimEnd('/') ?: error("no relay")
        val k = keys()
        val at = now()
        val sig = Crypto.encode(k.sign(listOf("bluemob-backup-get", name, at).joinToString("|").toByteArray()))
        return "$base/v1/backups$suffix?id=${k.nodeId}&at=$at&sig=${URLEncoder.encode(sig, "UTF-8")}"
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 20_000; c.readTimeout = 70_000
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally { c.disconnect() }
    }

    companion object { const val FOLDER = "cloud" }
}
