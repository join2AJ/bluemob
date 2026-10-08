package com.bluemob.app.nearby

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

/** Someone's battery, as their phone last told us. */
data class Battery(val pct: Int, val charging: Boolean, val at: Long)

/** One "Check on everyone": who answered what. */
data class CheckIn(val id: String, val at: Long, val asked: Set<String>, val answers: Map<String, String>) {
    val ok get() = answers.count { it.value == OK }
    val help get() = answers.filterValues { it == HELP }.keys
    val waiting get() = asked - answers.keys
    companion object { const val OK = "ok"; const val HELP = "help" }
}

/** Someone checking on us: answer "I'm OK" or "I need help". */
data class CheckRequest(val from: String, val name: String, val id: String)

/**
 * Small status between phones nearby: each phone tells the others its battery when they connect and every few
 * minutes, so the Nearby tab can show who's running low. Also "Check on everyone": one tap asks every phone nearby
 * "Are you OK?", and the answers come back as they tap.
 */
class PeerStatus(
    private val mesh: NearbyMeshTransport,
    private val scope: CoroutineScope,
    private val battery: () -> Pair<Int, Boolean>?,
    private val onRequest: (CheckRequest) -> Unit = {},
) {
    private val _batteries = MutableStateFlow<Map<String, Battery>>(emptyMap())
    val batteries: StateFlow<Map<String, Battery>> = _batteries.asStateFlow()

    private val _check = MutableStateFlow<CheckIn?>(null)
    /** Our latest check-in and its answers. */
    val check: StateFlow<CheckIn?> = _check.asStateFlow()

    private val _requests = MutableStateFlow<List<CheckRequest>>(emptyList())
    /** People checking on us, waiting for an answer. */
    val requests: StateFlow<List<CheckRequest>> = _requests.asStateFlow()

    init {
        scope.launch {
            mesh.events.collect { e ->
                when {
                    e is MeshEvent.PeerConnected -> tell(e.nodeId)
                    e is MeshEvent.App && e.kind == KIND -> onPacket(e)
                }
            }
        }
        scope.launch { while (true) { delay(5 * 60_000L); mesh.connectedNodes().forEach { tell(it) } } }
    }

    private fun tell(to: String) {
        val (pct, charging) = battery() ?: return
        mesh.sendApp(to, KIND, JSONObject().put("a", "bat").put("p", pct).put("c", charging))
    }

    /** Asks everyone connected "Are you OK?". Returns how many were asked. */
    fun checkOnEveryone(): Int {
        val people = mesh.connectedNodes().toSet()
        if (people.isEmpty()) return 0
        val id = "chk-" + System.currentTimeMillis()
        _check.value = CheckIn(id, System.currentTimeMillis(), people, emptyMap())
        people.forEach { mesh.sendApp(it, KIND, JSONObject().put("a", "ask").put("id", id)) }
        return people.size
    }

    fun answer(r: CheckRequest, answer: String) {
        _requests.update { l -> l.filterNot { it.id == r.id && it.from == r.from } }
        mesh.sendApp(r.from, KIND, JSONObject().put("a", "ans").put("id", r.id).put("v", answer))
    }

    fun clearCheck() { _check.value = null }

    private fun onPacket(e: MeshEvent.App) {
        val b = e.body
        when (b.optString("a")) {
            "bat" -> b.optInt("p", -1).takeIf { it in 0..100 }?.let { p ->
                _batteries.update { it + (e.fromNodeId to Battery(p, b.optBoolean("c"), System.currentTimeMillis())) }
            }
            "ask" -> {
                val r = CheckRequest(e.fromNodeId, e.name.ifBlank { "Someone" }, b.optString("id").take(40))
                if (_requests.value.none { it.from == r.from }) { _requests.update { it + r }; onRequest(r) }
            }
            "ans" -> _check.update { c ->
                val v = b.optString("v").takeIf { it == CheckIn.OK || it == CheckIn.HELP }
                if (c == null || c.id != b.optString("id") || v == null) c else c.copy(answers = c.answers + (e.fromNodeId to v))
            }
        }
    }

    companion object { const val KIND = "stat" }
}
