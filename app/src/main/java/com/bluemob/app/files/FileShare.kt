package com.bluemob.app.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.data.MessageDao
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Photos, videos, documents and voice notes in chats.
 *
 * - Sending: the file is read, photos are resized (which also drops their hidden GPS data), then it's encrypted with a
 *   fresh key and saved in BlueMob's private folder. The key travels inside the end-to-end encrypted chat message.
 * - The encrypted file goes straight to the other phone over Bluetooth / Wi-Fi when they're in range (Wi-Fi is much
 *   faster for big files). If they're not and this phone has internet, it goes through the relay ([RelayFiles]):
 *   the relay holds the encrypted bytes until the other phone downloads them. Otherwise it waits until you meet.
 * - Receiving: the file stays encrypted on the phone. It's decrypted only to show or open it, into a temporary
 *   folder that's emptied every time BlueMob starts.
 */
class FileShare(
    context: Context,
    private val mesh: NearbyMeshTransport,
    private val dao: MessageDao,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val store = File(app.filesDir, "att").apply { mkdirs() }
    private val openDir = File(app.cacheDir, "open").apply { deleteRecursively(); mkdirs() }

    /** Transfer progress by file ID, 0..1. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** (phone, transfer ID) → file ID, from the "file" note sent just before each file. */
    private val expected = mutableMapOf<Pair<String, Long>, String>()
    /** Files that arrived before their note: (phone, transfer ID) → saved copy. */
    private val early = mutableMapOf<Pair<String, Long>, File>()
    /** Our outgoing transfers: (phone, transfer ID) → file ID. */
    private val sending = mutableMapOf<Pair<String, Long>, String>()
    private val lock = Mutex()
    private val thumbs = LruCache<String, Bitmap>(40)

    /** The relay, for files to and from people who aren't nearby. Set by the app. */
    @Volatile var net: com.bluemob.app.bridge.RelayFiles? = null
    /** True while this phone can reach the relay. */
    @Volatile var netUp: () -> Boolean = { false }
    private val netLock = Mutex()
    /** Files we've uploaded this run (the relay keeps them until the other phone has them). */
    private val uploaded = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val downloading = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        scope.launch {
            mesh.events.collect { e ->
                when {
                    e is MeshEvent.PeerConnected -> push(e.nodeId)
                    e is MeshEvent.App && e.kind == KIND -> onNote(e)
                }
            }
        }
        scope.launch { mesh.fileEvents.collect { onFileEvent(it) } }
    }

    // ---- Sending ----

    /** Reads, shrinks (photos) and encrypts a file the user picked. Returns null with a reason if it can't be sent. */
    suspend fun prepare(uri: Uri): Pair<Pair<Attachment, File>?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = app.contentResolver
            var name = "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val kind = AttKind.forMime(mime)
            if (size > Attachment.MAX_BYTES && kind != AttKind.IMAGE) return@withContext null to "That file is ${Attachment.sizeText(size)}. The limit is ${Attachment.sizeText(Attachment.MAX_BYTES)}."
            var bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null to "Couldn't read that file."
            var outMime = mime
            var outName = Attachment.safeName(name)
            if (kind == AttKind.IMAGE && !mime.contains("gif")) {
                shrinkPhoto(bytes)?.let { bytes = it; outMime = "image/jpeg"; outName = outName.substringBeforeLast('.') + ".jpg" }
            }
            if (bytes.size > Attachment.MAX_BYTES) return@withContext null to "That file is ${Attachment.sizeText(bytes.size.toLong())}. The limit is ${Attachment.sizeText(Attachment.MAX_BYTES)}."
            seal(bytes, outName, outMime, kind, 0) to null
        }.getOrElse { Log.w(TAG, "Couldn't prepare file", it); null to "Couldn't read that file." }
    }

    /** A voice note recorded by [VoiceNotes]. */
    suspend fun prepareVoiceNote(file: File, durationMs: Long): Pair<Attachment, File> = withContext(Dispatchers.IO) {
        val bytes = file.readBytes().also { file.delete() }
        seal(bytes, "Voice note.m4a", "audio/mp4", AttKind.AUDIO, durationMs)
    }

    private fun seal(bytes: ByteArray, name: String, mime: String, kind: AttKind, durationMs: Long): Pair<Attachment, File> {
        val key = FileCrypto.newKey()
        val fid = "f-" + UUID.randomUUID().toString().replace("-", "").take(20)
        val out = File(store, "$fid.bin")
        out.writeBytes(FileCrypto.encrypt(key, bytes))
        return Attachment(fid, name, mime, bytes.size.toLong(), Base64.encodeToString(key, Base64.NO_WRAP), kind, durationMs) to out
    }

    /** Longest side 1600 px, JPEG quality 80: a phone photo drops from ~4 MB to ~300 KB, and loses its EXIF location. */
    private fun shrinkPhoto(bytes: ByteArray): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = PHOTO_MAX.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val rotated = rotateByExif(bytes, sized)
        ByteArrayOutputStream().also { rotated.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }.getOrNull()

    private fun rotateByExif(original: ByteArray, bmp: Bitmap): Bitmap = runCatching {
        val exif = android.media.ExifInterface(original.inputStream())
        val deg = when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90; 3 -> 180; 8 -> 270; else -> 0
        }
        if (deg == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg.toFloat()) }, true)
    }.getOrDefault(bmp)

    /** Sends every file still waiting for [peer]: directly if they're nearby, otherwise through the relay. */
    suspend fun push(peer: String) {
        if (mesh.isConnected(peer)) pushNearby(peer) else pushOnline(peer)
    }

    /** After the internet comes back: every file still waiting goes through the relay. */
    suspend fun pushAllOnline() {
        if (!netUp()) return
        dao.pendingFiles().map { it.peer }.distinct().filter { !mesh.isConnected(it) }.forEach { pushOnline(it) }
    }

    /** Uploads [peer]'s waiting files to the relay (encrypted as they are) and tells them to fetch each one. */
    private suspend fun pushOnline(peer: String) = netLock.withLock {
        val relay = net ?: return@withLock
        if (!netUp()) return@withLock
        dao.filesToSend(peer).forEach { m ->
            val att = Attachment.fromJson(m.att) ?: return@forEach
            if (att.fid in uploaded) return@forEach
            val file = m.attPath?.let(::File)?.takeIf { it.exists() } ?: return@forEach
            dao.update(m.copy(attState = AttState.SENDING))
            val ok = withContext(Dispatchers.IO) { relay.upload(att.fid, peer, file) { p -> _progress.update { it + (att.fid to p) } } }
            _progress.update { it - att.fid }
            val now = dao.byFile(att.fid) ?: return@forEach
            if (!ok) { dao.update(now.copy(attState = AttState.WAITING)); return@forEach }
            uploaded += att.fid
            dao.update(now.copy(history = now.history + com.bluemob.app.chat.MessageRepository.event(System.currentTimeMillis(), "File sent to the BlueMob relay (encrypted)")))
            mesh.sendApp(peer, KIND, JSONObject().put("a", "net").put("fid", att.fid))
        }
    }

    /** Sends every file still waiting for [peer] straight to them, if they're connected and their BlueMob takes files. */
    private suspend fun pushNearby(peer: String) = lock.withLock {
        // Phones before 0.9 can't take files (0.7 and older don't say what they support).
        if (!mesh.isConnected(peer) || mesh.featureGap(peer, "file") != null || mesh.mayBeOld(peer)) return@withLock
        dao.filesToSend(peer).forEach { m ->
            val att = Attachment.fromJson(m.att) ?: return@forEach
            if (sending.containsValue(att.fid)) return@forEach
            val file = m.attPath?.let(::File)?.takeIf { it.exists() } ?: return@forEach
            val pid = mesh.sendFile(peer, file) { pid ->
                mesh.sendApp(peer, KIND, JSONObject().put("a", "file").put("fid", att.fid).put("pid", pid).put("size", file.length()))
            } ?: return@forEach
            sending[peer to pid] = att.fid
            dao.update(m.copy(attState = AttState.SENDING))
        }
    }

    /** A message with an attachment was saved: send ours now, or match a file that already arrived. */
    suspend fun onMessage(m: MessageEntity) {
        if (m.fromMe) { push(m.peer); return }
        val att = Attachment.fromJson(m.att) ?: return
        val matched = lock.withLock {
            val key = expected.entries.firstOrNull { it.value == att.fid && it.key.first == m.peer }?.key ?: return@withLock false
            early.remove(key)?.let { saveArrived(key, att.fid, it) }
            true
        }
        // Not nearby: it may be waiting on the relay already.
        if (!matched && !mesh.isConnected(m.peer)) scope.launch { fetchOnline(m.peer, att.fid) }
    }

    /** Downloads a file the relay holds for us, then treats it like one that arrived over the mesh. */
    private suspend fun fetchOnline(from: String, fid: String) {
        val relay = net ?: return
        if (!netUp() || !downloading.add(fid)) return
        try {
            if (dao.byFile(fid)?.attState == AttState.DONE) return
            val tmp = File(store, "net-$fid.part")
            val ok = withContext(Dispatchers.IO) { relay.download(fid, tmp) { p -> _progress.update { it + (fid to p) } } }
            if (!ok) { tmp.delete(); _progress.update { it - fid }; return }
            lock.withLock {
                val key = from to NET_TRANSFER
                expected[key] = fid
                saveArrived(key, fid, tmp)
            }
        } finally { downloading.remove(fid) }
    }

    // ---- Receiving ----

    private suspend fun onNote(e: MeshEvent.App) {
        val b = e.body
        when (b.optString("a")) {
            "file" -> lock.withLock {
                val fid = b.optString("fid").takeIf { it.startsWith("f-") && it.length <= 40 } ?: return@withLock
                val key = e.fromNodeId to b.optLong("pid")
                expected[key] = fid
                early.remove(key)?.let { saveArrived(key, fid, it) }
            }
            "net" -> b.optString("fid").takeIf { it.startsWith("f-") && it.length <= 40 }?.let { fid -> scope.launch { fetchOnline(e.fromNodeId, fid) } }
            "ok" -> {
                val fid = b.optString("fid")
                val m = dao.byFile(fid)?.takeIf { it.fromMe && it.peer == e.fromNodeId } ?: return
                val via = if (fid in uploaded || !mesh.isConnected(e.fromNodeId)) "the internet" else mesh.linkName(e.fromNodeId)
                dao.update(m.copy(attState = AttState.DONE, history = m.history + com.bluemob.app.chat.MessageRepository.event(System.currentTimeMillis(),
                    "File delivered over $via")))
                _progress.update { it - fid }
            }
        }
    }

    private suspend fun onFileEvent(e: NearbyMeshTransport.FileEvent) {
        when (e) {
            is NearbyMeshTransport.FileEvent.Progress -> {
                val fid = lock.withLock { (if (e.outgoing) sending else expected)[e.nodeId to e.transferId] } ?: return
                if (e.total > 0) _progress.update { it + (fid to (e.done.toFloat() / e.total)) }
            }
            is NearbyMeshTransport.FileEvent.Arrived -> {
                val copy = withContext(Dispatchers.IO) { copyIn(e.file) } ?: return
                lock.withLock {
                    val key = e.nodeId to e.transferId
                    val fid = expected[key]
                    if (fid == null) early[key] = copy else saveArrived(key, fid, copy)
                }
            }
            is NearbyMeshTransport.FileEvent.Sent -> lock.withLock { sending.remove(e.nodeId to e.transferId) }
            is NearbyMeshTransport.FileEvent.Failed -> {
                val fid = lock.withLock { (if (e.outgoing) sending.remove(e.nodeId to e.transferId) else expected[e.nodeId to e.transferId]) } ?: return
                _progress.update { it - fid }
                if (e.outgoing) dao.byFile(fid)?.let { dao.update(it.copy(attState = AttState.WAITING)) }
            }
        }
    }

    /** Copies a received file into BlueMob's folder (still encrypted) and removes Nearby's copy. */
    private fun copyIn(f: com.google.android.gms.nearby.connection.Payload.File): File? = runCatching {
        val tmp = File(store, "in-" + UUID.randomUUID() + ".part")
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = f.asUri() ?: return null
            app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            runCatching { app.contentResolver.delete(uri, null, null) }
        } else {
            @Suppress("DEPRECATION")
            val src = f.asJavaFile() ?: return null
            src.copyTo(tmp, overwrite = true)
            src.delete()
        }
        tmp
    }.getOrNull()

    /** Must hold [lock]. Links an arrived file to its message, checks it decrypts, and tells the sender. */
    private suspend fun saveArrived(key: Pair<String, Long>, fid: String, tmp: File) {
        val from = key.first
        val m = dao.byFile(fid)?.takeIf { !it.fromMe && it.peer == from }
        if (m == null) {
            // The message hasn't come yet (it may be travelling another way): keep the file until it does.
            early[key] = tmp
            return
        }
        val att = Attachment.fromJson(m.att) ?: return
        val dest = File(store, "$fid.bin")
        tmp.renameTo(dest)
        val ok = withContext(Dispatchers.IO) { FileCrypto.decrypt(Base64.decode(att.key, Base64.NO_WRAP), dest.readBytes()) != null }
        if (!ok) {
            dest.delete()
            audit.add(AuditKind.MESSAGE, "A file from ${mesh.nameOf(from)} failed its check and was deleted")
            return
        }
        dao.update(m.copy(attPath = dest.path, attState = AttState.DONE))
        _progress.update { it - fid }
        mesh.sendApp(from, KIND, JSONObject().put("a", "ok").put("fid", fid))
        // Came through the relay: it can delete its copy now.
        if (key.second == NET_TRANSFER) { expected.remove(key); net?.let { r -> scope.launch(Dispatchers.IO) { r.done(fid) } } }
    }

    /** Deletes every attachment file on this phone (after "Delete all messages"). */
    fun deleteAll() { store.listFiles()?.forEach { it.delete() }; openDir.listFiles()?.forEach { it.delete() } }

    // ---- Opening ----

    /** The file's contents, decrypted in memory. */
    suspend fun bytes(m: MessageEntity): ByteArray? = withContext(Dispatchers.IO) {
        val att = Attachment.fromJson(m.att) ?: return@withContext null
        val f = m.attPath?.let(::File)?.takeIf { it.exists() } ?: return@withContext null
        FileCrypto.decrypt(Base64.decode(att.key, Base64.NO_WRAP), f.readBytes())
    }

    /** A small picture for chats and the media grid. */
    suspend fun thumbnail(m: MessageEntity, maxPx: Int = 480): Bitmap? {
        val att = Attachment.fromJson(m.att) ?: return null
        thumbs.get(att.fid + maxPx)?.let { return it }
        val data = bytes(m) ?: return null
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.also { thumbs.put(att.fid + maxPx, it) }
    }

    /** Decrypts into the temporary folder (emptied on every start), for opening in another app or playing. */
    suspend fun openCopy(m: MessageEntity): File? {
        val att = Attachment.fromJson(m.att) ?: return null
        val data = bytes(m) ?: return null
        return withContext(Dispatchers.IO) {
            File(openDir, att.fid.takeLast(6) + "-" + att.name).apply { writeBytes(data) }
        }
    }

    companion object {
        const val KIND = "file"
        /** Transfer ID used for files that came through the relay rather than over Nearby. */
        private const val NET_TRANSFER = -1L
        private const val TAG = "BlueMobFiles"
        private const val PHOTO_MAX = 1600
    }
}
