package com.bluemob.app.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * The daily altitude check: the Lake Louise score (2018), four questions each 0 to 3, plus the danger signs of HACE
 * and HAPE that mean "go down now" whatever the score. Not a diagnosis: it tells the person and the trip admin when
 * to rest, not go higher, or descend.
 */
object AltitudeCheck {
    data class Answers(val headache: Int = 0, val stomach: Int = 0, val tired: Int = 0, val dizzy: Int = 0,
        /** Confusion, can't walk a straight line, breathless at rest, or a wet cough: any of them. */
        val danger: Boolean = false) {
        val score get() = headache + stomach + tired + dizzy
    }

    enum class Level(val label: String, val advice: String) {
        WELL("Feeling fine", "Keep drinking water and go up slowly: sleep no more than 300–500 m higher each night above 3,000 m."),
        MILD("Mild altitude sickness", "Don't go higher today. Rest, drink water, eat light. If it isn't better tomorrow, go down."),
        MODERATE("Moderate altitude sickness", "Don't go higher. Tell your trip admin. If it gets worse or isn't better in a day, go down 500–1,000 m."),
        SEVERE("Severe: go down", "Go down now, with someone, at least 500–1,000 m. Tell your trip admin. Give oxygen if the group has it."),
    }

    /** Lake Louise: altitude sickness needs a headache; 3–5 mild, 6–9 moderate, 10–12 severe. Danger signs: severe. */
    fun level(a: Answers): Level = when {
        a.danger -> Level.SEVERE
        a.headache == 0 || a.score < 3 -> Level.WELL
        a.score <= 5 -> Level.MILD
        a.score <= 9 -> Level.MODERATE
        else -> Level.SEVERE
    }

    val QUESTIONS = listOf(
        "Headache" to listOf("None", "Mild", "Moderate", "Severe, can't bear it"),
        "Stomach" to listOf("Eating well", "Poor appetite or nausea", "Nausea or vomiting", "Severe nausea and vomiting"),
        "Tiredness" to listOf("Not tired", "A bit tired or weak", "Very tired or weak", "Can't do anything"),
        "Dizziness" to listOf("None", "Mild", "Moderate", "Severe, can't stand"),
    )
    const val DANGER = "Confused, can't walk in a straight line, breathless while resting, or a wet cough"
}

/** One person's altitude check, as the admin's phone keeps it. */
data class AmsCheck(val score: Int, val headache: Int, val danger: Boolean, val at: Long, val altM: Int = 0) {
    /** The same level the person saw: the score's other three answers only add up, so their split doesn't matter. */
    val level get() = AltitudeCheck.level(AltitudeCheck.Answers(headache, (score - headache).coerceIn(0, 9), 0, 0, danger))
    fun encode() = "$score,$headache,${if (danger) 1 else 0},$at,$altM"

    companion object {
        fun decode(s: String): AmsCheck? = s.split(",").takeIf { it.size == 5 }?.let { p ->
            runCatching { AmsCheck(p[0].toInt().coerceIn(0, 12), p[1].toInt().coerceIn(0, 3), p[2] == "1", p[3].toLong(), p[4].toInt().coerceIn(0, 9_000)) }.getOrNull()
        }
        fun mapToJson(m: Map<String, AmsCheck>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v.encode()) } }
        fun mapFromJson(o: JSONObject?): Map<String, AmsCheck> = o?.keys()?.asSequence()?.mapNotNull { k -> decode(o.optString(k))?.let { k to it } }?.toMap().orEmpty()
    }
}

/** A headcount (at the bus, at camp): who was there, who wasn't. Kept on the phone that took it. */
data class Headcount(val at: Long, val here: List<String>, val missing: List<String>) {
    companion object {
        const val KEEP = 30
        fun toJson(l: List<Headcount>) = JSONArray().apply {
            l.forEach { h -> put(JSONObject().put("at", h.at).put("h", JSONArray(h.here)).put("m", JSONArray(h.missing))) }
        }
        fun fromJson(a: JSONArray?): List<Headcount> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { o ->
                fun ids(x: JSONArray?) = if (x == null) emptyList() else (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() }
                Headcount(o.optLong("at"), ids(o.optJSONArray("h")), ids(o.optJSONArray("m")))
            }
        }
    }
}

/**
 * The trip report, as sections of lines: what the trip was, who came, the plan and who reached each checkpoint,
 * headcounts, roll calls, announcements and altitude checks. Health cards are never in it.
 */
object TripReport {
    data class Section(val title: String, val lines: List<String>)

    fun build(g: ChatGroup, teams: List<ChatGroup>, roll: RollCall?, me: String, nameOf: (String) -> String, now: Long,
        fmt: (Long) -> String = { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }): List<Section> = buildList {
        val everyone = listOf(me) + g.members.keys.filter { it != me }
        val ended = g.closedTime(now)
        add(Section("Trip", listOfNotNull(
            "Name: ${g.name}",
            "Started: ${fmt(g.createdAt)}",
            if (ended > 0) "Ended: ${fmt(ended)}" else if (g.expiresAt > 0) "Ends: ${fmt(g.expiresAt)}" else "Still going",
            "Admin: ${nameOf(g.owner.ifBlank { me })}",
            "People: ${everyone.size}",
        )))
        add(Section("People", everyone.map { id ->
            val team = teams.firstOrNull { id in it.members || it.captain == id }
            nameOf(id) + listOfNotNull(
                if (g.isOwner(id)) "admin" else null,
                team?.let { t -> if (t.captain == id) "captain of ${t.name}" else t.name },
                g.buddies[id]?.let { "buddy: ${nameOf(it)}" },
            ).joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }
        }))
        if (g.itinerary.isNotEmpty()) add(Section("Plan and check-ins", g.itinerary.sortedBy { it.at }.map { s ->
            val got = g.checkins[s.id].orEmpty()
            "${fmt(s.at)} · ${s.title}" + if (s.checkpoint) " · checkpoint: ${got.size} of ${everyone.size} checked in" +
                (everyone.filter { it !in got }.takeIf { it.isNotEmpty() && got.isNotEmpty() }?.let { m -> " (not: ${m.joinToString { nameOf(it) }})" } ?: "") else ""
        }))
        if (g.headcounts.isNotEmpty()) add(Section("Headcounts", g.headcounts.map { h ->
            "${fmt(h.at)} · ${h.here.size} of ${h.here.size + h.missing.size} here" + if (h.missing.isEmpty()) "" else " · missing: ${h.missing.joinToString { nameOf(it) }}"
        }))
        roll?.let { r -> add(Section("Last roll call", listOf("${fmt(r.at)} · ${r.reached} of ${r.members.size} phones reached"))) }
        if (g.announcements.isNotEmpty()) add(Section("Announcements", g.announcements.map { a ->
            "${fmt(a.at)} · ${nameOf(a.by)}: ${a.text}" + (g.acks[a.id]?.size?.let { " (${it} said got it)" } ?: "")
        }))
        if (g.amsChecks.isNotEmpty()) add(Section("Altitude checks (latest)", g.amsChecks.entries.sortedByDescending { it.value.score }.map { (id, c) ->
            "${nameOf(id)}: ${c.level.label}, score ${c.score}" + (if (c.altM > 0) " at ${c.altM} m" else "") + " · ${fmt(c.at)}"
        }))
        if (teams.isNotEmpty()) add(Section("Teams", teams.map { t -> "${t.name}: captain ${nameOf(t.captain)}, ${t.members.size + 1} people" }))
        add(Section("About this report", listOf(
            "Made with BlueMob on ${fmt(now)}, from what this phone knows. Phones out of range may have more.",
            "Health cards are not included.",
        )))
    }

    fun text(sections: List<Section>) = sections.joinToString("\n\n") { s -> s.title.uppercase() + "\n" + s.lines.joinToString("\n") { "• $it" } }
}
