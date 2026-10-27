package com.bluemob.app.chat

import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import java.util.Base64

/**
 * A trip group: a group chat for people going somewhere together (a trek, a yatra, a family at a mela). Everyone
 * joins before setting off by scanning one QR code, so the mesh is there from the first step. A roll call then shows,
 * for every member, whether their phone can be reached and how (directly, through other phones, or the internet).
 */
data class TripInvite(val groupId: String, val inviterId: String, val name: String) {
    fun encode(): String = PREFIX + listOf(groupId, inviterId, Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray())).joinToString(":")

    companion object {
        const val PREFIX = "bluemob:trip:1:"

        /** The invite in a scanned QR code, or null if it isn't one. */
        fun parse(text: String): TripInvite? {
            if (!text.startsWith(PREFIX)) return null
            val parts = text.removePrefix(PREFIX).split(":")
            if (parts.size != 3) return null
            val (gid, inviter, name64) = parts
            if (!GroupStore.isGroup(gid) || gid.length > 40 || inviter.length != 16 || !inviter.all { it.isLetterOrDigit() }) return null
            val name = runCatching { String(Base64.getUrlDecoder().decode(name64)) }.getOrNull()?.trim()?.take(40) ?: return null
            return TripInvite(gid, inviter, name.ifBlank { "Trip" })
        }
    }
}

/** How a member's phone was reached in the last roll call. */
sealed interface Reach {
    /** Straight to their phone, over Bluetooth or Wi-Fi. */
    data class Direct(val ms: Long) : Reach
    /** Passed on by [carriers] other phones. */
    data class Mesh(val carriers: Int, val ms: Long) : Reach
    /** Over the internet, through the BlueMob relay. */
    data class Internet(val ms: Long) : Reach
    /** Not reached yet. */
    data object Waiting : Reach

    companion object {
        /** The words a delivery receipt leaves in [MessageEntity.deliveredVia] (see [MessageRepository]). */
        fun via(hops: Int, viaInternet: Boolean, link: String): String = when {
            viaInternet -> INTERNET
            hops <= 1 -> link
            else -> "the mesh (${hops - 1} phone${if (hops > 2) "s" else ""} carried it)"
        }

        private const val INTERNET = "the internet (BlueMob relay)"
        private val carried = Regex("""\((\d+) phones? carried it\)""")

        /** Reads one member's copy of a roll call. */
        fun of(copy: MessageEntity): Reach {
            val at = copy.deliveredAt
            if (at == null || (copy.status != MessageStatus.DELIVERED && copy.status != MessageStatus.READ)) return Waiting
            val ms = (at - copy.createdAt).coerceAtLeast(0)
            val via = copy.deliveredVia.orEmpty()
            return when {
                via == INTERNET -> Internet(ms)
                else -> carried.find(via)?.let { Mesh(it.groupValues[1].toInt(), ms) } ?: Direct(ms)
            }
        }
    }
}

/** The last roll call in a trip group: when it went out, and how each member was reached (node ID → reach). */
data class RollCall(val messageId: String, val at: Long, val members: Map<String, Reach>) {
    val reached: Int get() = members.values.count { it != Reach.Waiting }

    companion object {
        const val TEXT = "📋 Roll call: checking every phone in the group can be reached"
        fun isRollCall(m: MessageEntity) = m.fromMe && !m.hidden && m.text.startsWith("📋 Roll call")

        /** The latest roll call of each trip group in [all] (every message, copies included). */
        fun latest(all: List<MessageEntity>, trips: Collection<String>): Map<String, RollCall> {
            val calls = all.filter { it.peer in trips && isRollCall(it) }.groupBy { it.peer }.mapValues { (_, l) -> l.maxBy { it.createdAt } }
            if (calls.isEmpty()) return emptyMap()
            val wanted = calls.values.associateBy { it.id }
            val copies = all.filter { it.hidden && it.fromMe && it.parent in wanted }.groupBy { it.parent }
            return calls.mapValues { (_, m) -> RollCall(m.id, m.createdAt, copies[m.id].orEmpty().associate { it.peer to Reach.of(it) }) }
        }
    }
}
