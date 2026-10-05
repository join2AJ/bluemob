package com.bluemob.app.audit

import com.bluemob.app.data.AuditEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys

class AuditChainTest {
    private fun chain(n: Int): List<AuditEntry> {
        val out = mutableListOf<AuditEntry>()
        repeat(n) { i -> out += AuditChain.next(out.lastOrNull(), 1_000L + i, "SOS", "entry $i") }
        return out
    }

    @Test fun intactChainVerifies() {
        val c = chain(6)
        assertNull(AuditChain.firstBroken(c))
        assertEquals(64, c.last().hash.length)
        assertEquals(c[2].hash, c[3].prev)
    }

    @Test fun editedTextIsCaught() {
        val c = chain(6).toMutableList()
        c[2] = c[2].copy(text = "nothing happened")
        assertEquals(3L, AuditChain.firstBroken(c))
    }

    @Test fun editedTextWithRecomputedHashStillBreaksTheNextEntry() {
        val c = chain(6).toMutableList()
        val e = c[2].copy(text = "nothing happened")
        c[2] = e.copy(hash = AuditChain.hash(e.prev, e.seq, e.time, e.kind, e.text))
        assertEquals(4L, AuditChain.firstBroken(c))
    }

    @Test fun removedEntryIsCaught() {
        val c = chain(6).toMutableList()
        c.removeAt(1)
        assertEquals(3L, AuditChain.firstBroken(c))
    }

    @Test fun changedTimeIsCaught() {
        val c = chain(4).toMutableList()
        c[0] = c[0].copy(time = 5)
        assertEquals(1L, AuditChain.firstBroken(c))
    }

    private val phone = DeviceKeys(Crypto.generate())
    private fun signed(n: Int): List<AuditEntry> {
        val out = mutableListOf<AuditEntry>()
        repeat(n) { i -> out += AuditChain.next(out.lastOrNull(), 2_000L + i, "SOS", "entry $i") { Crypto.encode(phone.sign(it.toByteArray())) } }
        return out
    }

    @Test fun signedTrailVerifies() {
        val c = signed(5)
        val v = AuditChain.verify(c, phone.keyPair.public, c.last().seq to c.last().hash)
        assertTrue(v.ok)
        assertEquals(5, v.count)
    }

    @Test fun recomputingEveryHashStillFailsTheSignatureCheck() {
        // An attacker rewrites entry 3 and recomputes all later hashes, so the chain itself looks intact.
        val c = signed(5).toMutableList()
        val attacker = DeviceKeys(Crypto.generate())
        var prev = c[1]
        for (i in 2 until c.size) {
            val text = if (i == 2) "nothing happened" else c[i].text
            c[i] = AuditChain.next(prev, c[i].time, c[i].kind, text) { Crypto.encode(attacker.sign(it.toByteArray())) }
            prev = c[i]
        }
        assertNull("the chain alone can be faked", AuditChain.firstBroken(c))
        assertEquals(3L, AuditChain.verify(c, phone.keyPair.public, null).badSignatureAt)
    }

    @Test fun deletingFromTheEndIsCaughtByTheCheckpoint() {
        val c = signed(6)
        val v = AuditChain.verify(c.dropLast(2), phone.keyPair.public, c.last().seq to c.last().hash)
        assertEquals(4L, v.missingAfter)
    }

    @Test fun anUnsignedEntryAfterSignedOnesIsRejected() {
        val c = signed(3).toMutableList()
        c += AuditChain.next(c.last(), 9_000, "SOS", "sneaked in")
        assertEquals(4L, AuditChain.verify(c, phone.keyPair.public, null).badSignatureAt)
    }

    @Test fun olderUnsignedEntriesAreAcceptedBeforeSigningStarted() {
        val old = chain(2).toMutableList()
        old += AuditChain.next(old.last(), 5_000, "APP", "updated") { Crypto.encode(phone.sign(it.toByteArray())) }
        val v = AuditChain.verify(old, phone.keyPair.public, null)
        assertTrue(v.ok)
        assertEquals(2, v.unsigned)
    }
}
