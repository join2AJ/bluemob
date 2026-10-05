package com.bluemob.app.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The few cryptographic building blocks BlueMob uses, all from the standard Java/Android libraries:
 *
 * - Each phone has a P-256 key pair. Its BlueMob ID is the first 8 bytes of SHA-256(public key), so an ID can't be
 *   claimed without the matching private key, and anyone holding a public key can check it belongs to an ID.
 * - Packets that matter (SOS, lost, rescue groups, messages, receipts) are signed with ECDSA-SHA256.
 * - Messages are end-to-end encrypted with AES-256-GCM, using a key from ECDH between sender and recipient
 *   (HKDF-SHA256), so phones that carry a message across the mesh can't read it.
 */
object Crypto {
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    fun encode(bytes: ByteArray): String = b64.encodeToString(bytes)
    fun decode(s: String): ByteArray? = runCatching { unb64.decode(s) }.getOrNull()

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** The BlueMob ID for a public key: 16 lowercase hex characters. */
    fun idFor(publicKey: ByteArray): String = sha256(publicKey).take(8).joinToString("") { "%02x".format(it) }

    fun publicKey(encoded: ByteArray): PublicKey? = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) }.getOrNull()
    fun privateKey(encoded: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))

    fun sign(key: PrivateKey, data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(data); sign() }

    fun verify(key: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
        runCatching { Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(data); verify(signature) } }.getOrDefault(false)

    /** A message key shared by exactly two phones: ECDH, then HKDF with both IDs, so it's the same key on both sides. */
    fun sharedKey(mine: PrivateKey, theirs: PublicKey, idA: String, idB: String): ByteArray {
        val secret = KeyAgreement.getInstance("ECDH").run { init(mine); doPhase(theirs, true); generateSecret() }
        val info = ("bluemob-msg-v1|" + listOf(idA, idB).sorted().joinToString("|")).toByteArray()
        return hkdf(secret, info)
    }

    private fun hkdf(ikm: ByteArray, info: ByteArray): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(ByteArray(32), "HmacSHA256")); doFinal(ikm) }
        return Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(prk, "HmacSHA256")); update(info); update(1); doFinal() }
    }

    /** AES-256-GCM. Returns base64 of nonce + ciphertext. [aad] binds the ciphertext to its message ID. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad) }
        return encode(nonce + c.doFinal(plaintext))
    }

    /** Null if the message was changed, or isn't for this key. */
    fun open(key: ByteArray, sealed: String, aad: ByteArray): ByteArray? = runCatching {
        val all = decode(sealed)!!
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12)); updateAAD(aad)
        }
        c.doFinal(all, 12, all.size - 12)
    }.getOrNull()

    /** Accepts "BM 3F9A 1C2B 7D4E 8A01", "#3f9a…", with or without spaces. Null if it isn't a valid ID. */
    fun normalizeId(input: String): String? {
        val hex = input.trim().lowercase().removePrefix("bm").filter { it.isLetterOrDigit() }
        return hex.takeIf { it.length == 16 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}

/** This phone's key pair and the ID that comes from it. */
class DeviceKeys(val keyPair: KeyPair) {
    val publicEncoded: ByteArray = keyPair.public.encoded
    val publicB64: String = Crypto.encode(publicEncoded)
    val nodeId: String = Crypto.idFor(publicEncoded)
    fun sign(data: ByteArray): ByteArray = Crypto.sign(keyPair.private, data)
}

/**
 * Public keys of other phones, by ID. A key is only accepted if it hashes to the ID, so it doesn't matter who
 * told us: a key can be learned from the phone itself, from an SOS it sent, or from any phone that knows it.
 */
class KeyBook(initial: Map<String, String> = emptyMap(), private val onChange: (Map<String, String>) -> Unit = {}) {
    private val keys = initial.filter { (id, pk) -> Crypto.decode(pk)?.let(Crypto::idFor) == id }.toMutableMap()
    private val parsed = mutableMapOf<String, PublicKey>()

    /** Adds a key if it's valid. Returns its ID, or null. */
    fun add(publicB64: String): String? {
        val bytes = Crypto.decode(publicB64) ?: return null
        Crypto.publicKey(bytes) ?: return null
        val id = Crypto.idFor(bytes)
        if (keys[id] != publicB64) { keys[id] = publicB64; onChange(keys.toMap()) }
        return id
    }

    fun b64(id: String): String? = keys[id]
    fun key(id: String): PublicKey? = parsed[id] ?: keys[id]?.let { Crypto.decode(it) }?.let(Crypto::publicKey)?.also { parsed[id] = it }
    fun knows(id: String) = keys.containsKey(id)
    val size: Int get() = keys.size
}
