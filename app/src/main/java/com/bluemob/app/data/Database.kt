package com.bluemob.app.data

import android.content.Context
import androidx.room.Dao
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
)

/** Message IDs this phone has already accepted, so a second copy is discarded. */
@Entity(tableName = "seen_ids")
data class SeenId(@PrimaryKey val id: String, val at: Long)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE peer = :peer AND fromMe = 1 AND status IN ('PENDING', 'SENT') ORDER BY createdAt")
    suspend fun unacknowledged(peer: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE peer = :peer AND fromMe = 0 AND status = 'RECEIVED'")
    suspend fun unread(peer: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE peer = :peer AND fromMe = 0 AND status = 'READ' AND readReceiptSent = 0")
    suspend fun readReceiptsOwed(peer: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE peer = :peer")
    suspend fun count(peer: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(seen: SeenId): Long
}

@Database(entities = [MessageEntity::class, SeenId::class], version = 1, exportSchema = false)
abstract class BlueMobDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao

    companion object {
        fun create(context: Context): BlueMobDatabase =
            Room.databaseBuilder(context, BlueMobDatabase::class.java, "bluemob.db").build()
    }
}
