package com.bluemob.app.ui.games

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.bluemob.app.games.ConnectFour
import com.bluemob.app.games.DotsAndBoxes
import com.bluemob.app.games.Engine
import com.bluemob.app.games.InfiniteTicTacToe
import com.bluemob.app.games.Match
import com.bluemob.app.games.MatchRules
import com.bluemob.app.games.MatchState
import com.bluemob.app.games.SurvivalQuiz
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import kotlinx.coroutines.delay


/** Pick a game: challenge someone (nearby or over the internet), or play the computer. Open games and invites first. */
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    people: List<Person> = emptyList(),
    matches: List<Match> = emptyList(),
    onChallenge: (Person, String) -> Unit = { _, _ -> },
    onOpenMatch: (String) -> Unit = {},
    onAccept: (String) -> Unit = {},
    onDecline: (String) -> Unit = {},
) {
    SubScreen("Games", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Play with someone nearby (no signal needed) or over the internet, or against the computer.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (matches.isNotEmpty()) {
            item { GroupLabel("Your games") }
            items(matches, key = { it.id }) { m ->
                Group(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = m.state == MatchState.PLAYING) { onOpenMatch(m.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(m.game, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("${m.engine.title} with ${m.opponentName}", style = MaterialTheme.typography.titleMedium)
                            Text(when (m.state) {
                                MatchState.INVITED -> "${m.opponentName} invited you"
                                MatchState.INVITING -> "Waiting for ${m.opponentName} to accept…"
                                MatchState.DECLINED -> "${m.opponentName} said not now"
                                MatchState.LEFT -> "${m.opponentName} left the game"
                                MatchState.NO_ANSWER -> "No answer. If they have an older BlueMob, they need to update"
                                MatchState.PLAYING -> "${m.myScore}–${m.theirScore} · " + when { m.over -> "round over"; m.myTurn -> "your move"; else -> "their move" }
                            }, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                        }
                        if (m.state == MatchState.INVITED) {
                            TextButton(onClick = { onDecline(m.id) }) { Text("Not now") }
                            Button(onClick = { onAccept(m.id); onOpenMatch(m.id) }) { Text("Play") }
                        } else if (m.state != MatchState.PLAYING) {
                            TextButton(onClick = { onDecline(m.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Play with someone") }
        if (people.isEmpty()) item {
            Text("No one is reachable right now. People connected nearby show up here, and so does anyone online when the internet relay is on.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(people, key = { "p" + it.nodeId }) { p ->
            var menu by remember { mutableStateOf(false) }
            Group(Modifier.padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.avatar ?: "🙂", fontSize = 26.sp)
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Box {
                        Button(onClick = { menu = true }) { Text("Challenge ▾") }
                        DropdownMenu(menu, { menu = false }) {
                            Engine.ALL.filter { it.listed }.forEach { e -> DropdownMenuItem(text = { Text(e.title) }, leadingIcon = { GameIcon(e.code, 28.dp) }, onClick = { menu = false; onChallenge(p, e.code) }) }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Play the computer") }
        Engine.ALL.filter { it.listed }.forEach { e ->
            item(key = "cpu-" + e.code) {
                GameTile(e.code, e.title, e.blurb) { onPlay(e.code) }
                Gap(10.dp)
            }
        }
    }
}

/** The rules card, shown before someone's first game of each kind, and from "How to play". */
@Composable
fun RulesDialog(engine: Engine, onDone: () -> Unit) {
    val accent = gameAccent(engine.code)
    AlertDialog(
        onDismissRequest = onDone,
        title = null,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The game and, in one line, how you win.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(engine.code, 52.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(engine.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("How to play", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                    }
                }
                Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 18.sp)
                    Text(engine.goal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
                }
                // Steps as a timeline: number, line down to the next.
                Column(Modifier.padding(top = 14.dp)) {
                    engine.rules.forEachIndexed { i, (t, body) ->
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                if (i < engine.rules.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(accent.copy(alpha = 0.3f)))
                            }
                            Column(Modifier.padding(start = 12.dp, bottom = 14.dp)) {
                                Text(t, style = MaterialTheme.typography.titleSmall)
                                Text(body, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                            }
                        }
                    }
                }
                Text("With a friend, tap the reactions under the board to say something.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
            }
        },
        confirmButton = { Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent)) { Text("Let's play", color = Color.White) } },
    )
}

/** Emojis and quick phrases to send during a game, and the latest one from either side. */
@Composable
private fun Reactions(latest: com.bluemob.app.games.Matches.Reaction?, them: String, onReact: (String) -> Unit) {
    var visible by remember(latest?.at) { mutableStateOf(latest != null && System.currentTimeMillis() - latest.at < 5_000) }
    LaunchedEffect(latest?.at) { if (visible) { kotlinx.coroutines.delay(4_000); visible = false } }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.animation.AnimatedVisibility(visible && latest != null,
            enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            latest?.let { r ->
                Text((if (r.fromMe) "You: " else "$them: ") + r.text, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (r.fromMe) Extra.pineTint else Extra.emberTint).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bluemob.app.games.Matches.EMOJI.forEach { e ->
                Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { onReact(e) }.padding(6.dp))
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            items(com.bluemob.app.games.Matches.PHRASES) { p -> Chip(p, false) { onReact(p) } }
        }
    }
}

/** A game against a person. */
@Composable
fun MatchScreen(m: Match, onBack: () -> Unit, onPlay: (Int) -> Unit, onAgain: () -> Unit, onLeave: () -> Unit,
    reaction: com.bluemob.app.games.Matches.Reaction? = null, onReact: (String) -> Unit = {}) {
    var rules by rememberSaveable(m.game) { mutableStateOf(m.round == 0 && m.moveCount == 0) }
    if (rules) RulesDialog(m.engine) { rules = false }
    SubScreen(m.engine.title, onBack, actions = { TextButton(onClick = { rules = true }) { Text("How to play") } }) {
        item {
            Score(m.myScore, m.theirScore, status(m, m.opponentName), them = m.opponentName, themEmoji = "🧑", myTurn = if (m.over || !m.engine.turnBased) null else m.myTurn, accent = gameAccent(m.game))
        }
        item { Board(m, onPlay) }
        if (m.state == MatchState.PLAYING) item { Reactions(reaction, m.opponentName, onReact) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center) {
                if (m.over && m.state == MatchState.PLAYING) Button(onClick = onAgain) { Text("Play again") }
                TextButton(onClick = { onLeave(); onBack() }) { Text("Leave game") }
            }
        }
        item {
            Text("Moves are signed by each phone and go over the mesh, or the internet when you're apart.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

private fun status(m: Match, them: String): String = when {
    m.state == MatchState.LEFT -> "$them left the game"
    m.state == MatchState.INVITING -> "Waiting for $them to accept…"
    m.state == MatchState.DECLINED -> "$them said not now"
    m.state == MatchState.NO_ANSWER -> "No answer from $them"
    m.game == SurvivalQuiz.code -> SurvivalQuiz.score(m.board, SurvivalQuiz.questionsFor(m.id)).let { (a, b) ->
        if (m.over) (if (a > b) "You win! 🎉 $a–$b" else if (b > a) "$them wins $b–$a" else "Draw, $a–$b") else "You $a · $them $b"
    }
    m.winner?.first == 1 -> "You win! 🎉"
    m.winner?.first == 2 -> "$them wins"
    m.over -> m.engine.points(m.board)?.let { (a, b) -> "Draw, $a–$b" } ?: "Draw"
    m.myTurn -> "Your move" + (m.engine.points(m.board)?.let { (a, b) -> " · boxes $a–$b" } ?: "")
    else -> "$them is thinking…" + (m.engine.points(m.board)?.let { (a, b) -> " · boxes $a–$b" } ?: "")
}

/** The board for any game; [onPlay] gets the move (cell, column, line or answer). */
@Composable
private fun Board(m: Match, onPlay: (Int) -> Unit) {
    val enabled = m.myTurn
    val win = m.winner?.second
    when (m.game) {
        Match.C4 -> C4Board(m.board, win, enabled, onPlay)
        InfiniteTicTacToe.code -> TttBoard(m.board.map { InfiniteTicTacToe.owner(it) }, win, enabled, onPlay,
            fading = if (m.over) null else InfiniteTicTacToe.fading(m.board, m.turn))
        DotsAndBoxes.code -> DotsBoard(m.board, enabled, onPlay)
        com.bluemob.app.games.FiveInARow.code -> StoneBoard(m.board, win, enabled, onPlay)
        SurvivalQuiz.code -> QuizBoard(m, onPlay)
        else -> TttBoard(m.board, win, enabled, onPlay)
    }
}

/** A 9×9 board of stones, for Five in a row. */
@Composable
private fun StoneBoard(board: List<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit) {
    val n = com.bluemob.app.games.FiveInARow.N
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFD9B77A)).padding(6.dp)) {
        for (r in 0 until n) Row {
            for (c in 0 until n) {
                val i = r * n + c
                Box(Modifier.weight(1f).aspectRatio(1f).border(0.5.dp, Color(0x55000000)).clickable(enabled = enabled && board[i] == 0) { onTap(i) },
                    contentAlignment = Alignment.Center) {
                    if (board[i] != 0) Box(Modifier.fillMaxSize(0.8f).clip(CircleShape)
                        .background(if (board[i] == 1) Color(0xFF1D2A24) else Color(0xFFF7F4EC))
                        .border(if (line?.contains(i) == true) 3.dp else 0.dp, Color(0xFFE5484D), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun TttBoard(board: List<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit, fading: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in 0 until 3) {
                val i = r * 3 + c
                val inLine = line?.contains(i) == true
                val bg by animateColorAsState(if (inLine) Extra.pineTint else MaterialTheme.colorScheme.surface, label = "cell")
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, Extra.line, RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled && board[i] == 0) { onTap(i) },
                    contentAlignment = Alignment.Center) {
                    if (board[i] != 0) {
                        val color = if (board[i] == 1) gameAccent(Match.TTT) else Extra.ember
                        // Marks draw themselves in.
                        val grow = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(220)) }
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize(0.5f).alpha(if (i == fading) 0.3f else 1f)) {
                            val st = size.width * 0.16f
                            if (board[i] == 1) {
                                val g = grow.value
                                drawLine(color, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width * g, size.height * g), st, androidx.compose.ui.graphics.StrokeCap.Round)
                                drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width * (1 - g), size.height * g), st, androidx.compose.ui.graphics.StrokeCap.Round)
                            } else drawArc(color, -90f, 360f * grow.value, false, style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun C4Board(board: List<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1F5FA0)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until ConnectFour.ROWS) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until ConnectFour.COLS) {
                val i = r * ConnectFour.COLS + c
                val inLine = line?.contains(i) == true
                Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                    .background(when (board[i]) { 1 -> Extra.rose; 2 -> Color(0xFFF2C14E); else -> Color(0xFFEFF3EC) })
                    .then(if (inLine) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable(enabled = enabled && ConnectFour.dropRow(board, c) != null) { onTap(c) })
            }
        }
    }
}

/** Dots and boxes: tap between two dots to draw a line; closed boxes are coloured by who took them. */
@Composable
private fun DotsBoard(board: List<Int>, enabled: Boolean, onTap: (Int) -> Unit) {
    val n = DotsAndBoxes.N
    val mine = Palette.Pine
    val theirs = Extra.ember
    val faint = Extra.line
    val dot = MaterialTheme.colorScheme.onSurface
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val canTap by androidx.compose.runtime.rememberUpdatedState(enabled)
    val current by androidx.compose.runtime.rememberUpdatedState(board)
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().padding(top = 20.dp, start = 12.dp, end = 12.dp).aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    if (!canTap) return@detectTapGestures
                    val pad = size.width * 0.08f
                    val step = (size.width - 2 * pad) / n
                    // The nearest line's middle, if the tap is close enough to it.
                    val best = (0 until DotsAndBoxes.LINES).minByOrNull { l -> lineMid(l, pad, step).let { (x, y) -> (x - pos.x) * (x - pos.x) + (y - pos.y) * (y - pos.y) } }
                    if (best != null && current[best] == 0) {
                        val (x, y) = lineMid(best, pad, step)
                        if ((x - pos.x) * (x - pos.x) + (y - pos.y) * (y - pos.y) < (step * 0.45f) * (step * 0.45f)) tap(best)
                    }
                }
            },
    ) {
        val pad = size.width * 0.08f
        val step = (size.width - 2 * pad) / n
        for (r in 0 until n) for (c in 0 until n) {
            val owner = board[DotsAndBoxes.LINES + r * n + c]
            if (owner != 0) drawRect((if (owner == 1) mine else theirs).copy(alpha = 0.25f),
                androidx.compose.ui.geometry.Offset(pad + c * step + 4, pad + r * step + 4), androidx.compose.ui.geometry.Size(step - 8, step - 8))
        }
        for (l in 0 until DotsAndBoxes.LINES) {
            val (a, b) = lineEnds(l, pad, step)
            val v = board[l]
            drawLine(when (v) { 1 -> mine; 2 -> theirs; else -> faint }, a, b, strokeWidth = if (v != 0) 12f else 4f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        for (r in 0..n) for (c in 0..n) drawCircle(dot, radius = 11f, center = androidx.compose.ui.geometry.Offset(pad + c * step, pad + r * step))
    }
}

private fun lineEnds(l: Int, pad: Float, step: Float): Pair<androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset> {
    val n = DotsAndBoxes.N
    return if (l < DotsAndBoxes.H) {
        val r = l / n; val c = l % n
        androidx.compose.ui.geometry.Offset(pad + c * step, pad + r * step) to androidx.compose.ui.geometry.Offset(pad + (c + 1) * step, pad + r * step)
    } else {
        val k = l - DotsAndBoxes.H; val r = k / (n + 1); val c = k % (n + 1)
        androidx.compose.ui.geometry.Offset(pad + c * step, pad + r * step) to androidx.compose.ui.geometry.Offset(pad + c * step, pad + (r + 1) * step)
    }
}

private fun lineMid(l: Int, pad: Float, step: Float): Pair<Float, Float> = lineEnds(l, pad, step).let { (a, b) -> (a.x + b.x) / 2 to (a.y + b.y) / 2 }

/** The quiz: the next question you haven't answered, then whether you were right and why. */
@Composable
private fun QuizBoard(m: Match, onAnswer: (Int) -> Unit) {
    val qs = remember(m.id) { SurvivalQuiz.questionsFor(m.id) }
    val next = qs.indices.firstOrNull { m.board[it * 2] == 0 }
    var shown by remember(m.id, m.round) { mutableStateOf<Int?>(null) }
    val q = shown ?: next
    val theirDone = qs.indices.count { m.board[it * 2 + 1] != 0 }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Their progress: $theirDone of ${qs.size} answered", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        if (q == null) {
            Text(if (m.over) "All done. Check the score above." else "You've answered everything. Waiting for them to finish…",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        val question = qs[q]
        val mine = m.board[q * 2] - 1
        Text("Question ${q + 1} of ${qs.size}", style = MaterialTheme.typography.labelMedium, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp))
        Text(question.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        question.options.forEachIndexed { i, o ->
            val answered = mine >= 0
            val color = when {
                answered && i == question.right -> Extra.pineTint
                answered && i == mine -> Extra.emberTint
                else -> MaterialTheme.colorScheme.surface
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(color).border(1.dp, Extra.line, RoundedCornerShape(14.dp))
                .clickable(enabled = !answered) { shown = q; onAnswer(q * 4 + i) }.padding(14.dp)) {
                Text(o + if (answered && i == question.right) "  ✓" else "", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (mine >= 0) {
            Text(if (mine == question.right) "Right! 🎉" else "Not quite. The right answer is marked ✓.", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp))
            // Why: what was wrong with their choice, and why the right one is right.
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Extra.sand).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine != question.right) question.wrong.getOrNull(mine)?.takeIf { it.isNotBlank() }?.let {
                    Text("✕ Why not “${question.options[mine]}”", style = MaterialTheme.typography.labelLarge, color = Extra.rose)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (question.why.isNotBlank()) {
                    Text("✓ Why “${question.options[question.right]}”", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(question.why, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = { shown = null }, modifier = Modifier.padding(top = 8.dp)) { Text(if (next != null) "Next question" else "See result") }
        }
    }
}

@Composable
private fun GameTile(code: String, title: String, body: String, onClick: () -> Unit) {
    val accent = gameAccent(code)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, Extra.line, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GameIcon(code, 60.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, maxLines = 2)
        }
        Box(Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text("▶", color = accent, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Score(you: Int, cpu: Int, status: String, them: String, themEmoji: String, myTurn: Boolean? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Extra.line, RoundedCornerShape(22.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            PlayerPill("🙂", "You", you, myTurn == true, accent, Modifier.weight(1f))
            Text("vs", style = MaterialTheme.typography.labelLarge, color = Extra.ink3, modifier = Modifier.padding(horizontal = 6.dp))
            PlayerPill(themEmoji, them, cpu, myTurn == false, accent, Modifier.weight(1f))
        }
        Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

/** One side of the score bar; lit up while it's that side's turn. */
@Composable
private fun PlayerPill(emoji: String, name: String, score: Int, active: Boolean, accent: Color, modifier: Modifier) {
    val bg by animateColorAsState(if (active) accent.copy(alpha = 0.16f) else Color.Transparent, label = "turn")
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(if (active) "playing" else " ", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * Any game against the computer, on this phone. The computer is player 2; it moves after a short pause. In the quiz
 * it answers too, getting about 6 in 10 right, so it can be beaten.
 */
@Composable
fun ComputerGameScreen(code: String, onBack: () -> Unit) {
    val engine = Engine.of(code) ?: return
    var m by remember(code) { mutableStateOf(Match("g-local-" + System.nanoTime().toString().takeLast(8), code, "computer", "Computer", iInvited = true, state = MatchState.PLAYING)) }
    var hard by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable(code) { mutableStateOf(code != Match.TTT) }
    if (rules) RulesDialog(engine) { rules = false }
    // The computer's move.
    LaunchedEffect(m.board, m.turn, m.round, m.over) {
        if (m.over) return@LaunchedEffect
        if (engine.turnBased && m.turn == 2) {
            delay(500)
            engine.computerMove(m.board, 2, hard)?.let { spot -> m = MatchRules.move(m, 2, spot, m.moveCount + 1, m.round) }
        }
        if (code == SurvivalQuiz.code) {
            val qs = SurvivalQuiz.questionsFor(m.id)
            val q = qs.indices.firstOrNull { m.board[it * 2 + 1] == 0 } ?: return@LaunchedEffect
            delay(2_500)
            val pick = if (kotlin.random.Random.nextInt(10) < 6) qs[q].right else (qs[q].right + 1 + kotlin.random.Random.nextInt(3)) % 4
            m = MatchRules.move(m, 2, q * 4 + pick, m.moveCount + 1, m.round)
        }
    }
    SubScreen(engine.title, onBack, actions = { TextButton(onClick = { rules = true }) { Text("How to play") } }) {
        if (code == Match.TTT || code == InfiniteTicTacToe.code) item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Chip("Easy", !hard) { hard = false }
                Box(Modifier.padding(4.dp))
                Chip(if (code == Match.TTT) "Unbeatable" else "Hard", hard) { hard = true }
            }
        }
        item { Score(m.myScore, m.theirScore, status(m, "Computer"), them = "Computer", themEmoji = "🤖", myTurn = if (m.over || !engine.turnBased) null else m.turn == 1, accent = gameAccent(code)) }
        item { Board(m) { spot -> m = MatchRules.move(m, 1, spot, m.moveCount + 1, m.round) } }
        if (m.over) item {
            Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    m = if (code == SurvivalQuiz.code) Match("g-local-" + System.nanoTime().toString().takeLast(8), code, "computer", "Computer", iInvited = true,
                        state = MatchState.PLAYING, myScore = m.myScore, theirScore = m.theirScore)
                    else MatchRules.again(m, m.round + 1)
                }) { Text("Play again") }
            }
        }
        item {
            Text("Playing against the computer, right on this phone.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

/** Kept for links from older screens: the classic games against the computer. */
@Composable fun TicTacToeScreen(onBack: () -> Unit) = ComputerGameScreen(Match.TTT, onBack)
@Composable fun ConnectFourScreen(onBack: () -> Unit) = ComputerGameScreen(Match.C4, onBack)
