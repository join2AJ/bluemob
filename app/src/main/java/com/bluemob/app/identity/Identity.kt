package com.bluemob.app.identity

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import java.security.KeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This phone's identity and profile in the mesh.
 *
 * [keys] is this phone's key pair, made once on first launch. [nodeId] comes from the public key, so no other phone
 * can use it: everything that matters is signed, and others check the signature against the ID. The private key is
 * stored encrypted with a key that lives in Android's Keystore and never leaves it.
 */
class Identity(context: Context) {
    private val prefs = com.bluemob.app.crypto.SecurePrefs.open(context, "identity")

    val keys: DeviceKeys = DeviceKeys(loadOrCreateKeys())
    val nodeId: String = keys.nodeId

    /** The random ID older versions used, if this phone had one. IDs now come from the key, so it changed once. */
    val previousNodeId: String? = prefs.getString(KEY_NODE_ID, null)?.takeIf { it != nodeId }

    init {
        prefs.edit().putString(KEY_NODE_ID, nodeId).apply()
    }

    private fun loadOrCreateKeys(): KeyPair {
        val pub = prefs.getString(KEY_PUBLIC, null)?.let(Crypto::decode)
        val priv = prefs.getString(KEY_PRIVATE, null)?.let(Crypto::decode)?.let(::unwrap)
        if (pub != null && priv != null) {
            runCatching { return KeyPair(Crypto.publicKey(pub)!!, Crypto.privateKey(priv)) }
        }
        val pair = Crypto.generate()
        prefs.edit().putString(KEY_PUBLIC, Crypto.encode(pair.public.encoded)).putString(KEY_PRIVATE, Crypto.encode(wrap(pair.private.encoded))).apply()
        return pair
    }

    /** AES-GCM with a Keystore key. Falls back to storing the key as is if the Keystore isn't usable on this phone. */
    private fun wrap(plain: ByteArray): ByteArray = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        byteArrayOf(1) + c.iv + c.doFinal(plain)
    }.getOrElse { Log.w("BlueMob", "Keystore unavailable, storing key unwrapped", it); byteArrayOf(0) + plain }

    private fun unwrap(stored: ByteArray): ByteArray? = runCatching {
        if (stored[0].toInt() == 0) return stored.copyOfRange(1, stored.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, stored, 1, 12)) }
        c.doFinal(stored, 13, stored.size - 13)
    }.getOrNull()

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    private val _displayName = MutableStateFlow(prefs.getString(KEY_NAME, null) ?: Build.MODEL)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatar = MutableStateFlow(prefs.getString(KEY_AVATAR, null) ?: AVATARS.first())
    val avatar: StateFlow<String> = _avatar.asStateFlow()

    private val _shareLocation = MutableStateFlow(prefs.getBoolean(KEY_SHARE_LOCATION, false))
    val shareLocation: StateFlow<Boolean> = _shareLocation.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setDisplayName(value: String) {
        val clean = value.replace("|", " ").trim().take(MAX_NAME_LENGTH)
        if (clean.isEmpty()) return
        prefs.edit().putString(KEY_NAME, clean).apply()
        _displayName.value = clean
    }

    fun setAvatar(value: String) {
        prefs.edit().putString(KEY_AVATAR, value).apply()
        _avatar.value = value
    }

    fun setShareLocation(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_LOCATION, value).apply()
        _shareLocation.value = value
    }

    fun setOnboardingDone(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        _onboardingDone.value = value
    }

    companion object {
        const val MAX_NAME_LENGTH = 24
        /** Avatars to pick from: nature and freedom themed. */
        val AVATARS = listOf("🌿", "🦅", "🌊", "⛰️", "🌻", "🦋", "🐬", "🔥", "🌙", "🍀", "🐺", "🌵")
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_PRIVATE = "private_key_wrapped"
        private const val WRAP_ALIAS = "bluemob_identity_wrap"
        private const val KEY_NAME = "display_name"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_SHARE_LOCATION = "share_location"
        private const val KEY_ONBOARDED = "onboarding_done"
    }
}
