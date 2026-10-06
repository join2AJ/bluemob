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

enum class MatchState { INVITING, INVITED, PLAYING, DECLINED, LEFT, NO_ANSWER }

/**
 * A game against a person (nearby or over the internet), or the computer. The board is from this phone's side:
 * 1 = me, 2 = them. The inviter starts round 0, then players take turns starting each new round.
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
    /** Moves played this round. Each move carries its number, so repeats and out-of-order packets are ignored. */
    val moveCount: Int = 0,
    /** Whose turn: 1 = me, 2 = them. Null = whoever starts the round. */
    val toMove: Int? = null,
) {
    val engine: Engine get() = Engine.of(game) ?: TicTacToeEngine
    val iStart: Boolean get() = (round % 2 == 0) == iInvited
    val turn: Int get() = toMove ?: if (iStart) 1 else 2
    val winner: Pair<Int, List<Int>>? get() = if (game == SurvivalQuiz.code) quizWinner() else engine.winner(board)
    val over: Boolean get() = engine.over(board) || winner != null
    val myTurn: Boolean get() = state == MatchState.PLAYING && !over && (!engine.turnBased || turn == 1)

    private fun quizWinner(): Pair<Int, List<Int>>? {
        if (!engine.over(board)) return null
        val (a, b) = SurvivalQuiz.score(board, SurvivalQuiz.questionsFor(id))
        return when { a > b -> 1 to emptyList(); b > a -> 2 to emptyList(); else -> null }
    }

    companion object {
        const val TTT = "ttt"
        const val C4 = "c4"
        fun emptyBoard(game: String) = (Engine.of(game) ?: TicTacToeEngine).empty()
        fun title(game: String) = (Engine.of(game) ?: TicTacToeEngine).title
    }
}

/**
 * The rules for a match, kept apart from the network so they can be tested: every function returns the
 * new match, or the same one when the action doesn't apply (a duplicate packet, a move out of turn).
 */
object MatchRules {
    /** Plays [spot] for [who] (1 = me, 2 = them) as move number [n] in [round]. */
    fun move(m: Match, who: Int, spot: Int, n: Int, round: Int): Match {
        if (m.state != MatchState.PLAYING || round != m.round || m.over) return m
        val engine = m.engine
        if (engine.turnBased && (n != m.moveCount + 1 || who != m.turn)) return m
        val (board, again) = engine.play(m.board, who, spot, n) ?: return m
        val next = m.copy(board = board, moveCount = m.moveCount + 1, toMove = if (again || !engine.turnBased) who else 3 - who,
            updatedAt = System.currentTimeMillis())
        if (m.over) return next
        return when (next.winner?.first) {
            1 -> next.copy(myScore = m.myScore + 1)
            2 -> next.copy(theirScore = m.theirScore + 1)
            else -> next
        }
    }

    /** Starts round [round] once the last one is over. */
    fun again(m: Match, round: Int): Match =
        if (m.state == MatchState.PLAYING && round == m.round + 1 && m.over)
            m.copy(round = round, board = Match.emptyBoard(m.game), moveCount = 0, toMove = null, updatedAt = System.currentTimeMillis()) else m
}

/** Invites, moves and scores for games with people nearby. Packets are signed and pass through other phones if needed. */
class Matches(
    private val mesh: NearbyMeshTransport,
    private val scope: CoroutineScope,
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
        // No reply: they may be on a version without games (0.6 or older can't answer at all).
        scope.launch {
            kotlinx.coroutines.delay(INVITE_TIMEOUT_MS)
            change(m.id) { x -> if (x.state == MatchState.INVITING) x.copy(state = MatchState.NO_ANSWER) else x }
        }
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
        val next = MatchRules.move(m, 1, spot, m.moveCount + 1, m.round)
        if (next === m) return
        set(next)
        send(m, "move", JSONObject().put("spot", spot).put("n", m.moveCount + 1).put("round", m.round))
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
                // A game this version doesn't know (from a newer phone) is turned down politely.
                val game = b.optString("game").takeIf { Engine.of(it) != null } ?: run {
                    mesh.sendApp(e.fromNodeId, KIND, JSONObject().put("g", id).put("a", "decline"))
                    return
                }
                val m = Match(id, game, e.fromNodeId, e.name.ifBlank { "Someone" }, iInvited = false, state = MatchState.INVITED)
                _all.update { it + (id to m) }
                onInvite(m)
            }
            "accept" -> change(id) { m -> if (m.state == MatchState.INVITING || m.state == MatchState.NO_ANSWER) m.copy(state = MatchState.PLAYING) else m }
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
        const val INVITE_TIMEOUT_MS = 40_000L
    }
}
