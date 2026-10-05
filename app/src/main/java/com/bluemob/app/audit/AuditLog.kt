package com.bluemob.app.audit

import android.content.SharedPreferences
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.crypto.DeviceKeys
import com.bluemob.app.data.AuditDao
import com.bluemob.app.data.AuditEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.PublicKey

/** What an audit entry is about. */
enum class AuditKind(val label: String) { APP("App"), SOS("SOS"), MESSAGE("Message"), RECEIPT("Receipt"), POSITION("Position"), MESH("Mesh"), TRUST("Trust") }

/**
 * A tamper-evident record of what happened on this phone: SOS calls, messages, receipts, ratings and shared positions.
 *
 * Four layers of protection:
 * 1. Hash chain: each entry holds the SHA-256 of the one before, so changing or removing an entry breaks every later one.
 * 2. Signatures: each entry's hash is signed with this phone's private key (in the Keystore-protected identity), so even
 *    someone who edits the encrypted database and recomputes every hash can't produce valid entries.
 * 3. Checkpoint: the newest seq and hash are kept in encrypted settings, so deleting entries from the end is caught.
 * 4. Witnesses: phones nearby keep a signed copy of our newest entry and hand it back later ([AuditWitness]); if our
 *    chain no longer matches what they hold, tampering is caught from outside this phone.
 * The database itself refuses UPDATE and DELETE on this table, and the whole database is encrypted with SQLCipher.
 */
class AuditLog(
    private val dao: AuditDao,
    private val scope: CoroutineScope,
    private val keys: DeviceKeys,
    private val prefs: SharedPreferences,
) {
    private val lock = Mutex()

    val entries: Flow<List<AuditEntry>> = dao.observeAll()

    /** The newest seq and hash we ever wrote, from encrypted settings. */
    val checkpoint: Pair<Long, String>?
        get() = prefs.getString(KEY_HEAD, null)?.split("|")?.takeIf { it.size == 2 }?.let { it[0].toLong() to it[1] }

    val publicKey: PublicKey get() = keys.keyPair.public

    fun add(kind: AuditKind, text: String) {
        scope.launch {
            lock.withLock {
                val last = dao.last()
                val e = AuditChain.next(last, System.currentTimeMillis(), kind.name, text) { Crypto.encode(keys.sign(it.toByteArray())) }
                dao.append(e)
                prefs.edit().putString(KEY_HEAD, "${e.seq}|${e.hash}").apply()
            }
        }
    }

    fun verify(entries: List<AuditEntry>): AuditVerification = AuditChain.verify(entries, keys.keyPair.public, checkpoint)

    private companion object {
        const val KEY_HEAD = "audit_head"
    }
}

/** What a check of the whole trail found. Null fields mean "fine". */
data class AuditVerification(
    val count: Int,
    /** First entry whose hash or link to the previous entry doesn't add up. */
    val brokenAt: Long? = null,
    /** First entry whose signature doesn't match this phone's key. */
    val badSignatureAt: Long? = null,
    /** Entries after this seq were removed (the checkpoint says there were more). */
    val missingAfter: Long? = null,
    /** Entries written before signing existed (older versions). Still chain-checked. */
    val unsigned: Int = 0,
) {
    val ok: Boolean get() = brokenAt == null && badSignatureAt == null && missingAfter == null
}

object AuditChain {
    const val GENESIS = "0"

    fun hash(prev: String, seq: Long, time: Long, kind: String, text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$prev$seq|$time|$kind|$text".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun next(last: AuditEntry?, time: Long, kind: String, text: String, sign: (String) -> String = { "" }): AuditEntry {
        val seq = (last?.seq ?: 0) + 1
        val prev = last?.hash ?: GENESIS
        val h = hash(prev, seq, time, kind, text)
        return AuditEntry(seq, time, kind, text, prev, h, sign(h))
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

    /** Checks the chain, every signature against [key], and that nothing after the [checkpoint] went missing. */
    fun verify(entries: List<AuditEntry>, key: PublicKey, checkpoint: Pair<Long, String>?): AuditVerification {
        val sorted = entries.sortedBy { it.seq }
        val broken = firstBroken(sorted)
        var signedSeen = false
        var unsigned = 0
        var badSig: Long? = null
        for (e in sorted) {
            if (e.sig.isEmpty()) {
                // Unsigned entries are only accepted before the first signed one (from versions without signing).
                if (signedSeen && badSig == null) badSig = e.seq else unsigned++
                continue
            }
            signedSeen = true
            val sig = Crypto.decode(e.sig)
            if (badSig == null && (sig == null || !Crypto.verify(key, e.hash.toByteArray(), sig))) badSig = e.seq
        }
        val missing = checkpoint?.let { (seq, h) ->
            val atSeq = sorted.firstOrNull { it.seq == seq }
            if (atSeq == null || atSeq.hash != h) (sorted.lastOrNull()?.seq ?: 0) else null
        }
        return AuditVerification(sorted.size, broken, badSig, missing, unsigned)
    }

    /** Whether our trail holds entry [seq] with exactly [hash]: used to check what a witness phone handed back. */
    fun matches(entries: List<AuditEntry>, seq: Long, hash: String) = entries.any { it.seq == seq && it.hash == hash }
}
