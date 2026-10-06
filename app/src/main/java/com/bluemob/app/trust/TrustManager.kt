package com.bluemob.app.trust

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.bridge.BridgeClient
import com.bluemob.app.contacts.ContactsStore
import com.bluemob.app.data.RatingDao
import com.bluemob.app.data.RatingRow
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.Envelope
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Star ratings, spread like everything else in BlueMob: each rating is signed by the person who gave it, stored on
 * every phone that hears it, passed on over the mesh, and kept on the relay when someone has internet. Nobody can
 * forge or change a rating, and anyone can check it. Scores are worked out on each phone by [Trust].
 */
class TrustManager(
    private val dao: RatingDao,
    private val identity: Identity,
    private val mesh: NearbyMeshTransport,
    private val bridge: BridgeClient,
    private val contacts: ContactsStore,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
) {
    val ratings: StateFlow<List<Rating>> = dao.observeAll()
        .map { rows -> rows.mapNotNull { r -> RatingKind.of(r.kind)?.let { Rating(r.rater, r.raterName, r.subject, it, r.ctx, r.remark, r.at) } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Scores for everyone we have ratings about. Use [scoreFor] for anyone. */
    val scores: StateFlow<Map<String, TrustScore>> = combine(ratings, contacts.contacts) { all, people ->
        val now = System.currentTimeMillis()
        all.map { it.subject }.distinct().associateWith { id -> Trust.score(id, all, now) { rater -> weight(rater, people.keys) } }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Someone rated us: shown as a banner or notification. */
    private val _aboutMe = MutableSharedFlow<Rating>(extraBufferCapacity = 8)
    val aboutMe: SharedFlow<Rating> = _aboutMe.asSharedFlow()

    fun scoreFor(id: String): TrustScore = scores.value[id] ?: Trust.score(id, emptyList(), System.currentTimeMillis())

    init {
        scope.launch {
            mesh.events.collect { e -> if (e is MeshEvent.Extra && e.type == NearbyMeshTransport.TYPE_RATE) receive(e.json) }
        }
        bridge.onRatings = { list -> list.forEach { receive(it) } }
    }

    /** Gives a rating. One per person, kind and SOS/message: rating again replaces it. */
    fun rate(subject: String, kind: RatingKind, ctx: String, remark: String) {
        if (subject == identity.nodeId) return
        // You can't both thank someone and call their SOS fake.
        val mine = ratings.value.filter { it.rater == identity.nodeId && it.subject == subject }.map { it.kind }
        if (kind == RatingKind.FAKE_SOS && (RatingKind.THANKS in mine || RatingKind.GENUINE_SOS in mine)) return
        if ((kind == RatingKind.THANKS || kind == RatingKind.GENUINE_SOS) && RatingKind.FAKE_SOS in mine) return
        val body = JSONObject().put("subject", subject).put("kind", kind.code).put("ctx", ctx.take(64))
            .put("remark", remark.trim().take(MAX_REMARK)).put("at", System.currentTimeMillis()).put("name", identity.displayName.value)
        val json = Envelope.seal(NearbyMeshTransport.TYPE_RATE, body, identity.keys)
        scope.launch { store(json) }
        mesh.broadcastRate(json, listOf(subject, kind.code, ctx, body.optLong("at")).joinToString("|"))
        bridge.enqueue("rate:" + keyOf(identity.nodeId, subject, kind.code, ctx), json.toString())
        val name = contacts.contacts.value[subject]?.name ?: "someone"
        audit.add(AuditKind.TRUST, "You rated $name: ${kind.label}" + if (remark.isNotBlank()) " · \"${remark.take(MAX_REMARK)}\"" else "")
    }

    /** Signed ratings to give a phone that just connected, so ratings spread as people meet. */
    suspend fun packetsToShare(): List<JSONObject> = dao.recent(SHARE_ON_CONNECT).mapNotNull { runCatching { JSONObject(it.packet) }.getOrNull() }

    fun receive(json: JSONObject) { scope.launch { store(json) } }

    private suspend fun store(json: JSONObject) {
        val o = Envelope.open(json) ?: return
        if (o.type != NearbyMeshTransport.TYPE_RATE) return
        val b = o.body
        val kind = RatingKind.of(b.optString("kind")) ?: return
        val subject = b.optString("subject")
        if (subject.length != 16 || subject == o.from) return
        val at = b.optLong("at")
        if (at > System.currentTimeMillis() + 10 * 60_000) return // dated in the future: refuse
        val ctx = b.optString("ctx").take(64)
        val key = keyOf(o.from, subject, kind.code, ctx)
        val existing = dao.get(key)
        if (existing != null && existing.at >= at) return
        dao.put(RatingRow(key, subject, o.from, b.optString("name").take(24).ifBlank { "Someone" }, kind.code, ctx, b.optString("remark").take(MAX_REMARK), at, json.toString()))
        if (existing == null && subject == identity.nodeId) {
            val r = Rating(o.from, b.optString("name").take(24), subject, kind, ctx, b.optString("remark").take(MAX_REMARK), at)
            _aboutMe.tryEmit(r)
            audit.add(AuditKind.TRUST, "${r.raterName} rated you: ${kind.label}" + if (r.remark.isNotBlank()) " · \"${r.remark}\"" else "")
        }
    }

    /** People we've met in person count fully; strangers (whose keys cost nothing to make) count half. */
    private fun weight(rater: String, known: Set<String>): Double =
        if (rater == identity.nodeId || (rater in known && (contacts.contacts.value[rater]?.lastSeen ?: 0L) > 0)) 1.0 else 0.5

    private fun keyOf(rater: String, subject: String, kind: String, ctx: String) = listOf(rater, subject, kind, ctx).joinToString("|")

    companion object {
        const val MAX_REMARK = 140
        const val SHARE_ON_CONNECT = 60
    }
}
