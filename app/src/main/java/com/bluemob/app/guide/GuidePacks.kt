package com.bluemob.app.guide

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A downloadable set of guides for one kind of trip, e.g. "Mountains & snow". */
data class PackInfo(val id: String, val version: Int, val emoji: String, val title: String, val about: String, val articles: Int, val bytes: Long)

/**
 * Extra survival guides people download when they have internet, for the trip they're planning: mountains, monsoon,
 * heat, wildlife… Once downloaded they work offline like the built-in guides, and Sky answers from them too.
 * They come from the BlueMob relay and are kept on the phone (encrypted, like everything else).
 */
object GuidePacks {
    @Volatile var installedArticles: List<Article> = emptyList()
        private set

    private val _installed = MutableStateFlow<List<PackInfo>>(emptyList())
    val installed: StateFlow<List<PackInfo>> = _installed.asStateFlow()

    /** When each pack was downloaded (or last updated), by pack ID. */
    @Volatile var installedAt: Map<String, Long> = emptyMap()
        private set

    private val _read = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Guides the user has opened, with when they last did: for the dashboard and "continue reading". */
    val read: StateFlow<Map<String, Long>> = _read.asStateFlow()

    /** The pack a guide came from, or null for a built-in one. */
    fun packOf(articleId: String): String? = installedAt.keys.firstOrNull { articleId.startsWith("$it-") }

    /** True for a guide from a pack downloaded in the last [NEW_FOR_MS] (about a day). */
    fun isNew(articleId: String, now: Long = System.currentTimeMillis()): Boolean =
        packOf(articleId)?.let { installedAt[it] }?.let { now - it < NEW_FOR_MS } == true

    fun markRead(articleId: String, now: Long = System.currentTimeMillis()) {
        _read.value = _read.value + (articleId to now)
        val day = Streak.dayKey(now)
        _readDays.value = _readDays.value + day
        prefs?.edit()?.putLong("read:$articleId", now)?.putStringSet("read_days", _readDays.value)?.apply()
    }

    private val _readDays = MutableStateFlow<Set<String>>(emptySet())
    /** Days (yyyy-MM-dd) on which at least one guide was opened: for the reading streak. */
    val readDays: StateFlow<Set<String>> = _readDays.asStateFlow()

    private val _quizzes = MutableStateFlow<Set<String>>(emptySet())
    /** Guides whose "Test yourself" quiz was passed. */
    val quizzesPassed: StateFlow<Set<String>> = _quizzes.asStateFlow()
    fun markQuizPassed(articleId: String) {
        _quizzes.value = _quizzes.value + articleId
        prefs?.edit()?.putStringSet("quiz_passed", _quizzes.value)?.apply()
    }

    const val NEW_FOR_MS = 24 * 3_600_000L

    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        reload()
    }

    private fun reload() {
        val p = prefs ?: return
        _readDays.value = p.getStringSet("read_days", emptySet())!!.toSet()
        _quizzes.value = p.getStringSet("quiz_passed", emptySet())!!.toSet()
        val packs = p.all.keys.filter { it.startsWith("pack:") }.mapNotNull { k -> p.getString(k, null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() } }
        installedArticles = packs.flatMap { it.second }
        _installed.value = packs.map { it.first }.sortedBy { it.title }
        installedAt = packs.associate { (info, _) -> info.id to p.getLong("packAt:${info.id}", 0L) }
        _read.value = p.all.keys.filter { it.startsWith("read:") }.associate { it.removePrefix("read:") to p.getLong(it, 0L) }
    }

    /** What the relay offers. Null if it couldn't be reached. */
    suspend fun available(relay: String): List<PackInfo>? = withContext(Dispatchers.IO) {
        get("$relay/v1/guides")?.let { body ->
            val a = JSONObject(body).optJSONArray("packs") ?: JSONArray()
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { j -> info(j, j.optInt("articles"), j.optLong("bytes")) } }
        }
    }

    /** Downloads (or updates) a pack. Returns an error to show, or null when it's installed. */
    suspend fun download(relay: String, id: String): String? = withContext(Dispatchers.IO) {
        val body = get("$relay/v1/guides/$id") ?: return@withContext "Couldn't download it. Check the internet connection and try again."
        val parsed = runCatching { parse(JSONObject(body)) }.getOrNull() ?: return@withContext "That pack couldn't be read."
        if (parsed.second.isEmpty()) return@withContext "That pack is empty."
        prefs?.edit()?.putString("pack:$id", body)?.putLong("packAt:$id", System.currentTimeMillis())?.apply()
        reload()
        null
    }

    fun remove(id: String) { prefs?.edit()?.remove("pack:$id")?.remove("packAt:$id")?.apply(); reload() }

    private fun info(j: JSONObject, articles: Int, bytes: Long) = PackInfo(j.getString("id"), j.optInt("version", 1), j.optString("emoji", "📘"),
        j.getString("title"), j.optString("about"), articles, bytes)

    /** Reads a pack. Article IDs get the pack's prefix so they never clash with built-in ones. */
    fun parse(j: JSONObject): Pair<PackInfo, List<Article>> {
        val a = j.getJSONArray("articles")
        val articles = (0 until a.length()).mapNotNull { i ->
            val x = a.getJSONObject(i)
            runCatching {
                Article(
                    id = j.getString("id") + "-" + x.getString("id"),
                    category = runCatching { GuideCategory.valueOf(x.getString("category")) }.getOrDefault(GuideCategory.BASICS),
                    title = x.getString("title").take(80), minutes = x.optInt("minutes", 2).coerceIn(1, 30), intro = x.optString("intro").take(400),
                    steps = x.getJSONArray("steps").let { s -> (0 until minOf(s.length(), 20)).map { s.getString(it).take(500) } },
                    avoid = x.optJSONArray("avoid")?.let { s -> (0 until minOf(s.length(), 10)).map { s.getString(it).take(400) } }.orEmpty(),
                )
            }.getOrNull()
        }
        return info(j, articles.size, j.toString().length.toLong()) to articles
    }

    private fun get(url: String): String? = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText().takeIf { it.length < 512 * 1024 } else null } finally { c.disconnect() }
    }.getOrNull()
}

/** The numbers on the guide dashboard. */
data class GuideDashboard(
    val total: Int,
    val downloaded: Int,
    val packs: Int,
    val newCount: Int,
    val saved: Int,
    val readCount: Int,
    val byCategory: Map<GuideCategory, Int>,
    /** The guide opened most recently, to carry on with. */
    val lastRead: String?,
) {
    val readShare: Float get() = if (total == 0) 0f else readCount.toFloat() / total

    companion object {
        fun of(all: List<Article>, saved: Set<String>, read: Map<String, Long>, isNew: (String) -> Boolean, packs: Int): GuideDashboard {
            val ids = all.map { it.id }.toSet()
            return GuideDashboard(
                total = all.size,
                downloaded = all.count { GuidePacks.packOf(it.id) != null },
                packs = packs,
                newCount = all.count { isNew(it.id) },
                saved = saved.count { it in ids },
                readCount = read.keys.count { it in ids },
                byCategory = all.groupingBy { it.category }.eachCount(),
                lastRead = read.filterKeys { it in ids }.maxByOrNull { it.value }?.key,
            )
        }

        /** Same guide all day, a different one tomorrow. */
        fun ofTheDay(all: List<Article>, day: Long = System.currentTimeMillis() / 86_400_000L): Article? =
            all.takeIf { it.isNotEmpty() }?.let { it.sortedBy { a -> a.id }[(day % it.size).toInt()] }
    }
}


/** Days in a row with some reading, counting today (or up to yesterday, so a streak isn't lost before you read today). */
object Streak {
    fun dayKey(at: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(at))

    fun of(days: Set<String>, now: Long = System.currentTimeMillis()): Int {
        val dayMs = 86_400_000L
        var t = if (dayKey(now) in days) now else now - dayMs
        var n = 0
        while (dayKey(t) in days) { n++; t -= dayMs }
        return n
    }
}

/**
 * "Test yourself" after a guide: three questions made from the guide itself, so every guide (downloaded ones too)
 * has a quiz. What comes first, what to avoid, and what comes next.
 */
object GuideQuiz {
    data class Question(val prompt: String, val options: List<String>, val answer: Int, val why: String)

    fun of(a: Article, all: List<Article>, seed: Long = a.id.hashCode().toLong()): List<Question> {
        val rnd = java.util.Random(seed)
        val others = all.filter { it.id != a.id && it.category != a.category }.flatMap { it.steps }.shuffled(rnd)
        val qs = mutableListOf<Question>()
        fun ask(prompt: String, right: String, wrong: List<String>, why: String) {
            val opts = (wrong.distinct().filter { it != right }.take(2) + right).shuffled(rnd)
            if (opts.size == 3) qs += Question(prompt, opts.map { short(it) }, opts.indexOf(right), why)
        }
        if (a.steps.size >= 2) ask("${a.title}: what do you do first?", a.steps[0], a.steps.drop(2).take(1) + others.take(1) + a.steps.drop(1).take(1),
            "The guide starts with: ${short(a.steps[0])}. The order matters: the first step keeps you or them safe before anything else.")
        if (a.avoid.isNotEmpty()) ask("Which of these should you NOT do?", a.avoid[rnd.nextInt(a.avoid.size)], a.steps.shuffled(rnd).take(2),
            "That one is on the guide's \"don't\" list. The others are steps the guide tells you to do.")
        if (a.steps.size >= 3) {
            val i = rnd.nextInt(a.steps.size - 1)
            ask("After \"${short(a.steps[i], 60)}\", what comes next?", a.steps[i + 1], a.steps.filterIndexed { j, _ -> j != i && j != i + 1 }.shuffled(rnd).take(1) + others.drop(1).take(1),
                "Next is: ${short(a.steps[i + 1])}.")
        }
        return qs
    }

    private fun short(s: String, max: Int = 110) = s.substringBefore(". ").let { if (it.length > max) it.take(max - 1) + "…" else it }
}
