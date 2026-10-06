package com.bluemob.app.games

import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.mesh.NearbyMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

enum class MatchState { INVITING, INVITED, PLAYING, DECLINED, LEFT }

/**
 * A game against a person over the mesh. The board is from this phone's side: 1 = me, 2 = them.
 * The inviter starts round 0, then players take turns starting each new round.
 */
data class Match(
    val id: String,
    val game: String,
    val opponent: String,
    val opponentName: String,
    val iInvited: Boolean,
    val state: MatchState,
    val board: List<Int> = emptyBoard(game),
    val round: Int = 0,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val iStart: Boolean get() = (round % 2 == 0) == iInvited
    val moves: Int get() = board.count { it != 0 }
    val winner: Pair<Int, List<Int>>? get() = if (game == C4) ConnectFour.winner(board) else TicTacToe.winner(board)
    val over: Boolean get() = winner != null || (if (game == C4) ConnectFour.full(board) else TicTacToe.full(board))
    val myTurn: Boolean get() = state == MatchState.PLAYING && !over && (moves % 2 == 0) == iStart

    companion object {
        const val TTT = "ttt"
        const val C4 = "c4"
        fun emptyBoard(game: String) = if (game == C4) ConnectFour.empty() else List(9) { 0 }
        fun title(game: String) = if (game == C4) "Connect 4" else "Tic-tac-toe"
    }
}

/**
 * The rules for a match, kept apart from the network so they can be tested: every function returns the
 * new match, or the same one when the action doesn't apply (a duplicate packet, a move out of turn).
 */
object MatchRules {
    /** Places a piece for [who] (1 = me, 2 = them) as move number [n]. [spot] is a cell (tic-tac-toe) or column (Connect 4). */
    fun move(m: Match, who: Int, spot: Int, n: Int, round: Int): Match {
        if (m.state != MatchState.PLAYING || round != m.round || m.over || n != m.moves + 1) return m
        val mineToMove = (m.moves % 2 == 0) == m.iStart
        if ((who == 1) != mineToMove) return m
        val board = when (m.game) {
            Match.C4 -> if (spot in 0 until ConnectFour.COLS) ConnectFour.drop(m.board, spot, who) else null
            else -> if (spot in 0..8 && m.board[spot] == 0) m.board.toMutableList().also { it[spot] = who } else null
        } ?: return m
        val next = m.copy(board = board, updatedAt = System.currentTimeMillis())
        return when (next.winner?.first) {
            1 -> next.copy(myScore = m.myScore + 1)
            2 -> next.copy(theirScore = m.theirScore + 1)
            else -> next
        }
    }

    /** Starts round [round] once the last one is over. */
    fun again(m: Match, round: Int): Match =
        if (m.state == MatchState.PLAYING && round == m.round + 1 && m.over) m.copy(round = round, board = Match.emptyBoard(m.game), updatedAt = System.currentTimeMillis()) else m
}

/** Invites, moves and scores for games with people nearby. Packets are signed and pass through other phones if needed. */
class Matches(
    private val mesh: NearbyMeshTransport,
    scope: CoroutineScope,
    /** Called when someone invites us, so the app can notify while it's in the background. */
    private val onInvite: (Match) -> Unit = {},
) {
    private val _all = MutableStateFlow<Map<String, Match>>(emptyMap())
    val all: StateFlow<Map<String, Match>> = _all.asStateFlow()

    init {
        scope.launch {
            mesh.events.collect { e -> if (e is MeshEvent.App && e.kind == KIND) onPacket(e) }
        }
    }

    /** Invites someone. Returns the match, or null if no one is in range to carry the invite. */
    fun invite(nodeId: String, name: String, game: String): Match? {
        val m = Match("g-" + UUID.randomUUID().toString().take(10), game, nodeId, name, iInvited = true, state = MatchState.INVITING)
        if (!send(m, "invite", JSONObject().put("game", game))) return null
        _all.update { it + (m.id to m) }
        return m
    }

    fun accept(id: String) {
        val m = _all.value[id]?.takeIf { it.state == MatchState.INVITED } ?: return
        set(m.copy(state = MatchState.PLAYING))
        send(m, "accept")
    }

    fun decline(id: String) {
        val m = _all.value[id] ?: return
        send(m, "decline")
        _all.update { it - id }
    }

    fun play(id: String, spot: Int) {
        val m = _all.value[id]?.takeIf { it.myTurn } ?: return
        val next = MatchRules.move(m, 1, spot, m.moves + 1, m.round)
        if (next === m) return
        set(next)
        send(m, "move", JSONObject().put("spot", spot).put("n", m.moves + 1).put("round", m.round))
    }

    fun again(id: String) {
        val m = _all.value[id] ?: return
        val next = MatchRules.again(m, m.round + 1)
        if (next === m) return
        set(next)
        send(m, "again", JSONObject().put("round", next.round))
    }

    fun leave(id: String) {
        val m = _all.value[id] ?: return
        if (m.state == MatchState.PLAYING || m.state == MatchState.INVITING) send(m, "leave")
        _all.update { it - id }
    }

    private fun onPacket(e: MeshEvent.App) {
        val b = e.body
        val id = b.optString("g").takeIf { it.startsWith("g-") && it.length <= 20 } ?: return
        val existing = _all.value[id]
        // Only the person we're playing can change a match.
        if (existing != null && existing.opponent != e.fromNodeId) return
        when (b.optString("a")) {
            "invite" -> {
                if (existing != null) return
                val game = b.optString("game").takeIf { it == Match.TTT || it == Match.C4 } ?: return
                val m = Match(id, game, e.fromNodeId, e.name.ifBlank { "Someone" }, iInvited = false, state = MatchState.INVITED)
                _all.update { it + (id to m) }
                onInvite(m)
            }
            "accept" -> change(id) { m -> if (m.state == MatchState.INVITING) m.copy(state = MatchState.PLAYING) else m }
            "decline" -> change(id) { m -> if (m.state == MatchState.INVITING) m.copy(state = MatchState.DECLINED) else m }
            "leave" -> change(id) { m -> m.copy(state = MatchState.LEFT) }
            "move" -> change(id) { m -> MatchRules.move(m, 2, b.optInt("spot", -1), b.optInt("n"), b.optInt("round")) }
            "again" -> change(id) { m -> MatchRules.again(m, b.optInt("round")) }
        }
    }

    private fun set(m: Match) = _all.update { it + (m.id to m) }
    private fun change(id: String, f: (Match) -> Match) = _all.update { all -> all[id]?.let { all + (id to f(it)) } ?: all }

    private fun send(m: Match, action: String, extra: JSONObject = JSONObject()): Boolean =
        mesh.sendApp(m.opponent, KIND, extra.put("g", m.id).put("a", action))

    companion object {
        const val KIND = "game"
    }
}
