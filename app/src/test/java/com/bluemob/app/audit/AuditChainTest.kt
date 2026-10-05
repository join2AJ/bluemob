package com.bluemob.app.audit

import com.bluemob.app.data.AuditEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
