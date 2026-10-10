package com.bluemob.app.rescue

import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditLog
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.RescueDao
import com.bluemob.app.data.RescueMessage
import com.bluemob.app.identity.Identity
import com.bluemob.app.location.LocationTracker
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import com.bluemob.app.mesh.RoomPayload
import com.bluemob.app.mesh.SosSignal
import com.bluemob.app.sos.SosManager
import com.bluemob.app.trail.PosCodec
import com.bluemob.app.trail.PositionEstimate
import com.bluemob.app.trail.TrailRecorder
import com.bluemob.app.util.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Something worth a banner: a helper joined, arrived, wrote, or the SOS ended. */
data class RescueNotice(val room: String, val text: String)

/**
 * Rescue groups: when someone taps "I'm coming" on an SOS, they join that SOS's group. Everyone in it (the person who
 * needs help and every helper) sees who's coming, how far away they are, and a shared chat.
 *
 * Group messages travel the mesh like an SOS (every phone passes them on, up to 5 hops), and are sent again to each
 * phone that connects, so people who were out of range catch up. Helpers and the person in need share their position
 * every 45 seconds while it matters.
 */
private const val RESEND_MS = 5 * 60_000L
private const val STALE_MS = 24 * 3_600_000L

class RescueManager(
    private val mesh: NearbyMeshTransport,
    private val identity: Identity,
    private val dao: RescueDao,
    private val trail: TrailRecorder,
    private val location: LocationTracker,
    private val sos: SosManager,
    private val audit: AuditLog,
    private val scope: CoroutineScope,
) {
    val rooms: StateFlow<List<RescueRoom>> = dao.observeAll()
        .map { RescueRoom.build(it, identity.nodeId, PosCodec::decode) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _notices = MutableSharedFlow<RescueNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<RescueNotice> = _notices.asSharedFlow()

    /** Position-sharing loops, by room. */
    private val sharing = mutableMapOf<String, Job>()

    init {
        scope.launch {
            mesh.events.collect { e ->
                when (e) {
                    is MeshEvent.RoomReceived -> receive(e.msg)
                    is MeshEvent.PeerConnected -> catchUp(e.nodeId)
                    else -> Unit
                }
            }
        }
        scope.launch { sos.incoming.collect { onSos(it) } }
        scope.launch {
            var previous: SosSignal? = null
            sos.mine.collect { mine ->
                if (mine != null && previous?.id != mine.id) onSos(mine)
                val before = previous
                if (mine == null && before != null) {
                    post(before.id, RescueRoom.ENDED, "${identity.displayName.value} is safe now. Thank you for coming! 🙏")
                }
                previous = mine
            }
        }
        scope.launch { rooms.collect { reconcile(it) } }
        // The internet only carries group messages to phones that are online at that moment. So every few minutes, our
        // own recent messages go again to members who aren't nearby (duplicates are dropped on arrival): an "I'm
        // coming" or "I'm safe" sent while they were offline still reaches them. Rescues with no news for a day close.
        scope.launch {
            delay(30_000)
            while (true) {
                runCatching { resendOnline(); closeStale() }
                delay(RESEND_MS)
            }
        }
    }

    private suspend fun resendOnline() {
        val now = System.currentTimeMillis()
        rooms.value.filter { r -> r.iAmIn && (!r.ended || r.chat.lastOrNull()?.let { now - it.at < 6 * 3_600_000L } == true) && now - r.startedAt < 2 * 86_400_000L }
            .forEach { r -> members(r).filter { !mesh.isConnected(it) }.forEach { catchUpOnline(it, r.id) } }
    }

    /** A rescue we're helping with that has had no news from the person for a day: closed here, with a note why. */
    private suspend fun closeStale() {
        val now = System.currentTimeMillis()
        rooms.value.filter { !it.mine && !it.ended && now - it.lastNews > STALE_MS }.forEach { r ->
            dao.insert(RescueMessage("end-" + r.id, r.id, r.victimId, r.victimName, RescueRoom.ENDED,
                "No news from ${r.victimName} for a day, so this rescue was closed here. Message them to check they're OK.", now, local = true))
        }
    }

    fun room(id: String): RescueRoom? = rooms.value.firstOrNull { it.id == id }

    /**
     * People outside radio range who belong in a group, so its messages also go to them over the internet: set by the
     * app (for our own SOS, our SOS contacts).
     */
    var remoteMembers: (RescueRoom) -> Collection<String> = { emptySet() }

    /** Everyone in a group but us: the person in need, the helpers, and [remoteMembers]. */
    private fun members(r: RescueRoom): Set<String> =
        (listOf(r.victimId) + r.helpers.filter { it.status != HelperStatus.LEFT }.map { it.nodeId } + remoteMembers(r)).toSet() - identity.nodeId

    /** A line in the group that only this phone shows ("Asha got your SOS"). */
    fun note(roomId: String, text: String) {
        scope.launch { dao.insert(RescueMessage("note-" + UUID.randomUUID().toString().take(12), roomId, "", "", RescueRoom.NOTE, text, System.currentTimeMillis(), local = true)) }
    }

    /** Last catch-up sent to each person over the internet, so it isn't repeated every message. */
    private val caughtUp = mutableMapOf<String, Long>()

    /** "I'm coming": joins the group for this SOS and starts sharing our position with it. */
    fun join(roomId: String) {
        val r = room(roomId) ?: return
        if (r.myStatus == HelperStatus.COMING) return
        val here = trail.snapshot()
        val away = here?.let { h -> r.victimPos?.let { v -> Geo.distanceM(GeoPoint(h.lat, h.lon, 0f, 0), GeoPoint(v.lat, v.lon, 0f, 0)) } }
        post(roomId, RescueRoom.JOIN, "I'm coming" + (away?.let { " · ${Geo.formatDistance(it)} away, about ${RescueRoom.walkMinutes(it)} min" } ?: ""), here)
        audit.add(AuditKind.SOS, "Joined the rescue for ${r.victimName}: \"I'm coming\"")
    }

    fun send(roomId: String, text: String) {
        val clean = text.trim().take(300)
        if (clean.isNotEmpty()) post(roomId, RescueRoom.TEXT, clean)
    }

    fun arrived(roomId: String) {
        post(roomId, RescueRoom.ARRIVED, "I'm here", trail.snapshot())
        audit.add(AuditKind.SOS, "Arrived at the rescue for ${room(roomId)?.victimName ?: "someone"}")
    }

    fun leave(roomId: String) {
        post(roomId, RescueRoom.LEAVE, "I can't come after all")
        audit.add(AuditKind.SOS, "Left the rescue for ${room(roomId)?.victimName ?: "someone"}")
    }

    /** Keeps the SOS as the start of its group, so the group shows who it's for. */
    private fun onSos(s: SosSignal) {
        if (s.id == SosManager.PREVIEW_ID) return
        scope.launch {
            if (s.cancelled) {
                dao.insert(RescueMessage("end-" + s.id, s.id, s.fromNodeId, s.name, RescueRoom.ENDED, "${s.name} is safe now", s.at, local = true))
                return@launch
            }
            dao.insert(RescueMessage("open-" + s.id, s.id, s.fromNodeId, s.name, RescueRoom.OPEN, s.note, s.at, PosCodec.encode(s.pos), s.battery, local = true))
        }
    }

    private fun post(room: String, kind: String, text: String, pos: PositionEstimate? = null) {
        val m = RoomPayload("r-" + UUID.randomUUID().toString().take(16), room, identity.nodeId, identity.displayName.value, kind, text, System.currentTimeMillis(), pos)
        scope.launch { dao.insert(m.toRow(battery = if (room(room)?.mine == true) sos.batteryPct() else null)) }
        mesh.broadcastRoom(m)
        // Members who aren't nearby get it over the internet.
        room(room)?.let { r -> members(r).filter { !mesh.isConnected(it) }.forEach { mesh.sendRoomOnline(it, m) } }
    }

    private suspend fun receive(m: RoomPayload) {
        val isNew = dao.insert(m.toRow()) != -1L
        if (!isNew) return
        val r = room(m.room)
        // Someone far away in the group just spoke: send them what they may have missed (at most every 2 minutes).
        if (r != null && m.fromNodeId != identity.nodeId && !mesh.isConnected(m.fromNodeId) &&
            System.currentTimeMillis() - (caughtUp[m.fromNodeId] ?: 0L) > 120_000) {
            caughtUp[m.fromNodeId] = System.currentTimeMillis()
            catchUpOnline(m.fromNodeId, r.id)
        }
        if (m.kind == RescueRoom.TEXT) audit.add(AuditKind.MESSAGE, "Rescue group for ${r?.victimName ?: "an SOS"}: ${m.fromName}: \"${m.text.take(80)}\"")
        if (r == null || !r.iAmIn) return
        if (r.ended && m.kind != RescueRoom.ENDED) return
        val notice = when (m.kind) {
            RescueRoom.JOIN -> "${m.fromName} is coming to help" + (m.text.substringAfter(" · ", "").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            RescueRoom.ARRIVED -> "${m.fromName} has arrived"
            RescueRoom.LEAVE -> "${m.fromName} can't come after all"
            RescueRoom.TEXT -> "${m.fromName}: ${m.text}"
            RescueRoom.ENDED -> m.text
            else -> null
        }
        notice?.let { _notices.tryEmit(RescueNotice(m.room, it)) }
        // Our own SOS: say plainly what the helper already knows about us. It gives hope, and says what to do next.
        if (r.mine && m.kind == RescueRoom.JOIN) {
            val mine = sos.mine.value
            note(m.room, "🏃 ${m.fromName} is on the way. They have your note, battery and blood group" +
                (if (mine?.pos != null) ", and your position (±${mine.pos.uncertaintyM.toInt()} m). Stay where you are if you can." else ". Your position isn't known yet: turn on location, or tell them where you are."))
        }
    }

    /** Our own recent messages in a group, to someone far away. (We can only re-send what we signed ourselves.) */
    private suspend fun catchUpOnline(nodeId: String, roomId: String) {
        val since = System.currentTimeMillis() - 6 * 3_600_000L
        dao.recentShared(roomId, since, 30).asReversed().filter { it.fromNodeId == identity.nodeId }.forEach { row ->
            mesh.sendRoomOnline(nodeId, RoomPayload(row.id, row.room, row.fromNodeId, row.fromName, row.kind, row.text, row.at, PosCodec.decode(row.pos)))
        }
    }

    /** A phone just connected: send it what's happened in open groups, so it catches up. Duplicates are dropped on arrival. */
    private suspend fun catchUp(nodeId: String) {
        val since = System.currentTimeMillis() - 6 * 3_600_000L
        rooms.value.filter { !it.ended || it.startedAt > since }.forEach { r ->
            dao.recentShared(r.id, since, 60).asReversed().forEach { row ->
                mesh.sendRoom(nodeId, RoomPayload(row.id, row.room, row.fromNodeId, row.fromName, row.kind, row.text, row.at, PosCodec.decode(row.pos)))
            }
        }
    }

    /** Shares our position while we're on our way to someone, or while our own SOS is active. */
    private fun reconcile(all: List<RescueRoom>) {
        val want = all.filter { r -> !r.ended && (r.myStatus == HelperStatus.COMING || (r.mine && sos.mine.value?.id == r.id)) }.map { it.id }.toSet()
        (sharing.keys - want).forEach { id -> sharing.remove(id)?.cancel(); location.release(keepForSharing = identity.shareLocation.value) }
        (want - sharing.keys).forEach { id ->
            location.hold()
            sharing[id] = scope.launch {
                var last: PositionEstimate? = null
                var told = false
                var first = true
                while (true) {
                    // The first position goes out as soon as there is one; then about every 45 s.
                    if (!first) delay(45_000)
                    first = false
                    val here = trail.snapshot() ?: run { delay(5_000); first = true; null } ?: continue
                    // Say once that our position is going out: the person in need knows help can find them, and a
                    // helper knows they can be followed.
                    if (!told) {
                        told = true
                        room(id)?.let { r ->
                            val who = (r.helpers.filter { it.nodeId != identity.nodeId }.map { it.name } + (if (!r.mine) listOf(r.victimName) else emptyList())).distinct()
                            note(id, "📍 Your position (±${here.uncertaintyM.toInt()} m) is now shared with " + (if (who.isEmpty()) "the group" else who.joinToString()) + ", and updated as you move.")
                        }
                    }
                    val moved = last?.let { Geo.distanceM(GeoPoint(it.lat, it.lon, 0f, 0), GeoPoint(here.lat, here.lon, 0f, 0)) } ?: Double.MAX_VALUE
                    if (moved >= 10 || here.at - (last?.at ?: 0) >= 3 * 60_000) {
                        post(id, RescueRoom.POS, "", here)
                        last = here
                    }
                }
            }
        }
    }

    private fun RoomPayload.toRow(battery: Int? = null) = RescueMessage(id, room, fromNodeId, fromName, kind, text, at, PosCodec.encode(pos), battery)
}
