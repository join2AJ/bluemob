package com.bluemob.app.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a chat message can carry besides its text: the group it belongs to, the message it replies to, or a reaction.
 * It travels inside the normal (signed, end-to-end encrypted) message text, behind an invisible marker, so every
 * path that carries messages (Bluetooth, Wi-Fi, other phones, the internet) carries these too.
 */
data class Rich(
    val text: String = "",
    /** Group ID, name and members (node ID → name), when it's a group message. */
    val group: String? = null,
    val groupName: String = "",
    val members: Map<String, String> = emptyMap(),
    /** The group message's own ID, the same on every phone (each copy has its own ID for receipts). */
    val groupMsgId: String? = null,
    /** Replying to: message ID, its author's name, and its first words. */
    val replyId: String? = null,
    val replyName: String = "",
    val replyText: String = "",
    /** A reaction ([react] on message [reactTo]); "" removes ours. */
    val react: String? = null,
    val reactTo: String? = null,
    /** Leaving the group. */
    val leave: Boolean = false,
) {
    fun encode(): String {
        val j = JSONObject()
        if (text.isNotEmpty()) j.put("t", text)
        group?.let { g ->
            j.put("g", g).put("gn", groupName)
            j.put("gm", JSONArray().apply { members.forEach { (id, n) -> put(JSONArray().put(id).put(n)) } })
        }
        groupMsgId?.let { j.put("gi", it) }
        replyId?.let { j.put("r", it).put("rn", replyName).put("rq", replyText) }
        react?.let { j.put("x", it).put("xi", reactTo) }
        if (leave) j.put("lv", 1)
        return MARK + j.toString()
    }

    val isControl: Boolean get() = react != null || leave

    companion object {
        /** Invisible separator, then JSON. Plain messages never start with it. */
        const val MARK = "⁣bm1"
        const val MAX_MEMBERS = 30

        fun decode(raw: String): Rich? {
            if (!raw.startsWith(MARK)) return null
            val j = runCatching { JSONObject(raw.substring(MARK.length)) }.getOrNull() ?: return null
            val members = LinkedHashMap<String, String>()
            j.optJSONArray("gm")?.let { a ->
                for (i in 0 until minOf(a.length(), MAX_MEMBERS)) {
                    val m = a.optJSONArray(i) ?: continue
                    val id = m.optString(0)
                    if (id.length == 16) members[id] = m.optString(1).take(40)
                }
            }
            return Rich(
                text = j.optString("t").take(4000),
                group = j.optString("g").takeIf { it.isNotBlank() && it.length <= 40 },
                groupName = j.optString("gn").take(40), members = members,
                groupMsgId = j.optString("gi").takeIf { it.isNotBlank() && it.length <= 40 },
                replyId = j.optString("r").takeIf { it.isNotBlank() && it.length <= 40 },
                replyName = j.optString("rn").take(40), replyText = j.optString("rq").take(120),
                react = if (j.has("x")) j.optString("x").take(8) else null, reactTo = j.optString("xi").takeIf { it.isNotBlank() },
                leave = j.optInt("lv") == 1,
            )
        }

        /** "id|name|first words" for [com.bluemob.app.data.MessageEntity.replyTo]. */
        fun replyField(id: String, name: String, text: String) = "$id|${name.replace("|", " ")}|${text.replace("\n", " ").take(80)}"
        fun replyParts(field: String): Triple<String, String, String>? = field.split("|", limit = 3).takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }

        /** Reactions field → emoji → how many, and whether [me] chose it. */
        fun reactionsOf(field: String): Map<String, String> = field.lines().filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }
        fun withReaction(field: String, who: String, emoji: String): String {
            val m = LinkedHashMap(reactionsOf(field))
            if (emoji.isBlank()) m.remove(who) else m[who] = emoji
            return m.entries.joinToString("\n") { "${it.key}|${it.value}" }
        }

        val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏", "✅")
    }
}

/** A group chat this phone is in. [id] is "g-…", used as the chat's peer ID. */
data class ChatGroup(val id: String, val name: String, val members: Map<String, String>, val createdAt: Long = System.currentTimeMillis())

/** Groups are kept in encrypted settings: name and members. Messages live with all the others. */
/** [prefs] null keeps groups in memory only (tests). */
class GroupStore(private val prefs: android.content.SharedPreferences?) {
    private val _groups = kotlinx.coroutines.flow.MutableStateFlow(load())
    val groups: kotlinx.coroutines.flow.StateFlow<Map<String, ChatGroup>> = _groups

    fun get(id: String) = _groups.value[id]

    fun put(g: ChatGroup) { _groups.value = _groups.value + (g.id to g); save() }
    fun remove(id: String) { _groups.value = _groups.value - id; save() }

    private fun load(): Map<String, ChatGroup> = runCatching {
        val a = JSONArray(prefs?.getString(KEY, "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            val m = LinkedHashMap<String, String>()
            o.optJSONObject("m")?.let { mo -> mo.keys().forEach { k -> m[k] = mo.optString(k) } }
            o.getString("id") to ChatGroup(o.getString("id"), o.optString("n"), m, o.optLong("at"))
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        if (prefs == null) return
        val a = JSONArray()
        _groups.value.values.forEach { g -> a.put(JSONObject().put("id", g.id).put("n", g.name).put("at", g.createdAt).put("m", JSONObject(g.members as Map<*, *>))) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    companion object {
        const val PREFIX = "g-"
        private const val KEY = "groups"
        fun isGroup(peer: String) = peer.startsWith(PREFIX)
    }
}
