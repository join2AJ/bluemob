package com.bluemob.app.audit

import com.bluemob.app.data.AuditDao
import com.bluemob.app.data.AuditEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** What an audit entry is about. */
enum class AuditKind(val label: String) { APP("App"), SOS("SOS"), MESSAGE("Message"), RECEIPT("Receipt"), POSITION("Position"), MESH("Mesh") }

/**
 * A tamper-evident record of what happened on this phone: SOS calls, messages, receipts and shared positions.
 *
 * Entries can only be added. Each one stores the SHA-256 of the entry before it, so editing or removing any entry
 * breaks the chain from that point on, and [AuditChain.firstBroken] finds where.
 */
class AuditLog(private val dao: AuditDao, private val scope: CoroutineScope) {
    private val lock = Mutex()

    val entries: Flow<List<AuditEntry>> = dao.observeAll()

    fun add(kind: AuditKind, text: String) {
        scope.launch {
            lock.withLock {
                val last = dao.last()
                dao.append(AuditChain.next(last, System.currentTimeMillis(), kind.name, text))
            }
        }
    }
}

object AuditChain {
    const val GENESIS = "0"

    fun hash(prev: String, seq: Long, time: Long, kind: String, text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$prev$seq|$time|$kind|$text".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun next(last: AuditEntry?, time: Long, kind: String, text: String): AuditEntry {
        val seq = (last?.seq ?: 0) + 1
        val prev = last?.hash ?: GENESIS
        return AuditEntry(seq, time, kind, text, prev, hash(prev, seq, time, kind, text))
    }

    /** The seq of the first entry that was changed, removed or put out of order, or null if the chain is intact. */
    fun firstBroken(entries: List<AuditEntry>): Long? {
        var prev = GENESIS
        var expectedSeq = 1L
        for (e in entries.sortedBy { it.seq }) {
            if (e.seq != expectedSeq || e.prev != prev || e.hash != hash(e.prev, e.seq, e.time, e.kind, e.text)) return e.seq
            prev = e.hash
            expectedSeq++
        }
        return null
    }
}
