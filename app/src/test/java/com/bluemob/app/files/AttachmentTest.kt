package com.bluemob.app.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentTest {
    private val sample = Attachment("f-abc123def456", "Trail map.pdf", "application/pdf", 1_234_567, "a2V5", AttKind.DOC)

    @Test fun detailsRoundTripThroughTheMessage() {
        assertEquals(sample, Attachment.fromJson(sample.toJson()))
        val voice = sample.copy(kind = AttKind.AUDIO, durationMs = 12_300, name = "Voice note.m4a", mime = "audio/mp4")
        assertEquals(voice, Attachment.fromJson(voice.toJson()))
    }

    @Test fun olderVersionsGetReadableText() {
        val t = sample.fallbackText()
        assertTrue(t, t.startsWith("📄 Trail map.pdf (1.2 MB)"))
        assertTrue(t.contains("Update BlueMob"))
    }

    @Test fun namesFromOtherPhonesCantEscapeTheFolder() {
        assertEquals("passwd", Attachment.safeName("../../etc/passwd"))
        assertEquals("evil.sh", Attachment.safeName("C:\\\\evil.sh"))
        assertEquals("a_b.txt", Attachment.safeName("a<b.txt"))
        assertEquals("file", Attachment.safeName(""))
    }

    @Test fun badDetailsAreRejected() {
        assertNull(Attachment.fromJson("{}"))
        assertNull(Attachment.fromJson("not json"))
        assertNull(Attachment.fromJson("""{"fid":"../x","key":"k"}"""))
        assertNull(Attachment.fromJson(""))
    }

    @Test fun kindsFromMimeTypes() {
        assertEquals(AttKind.IMAGE, AttKind.forMime("image/jpeg"))
        assertEquals(AttKind.VIDEO, AttKind.forMime("video/mp4"))
        assertEquals(AttKind.AUDIO, AttKind.forMime("audio/mp4"))
        assertEquals(AttKind.DOC, AttKind.forMime("application/pdf"))
        assertEquals(AttKind.DOC, AttKind.forMime(null))
    }

    @Test fun filesAreEncryptedAndTamperingIsCaught() {
        val key = FileCrypto.newKey()
        val plain = "medical report".toByteArray()
        val sealed = FileCrypto.encrypt(key, plain)
        assertFalse(String(sealed).contains("medical"))
        assertArrayEquals(plain, FileCrypto.decrypt(key, sealed))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(FileCrypto.decrypt(key, sealed))
        assertNull(FileCrypto.decrypt(FileCrypto.newKey(), FileCrypto.encrypt(key, plain)))
    }

    @Test fun sizesAndDurationsReadWell() {
        assertEquals("900 B", Attachment.sizeText(900))
        assertEquals("240 KB", Attachment.sizeText(240 * 1024))
        assertEquals("0:42", Attachment.durationText(42_000))
        assertEquals("2:05", Attachment.durationText(125_000))
    }
}
