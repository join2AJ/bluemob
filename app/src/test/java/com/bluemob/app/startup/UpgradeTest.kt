package com.bluemob.app.startup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.robolectric.RuntimeEnvironment
import com.bluemob.app.crypto.SecurePrefs
import com.bluemob.app.data.BlueMobDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A phone that had 0.5 opens 0.6: its database (version 4) and plain settings must carry over. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /** The database exactly as 0.5 left it: schema version 4, data, and the read-only triggers on the audit table. */
    private fun write05Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        listOf(
            "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `peer` TEXT NOT NULL, `fromMe` INTEGER NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `directState` TEXT NOT NULL, `internetState` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `deliveredAt` INTEGER, `deliveredVia` TEXT, `readAt` INTEGER, `readReceiptSent` INTEGER NOT NULL, `history` TEXT NOT NULL, `actions` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_messages_peer` ON `messages` (`peer`)",
            "CREATE INDEX IF NOT EXISTS `index_messages_status` ON `messages` (`status`)",
            "CREATE TABLE IF NOT EXISTS `seen_ids` (`id` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `audit` (`seq` INTEGER NOT NULL, `time` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `prev` TEXT NOT NULL, `hash` TEXT NOT NULL, PRIMARY KEY(`seq`))",
            "CREATE TABLE IF NOT EXISTS `trail` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `accuracyM` REAL NOT NULL, `estimated` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_trail_time` ON `trail` (`time`)",
            "CREATE TABLE IF NOT EXISTS `rescue` (`id` TEXT NOT NULL, `room` TEXT NOT NULL, `fromNodeId` TEXT NOT NULL, `fromName` TEXT NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `at` INTEGER NOT NULL, `pos` TEXT, `battery` INTEGER, `local` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_rescue_room` ON `rescue` (`room`)",
            "CREATE TABLE IF NOT EXISTS `relay` (`key` TEXT NOT NULL, `toNode` TEXT NOT NULL, `origin` TEXT NOT NULL, `packet` TEXT NOT NULL, `copies` INTEGER NOT NULL, `givenTo` TEXT NOT NULL, `expiresAt` INTEGER NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'hash-of-0.5')",
            "INSERT INTO messages VALUES('m1','a1c2',1,'Meet at the stream?',1,'DELIVERED','DELIVERED','UNAVAILABLE',1,2,'Wi-Fi',NULL,0,'','')",
            "INSERT INTO audit VALUES(1,1,'APP','BlueMob started','0','h1')",
            "INSERT INTO audit VALUES(2,2,'SOS','SOS sent','h1','h2')",
            "CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit BEGIN SELECT RAISE(ABORT, 'audit trail is read-only'); END",
            "CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit BEGIN SELECT RAISE(ABORT, 'audit trail is read-only'); END",
            "PRAGMA user_version = 4",
        ).forEach(db::execSQL)
        db.close()
    }

    @Test fun databaseFrom05OpensWithItsDataAndGainsTheNewTables() = runBlocking {
        write05Database("upgrade.db")
        val db = BlueMobDatabase.create(context, encrypted = false, name = "upgrade.db")
        val audit = db.audit().observeAll().first()
        assertEquals(listOf(1L, 2L), audit.map { it.seq })
        assertTrue(audit.all { it.sig.isEmpty() }) // older entries stay unsigned, and the chain still checks
        assertEquals("Meet at the stream?", db.messages().get("m1")?.text)
        assertEquals(0, db.ratings().recent(5).size)
        db.close()
    }

    @Test fun plainSettingsFrom05AreEncryptedAndTheOldFileRemoved() {
        val plain = context.getSharedPreferences("legacy", Context.MODE_PRIVATE)
        plain.edit().putString("display_name", "Arjun").putBoolean("onboarding_done", true).putStringSet("bookmarks", setOf("burns", "cpr")).commit()
        val secure = SecurePrefs.open(context, "legacy")
        assertEquals("Arjun", secure.getString("display_name", null))
        assertTrue(secure.getBoolean("onboarding_done", false))
        assertEquals(setOf("burns", "cpr"), secure.getStringSet("bookmarks", null))
        assertTrue(context.getSharedPreferences("legacy", Context.MODE_PRIVATE).all.isEmpty())
        val onDisk = File(context.applicationInfo.dataDir, "shared_prefs/legacy.enc.xml").readText()
        assertFalse("names and values are not readable on disk", onDisk.contains("Arjun") || onDisk.contains("display_name"))
    }
}
