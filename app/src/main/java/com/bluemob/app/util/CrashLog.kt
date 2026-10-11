package com.bluemob.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the details of a crash on the phone, so the next launch can show them and the user can send them to us.
 * It contains only the error and phone model: no messages, contacts or keys.
 */
object CrashLog {
    private fun file(context: Context) = File(context.noBackupFilesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, error, "thread ${thread.name}") }
            previous?.uncaughtException(thread, error)
        }
    }

    fun save(context: Context, error: Throwable, where: String) {
        val text = buildString {
            appendLine(deviceLine(context))
            unfinishedStep(context)?.let { appendLine("While: $it") }
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " · " + where)
            appendLine()
            append(error.stackTraceToString().take(12_000))
        }
        file(context).writeText(text)
        ErrorOutbox.add(context, "Crashed", error, text)
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { file(context).delete(); stepFile(context).delete(); retryFile(context).delete() }

    // ---- startup steps: if the app dies while starting (even in native code, which no handler can catch),
    // the next launch knows exactly where, and shows it instead of trying again blindly. ----
    private fun stepFile(context: Context) = File(context.noBackupFilesDir, "starting.txt")

    fun step(context: Context, what: String) = runCatching { stepFile(context).writeText("v=${appVersion(context)}\n$what") }

    /** Start-up finished. Also forgets an earlier quiet retry, unless [keepRetry]. */
    fun started(context: Context, keepRetry: Boolean = false) { stepFile(context).delete(); if (!keepRetry) retryFile(context).delete() }

    private fun retryFile(context: Context) = File(context.noBackupFilesDir, "retried.txt")

    /** True the first time start-up is retried after an unexplained stop; false if it was already retried once. */
    fun firstRetry(context: Context): Boolean {
        val f = retryFile(context)
        if (f.exists()) return false
        runCatching { f.writeText("1") }
        return true
    }

    /** The step the previous launch was on when it stopped, if it never finished starting. */
    fun unfinishedStep(context: Context): String? = stepFile(context).takeIf { it.exists() }?.readText()?.lines()?.filterNot { it.startsWith("v=") }?.joinToString("\n")

    /**
     * Forgets crash records left by a different version of BlueMob. After an update fixes a crash, the old report
     * (and an unfinished start-up step) would otherwise keep showing, as if the new version had crashed too.
     * True if something was forgotten.
     */
    fun forgetOtherVersions(context: Context, current: String? = appVersion(context)): Boolean {
        val stepVersion = stepFile(context).takeIf { it.exists() }?.readText()?.lines()?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        val stepStale = stepFile(context).exists() && stepVersion != current
        val reportStale = read(context)?.let { reportVersion(it) != current } ?: false
        if (!stepStale && !reportStale) return false
        clear(context)
        return true
    }

    /** "0.11.1" from a report's first line, "BlueMob 0.11.1 · Android …". */
    fun reportVersion(report: String): String? =
        report.lineSequence().firstOrNull()?.takeIf { it.startsWith("BlueMob ") }?.removePrefix("BlueMob ")?.substringBefore(" ·")?.trim()

    private fun appVersion(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    fun deviceLine(context: Context): String {
        return "BlueMob ${appVersion(context)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.joinToString()}"
    }

    // ---- errors in background work that were contained instead of closing the app ----
    private fun errorFile(context: Context) = File(context.noBackupFilesDir, "last_error.txt")
    fun saveNonFatal(context: Context, error: Throwable) = runCatching {
        val text = deviceLine(context) + "\n" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "\n\n" + error.stackTraceToString().take(8_000)
        errorFile(context).writeText(text)
        ErrorOutbox.add(context, "Background error", error, text)
    }
    fun lastNonFatal(context: Context): String? = errorFile(context).takeIf { it.exists() }?.readText()
}


/**
 * Errors waiting to go to the BlueMob team automatically (if the user allows it: Diagnostics → Send error reports).
 * Only the error, where in BlueMob's code it happened, and the phone model: never messages, contacts, keys or
 * positions. The same error is sent at most once a day; at most 20 wait at a time.
 */
object ErrorOutbox {
    private fun file(context: Context) = File(context.noBackupFilesDir, "error_outbox.json")
    private fun sentFile(context: Context) = File(context.noBackupFilesDir, "error_sent.json")
    private val lock = Any()

    /** Same error, same place: the exception type and BlueMob's own top frames. */
    fun signature(error: Throwable): String {
        val frames = error.stackTrace.filter { it.className.startsWith("com.bluemob") }.take(3).joinToString("|") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return (error.javaClass.name + "|" + frames).hashCode().toUInt().toString(16)
    }

    fun add(context: Context, kind: String, error: Throwable, details: String) = runCatching {
        synchronized(lock) {
            val sig = signature(error)
            val a = read(file(context))
            if ((0 until a.length()).any { a.getJSONObject(it).optString("sig") == sig }) return@synchronized
            if (a.length() >= 20) return@synchronized
            a.put(org.json.JSONObject().put("sig", sig).put("kind", kind).put("at", System.currentTimeMillis())
                .put("title", "${error.javaClass.simpleName}: ${error.message ?: ""}".take(300)).put("details", details.take(12_000)))
            file(context).writeText(a.toString())
        }
    }

    private fun read(f: File) = runCatching { org.json.JSONArray(f.readText()) }.getOrDefault(org.json.JSONArray())

    /** Sends what's waiting. Call with internet; returns how many went. */
    fun flush(context: Context, relay: String?): Int = synchronized(lock) {
        val a = read(file(context))
        if (a.length() == 0) return 0
        val sent = runCatching { org.json.JSONObject(sentFile(context).readText()) }.getOrDefault(org.json.JSONObject())
        val day = System.currentTimeMillis() - 86_400_000L
        val keep = org.json.JSONArray()
        var n = 0
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            if (sent.optLong(e.getString("sig")) > day) continue
            val ok = ProblemReport.send(relay, "${e.getString("kind")}: ${e.getString("title")}", CrashLog.deviceLine(context).substringBefore(" · "),
                CrashLog.deviceLine(context), e.getString("details"), category = "The app", sub = e.getString("kind"), auto = true) == null
            if (ok) { sent.put(e.getString("sig"), System.currentTimeMillis()); n++ } else keep.put(e)
        }
        file(context).writeText(keep.toString())
        sentFile(context).writeText(sent.toString())
        n
    }
}
