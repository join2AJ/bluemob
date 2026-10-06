package com.bluemob.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Where a message is. Outgoing: LOCAL (Sky) → PENDING → SENT → DELIVERED → READ. Incoming: RECEIVED → READ. */
enum class MessageStatus { LOCAL, PENDING, SENT, DELIVERED, READ, RECEIVED }

/** The state of one delivery path. */
enum class PathState { WAITING, TRYING, DELIVERED, CANCELLED, UNAVAILABLE, NOT_NEEDED }

/**
 * One chat message, kept on the phone. [id] is unique across all phones and travels with every
 * copy, so the receiver can recognise a copy it already has.
 */
@Entity(tableName = "messages", indices = [Index("peer"), Index("status")])
data class MessageEntity(
    @PrimaryKey val id: String,
    /** The other person's node ID, or "sky". */
    val peer: String,
    val fromMe: Boolean,
    val text: String,
    val createdAt: Long,
    val status: MessageStatus,
    val directState: PathState = PathState.NOT_NEEDED,
    val internetState: PathState = PathState.NOT_NEEDED,
    val attempts: Int = 0,
    val deliveredAt: Long? = null,
    val deliveredVia: String? = null,
    val readAt: Long? = null,
    /** Incoming only: whether our read receipt has reached the sender. */
    val readReceiptSent: Boolean = false,
    /** What happened to this message, one "epochMillis|text" event per line. */
    val history: String = "",
    /** Sky only: buttons under the reply, "label|target" per line. */
    val actions: String = "",
    /** A photo, document or voice note: its details as JSON ([com.bluemob.app.files.Attachment]), or empty. */
    @ColumnInfo(defaultValue = "''") val att: String = "",
    /** The file on this phone, still encrypted with the key in [att]. Null until it has arrived. */
    val attPath: String? = null,
    /** Where the file is: see [com.bluemob.app.files.AttState]. */
    @ColumnInfo(defaultValue = "0") val attState: Int = 0,
)

/** One call, for the Calls list. */
@Entity(tableName = "calls", indices = [Index("startedAt")])
data class CallLogEntry(
    @PrimaryKey val id: String,
    val peer: String,
    val name: String,
    val video: Boolean,
    val outgoing: Boolean,
    /** ANSWERED, MISSED, DECLINED, NO_ANSWER, BUSY, FAILED. */
    val outcome: String,
    val startedAt: Long,
    val durationS: Long,
)

@Dao
interface CallLogDao {
    @Query("SELECT * FROM calls ORDER BY startedAt DESC LIMIT 500")
    fun observe(): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM calls ORDER BY startedAt")
    suspend fun all(): List<CallLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallLogEntry)

    @Query("DELETE FROM calls")
    suspend fun clear()
}

/** Message IDs this phone has already accepted, so a second copy is discarded. */
@Entity(tableName = "seen_ids")
data class SeenId(@PrimaryKey val id: String, val at: Long)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY createdAt")
    suspend fun all(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE fromMe = 1 AND status IN ('PENDING', 'SENT') ORDER BY createdAt")
    suspend fun unacknowledgedAll(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE fromMe = 0 AND status = 'READ' AND readReceiptSent = 0 AND peer != 'sky'")
    suspend fun readReceiptsOwedAll(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE peer = :peer AND fromMe = 0 AND status = 'RECEIVED'")
    suspend fun unread(peer: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE peer = :peer")
    suspend fun count(peer: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun clear()

    /** Our attachments that haven't reached [peer] yet. */
    @Query("SELECT * FROM messages WHERE peer = :peer AND fromMe = 1 AND att != '' AND attState != 3")
    suspend fun filesToSend(peer: String): List<MessageEntity>

    /** The message carrying file [fid] (its details include `"fid":"…"`). */
    @Query("SELECT * FROM messages WHERE att LIKE '%\"fid\":\"' || :fid || '\"%' LIMIT 1")
    suspend fun byFile(fid: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(seen: SeenId): Long
}

/**
 * One line of the audit trail. Each entry holds the SHA-256 [hash] of the one before it ([prev]) plus its own
 * content, so changing or removing any entry breaks every hash after it.
 */
@Entity(tableName = "audit")
data class AuditEntry(
    @PrimaryKey val seq: Long,
    val time: Long,
    val kind: String,
    val text: String,
    val prev: String,
    val hash: String,
    /** ECDSA signature of [hash] by this phone's key. Empty for entries written before signing existed. */
    @androidx.room.ColumnInfo(defaultValue = "''") val sig: String = "",
)

/**
 * Append-only on purpose: there is no update or delete here, and "Delete all messages" doesn't touch this table.
 * (A database trigger also refuses changes, in case other code tries.)
 */
@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun append(entry: AuditEntry)

    @Query("SELECT * FROM audit ORDER BY seq DESC LIMIT 1")
    suspend fun last(): AuditEntry?

    @Query("SELECT * FROM audit ORDER BY seq")
    fun observeAll(): Flow<List<AuditEntry>>
}

/** A point on the user's trail: a real GPS fix, or an estimate from steps and the compass when there's no GPS. */
@Entity(tableName = "trail", indices = [Index("time")])
data class TrailPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val estimated: Boolean,
    /** The trip this point belongs to ("" for points recorded before trips existed). */
    @ColumnInfo(defaultValue = "''") val tripId: String = "",
)

/** One trip: a trail recorded from "start" to "stop", kept on the phone as long as the user wants. */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceM: Double = 0.0,
    val points: Int = 0,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY startedAt")
    suspend fun all(): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(trip: Trip)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TrailDao {
    @Query("SELECT * FROM trail WHERE tripId = :tripId ORDER BY time")
    fun observeTrip(tripId: String): Flow<List<TrailPoint>>

    @Query("SELECT * FROM trail WHERE tripId = :tripId ORDER BY time")
    suspend fun pointsOf(tripId: String): List<TrailPoint>

    @Query("SELECT * FROM trail ORDER BY time")
    suspend fun all(): List<TrailPoint>

    @Query("DELETE FROM trail WHERE tripId = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Insert
    suspend fun insert(point: TrailPoint)

    @Query("SELECT * FROM (SELECT * FROM trail ORDER BY time DESC LIMIT :limit) ORDER BY time")
    fun observeRecent(limit: Int): Flow<List<TrailPoint>>

    @Query("DELETE FROM trail")
    suspend fun clear()
}

/**
 * One line in an SOS rescue group. [room] is the SOS ID. [kind]: OPEN (the SOS itself, kept locally), JOIN, TEXT, POS,
 * ARRIVED, LEAVE, ENDED. [pos] is a PositionEstimate as JSON. [local] rows never go out over the mesh.
 */
@Entity(tableName = "rescue", indices = [Index("room")])
data class RescueMessage(
    @PrimaryKey val id: String,
    val room: String,
    val fromNodeId: String,
    val fromName: String,
    val kind: String,
    val text: String,
    val at: Long,
    val pos: String? = null,
    val battery: Int? = null,
    val local: Boolean = false,
)

@Dao
interface RescueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: RescueMessage): Long

    @Query("SELECT * FROM rescue ORDER BY at")
    fun observeAll(): Flow<List<RescueMessage>>

    @Query("SELECT * FROM rescue WHERE room = :room AND local = 0 AND at > :since ORDER BY at DESC LIMIT :limit")
    suspend fun recentShared(room: String, since: Long, limit: Int): List<RescueMessage>
}

/** A message or receipt this phone carries for other people (see MeshRouter). Encrypted and signed by its sender. */
@Entity(tableName = "relay")
data class RelayRow(
    @PrimaryKey val key: String,
    val toNode: String,
    val origin: String,
    val packet: String,
    val copies: Int,
    /** Phones already given a copy, comma-separated. */
    val givenTo: String,
    val expiresAt: Long,
    val receivedAt: Long,
)

@Dao
interface RelayDao {
    @Query("SELECT * FROM relay")
    suspend fun all(): List<RelayRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: RelayRow)

    @Query("DELETE FROM relay WHERE `key` = :key")
    suspend fun remove(key: String)
}

/** A signed star rating one person gave another (see trust/Trust.kt). [packet] is the signed original, to pass on. */
@Entity(tableName = "ratings", indices = [Index("subject")])
data class RatingRow(
    @PrimaryKey val key: String,
    val subject: String,
    val rater: String,
    val raterName: String,
    val kind: String,
    val ctx: String,
    val remark: String,
    val at: Long,
    val packet: String,
)

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: RatingRow)

    @Query("SELECT * FROM ratings WHERE `key` = :key")
    suspend fun get(key: String): RatingRow?

    @Query("SELECT * FROM ratings ORDER BY at")
    fun observeAll(): Flow<List<RatingRow>>

    @Query("SELECT * FROM ratings ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RatingRow>
}

@Database(entities = [MessageEntity::class, SeenId::class, AuditEntry::class, TrailPoint::class, RescueMessage::class, RelayRow::class, RatingRow::class, CallLogEntry::class, Trip::class], version = 7, exportSchema = false)
abstract class BlueMobDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun audit(): AuditDao
    abstract fun trail(): TrailDao
    abstract fun rescue(): RescueDao
    abstract fun relay(): RelayDao
    abstract fun ratings(): RatingDao
    abstract fun calls(): CallLogDao
    abstract fun trips(): TripDao

    companion object {
        /** Opens the database encrypted with SQLCipher (AES-256). An older plain database is encrypted first. */
        fun create(context: Context, encrypted: Boolean = true, name: String = NAME): BlueMobDatabase {
            val builder = Room.databaseBuilder(context, BlueMobDatabase::class.java, name)
            if (encrypted) {
                System.loadLibrary("sqlcipher")
                val pass = DbKey.passphrase(context)
                DbKey.encryptInPlace(context, name, pass)
                builder.openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(pass))
            }
            return builder
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = lockAudit(db)
                })
                .build()
        }

        const val NAME = "bluemob.db"

        /** Adds the audit trail and the trail of positions, keeping every message. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit` (`seq` INTEGER NOT NULL, `time` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, `prev` TEXT NOT NULL, `hash` TEXT NOT NULL, PRIMARY KEY(`seq`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `trail` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, " +
                    "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `accuracyM` REAL NOT NULL, `estimated` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trail_time` ON `trail` (`time`)")
            }
        }

        /** Adds SOS rescue groups. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rescue` (`id` TEXT NOT NULL, `room` TEXT NOT NULL, `fromNodeId` TEXT NOT NULL, `fromName` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `text` TEXT NOT NULL, `at` INTEGER NOT NULL, `pos` TEXT, `battery` INTEGER, `local` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rescue_room` ON `rescue` (`room`)")
            }
        }

        /** Adds messages carried for others. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `relay` (`key` TEXT NOT NULL, `toNode` TEXT NOT NULL, `origin` TEXT NOT NULL, `packet` TEXT NOT NULL, " +
                    "`copies` INTEGER NOT NULL, `givenTo` TEXT NOT NULL, `expiresAt` INTEGER NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        /** Audit entries get signatures, and star ratings get a table. ALTER TABLE doesn't touch existing rows, so the read-only triggers stay intact. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `audit` ADD COLUMN `sig` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ratings` (`key` TEXT NOT NULL, `subject` TEXT NOT NULL, `rater` TEXT NOT NULL, `raterName` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `ctx` TEXT NOT NULL, `remark` TEXT NOT NULL, `at` INTEGER NOT NULL, `packet` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ratings_subject` ON `ratings` (`subject`)")
            }
        }

        /** Photos, documents and voice notes in chats, and the call history. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `att` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `attPath` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `attState` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calls` (`id` TEXT NOT NULL, `peer` TEXT NOT NULL, `name` TEXT NOT NULL, `video` INTEGER NOT NULL, " +
                    "`outgoing` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationS` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_startedAt` ON `calls` (`startedAt`)")
            }
        }

        /** Trips: every trail point belongs to one, so past trips stay on the phone. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trail` ADD COLUMN `tripId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, " +
                    "`distanceM` REAL NOT NULL, `points` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                // Points recorded before trips existed become one "Earlier trail" trip.
                db.execSQL("INSERT INTO `trips` (`id`, `name`, `startedAt`, `endedAt`, `distanceM`, `points`) " +
                    "SELECT 'earlier', 'Earlier trail', mn, mx, 0, c FROM (SELECT MIN(`time`) AS mn, MAX(`time`) AS mx, COUNT(*) AS c FROM `trail`) WHERE c > 0")
                db.execSQL("UPDATE `trail` SET `tripId` = 'earlier' WHERE `tripId` = ''")
            }
        }

        /** The database itself refuses to change or delete audit entries. */
        private fun lockAudit(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit BEGIN SELECT RAISE(ABORT, 'audit trail is read-only'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit BEGIN SELECT RAISE(ABORT, 'audit trail is read-only'); END")
        }
    }
}
