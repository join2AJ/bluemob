package com.bluemob.app.backup

import com.bluemob.app.contacts.Contact
import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.data.Trip
import com.bluemob.app.settings.Spot
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything a backup holds. Attachment files travel separately, as `att/<name>` entries. */
data class BackupData(
    val createdAt: Long,
    val app: String,
    val nodeId: String,
    /** The recovery code, so a backup can bring the BlueMob ID back on a new phone. */
    val recovery: String?,
    val messages: List<MessageEntity>,
    val trips: List<Trip>,
    val trail: List<TrailPoint>,
    val calls: List<CallLogEntry>,
    val contacts: List<Contact>,
    val spots: List<Spot>,
)

/**
 * The backup file: `BMBK1 | salt (16) | iv (12) | AES-256-GCM( zip )`. The key comes from the backup password
 * (PBKDF2-SHA256, [ROUNDS] rounds), so the file is useless without it, wherever it's kept (phone, Google Drive,
 * OneDrive…). The zip holds `data.json` and the chat attachments (already encrypted with their own keys).
 */
object BackupFile {
    private val MAGIC = "BMBK1".toByteArray()
    const val ROUNDS = 150_000
    const val MIN_PASSWORD = 8
    const val MIME = "application/octet-stream"

    fun key(password: String, salt: ByteArray, rounds: Int = ROUNDS): SecretKeySpec =
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, rounds, 256)).encoded, "AES")

    /** Writes [data] and the files from [attachments] (name → file) to [out], encrypted with [password]. */
    fun write(out: OutputStream, password: String, data: BackupData, attachments: Map<String, File>, rounds: Int = ROUNDS) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt, rounds)) }
        out.write(MAGIC); out.write(salt); out.write(cipher.iv)
        ZipOutputStream(CipherOutputStream(out, cipher)).use { zip ->
            zip.setLevel(9)
            zip.putNextEntry(ZipEntry("data.json")); zip.write(toJson(data).toString().toByteArray()); zip.closeEntry()
            attachments.forEach { (name, f) ->
                if (!f.exists()) return@forEach
                zip.putNextEntry(ZipEntry("att/$name")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    sealed interface ReadResult {
        data class Ok(val data: BackupData, val attachments: Map<String, ByteArray>) : ReadResult
        data object WrongPassword : ReadResult
        data object NotABackup : ReadResult
    }

    /** Opens a backup. A wrong password (or a changed file) is reported, never half-read. */
    fun read(input: InputStream, password: String, rounds: Int = ROUNDS): ReadResult {
        val din = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        if (runCatching { din.readFully(magic) }.isFailure || !magic.contentEquals(MAGIC)) return ReadResult.NotABackup
        val salt = ByteArray(16).also { din.readFully(it) }
        val iv = ByteArray(12).also { din.readFully(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt, rounds), GCMParameterSpec(128, iv)) }
        // GCM checks the whole file before giving any of it back, so a wrong password fails here.
        val plain = runCatching { CipherInputStream(din, cipher).readBytes() }.getOrNull()
        if (plain == null || plain.isEmpty()) return ReadResult.WrongPassword
        var data: BackupData? = null
        val atts = mutableMapOf<String, ByteArray>()
        ZipInputStream(plain.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "data.json" -> data = fromJson(JSONObject(String(zip.readBytes())))
                    e.name.startsWith("att/") && !e.name.contains("..") -> atts[e.name.removePrefix("att/")] = zip.readBytes()
                }
            }
        }
        return data?.let { ReadResult.Ok(it, atts) } ?: ReadResult.NotABackup
    }

    // ---- JSON ----

    fun toJson(d: BackupData): JSONObject = JSONObject()
        .put("v", 1).put("createdAt", d.createdAt).put("app", d.app).put("nodeId", d.nodeId).put("recovery", d.recovery ?: "")
        .put("messages", JSONArray(d.messages.map { m ->
            JSONObject().put("id", m.id).put("peer", m.peer).put("fromMe", m.fromMe).put("text", m.text).put("createdAt", m.createdAt)
                .put("status", m.status.name).put("directState", m.directState.name).put("internetState", m.internetState.name)
                .put("attempts", m.attempts).put("deliveredAt", m.deliveredAt ?: -1).put("deliveredVia", m.deliveredVia ?: "")
                .put("readAt", m.readAt ?: -1).put("readReceiptSent", m.readReceiptSent).put("history", m.history).put("actions", m.actions)
                .put("att", m.att).put("attFile", m.attPath?.let { File(it).name } ?: "").put("attState", m.attState)
        }))
        .put("trips", JSONArray(d.trips.map { t -> JSONObject().put("id", t.id).put("name", t.name).put("startedAt", t.startedAt).put("endedAt", t.endedAt ?: -1).put("distanceM", t.distanceM).put("points", t.points) }))
        .put("trail", JSONArray(d.trail.map { p -> JSONArray().put(p.time).put(p.lat).put(p.lon).put(p.accuracyM.toDouble()).put(p.estimated).put(p.tripId) }))
        .put("calls", JSONArray(d.calls.map { c -> JSONObject().put("id", c.id).put("peer", c.peer).put("name", c.name).put("video", c.video).put("outgoing", c.outgoing).put("outcome", c.outcome).put("startedAt", c.startedAt).put("durationS", c.durationS) }))
        .put("contacts", JSONArray(d.contacts.map { c -> JSONObject().put("id", c.nodeId).put("name", c.name).put("avatar", c.avatar ?: "").put("lastSeen", c.lastSeen) }))
        .put("spots", JSONArray(d.spots.map { s -> JSONObject().put("id", s.id).put("name", s.name).put("lat", s.lat).put("lon", s.lon).put("time", s.time) }))

    private fun JSONObject.longOrNull(k: String) = optLong(k, -1).takeIf { it >= 0 }
    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> = if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { runCatching { f(it) }.getOrNull() } }

    fun fromJson(j: JSONObject): BackupData = BackupData(
        createdAt = j.optLong("createdAt"), app = j.optString("app"), nodeId = j.optString("nodeId"), recovery = j.optString("recovery").ifBlank { null },
        messages = j.optJSONArray("messages").mapObjects { m ->
            MessageEntity(m.getString("id"), m.getString("peer"), m.getBoolean("fromMe"), m.optString("text"), m.getLong("createdAt"),
                MessageStatus.valueOf(m.getString("status")), PathState.valueOf(m.optString("directState", "NOT_NEEDED")),
                PathState.valueOf(m.optString("internetState", "NOT_NEEDED")), m.optInt("attempts"), m.longOrNull("deliveredAt"),
                m.optString("deliveredVia").ifBlank { null }, m.longOrNull("readAt"), m.optBoolean("readReceiptSent"), m.optString("history"),
                m.optString("actions"), m.optString("att"), m.optString("attFile").ifBlank { null }, m.optInt("attState"))
        },
        trips = j.optJSONArray("trips").mapObjects { t -> Trip(t.getString("id"), t.getString("name"), t.getLong("startedAt"), t.longOrNull("endedAt"), t.optDouble("distanceM", 0.0), t.optInt("points")) },
        trail = j.optJSONArray("trail")?.let { a -> (0 until a.length()).mapNotNull { i ->
            a.optJSONArray(i)?.let { p -> runCatching { TrailPoint(0, p.getLong(0), p.getDouble(1), p.getDouble(2), p.getDouble(3).toFloat(), p.getBoolean(4), p.optString(5)) }.getOrNull() }
        } }.orEmpty(),
        calls = j.optJSONArray("calls").mapObjects { c -> CallLogEntry(c.getString("id"), c.getString("peer"), c.getString("name"), c.getBoolean("video"), c.getBoolean("outgoing"), c.getString("outcome"), c.getLong("startedAt"), c.getLong("durationS")) },
        contacts = j.optJSONArray("contacts").mapObjects { c -> Contact(c.getString("id"), c.getString("name"), c.optString("avatar").ifBlank { null }, c.optLong("lastSeen")) },
        spots = j.optJSONArray("spots").mapObjects { s -> Spot(s.getString("id"), s.getString("name"), s.getDouble("lat"), s.getDouble("lon"), s.getLong("time")) },
    )
}
