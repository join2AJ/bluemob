package com.bluemob.app.identity

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * This phone's identity and profile in the mesh.
 *
 * [nodeId] is a random ID generated once on first launch and never changes. It is how other
 * phones (and later the Firebase server) know who sent a message, no matter which Bluetooth
 * or Wi-Fi link it arrived over.
 */
class Identity(context: Context) {
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)

    val nodeId: String = prefs.getString(KEY_NODE_ID, null) ?: UUID.randomUUID().toString()
        .replace("-", "")
        .take(16)
        .also { prefs.edit().putString(KEY_NODE_ID, it).apply() }

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
        private const val KEY_NAME = "display_name"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_SHARE_LOCATION = "share_location"
        private const val KEY_ONBOARDED = "onboarding_done"
    }
}
