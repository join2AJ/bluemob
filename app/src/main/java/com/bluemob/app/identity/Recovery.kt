package com.bluemob.app.identity

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec

/**
 * Recovery codes: the only way to move a BlueMob ID to another phone.
 *
 * A BlueMob ID comes from a P-256 key pair. New accounts derive that key from a random 120-bit seed, so the code is
 * short: 28 characters (`XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`). Accounts made before 0.8 have a random key, so their
 * code carries the key itself: 56 characters. Both end with a checksum, so a typo is caught instead of quietly
 * restoring a different ID.
 *
 * Codes use Crockford's base 32 (no I, L, O or U), and reading is forgiving: lower case, spaces, and I/L/O typed
 * for 1/1/0 all work.
 */
object Recovery {
    const val SEED_BYTES = 15
    private const val SEED_CHECK = 2
    private const val KEY_BYTES = 32
    private const val KEY_CHECK = 3
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    sealed interface Parsed {
        class Seed(val seed: ByteArray) : Parsed
        class Key(val scalar: BigInteger) : Parsed
    }

    fun newSeed(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SEED_BYTES).also(random::nextBytes)

    fun seedCode(seed: ByteArray): String {
        require(seed.size == SEED_BYTES)
        return group(base32(seed + check("seed", seed, SEED_CHECK)))
    }

    fun keyCode(private: ECPrivateKey): String {
        val raw = fixed32(private.s)
        return group(base32(raw + check("key", raw, KEY_CHECK)))
    }

    /** Reads a code someone typed. Null if it isn't a valid code (wrong length, a typo, a bad checksum). */
    fun parse(input: String): Parsed? {
        val clean = normalize(input)
        val bytes = unbase32(clean) ?: return null
        return when (clean.length) {
            28 -> {
                val seed = bytes.copyOfRange(0, SEED_BYTES)
                if (!bytes.copyOfRange(SEED_BYTES, SEED_BYTES + SEED_CHECK).contentEquals(check("seed", seed, SEED_CHECK))) null else Parsed.Seed(seed)
            }
            56 -> {
                val raw = bytes.copyOfRange(0, KEY_BYTES)
                if (!bytes.copyOfRange(KEY_BYTES, KEY_BYTES + KEY_CHECK).contentEquals(check("key", raw, KEY_CHECK))) return null
                val s = BigInteger(1, raw)
                if (s.signum() == 0 || s >= N) null else Parsed.Key(s)
            }
            else -> null
        }
    }

    /** The key pair for a parsed code. */
    fun keysFor(p: Parsed): KeyPair = when (p) {
        is Parsed.Seed -> keysFromSeed(p.seed)
        is Parsed.Key -> keysFromScalar(p.scalar)
    }

    /** Always the same key pair for the same seed. */
    fun keysFromSeed(seed: ByteArray): KeyPair {
        var counter = 0
        while (true) {
            val h = MessageDigest.getInstance("SHA-256").digest("bluemob-identity-v1".toByteArray() + seed + byteArrayOf(counter.toByte()))
            val d = BigInteger(1, h)
            if (d.signum() > 0 && d < N) return keysFromScalar(d)
            counter++
        }
    }

    fun keysFromScalar(d: BigInteger): KeyPair {
        val (x, y) = multiply(d, GX, GY)
        val kf = KeyFactory.getInstance("EC")
        val pub = kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
        val priv = kf.generatePrivate(ECPrivateKeySpec(d, params))
        return KeyPair(pub, priv)
    }

    /** Upper case, no separators, and the look-alike letters read as digits. */
    fun normalize(input: String): String = input.uppercase()
        .replace('O', '0').replace('I', '1').replace('L', '1')
        .filter { it in ALPHABET }

    fun group(code: String): String = code.chunked(4).joinToString("-")

    private fun check(tag: String, data: ByteArray, n: Int) =
        MessageDigest.getInstance("SHA-256").digest("bluemob-recovery-$tag".toByteArray() + data).copyOf(n)

    private fun fixed32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    fun base32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun unbase32(s: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    // P-256 (secp256r1). Android can't compute a public key from a private one, so we do the curve maths here.
    private val P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    private val A = P - BigInteger.valueOf(3)
    private val GX = BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16)
    private val GY = BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16)
    val N = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

    private val params: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun multiply(k: BigInteger, x: BigInteger, y: BigInteger): Pair<BigInteger, BigInteger> {
        var rx: BigInteger? = null
        var ry: BigInteger? = null
        for (i in k.bitLength() - 1 downTo 0) {
            if (rx != null) { val d = double(rx, ry!!); rx = d.first; ry = d.second }
            if (k.testBit(i)) {
                if (rx == null) { rx = x; ry = y } else { val s = add(rx, ry!!, x, y); rx = s.first; ry = s.second }
            }
        }
        return rx!! to ry!!
    }

    private fun double(x: BigInteger, y: BigInteger): Pair<BigInteger, BigInteger> {
        val l = (x.pow(2).multiply(BigInteger.valueOf(3)).add(A)).multiply(y.shiftLeft(1).modInverse(P)).mod(P)
        val nx = l.pow(2).subtract(x.shiftLeft(1)).mod(P)
        return nx to l.multiply(x.subtract(nx)).subtract(y).mod(P)
    }

    private fun add(x1: BigInteger, y1: BigInteger, x2: BigInteger, y2: BigInteger): Pair<BigInteger, BigInteger> {
        if (x1 == x2) return if (y1 == y2) double(x1, y1) else error("point at infinity")
        val l = y2.subtract(y1).multiply(x2.subtract(x1).modInverse(P)).mod(P)
        val nx = l.pow(2).subtract(x1).subtract(x2).mod(P)
        return nx to l.multiply(x1.subtract(nx)).subtract(y1).mod(P)
    }
}
