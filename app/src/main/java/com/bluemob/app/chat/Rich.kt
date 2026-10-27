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
    /**
     * A signed rescue-group message riding along a hidden chat message: chats are kept by the relay until delivered
     * and acknowledged, so "I'm coming" and "I'm safe" reach someone who was offline at that moment.
     */
    val env: String? = null,
    /** The group is a trip group (see [TripInvite]). */
    val trip: Boolean = false,
    /** Joining a trip group by its QR code: sent to whoever showed the code, who adds us and tells everyone. */
    val join: Boolean = false,
    /** A roll call in a trip group: the phone that gets it shows "I'm OK" and "I need help". */
    val rollCall: Boolean = false,
    /** A trip group's settings as the sender knows them; only the group admin's count (see [ChatGroup.merge]). */
    val meta: TripMeta? = null,
    /** A new itinerary ([ChatGroup.canPlan] only): its stops and when it was set. */
    val itinerary: List<Stop>? = null, val itineraryAt: Long = 0,
    /** "I reached this checkpoint" (a stop ID), at [checkInAt]. */
    val checkIn: String? = null, val checkInAt: Long = 0,
    /** Joining by passcode from a group shown nearby. */
    val passcode: String? = null,
    /** The admin says no to a join (wrong passcode, or the group is full or closed): why. */
    val denied: String? = null,
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
        if (trip) j.put("tp", 1)
        if (join) j.put("jn", 1)
        if (rollCall) j.put("rc", 1)
        meta?.let { j.put("tm", it.toJson()) }
        itinerary?.let { j.put("it", Stop.toJson(it)).put("ia", itineraryAt) }
        checkIn?.let { j.put("ci", it).put("cia", checkInAt) }
        passcode?.let { j.put("pc", it) }
        denied?.let { j.put("dn", it) }
        env?.let { j.put("rm", it) }
        return MARK + j.toString()
    }

    val isControl: Boolean get() = react != null || leave || env != null || join || denied != null

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
                env = j.optString("rm").takeIf { it.isNotBlank() && it.length <= 8_000 },
                trip = j.optInt("tp") == 1, join = j.optInt("jn") == 1, rollCall = j.optInt("rc") == 1,
                meta = j.optJSONObject("tm")?.let(TripMeta::fromJson),
                itinerary = j.optJSONArray("it")?.let(Stop::fromJson), itineraryAt = j.optLong("ia"),
                checkIn = j.optString("ci").takeIf { it.isNotBlank() && it.length <= 40 }, checkInAt = j.optLong("cia"),
                passcode = if (j.has("pc")) j.optString("pc").take(12) else null,
                denied = if (j.has("dn")) j.optString("dn").take(120) else null,
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
data class ChatGroup(
    val id: String, val name: String, val members: Map<String, String>, val createdAt: Long = System.currentTimeMillis(), val trip: Boolean = false,
    /** Trip groups: the group admin (who made it), and for a team, its main group and its captain. */
    val owner: String = "", val parent: String = "", val captain: String = "",
    /** When the trip ends (0: no end), and when the group was closed (0: open). */
    val expiresAt: Long = 0, val closedAt: Long = 0,
    /** Automatic roll call every this many minutes (0: off), sent by the admin's or captain's phone. */
    val autoMin: Int = 0,
    /** This phone only: we already asked the admin whether to close it when the trip ended. */
    val askedToClose: Boolean = false,
    /** The plan: stops in time order, some of them checkpoints. Set by the admin (a team's: by its captain). */
    val itinerary: List<Stop> = emptyList(), val itineraryAt: Long = 0,
    /** Who reached which checkpoint, and when: stop ID → (member → time). */
    val checkins: Map<String, Map<String, Long>> = emptyMap(),
    /** Admin's phone only: show this group to phones nearby, and the passcode they type to join. Never sent. */
    val advertise: Boolean = false, val passcode: String = "",
) {
    /** Who may set the itinerary: the admin, and a team's captain for the team. */
    fun canPlan(id: String) = isOwner(id) || (isTeam && captain.isNotBlank() && captain == id) || (trip && owner.isBlank())
    val meta: TripMeta get() = TripMeta(owner, parent, captain, expiresAt, closedAt, autoMin)
    val isTeam: Boolean get() = parent.isNotBlank()

    /** Closed: by the admin, or by itself a day after the trip ended without the admin keeping it open. */
    fun closedTime(now: Long): Long = when {
        closedAt > 0 -> closedAt
        expiresAt > 0 && now >= expiresAt + TripMeta.GRACE_MS -> expiresAt + TripMeta.GRACE_MS
        else -> 0
    }
    fun isClosed(now: Long) = closedTime(now) > 0
    fun isOwner(me: String) = owner.isNotBlank() && owner == me
    /** Roll calls, the automatic check and adding people: the admin, and a team's captain. */
    fun canManage(me: String) = isOwner(me) || (captain.isNotBlank() && captain == me) || (trip && owner.isBlank())

    /**
     * What we know after a trip-group message from [from]: members only grow (leaving is its own message), and the
     * name and settings change only when the admin sent them (or when we don't know the admin yet).
     */
    fun merge(from: String, name: String, others: Map<String, String>, m: TripMeta?): ChatGroup {
        val trusted = owner.isBlank() || from == owner
        val members = members + others.mapValues { (id, n) -> n.ifBlank { members[id].orEmpty() } }
        if (!trusted || m == null) return copy(members = members, trip = true)
        return copy(name = name.ifBlank { this.name }, members = members, trip = true, owner = owner.ifBlank { m.owner },
            parent = m.parent, captain = m.captain, expiresAt = m.expiresAt, closedAt = maxOf(closedAt, m.closedAt), autoMin = m.autoMin,
            askedToClose = askedToClose && m.expiresAt == expiresAt)
    }
}

/** A trip group's settings, as they travel inside its messages. */
data class TripMeta(val owner: String = "", val parent: String = "", val captain: String = "", val expiresAt: Long = 0, val closedAt: Long = 0, val autoMin: Int = 0) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (owner.isNotBlank()) put("o", owner)
        if (parent.isNotBlank()) put("p", parent)
        if (captain.isNotBlank()) put("c", captain)
        if (expiresAt > 0) put("x", expiresAt)
        if (closedAt > 0) put("cl", closedAt)
        if (autoMin > 0) put("a", autoMin)
    }

    companion object {
        /** After the end time, the group stays open this long unless the admin closes it or keeps it open. */
        const val GRACE_MS = 24 * 3_600_000L

        private fun id(s: String) = s.takeIf { it.length in 1..16 && it.all { c -> c.isLetterOrDigit() } }.orEmpty()
        fun fromJson(j: JSONObject) = TripMeta(id(j.optString("o")), j.optString("p").takeIf { GroupStore.isGroup(it) && it.length <= 40 }.orEmpty(),
            id(j.optString("c")), j.optLong("x").coerceAtLeast(0), j.optLong("cl").coerceAtLeast(0), j.optInt("a").coerceIn(0, 24 * 60))
    }
}

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
            o.getString("id") to ChatGroup(o.getString("id"), o.optString("n"), m, o.optLong("at"), o.optBoolean("tr"),
                o.optString("ow"), o.optString("pa"), o.optString("cp"), o.optLong("ex"), o.optLong("cl"), o.optInt("am"), o.optBoolean("ask"),
                o.optJSONArray("it")?.let(Stop::fromJson).orEmpty(), o.optLong("ia"), Stop.checkinsFromJson(o.optJSONObject("ci")),
                o.optBoolean("ad"), o.optString("pc"))
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        if (prefs == null) return
        val a = JSONArray()
        _groups.value.values.forEach { g -> a.put(JSONObject().put("id", g.id).put("n", g.name).put("at", g.createdAt).put("m", JSONObject(g.members as Map<*, *>)).put("tr", g.trip)
            .put("ow", g.owner).put("pa", g.parent).put("cp", g.captain).put("ex", g.expiresAt).put("cl", g.closedAt).put("am", g.autoMin).put("ask", g.askedToClose)
            .put("it", Stop.toJson(g.itinerary)).put("ia", g.itineraryAt).put("ci", Stop.checkinsToJson(g.checkins)).put("ad", g.advertise).put("pc", g.passcode)) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    companion object {
        const val PREFIX = "g-"
        private const val KEY = "groups"
        fun isGroup(peer: String) = peer.startsWith(PREFIX)
    }
}
