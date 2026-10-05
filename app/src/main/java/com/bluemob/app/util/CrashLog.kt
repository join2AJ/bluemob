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
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { file(context).delete(); stepFile(context).delete() }

    // ---- startup steps: if the app dies while starting (even in native code, which no handler can catch),
    // the next launch knows exactly where, and shows it instead of trying again blindly. ----
    private fun stepFile(context: Context) = File(context.noBackupFilesDir, "starting.txt")

    fun step(context: Context, what: String) = runCatching { stepFile(context).writeText(what) }

    fun started(context: Context) { stepFile(context).delete() }

    /** The step the previous launch was on when it stopped, if it never finished starting. */
    fun unfinishedStep(context: Context): String? = stepFile(context).takeIf { it.exists() }?.readText()

    fun deviceLine(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "BlueMob $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.joinToString()}"
    }

    // ---- errors in background work that were contained instead of closing the app ----
    private fun errorFile(context: Context) = File(context.noBackupFilesDir, "last_error.txt")
    fun saveNonFatal(context: Context, error: Throwable) = runCatching {
        errorFile(context).writeText(deviceLine(context) + "\n" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "\n\n" + error.stackTraceToString().take(8_000))
    }
    fun lastNonFatal(context: Context): String? = errorFile(context).takeIf { it.exists() }?.readText()
}
