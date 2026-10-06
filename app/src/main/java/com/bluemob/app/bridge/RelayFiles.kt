package com.bluemob.app.bridge

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Chat attachments through the relay, for people who aren't nearby. The file is already encrypted on the phone with a
 * key that travels only inside the end-to-end encrypted message, so the relay stores bytes it can't read. Uploads are
 * signed by us; only the phone a file is addressed to can download it, and it's deleted once that phone has it.
 */
class RelayFiles(private val baseUrl: () -> String?, private val keys: DeviceKeys, private val now: () -> Long = System::currentTimeMillis) {

    /** Uploads [file] (encrypted) as [fid] for [to]. Calls [progress] with 0..1. True when the relay has it. */
    fun upload(fid: String, to: String, file: File, progress: (Float) -> Unit = {}): Boolean = runCatching {
        val base = baseUrl()?.trimEnd('/') ?: return false
        val bytes = file.readBytes()
        val at = now()
        val hash = Crypto.sha256(bytes).joinToString("") { "%02x".format(it) }
        val sig = Crypto.encode(keys.sign(listOf("bluemob-blob", fid, to, at, hash).joinToString("|").toByteArray()))
        val c = URL("$base/v1/blob/$fid").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"
            c.connectTimeout = 20_000; c.readTimeout = 120_000
            c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("content-type", "application/octet-stream")
            c.setRequestProperty("x-to", to); c.setRequestProperty("x-at", at.toString())
            c.setRequestProperty("x-pk", keys.publicB64); c.setRequestProperty("x-sig", sig)
            c.outputStream.use { out ->
                var sent = 0
                while (sent < bytes.size) {
                    val n = minOf(CHUNK, bytes.size - sent)
                    out.write(bytes, sent, n); sent += n
                    progress(sent.toFloat() / bytes.size)
                }
            }
            c.responseCode == 200
        } finally { c.disconnect() }
    }.getOrDefault(false)

    /** Downloads [fid] into [dest]. True if it was there and came down whole. */
    fun download(fid: String, dest: File, progress: (Float) -> Unit = {}): Boolean = runCatching {
        val c = URL(signedUrl(fid, "")).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000; c.readTimeout = 120_000
            if (c.responseCode != 200) return false
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(CHUNK)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        if (total > 0) progress(got.toFloat() / total)
                    }
                    total <= 0 || got == total
                }
            }
        } finally { c.disconnect() }
    }.getOrDefault(false)

    /** Tells the relay we have [fid], so it deletes its copy. */
    fun done(fid: String) {
        runCatching {
            val c = URL(signedUrl(fid, "/done")).openConnection() as HttpURLConnection
            try { c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 20_000; c.doOutput = true; c.outputStream.close(); c.responseCode } finally { c.disconnect() }
        }
    }

    private fun signedUrl(fid: String, suffix: String): String {
        val base = baseUrl()?.trimEnd('/') ?: error("no relay")
        val at = now()
        val sig = Crypto.encode(keys.sign(listOf("bluemob-blob-get", fid, at).joinToString("|").toByteArray()))
        return "$base/v1/blob/$fid$suffix?id=${keys.nodeId}&at=$at&sig=${URLEncoder.encode(sig, "UTF-8")}"
    }

    private companion object { const val CHUNK = 64 * 1024 }
}
