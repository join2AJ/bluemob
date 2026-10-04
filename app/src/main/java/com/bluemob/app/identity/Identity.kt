package com.bluemob.app.identity

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * This phone's identity in the mesh.
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

    var displayName: String
        get() = prefs.getString(KEY_NAME, null) ?: Build.MODEL
        set(value) = prefs.edit().putString(KEY_NAME, value.trim().take(MAX_NAME_LENGTH)).apply()

    companion object {
        const val MAX_NAME_LENGTH = 24
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_NAME = "display_name"
    }
}
