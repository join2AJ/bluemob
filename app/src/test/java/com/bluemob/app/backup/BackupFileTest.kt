package com.bluemob.app.backup

import com.bluemob.app.contacts.Contact
import com.bluemob.app.data.CallLogEntry
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.data.Trip
import com.bluemob.app.settings.Spot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class BackupFileTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rounds = 1_000 // fast for tests; the app uses BackupFile.ROUNDS

    private val data = BackupData(
        createdAt = 1_700_000_000_000, app = "0.11.0", nodeId = "3f9a1c2b7d4e8a01", recovery = "7KQ2-M9XA-T4PB-0RCE-W3HD-NF6Y-JS8G",
        messages = listOf(
            MessageEntity("m1", "a1c2", true, "Meet at the stream?", 10, MessageStatus.READ, history = "10|Written"),
            MessageEntity("m2", "a1c2", false, "", 20, MessageStatus.RECEIVED, att = """{"fid":"f-abc123def456","name":"map.pdf"}""", attPath = "/data/x/f-abc123def456.bin", attState = 3),
        ),
        trips = listOf(Trip("t-1", "Kedarkantha day 1", 100, 200, 5_400.0, 2)),
        trail = listOf(TrailPoint(0, 110, 30.08, 78.26, 5f, false, "t-1"), TrailPoint(0, 120, 30.09, 78.27, 40f, true, "t-1")),
        calls = listOf(CallLogEntry("c-1", "a1c2", "Asha", true, false, "MISSED", 300, 0)),
        contacts = listOf(Contact("a1c2", "Asha", "🦋", 400)),
        spots = listOf(Spot("base", "Base camp", 30.08, 78.26, 500)),
    )

    @Test fun everythingComesBackWithTheRightPassword() {
        val att = tmp.newFile("f-abc123def456.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val out = ByteArrayOutputStream()
        BackupFile.write(out, "correct horse", data, mapOf(att.name to att), rounds)
        val r = BackupFile.read(out.toByteArray().inputStream(), "correct horse", rounds) as BackupFile.ReadResult.Ok
        assertEquals(data.trips, r.data.trips)
        assertEquals(data.calls, r.data.calls)
        assertEquals(data.contacts, r.data.contacts)
        assertEquals(data.spots, r.data.spots)
        assertEquals(data.recovery, r.data.recovery)
        assertEquals(2, r.data.trail.size)
        assertEquals("t-1", r.data.trail[1].tripId)
        assertTrue(r.data.trail[1].estimated)
        assertEquals("Meet at the stream?", r.data.messages[0].text)
        assertEquals("f-abc123def456.bin", r.data.messages[1].attPath) // just the file name: the restore puts it in place
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), r.attachments["f-abc123def456.bin"])
    }

    @Test fun wrongPasswordAndOtherFilesAreRefused() {
        val out = ByteArrayOutputStream()
        BackupFile.write(out, "correct horse", data, emptyMap(), rounds)
        assertEquals(BackupFile.ReadResult.WrongPassword, BackupFile.read(out.toByteArray().inputStream(), "wrong horse", rounds))
        assertEquals(BackupFile.ReadResult.NotABackup, BackupFile.read("hello".toByteArray().inputStream(), "x", rounds))
        // A changed byte anywhere is caught by GCM.
        val bytes = out.toByteArray(); bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertEquals(BackupFile.ReadResult.WrongPassword, BackupFile.read(bytes.inputStream(), "correct horse", rounds))
    }

    @Test fun nothingReadableWithoutThePassword() {
        val out = ByteArrayOutputStream()
        BackupFile.write(out, "correct horse", data, emptyMap(), rounds)
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertFalse(text.contains("stream"))
        assertFalse(text.contains("Kedarkantha"))
        assertFalse(text.contains("data.json"))
    }
}
