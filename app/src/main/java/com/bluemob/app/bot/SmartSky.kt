package com.bluemob.app.bot

import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Smart Sky: when the phone is online and the user has turned it on, Sky's questions go to Claude through the BlueMob
 * relay. Each question is signed by this phone, and the relay gives each phone a daily allowance. Offline, or if the
 * relay says no, Sky answers from what's on the phone, as before.
 */
object SmartSky {
    sealed interface Result {
        data class Answer(val text: String, val left: Int) : Result
        /** The relay has no AI set up. */
        data object Off : Result
        /** Today's questions are used up. */
        data object Limit : Result
        /** Claude declined to answer this one. */
        data object Refused : Result
        data object Failed : Result
    }

    /** One earlier line of the Sky chat: [fromMe] is the user's question. */
    data class Turn(val fromMe: Boolean, val text: String)

    suspend fun ask(baseUrl: String, keys: DeviceKeys, question: String, history: List<Turn>): Result = withContext(Dispatchers.IO) {
        runCatching {
            val q = question.trim().take(4000)
            val at = System.currentTimeMillis()
            val h = MessageDigest.getInstance("SHA-256").digest(q.toByteArray()).joinToString("") { "%02x".format(it) }
            val sig = Crypto.encode(keys.sign(listOf("bluemob-sky", at, h).joinToString("|").toByteArray()))
            val turns = JSONArray()
            history.takeLast(12).forEach { turns.put(JSONObject().put("role", if (it.fromMe) "user" else "assistant").put("text", it.text.take(4000))) }
            val body = JSONObject().put("pk", keys.publicB64).put("at", at).put("q", q).put("history", turns).put("sig", sig)
            val c = URL(baseUrl.trimEnd('/') + "/v1/sky").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20_000; c.readTimeout = 90_000
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                when (c.responseCode) {
                    200 -> {
                        val r = JSONObject(c.inputStream.bufferedReader().readText())
                        val text = r.optString("answer").trim()
                        when {
                            r.optBoolean("refused") -> Result.Refused
                            text.isEmpty() -> Result.Failed
                            else -> Result.Answer(if (r.optBoolean("cut")) "$text…" else text, r.optInt("left", -1))
                        }
                    }
                    503 -> Result.Off
                    429 -> Result.Limit
                    else -> Result.Failed
                }
            } finally { c.disconnect() }
        }.getOrDefault(Result.Failed)
    }

    /** Sky chat lines worth sending as context: real questions and answers, not the greeting or "thinking" notes. */
    fun history(lines: List<Turn>, greeting: List<String>): List<Turn> = lines.filter { it.text.isNotBlank() && it.text !in greeting }

    /** What to say when the online answer isn't available, put in front of the on-phone answer. */
    fun note(r: Result): String? = when (r) {
        Result.Off -> "✨ Smart Sky isn't set up on this BlueMob server yet, so here's what I know offline:"
        Result.Limit -> "✨ You've used today's Smart Sky questions. Answering from what's on your phone:"
        Result.Refused -> "✨ I can't help with that one online. Here's what I know offline:"
        Result.Failed -> "✨ I couldn't reach Smart Sky just now. Answering from what's on your phone:"
        is Result.Answer -> null
    }
}
