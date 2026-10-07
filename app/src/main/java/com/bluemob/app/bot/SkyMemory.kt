package com.bluemob.app.bot

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Answers the user taught Sky ("Teach Sky the answer"). Kept only on this phone (encrypted, like all BlueMob
 * settings). A later question matches a taught one when most of its words are the same.
 */
object SkyMemory {
    private var prefs: SharedPreferences? = null
    private val taught = LinkedHashMap<String, String>()

    fun init(p: SharedPreferences) {
        prefs = p
        taught.clear()
        runCatching { JSONObject(p.getString("taught", "{}") ?: "{}").let { j -> j.keys().forEach { k -> taught[k] = j.getString(k) } } }
    }

    fun teach(question: String, answer: String) {
        val q = key(question)
        if (q.isEmpty() || answer.isBlank()) return
        taught[q] = answer.trim().take(1_000)
        while (taught.size > 200) taught.remove(taught.keys.first())
        save()
    }

    fun forget(question: String) { taught.remove(key(question)); save() }

    val all: Map<String, String> get() = taught.toMap()

    /** The taught answer for a question like [text], if one is close enough. */
    fun answer(text: String): String? {
        val words = words(text)
        if (words.isEmpty()) return null
        return taught.entries.map { (q, a) ->
            val qw = q.split(' ').toSet()
            val overlap = (words intersect qw).size.toDouble() / (words union qw).size
            a to overlap
        }.filter { it.second >= 0.6 }.maxByOrNull { it.second }?.first
    }

    private val filler = setOf("what", "is", "the", "a", "an", "to", "of", "how", "do", "does", "i", "my", "me", "can", "you", "please", "tell", "where", "which", "are")
    private fun words(t: String) = Regex("[\\p{L}\\p{N}]+").findAll(t.lowercase()).map { it.value }.filter { it.length > 1 && it !in filler }.toSet()
    private fun key(t: String) = words(t).sorted().joinToString(" ")
    private fun save() { prefs?.edit()?.putString("taught", JSONObject(taught as Map<*, *>).toString())?.apply() }
}
