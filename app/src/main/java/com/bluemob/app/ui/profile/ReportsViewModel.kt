package com.bluemob.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemob.app.BlueMobApp
import kotlinx.coroutines.launch

/** Problem reports (with photos) and reports about people, sent to the BlueMob team. */
class ReportsViewModel(app: Application) : AndroidViewModel(app) {
    private val blueMob = app as BlueMobApp
    private val identity = blueMob.identity
    private val settings = blueMob.settings
    private val repo = blueMob.messages

    /** A picture for a problem report: scaled to at most 1280 px and compressed, so a report stays small. */
    fun reportPhoto(uri: android.net.Uri, done: (com.bluemob.app.ui.profile.ReportPhoto?) -> Unit) = viewModelScope.launch {
        val p = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val app = getApplication<android.app.Application>()
                // Read the size first, then decode at about 1280 px (works on every Android version).
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
                val raw = app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: error("unreadable")
                val scale = minOf(1f, 1280f / maxOf(raw.width, raw.height))
                val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
                var q = 75
                var out: ByteArray
                do { out = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, it) }.toByteArray(); q -= 15 } while (out.size > 800 * 1024 && q > 20)
                com.bluemob.app.ui.profile.ReportPhoto(bmp, out)
            }.getOrNull()
        }
        done(p)
    }

    /** Sends a problem report. Null when sent, otherwise why not. */
    suspend fun reportProblem(text: String, details: Boolean, log: List<com.bluemob.app.mesh.LogLine>,
        category: String = "", sub: String = "", photos: List<ByteArray> = emptyList()): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val app = getApplication<android.app.Application>()
        // With technical details on, the team also gets this BlueMob ID, so they can reply to this person from the admin page.
        val device = com.bluemob.app.util.CrashLog.deviceLine(app) + " · reply to BM " + com.bluemob.app.util.formatId(identity.nodeId)
        val extra = if (!details) "" else buildString {
            com.bluemob.app.util.CrashLog.lastNonFatal(app)?.let { append("Last background error:\n").append(it.take(6000)).append("\n\n") }
            com.bluemob.app.util.CrashLog.read(app)?.let { append("Last crash:\n").append(it.take(6000)).append("\n\n") }
            append("Mesh log:\n")
            log.take(80).forEach { append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(it.timeMillis))).append("  ").append(it.text).append('\n') }
        }
        com.bluemob.app.util.ProblemReport.send(settings.bridgeUrl.value, text, com.bluemob.app.BuildConfig.VERSION_NAME, if (details) device else "", extra,
            category.substringAfter(' '), sub, photos)
    }

    /** Reports someone to the BlueMob team: why, what happened, and (if the user agrees) the last messages they sent us. */
    suspend fun reportPerson(id: String, reason: String, text: String, includeMessages: Boolean): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val name = blueMob.contacts.contacts.value[id]?.name ?: "Someone"
        val details = buildString {
            append("Reported: ").append(name).append(" · BlueMob ID ").append(id).append('\n')
            append("Reported by: ").append(identity.nodeId).append('\n')
            if (includeMessages) {
                append("\nTheir last messages to the reporter (shared with the reporter's consent):\n")
                repo.conversations.value[id].orEmpty().filter { !it.fromMe }.takeLast(10).forEach { m ->
                    append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(m.createdAt))).append("  ").append(m.text.take(500)).append('\n')
                }
            }
        }
        com.bluemob.app.util.ProblemReport.send(settings.bridgeUrl.value, text.ifBlank { reason }, com.bluemob.app.BuildConfig.VERSION_NAME, "", details, "Report a person", reason)
    }
}
