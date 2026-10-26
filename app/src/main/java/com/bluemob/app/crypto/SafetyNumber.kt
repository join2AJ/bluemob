package com.bluemob.app.crypto

import java.security.MessageDigest

/**
 * "Verify with a QR code": proves the key this phone uses for someone really is theirs, so nobody in between (a
 * relaying phone, or the server) can swap in a key of their own.
 *
 * - The **safety number** is 60 digits from both people's keys, the same on both phones (like Signal's). Reading it
 *   aloud or comparing on screen is enough.
 * - The **QR code** carries the BlueMob ID and full public key, so scanning checks the key directly.
 */
object SafetyNumber {
    private const val ITERATIONS = 5200
    const val PREFIX = "bluemob:verify:1:"

    /** 60 digits in 12 groups of 5, the same whichever phone works it out. */
    fun of(idA: String, keyA: ByteArray, idB: String, keyB: ByteArray): String {
        val (first, second) = listOf(idA to keyA, idB to keyB).sortedBy { it.first }
        return (half(first.first, first.second) + half(second.first, second.second)).chunked(5).joinToString(" ")
    }

    /** 30 digits for one person: a slow hash of their key and ID, so finding a look-alike key is costly. */
    private fun half(id: String, key: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-512")
        var h = byteArrayOf(0, 1) + key + id.toByteArray()
        repeat(ITERATIONS) { md.update(h); md.update(key); h = md.digest() }
        return (0 until 6).joinToString("") { i ->
            val chunk = (0 until 5).fold(0L) { acc, j -> (acc shl 8) or (h[i * 5 + j].toLong() and 0xff) }
            "%05d".format(chunk % 100_000)
        }
    }

    /** What my QR code says. */
    fun qr(myId: String, myKeyB64: String) = PREFIX + myId + ":" + myKeyB64

    /** Reads a scanned code: their ID and key, or null if it isn't a BlueMob code or the key doesn't match the ID. */
    fun read(text: String): Pair<String, String>? {
        if (!text.startsWith(PREFIX)) return null
        val rest = text.removePrefix(PREFIX)
        val id = rest.substringBefore(':'); val key = rest.substringAfter(':', "")
        val der = Crypto.decode(key) ?: return null
        return (id to key).takeIf { Crypto.idFor(der) == id && Crypto.publicKey(der) != null }
    }

    /** A short tag for a key, stored with "verified" so a changed key shows as no longer verified. */
    fun tag(keyB64: String): String = Crypto.sha256(keyB64.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
