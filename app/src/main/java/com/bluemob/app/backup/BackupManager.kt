package com.bluemob.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bluemob.app.BlueMobApp
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.files.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class BackupEvery(val label: String, val days: Long) { OFF("Off", 0), DAILY("Daily", 1), WEEKLY("Weekly", 7), MONTHLY("Monthly", 30) }

data class BackupStatus(val lastAt: Long = 0, val lastResult: String? = null, val lastSize: Long = 0)

/**
 * Backups that live only where the user puts them: this phone's Downloads folder, a folder they pick (an SD card, or a
 * cloud app that offers folders, like OneDrive), or a one-off copy saved to any app (Google Drive, OneDrive, email…).
 * Nothing goes to a BlueMob server. Each backup is one compressed file, encrypted with the user's backup password.
 */
class BackupManager(private val app: BlueMobApp) {
    private val prefs = com.bluemob.app.crypto.SecurePrefs.open(app, "backup")

    private val _status = MutableStateFlow(BackupStatus(prefs.getLong("last_at", 0), prefs.getString("last_result", null), prefs.getLong("last_size", 0)))
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _every = MutableStateFlow(runCatching { BackupEvery.valueOf(prefs.getString("every", null) ?: "") }.getOrDefault(BackupEvery.OFF))
    val every: StateFlow<BackupEvery> = _every.asStateFlow()

    private val _folder = MutableStateFlow(prefs.getString("folder", null))
    /** A folder the user picked (a content:// tree), or null for this phone's Downloads/BlueMob. */
    val folder: StateFlow<String?> = _folder.asStateFlow()

    /** Kept (encrypted with the phone's Keystore) only so scheduled backups can run without asking. */
    val hasPassword: Boolean get() = prefs.getString("password", null) != null
    private val password: String? get() = prefs.getString("password", null)

    fun setPassword(p: String) { require(p.length >= BackupFile.MIN_PASSWORD); prefs.edit().putString("password", p).apply() }

    fun setEvery(e: BackupEvery) {
        prefs.edit().putString("every", e.name).apply(); _every.value = e
        val wm = WorkManager.getInstance(app)
        if (e == BackupEvery.OFF) wm.cancelUniqueWork(WORK)
        else wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(e.days, TimeUnit.DAYS).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
    }

    /** A folder picked with Android's folder picker (permission already taken), or null for this phone. */
    fun setFolder(uri: String?) { prefs.edit().apply { if (uri == null) remove("folder") else putString("folder", uri) }.apply(); _folder.value = uri }

    fun folderName(): String = _folder.value?.let { Uri.decode(it).substringAfterLast(':').substringAfterLast('/').ifBlank { "chosen folder" } } ?: "This phone · Downloads/BlueMob"

    private suspend fun collect(): Pair<BackupData, Map<String, File>> {
        val db = app.database
        val messages = db.messages().all().filter { it.peer != com.bluemob.app.bot.SkyBot.NODE_ID }
        val atts = messages.mapNotNull { m -> m.attPath?.let(::File)?.takeIf { it.exists() }?.let { it.name to it } }.toMap()
        val data = BackupData(
            createdAt = System.currentTimeMillis(), app = com.bluemob.app.BuildConfig.VERSION_NAME, nodeId = app.identity.nodeId,
            recovery = runCatching { app.identity.recoveryCode() }.getOrNull(),
            messages = messages, trips = db.trips().all(), trail = db.trail().all(), calls = db.calls().all(),
            contacts = app.contacts.contacts.value.values.toList(), spots = app.settings.spots.value,
        )
        return data to atts
    }

    private fun fileName() = "BlueMob-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".bmbk"

    /** Backs up to the chosen place (Downloads/BlueMob, or the picked folder). Returns a message for the user. */
    suspend fun backupNow(passwordOverride: String? = null): String = withContext(Dispatchers.IO) {
        val pw = passwordOverride ?: password ?: return@withContext "Set a backup password first"
        runCatching {
            val (data, atts) = collect()
            val name = fileName()
            val out = _folder.value?.let { createInTree(Uri.parse(it), name) } ?: createInDownloads(name)
                ?: return@withContext "Couldn't create the backup file there. Pick the place again."
            app.contentResolver.openOutputStream(out)?.use { BackupFile.write(it, pw, data, atts) } ?: return@withContext "Couldn't write the backup"
            val size = runCatching { app.contentResolver.openFileDescriptor(out, "r")?.use { it.statSize } ?: 0 }.getOrDefault(0)
            _folder.value?.let { pruneTree(Uri.parse(it)) }
            done("Backed up ${data.messages.size} messages, ${data.trips.size} trips, ${data.calls.size} calls (${Attachment.sizeText(size)})", size)
        }.getOrElse { done("Backup failed: ${it.message ?: it.javaClass.simpleName}", 0) }
    }

    /** A one-off backup to a file the user created with Android's "Save to…" (Google Drive, OneDrive, phone…). */
    suspend fun backupTo(uri: Uri, pw: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val (data, atts) = collect()
            app.contentResolver.openOutputStream(uri)?.use { BackupFile.write(it, pw, data, atts) } ?: return@withContext "Couldn't write the backup"
            val size = runCatching { app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0 }.getOrDefault(0)
            done("Saved a backup: ${data.messages.size} messages, ${data.trips.size} trips (${Attachment.sizeText(size)})", size)
        }.getOrElse { done("Backup failed: ${it.message ?: it.javaClass.simpleName}", 0) }
    }

    fun suggestedName() = fileName()

    private fun done(msg: String, size: Long): String {
        val now = System.currentTimeMillis()
        if (size > 0) prefs.edit().putLong("last_at", now).putLong("last_size", size).apply()
        prefs.edit().putString("last_result", msg).apply()
        _status.value = BackupStatus(if (size > 0) now else _status.value.lastAt, msg, if (size > 0) size else _status.value.lastSize)
        app.audit.add(AuditKind.APP, msg)
        return msg
    }

    private fun createInDownloads(name: String): Uri? {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, BackupFile.MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlueMob")
            }
            return app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }
        // Android 8–9: BlueMob's own folder on the phone's storage.
        val dir = File(app.getExternalFilesDir(null), "backups").apply { mkdirs() }
        return Uri.fromFile(File(dir, name))
    }

    private fun createInTree(tree: Uri, name: String): Uri? = runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        DocumentsContract.createDocument(app.contentResolver, parent, BackupFile.MIME, name)
    }.getOrNull()

    /** Keeps the newest [KEEP] backups in the picked folder. */
    private fun pruneTree(tree: Uri) = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        app.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) { val n = c.getString(1) ?: continue; if (n.startsWith("BlueMob-backup-") && n.endsWith(".bmbk")) found += c.getString(0) to n }
        }
        found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            DocumentsContract.deleteDocument(app.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        }
    }

    sealed interface Preview {
        data class Ready(val data: BackupData, val attachments: Map<String, ByteArray>, val otherId: Boolean) : Preview
        data class Problem(val message: String) : Preview
    }

    /** Opens a backup file to show what's in it before restoring. */
    suspend fun open(uri: Uri, pw: String): Preview = withContext(Dispatchers.IO) {
        val input = runCatching { app.contentResolver.openInputStream(uri) }.getOrNull() ?: return@withContext Preview.Problem("Couldn't open that file")
        input.use {
            when (val r = BackupFile.read(it, pw)) {
                is BackupFile.ReadResult.Ok -> Preview.Ready(r.data, r.attachments, r.data.nodeId != app.identity.nodeId)
                BackupFile.ReadResult.WrongPassword -> Preview.Problem("Wrong password, or the file was changed")
                BackupFile.ReadResult.NotABackup -> Preview.Problem("That isn't a BlueMob backup")
            }
        }
    }

    /**
     * Adds a backup's contents to this phone. Nothing here is overwritten: messages, trips and calls already present
     * are kept. Returns a summary. If [restoreId], the backup's BlueMob ID replaces this phone's (the app restarts).
     */
    suspend fun restore(p: Preview.Ready, restoreId: Boolean): String = withContext(Dispatchers.IO) {
        val db = app.database
        val store = File(app.filesDir, "att").apply { mkdirs() }
        p.attachments.forEach { (name, bytes) -> File(store, Attachment.safeName(name)).takeIf { !it.exists() }?.writeBytes(bytes) }
        var msgs = 0
        p.data.messages.forEach { m ->
            val local = m.attPath?.let { File(store, Attachment.safeName(it)).takeIf { f -> f.exists() }?.path }
            if (db.messages().insert(m.copy(attPath = local)) != -1L) msgs++
        }
        val have = db.trips().all().map { it.id }.toSet()
        val newTrips = p.data.trips.filter { it.id !in have }
        newTrips.forEach { db.trips().put(it) }
        val newIds = newTrips.map { it.id }.toSet()
        p.data.trail.filter { it.tripId in newIds }.forEach { db.trail().insert(it.copy(id = 0)) }
        p.data.calls.forEach { db.calls().insert(it) }
        p.data.contacts.forEach { c -> app.contacts.upsert(c.nodeId) { it ?: c } }
        val spotIds = app.settings.spots.value.map { it.id }.toSet()
        p.data.spots.filter { it.id !in spotIds }.forEach { app.settings.addSpot(it) }
        val summary = "Restored $msgs messages, ${newTrips.size} trips, ${p.data.calls.size} calls and ${p.data.contacts.size} contacts"
        app.audit.add(AuditKind.APP, summary)
        if (restoreId && p.data.recovery != null) app.identity.restore(p.data.recovery)
        summary
    }

    companion object {
        const val WORK = "bluemob-backup"
        const val KEEP = 5
    }
}

/** Runs scheduled backups (daily, weekly or monthly), when the battery isn't low. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? BlueMobApp ?: return Result.failure()
        if (app.startupError != null) return Result.retry()
        val msg = app.backups.backupNow()
        return if (msg.startsWith("Backed up")) Result.success() else Result.retry()
    }
}
