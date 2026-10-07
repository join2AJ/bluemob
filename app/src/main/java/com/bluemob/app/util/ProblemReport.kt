package com.bluemob.app.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends a problem report to the BlueMob team (Diagnostics → Report a problem). No messages, contacts or keys. */
object ProblemReport {
    /** Null when sent, otherwise why not. */
    fun send(baseUrl: String?, text: String, app: String, device: String, details: String): String? = runCatching {
        val base = baseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return "Reporting isn't available in this version."
        val body = JSONObject().put("text", text.take(4000)).put("app", app).put("device", device.take(300)).put("details", details.take(20000)).toString()
        val c = URL("$base/v1/report").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 60_000; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode == 200) null else "Couldn't send it right now (${c.responseCode}). Try again later."
        } finally { c.disconnect() }
    }.getOrElse { "Couldn't send it: no internet. Try again when you're online." }
}
