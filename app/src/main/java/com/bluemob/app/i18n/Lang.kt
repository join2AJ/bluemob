package com.bluemob.app.i18n

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * BlueMob in another language. The screens are written in English; [tr] looks each piece of text up in a dictionary
 * (`assets/i18n/<code>.tsv`) when it's shown, so every screen changes language at once.
 *
 * Dictionary lines are `English<TAB>translation`. `{}` stands for a number or name that changes ("Age {}"), and the
 * translation can use `{}` in the same order, or `{1}`, `{2}`… to reorder them. Text with no entry stays in English.
 */
object Lang {
    val SUPPORTED = listOf("en" to "English", "hi" to "हिन्दी")

    /** The language shown now, e.g. "hi". A Compose state, so text redraws when it changes. */
    val current = mutableStateOf("en")

    private var app: Context? = null
    private val loaded = HashMap<String, Dictionary>()

    fun init(context: Context, choice: String) { app = context.applicationContext; set(choice) }

    /** [choice] is "en", "hi", or "" to follow the phone's language. */
    fun set(choice: String) {
        val code = choice.ifBlank { java.util.Locale.getDefault().language }
        current.value = if (SUPPORTED.any { it.first == code }) code else "en"
    }

    /** [text] in the current language, or as it is if there's no translation. */
    fun tr(text: String): String {
        val code = current.value
        if (code == "en" || text.isBlank()) return text
        return dictionary(code)?.translate(text) ?: text
    }

    private fun dictionary(code: String): Dictionary? = loaded[code] ?: app?.let { c ->
        runCatching { c.assets.open("i18n/$code.tsv").bufferedReader().use { Dictionary.parse(it.readText()) } }.getOrNull()?.also { loaded[code] = it }
    }
}

/** English → translation, exact first, then patterns with `{}` gaps. */
class Dictionary(private val exact: Map<String, String>, private val patterns: List<Pair<Regex, String>>) {
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 2_000
    }

    @Synchronized fun translate(text: String): String = cache.getOrPut(text) { find(text) }

    private fun find(text: String): String {
        exact[text]?.let { return it }
        // Keep spaces around the text (screens sometimes pad with them).
        val trimmed = text.trim()
        if (trimmed != text) exact[trimmed]?.let { return text.replace(trimmed, it) }
        for ((re, to) in patterns) {
            val m = re.matchEntire(text) ?: continue
            val values = m.groupValues.drop(1).map { translate(it) }
            var i = 0
            return Regex("\\{(\\d*)\\}").replace(to) { g ->
                val n = g.groupValues[1]
                (if (n.isEmpty()) values.getOrNull(i++) else values.getOrNull(n.toInt() - 1)) ?: ""
            }
        }
        return text
    }

    companion object {
        fun parse(tsv: String): Dictionary {
            val exact = HashMap<String, String>()
            val patterns = ArrayList<Pair<Regex, String>>()
            tsv.lineSequence().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val tab = line.indexOf('\t').takeIf { it > 0 } ?: return@forEach
                val en = line.substring(0, tab).replace("\\n", "\n")
                val to = line.substring(tab + 1).replace("\\n", "\n")
                if (to.isBlank()) return@forEach
                if ("{}" in en) {
                    val re = en.split("{}").joinToString("(.+?)") { Regex.escape(it) }
                    patterns += Regex(re, RegexOption.DOT_MATCHES_ALL) to to
                } else exact[en] = to
            }
            // Longer patterns first: "Age {} years" before "Age {}".
            patterns.sortByDescending { it.first.pattern.length }
            return Dictionary(exact, patterns)
        }
    }
}
