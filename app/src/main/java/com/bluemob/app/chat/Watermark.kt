package com.bluemob.app.chat

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The pattern hidden in every chat's dotted background. It changes every hour and hides the BlueMob ID of the phone
 * it's shown on, so if a screenshot of a chat is reported, the team can tell whose phone took it: the dots (big = 1,
 * small = 0, in blocks of 10 × 8 that start at a small ring) plus roughly when it was taken (the chat shows message
 * times) give back the ID on the admin page.
 *
 * The code is the ID XOR-ed with an hourly key stream, plus a 2-byte check, in Crockford base32 (16 characters). It's
 * a deterrent against passing screenshots around, not a secret: anyone with the app's code could read it.
 */
object Watermark {
    private val KEY = "bluemob-watermark-v1".toByteArray()
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val HOUR = 3_600_000L

    private fun mac(text: String) = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(KEY, "HmacSHA256")); doFinal(text.toByteArray()) }

    /** The code for [nodeId] (16 hex characters) at [at] ms, grouped as XXXX XXXX XXXX XXXX. */
    fun code(nodeId: String, at: Long): String = base32(bytes(nodeId, at)).chunked(4).joinToString(" ")

    /** The 10 bytes behind the code: the masked ID and a 2-byte check. */
    fun bytes(nodeId: String, at: Long): ByteArray {
        val hour = at / HOUR
        val id = nodeId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val stream = mac("stream|$hour")
        val masked = ByteArray(8) { (id[it].toInt() xor stream[it].toInt()).toByte() }
        return masked + mac("check|$nodeId|$hour").copyOf(2)
    }

    /** The same 80 bits, most significant first: what the dotted chat background shows (big dot = 1). */
    fun bits(nodeId: String, at: Long): BooleanArray =
        bytes(nodeId, at).let { b -> BooleanArray(80) { i -> (b[i / 8].toInt() shr (7 - i % 8)) and 1 == 1 } }

    const val COLS = 10
    const val ROWS = 8

    /** The BlueMob ID hidden in [code] if it was made in [hour], or null if the check doesn't match. */
    fun decode(code: String, hour: Long): String? {
        val bytes = unbase32(code.uppercase().filter { it in ALPHABET }) ?: return null
        if (bytes.size < 10) return null
        val stream = mac("stream|$hour")
        val id = ByteArray(8) { (bytes[it].toInt() xor stream[it].toInt()).toByte() }.joinToString("") { "%02x".format(it) }
        return id.takeIf { mac("check|$it|$hour").copyOf(2).contentEquals(bytes.copyOfRange(8, 10)) }
    }

    private fun base32(b: ByteArray): String {
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (x in b) { buf = (buf shl 8) or (x.toInt() and 0xff); bits += 8; while (bits >= 5) { sb.append(ALPHABET[(buf shr (bits - 5)) and 31]); bits -= 5 } }
        if (bits > 0) sb.append(ALPHABET[(buf shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun unbase32(s: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream(); var buf = 0; var bits = 0
        for (c in s) { val v = ALPHABET.indexOf(c); if (v < 0) return null; buf = (buf shl 5) or v; bits += 5; if (bits >= 8) { out.write((buf shr (bits - 8)) and 0xff); bits -= 8 } }
        return out.toByteArray()
    }
}

/**
 * The pattern hidden in a rescue screen's background (warm orange dots, so it's told apart from a chat's): it hides both
 * the BlueMob ID of the phone it's shown on and the ID of the person the rescue is for, changing every hour. From a
 * reported screenshot the team gets whose phone took it and whose rescue it was (with the time, which rescue).
 *
 * 18 bytes: (viewer ID + person-in-need ID) XOR-ed with an hourly key stream, plus a 2-byte check; shown as 144 dots in
 * blocks of 16 × 9. Like [Watermark], a deterrent, not a secret.
 */
object RescueMark {
    private val KEY = "bluemob-rescue-mark-v1".toByteArray()
    const val COLS = 16
    const val ROWS = 9

    private fun mac(text: String) = javax.crypto.Mac.getInstance("HmacSHA256").run { init(javax.crypto.spec.SecretKeySpec(KEY, "HmacSHA256")); doFinal(text.toByteArray()) }
    private fun raw(id: String) = id.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun bytes(viewer: String, victim: String, at: Long): ByteArray {
        val hour = at / Watermark.HOUR
        val ids = raw(viewer) + raw(victim)
        val stream = mac("stream|$hour")
        return ByteArray(16) { (ids[it].toInt() xor stream[it].toInt()).toByte() } + mac("check|$viewer|$victim|$hour").copyOf(2)
    }

    fun bits(viewer: String, victim: String, at: Long): BooleanArray =
        bytes(viewer, victim, at).let { b -> BooleanArray(COLS * ROWS) { i -> (b[i / 8].toInt() shr (7 - i % 8)) and 1 == 1 } }

    /** (viewer, person in need) hidden in [bits] if made in [hour], or null. */
    fun decode(bits: BooleanArray, hour: Long): Pair<String, String>? {
        if (bits.size != COLS * ROWS) return null
        val b = ByteArray(18) { i -> (0 until 8).fold(0) { acc, k -> (acc shl 1) or (if (bits[i * 8 + k]) 1 else 0) }.toByte() }
        val stream = mac("stream|$hour")
        val ids = ByteArray(16) { (b[it].toInt() xor stream[it].toInt()).toByte() }.joinToString("") { "%02x".format(it) }
        val viewer = ids.take(16); val victim = ids.drop(16)
        return (viewer to victim).takeIf { mac("check|$viewer|$victim|$hour").copyOf(2).contentEquals(b.copyOfRange(16, 18)) }
    }
}
