package com.bluemob.app.files

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Where an attachment's file is. */
object AttState {
    const val NONE = 0
    /** Sender: waiting for them to be in range. Receiver: the file hasn't arrived yet. */
    const val WAITING = 1
    const val SENDING = 2
    const val DONE = 3
    const val FAILED = 4
}

enum class AttKind(val code: String, val label: String, val emoji: String) {
    IMAGE("image", "Photo", "📷"), VIDEO("video", "Video", "🎬"), AUDIO("audio", "Voice note", "🎤"), DOC("doc", "Document", "📄");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: DOC
        fun forMime(mime: String?): AttKind = when {
            mime == null -> DOC
            mime.startsWith("image/") -> IMAGE
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            else -> DOC
        }
    }
}

/**
 * A photo, video, document or voice note in a chat. These details travel inside the end-to-end encrypted message,
 * so only the two people in the chat ever see the file's [key]. The file itself goes straight from phone to phone,
 * encrypted with that key, and stays encrypted on both phones.
 */
data class Attachment(
    val fid: String,
    val name: String,
    val mime: String,
    /** Size of the original file, in bytes. */
    val size: Long,
    /** AES-256 key for the file, base64. */
    val key: String,
    val kind: AttKind,
    /** Voice notes and videos: length in milliseconds. */
    val durationMs: Long = 0,
) {
    fun toJson(): String = JSONObject().put("fid", fid).put("name", name).put("mime", mime).put("size", size)
        .put("key", key).put("kind", kind.code).also { if (durationMs > 0) it.put("dur", durationMs) }.toString()

    /** What older BlueMob versions show instead (they only read the text). */
    fun fallbackText(): String = "${kind.emoji} ${if (kind == AttKind.DOC) name else kind.label} (${sizeText(size)}). Update BlueMob to 0.9 or newer to open it."

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024

        fun fromJson(s: String?): Attachment? = runCatching {
            if (s.isNullOrBlank()) return null
            val j = JSONObject(s)
            val fid = j.getString("fid").takeIf { it.matches(Regex("[a-zA-Z0-9-]{6,40}")) } ?: return null
            Attachment(fid, safeName(j.optString("name", "file")), j.optString("mime", "application/octet-stream").take(100),
                j.optLong("size").coerceIn(0, MAX_BYTES), j.getString("key"), AttKind.of(j.optString("kind")), j.optLong("dur"))
        }.getOrNull()

        /** No paths or odd characters in names from other phones. */
        fun safeName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^\\w .()\\-]"), "_").trim().take(80).ifBlank { "file" }

        fun sizeText(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        }

        fun durationText(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    }
}

/** File encryption: AES-256-GCM, stored as `iv (12 bytes) | ciphertext + tag`. */
object FileCrypto {
    fun newKey(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        return c.iv + c.doFinal(plain)
    }

    /** Null if the file was changed or the key is wrong (GCM checks both). */
    fun decrypt(key: ByteArray, sealed: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12)) }
        c.doFinal(sealed, 12, sealed.size - 12)
    }.getOrNull()
}
