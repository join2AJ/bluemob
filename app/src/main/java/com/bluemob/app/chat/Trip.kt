package com.bluemob.app.chat

import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import java.util.Base64

/**
 * A trip group: a group chat for people going somewhere together (a trek, a yatra, a family at a mela). Everyone
 * joins before setting off by scanning one QR code, so the mesh is there from the first step. A roll call then shows,
 * for every member, whether their phone can be reached and how (directly, through other phones, or the internet).
 */
data class TripInvite(
    val groupId: String, val inviterId: String, val name: String,
    /** The group admin, the trip's end (0: none) and when the group was made. */
    val owner: String = "", val expiresAt: Long = 0, val createdAt: Long = 0,
) {
    /** The first, readable kind of code (0.33). New codes are [TripCode]s. */
    fun encode(): String = PREFIX + listOf(groupId, inviterId, Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray())).joinToString(":")

    companion object {
        const val PREFIX = "bluemob:trip:1:"

        /** The invite in a scanned or pasted code (either kind), or null if it isn't one. */
        fun parse(text: String): TripInvite? {
            TripCode.find(text)?.let { return TripCode.read(it) }
            val raw = text.trim()
            if (!raw.startsWith(PREFIX)) return null
            val parts = raw.removePrefix(PREFIX).split(":")
            if (parts.size != 3) return null
            val (gid, inviter, name64) = parts
            if (!validGroup(gid) || !validId(inviter)) return null
            val name = runCatching { String(Base64.getUrlDecoder().decode(name64)) }.getOrNull()?.trim()?.take(40) ?: return null
            return TripInvite(gid, inviter, name.ifBlank { "Trip" })
        }

        internal fun validGroup(gid: String) = GroupStore.isGroup(gid) && gid.length <= 40
        internal fun validId(id: String) = id.length == 16 && id.all { it.isLetterOrDigit() }
    }
}

/**
 * The code that lets people join a trip group, as a QR code or as text to share. To anyone else it is random letters:
 *
 * - **For BlueMob's admin only:** who made the group and when, who made this code and when, the phone and app
 *   version. This part is sealed to the admin's public key (ECDH P-256 with a fresh key each time, then AES-256-GCM),
 *   and only the admin's private key, kept on the BlueMob server, opens it. Nobody else can read it, not even from the
 *   app's own code.
 * - **For the phone that joins:** the group, its name and whom to ask. A phone must be able to read this to join, so
 *   it's only scrambled with a key inside BlueMob: it looks random, but someone who takes the app apart could read it.
 *   It holds nothing beyond what a member sees anyway.
 */
object TripCode {
    const val PREFIX = "bluemob:t:"
    private const val VERSION: Byte = 2

    /** BlueMob admin's public key (P-256, uncompressed). Its private half is the server's ADMIN_SEAL_KEY. */
    const val ADMIN_PUBLIC = "040f12d952574e449ae1294712d110157d8eccee48abd0a9c5a230685a02ba5c56f3cabd32b0fa5e414bb8d880d77fc91f7bc567c3366e176b7dc0371eaadbf519"

    private val APP_KEY = java.security.MessageDigest.getInstance("SHA-256").digest("bluemob-trip-code-v2 for phones".toByteArray())
    private val ADMIN_INFO = "bluemob-trip-admin-v1".toByteArray()
    private val AAD = "bluemob:t:2".toByteArray()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    /** Who made it and when, for the admin. */
    data class Origin(val creator: String, val createdAt: Long, val madeBy: String, val madeAt: Long, val device: String, val version: String)

    fun encode(invite: TripInvite, origin: Origin, adminPublic: String = ADMIN_PUBLIC): String {
        val app = org.json.JSONObject().put("g", invite.groupId).put("n", invite.name).put("i", invite.inviterId)
            .put("o", invite.owner).put("x", invite.expiresAt).put("c", invite.createdAt)
        val admin = org.json.JSONObject().put("creator", origin.creator).put("createdAt", origin.createdAt).put("madeBy", origin.madeBy)
            .put("madeAt", origin.madeAt).put("device", origin.device).put("version", origin.version).put("group", invite.groupId).put("name", invite.name)
            .put("expiresAt", invite.expiresAt)
        val (eph, key) = agree(hex(adminPublic))
        val sealedAdmin = gcm(true, key, admin.toString().toByteArray())!!
        val sealedApp = gcm(true, APP_KEY, app.toString().toByteArray())!!
        val out = java.io.ByteArrayOutputStream()
        out.write(VERSION.toInt()); out.write(eph.size); out.write(eph)
        out.write(sealedAdmin.size shr 8); out.write(sealedAdmin.size and 0xff); out.write(sealedAdmin)
        out.write(sealedApp)
        return PREFIX + b64.encodeToString(out.toByteArray())
    }

    /** The code inside a shared message ("Join my trip group: bluemob:t:…"), or null. */
    fun find(text: String): String? = Regex("""bluemob:t:[A-Za-z0-9_-]{40,}""").find(text)?.value

    /** The part a phone reads to join. */
    fun read(code: String): TripInvite? = runCatching {
        val p = parts(code) ?: return null
        val j = org.json.JSONObject(String(gcm(false, APP_KEY, p.third) ?: return null))
        val gid = j.getString("g"); val inviter = j.getString("i")
        if (!TripInvite.validGroup(gid) || !TripInvite.validId(inviter)) return null
        TripInvite(gid, inviter, j.optString("n").trim().take(40).ifBlank { "Trip" }, j.optString("o").takeIf(TripInvite::validId).orEmpty(),
            j.optLong("x").coerceAtLeast(0), j.optLong("c").coerceAtLeast(0))
    }.getOrNull()

    /** The admin's part, opened with the admin's private key (hex). The server does the same (relay.js). */
    fun openForAdmin(code: String, privateHex: String): org.json.JSONObject? = runCatching {
        val (eph, sealed, _) = parts(code) ?: return null
        val spec = java.security.spec.ECPrivateKeySpec(java.math.BigInteger(1, hex(privateHex)), params)
        val priv = java.security.KeyFactory.getInstance("EC").generatePrivate(spec)
        val secret = javax.crypto.KeyAgreement.getInstance("ECDH").run { init(priv); doPhase(publicKey(eph), true); generateSecret() }
        org.json.JSONObject(String(gcm(false, com.bluemob.app.crypto.Crypto.hkdf(secret, ADMIN_INFO), sealed) ?: return null))
    }.getOrNull()

    private fun parts(code: String): Triple<ByteArray, ByteArray, ByteArray>? {
        val raw = unb64.decode(find(code)?.removePrefix(PREFIX) ?: return null)
        if (raw.size < 4 || raw[0] != VERSION) return null
        val el = raw[1].toInt() and 0xff
        val at = 2 + el
        if (raw.size < at + 2) return null
        val al = ((raw[at].toInt() and 0xff) shl 8) or (raw[at + 1].toInt() and 0xff)
        if (raw.size < at + 2 + al) return null
        return Triple(raw.copyOfRange(2, at), raw.copyOfRange(at + 2, at + 2 + al), raw.copyOfRange(at + 2 + al, raw.size))
    }

    private val params: java.security.spec.ECParameterSpec by lazy {
        (java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair().public
            as java.security.interfaces.ECPublicKey).params
    }

    private fun publicKey(raw: ByteArray): java.security.PublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte())
        val point = java.security.spec.ECPoint(java.math.BigInteger(1, raw.copyOfRange(1, 33)), java.math.BigInteger(1, raw.copyOfRange(33, 65)))
        return java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.ECPublicKeySpec(point, params))
    }

    /** A fresh key pair: its public half (uncompressed), and the key it shares with [adminRaw]. */
    private fun agree(adminRaw: ByteArray): Pair<ByteArray, ByteArray> {
        val pair = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val w = (pair.public as java.security.interfaces.ECPublicKey).w
        val raw = byteArrayOf(4) + fixed(w.affineX) + fixed(w.affineY)
        val secret = javax.crypto.KeyAgreement.getInstance("ECDH").run { init(pair.private); doPhase(publicKey(adminRaw), true); generateSecret() }
        return raw to com.bluemob.app.crypto.Crypto.hkdf(secret, ADMIN_INFO)
    }

    private fun fixed(n: java.math.BigInteger): ByteArray {
        val b = n.toByteArray()
        return when { b.size == 32 -> b; b.size > 32 -> b.copyOfRange(b.size - 32, b.size); else -> ByteArray(32 - b.size) + b }
    }

    /** AES-256-GCM: nonce (12) + ciphertext + tag (16). */
    private fun gcm(encrypt: Boolean, key: ByteArray, data: ByteArray): ByteArray? = runCatching {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        if (encrypt) {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce)); c.updateAAD(AAD)
            nonce + c.doFinal(data)
        } else {
            c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, data, 0, 12)); c.updateAAD(AAD)
            c.doFinal(data, 12, data.size - 12)
        }
    }.getOrNull()

    private fun hex(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
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
