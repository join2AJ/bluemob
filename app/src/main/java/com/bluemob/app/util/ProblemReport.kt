package com.bluemob.app.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends a problem report to the BlueMob team (Diagnostics → Report a problem). No messages, contacts or keys. */
object ProblemReport {
    /** Null when sent, otherwise why not. */
    fun send(baseUrl: String?, text: String, app: String, device: String, details: String,
             category: String = "", sub: String = "", photos: List<ByteArray> = emptyList()): String? = runCatching {
        val base = baseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return "Reporting isn't available in this version."
        val body = JSONObject().put("text", text.take(4000)).put("app", app).put("device", device.take(300)).put("details", details.take(20000))
            .put("category", category).put("sub", sub)
            .put("photos", org.json.JSONArray(photos.take(3).map { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })).toString()
        val c = URL("$base/v1/report").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 60_000; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
            when (c.responseCode) { 200 -> null; 413 -> "The photos are too big. Remove one and try again."; else -> "Couldn't send it right now (${c.responseCode}). Try again later." }
        } finally { c.disconnect() }
    }.getOrElse { "Couldn't send it: no internet. Try again when you're online." }
}


/** What a report is about: a category and, inside it, what exactly. The team filters reports by these. */
object ReportTopics {
    val ALL: List<Pair<String, List<String>>> = listOf(
        "💬 Messages & chats" to listOf("Not delivered", "Slow to arrive", "Read receipts", "Group chats", "Photos & files", "Notifications"),
        "📞 Calls" to listOf("No voice", "No video", "Didn't connect", "Didn't ring", "Poor quality", "Dropped"),
        "🆘 SOS & rescue" to listOf("SOS not received", "Wrong or no position", "Alarm didn't sound", "Rescue group", "SOS contacts"),
        "📡 Nearby & connection" to listOf("Can't find people", "Keeps disconnecting", "Internet / relay", "Battery drain"),
        "🧭 Compass & map" to listOf("Wrong direction", "GPS / position", "Trail & trips", "Sun & daylight"),
        "📖 Guides & Sky" to listOf("Wrong information", "Sky's answer", "Guide packs", "Read aloud"),
        "🎮 Games" to listOf("Game rules", "Playing with someone", "Survival quiz"),
        "👤 Account & backup" to listOf("Sign-up / code", "Backup & restore", "PIN & lock", "Profile & ratings"),
        "📱 The app" to listOf("Crashed", "Slow", "Looks wrong", "Idea or suggestion", "Something else"),
    )
}
