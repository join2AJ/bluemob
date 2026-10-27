package com.bluemob.app.nearby

import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A trip group shown by its admin's phone to phones nearby; joining takes the passcode the admin says out loud. */
data class TripAd(val groupId: String, val name: String, val owner: String, val ownerName: String, val people: Int, val endsAt: Long, val seenAt: Long)

/**
 * "Show this group to people nearby": the admin's phone tells each phone connected straight to it (not passed on, not
 * over the internet) the group's name, size and who runs it, when they connect and every 2 minutes. Never the
 * passcode: only the admin's phone checks it, when the join request comes.
 */
class TripAds(
    private val mesh: NearbyMeshTransport,
    private val scope: CoroutineScope,
    private val groups: () -> Collection<ChatGroup>,
    private val me: () -> String,
) {
    private val _nearby = MutableStateFlow<Map<String, TripAd>>(emptyMap())
    /** Groups shown by phones nearby, that we aren't in, newest first. */
    val nearby: StateFlow<Map<String, TripAd>> = _nearby.asStateFlow()

    init {
        scope.launch {
            mesh.events.collect { e ->
                when {
                    e is MeshEvent.PeerConnected -> tell(e.nodeId)
                    e is MeshEvent.App && e.kind == KIND && e.direct && !e.viaInternet -> onAd(e)
                }
            }
        }
        scope.launch {
            while (true) {
                delay(2 * 60_000L)
                mesh.connectedNodes().forEach { tell(it) }
                val now = System.currentTimeMillis()
                _nearby.update { m -> m.filterValues { now - it.seenAt < STALE_MS } }
            }
        }
    }

    /** Shows our groups now, to everyone connected (right after the admin turns it on). */
    fun announce() = mesh.connectedNodes().forEach { tell(it) }

    private fun shown(): List<ChatGroup> {
        val now = System.currentTimeMillis()
        return groups().filter { it.trip && !it.isTeam && it.advertise && it.passcode.isNotBlank() && it.isOwner(me()) && !it.isClosed(now) }
    }

    private fun tell(to: String) = shown().forEach { g ->
        mesh.sendApp(to, KIND, JSONObject().put("g", g.id).put("n", g.name).put("p", g.members.size + 1).put("x", g.expiresAt))
    }

    private fun onAd(e: MeshEvent.App) {
        val gid = e.body.optString("g").takeIf { com.bluemob.app.chat.GroupStore.isGroup(it) && it.length <= 40 } ?: return
        // Already in it: nothing to show.
        if (groups().any { it.id == gid }) { _nearby.update { it - gid }; return }
        val ad = TripAd(gid, e.body.optString("n").take(40).ifBlank { "Trip group" }, e.fromNodeId, e.name.take(40), e.body.optInt("p").coerceIn(1, 999),
            e.body.optLong("x").coerceAtLeast(0), System.currentTimeMillis())
        _nearby.update { it + (gid to ad) }
    }

    /** Forget a shown group (we joined it, or it's gone). */
    fun forget(gid: String) = _nearby.update { it - gid }

    companion object {
        const val KIND = "trip-ad"
        private const val STALE_MS = 10 * 60_000L
    }
}
