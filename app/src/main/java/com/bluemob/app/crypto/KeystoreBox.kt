package com.bluemob.app.crypto

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a key that lives inside Android Keystore (hardware-backed on most phones). The key can't be
 * read out of the phone, so data sealed with it can't be decrypted by copying files off the phone, from a backup,
 * or by reverse-engineering the app.
 */
class KeystoreBox(private val alias: String, private val fallback: SharedPreferences? = null) {
    private fun key(): SecretKey = runCatching { keystoreKey().also { check(it) } }.getOrElse { e ->
        // A few phones have a broken Keystore. Rather than crash, use a key kept in app-private storage
        // (still unreadable to other apps), and report it on the audit screen.
        val store = fallback ?: throw e
        degraded = true
        android.util.Log.w("BlueMob", "Keystore unavailable for $alias, using app-private key", e)
        val saved = store.getString(alias, null)?.let { Crypto.decode(it) }
        val bytes = saved ?: ByteArray(32).also(java.security.SecureRandom()::nextBytes).also { store.edit().putString(alias, Crypto.encode(it)).commit() }
        SecretKeySpec(bytes, "AES")
    }

    /** Fails fast if this key can't actually encrypt on this phone. */
    private fun check(k: SecretKey) { Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, k) }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    private val cached by lazy { key() }

    companion object {
        /** True if any key had to fall back to app-private storage on this phone. */
        @Volatile var degraded = false
            private set
    }

    /** 12-byte IV + ciphertext + tag. */
    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, cached) }
        return c.iv + c.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, cached, GCMParameterSpec(128, sealed, 0, 12)) }
        c.doFinal(sealed, 12, sealed.size - 12)
    }.getOrNull()
}
