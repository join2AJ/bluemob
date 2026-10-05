package com.bluemob.app.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.games.ConnectFour
import com.bluemob.app.games.TicTacToe
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** Pick a game. With no one nearby, the computer plays. */
@Composable
fun GamesScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    SubScreen("Games", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Games", style = MaterialTheme.typography.headlineMedium)
                Text("No one around? Play against the computer, right on this phone. No signal needed.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Gap(16.dp) }
        item { GameTile("⭕", "Tic-tac-toe", "Three in a row. Easy or unbeatable.", listOf(Palette.Pine, Color(0xFF0B3D2E))) { onPlay("ttt") } }
        item { Gap(12.dp) }
        item { GameTile("🔴", "Connect 4", "Drop discs, line up four.", listOf(Color(0xFFC2621A), Color(0xFF7A3A0E))) { onPlay("c4") } }
        item {
            Text("Playing with people nearby over the mesh is in the web preview now, and comes to the Android app next.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
        }
    }
}

@Composable
private fun GameTile(emoji: String, title: String, body: String, colors: List<Color>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 40.sp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Text("🤖 Play against the computer", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Score(you: Int, cpu: Int, status: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🙂", fontSize = 30.sp); Text("You", style = MaterialTheme.typography.labelMedium, color = Extra.ink2) }
            Text("$you – $cpu", style = MaterialTheme.typography.displaySmall)
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🤖", fontSize = 30.sp); Text("Computer", style = MaterialTheme.typography.labelMedium, color = Extra.ink2) }
        }
        Text(status, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun TicTacToeScreen(onBack: () -> Unit) {
    var board by rememberSaveable { mutableStateOf(List(9) { 0 }) }
    var hard by rememberSaveable { mutableStateOf(false) }
    var you by rememberSaveable { mutableIntStateOf(0) }
    var cpu by rememberSaveable { mutableIntStateOf(0) }
    var youStart by rememberSaveable { mutableStateOf(true) }
    val win = TicTacToe.winner(board)
    val over = win != null || TicTacToe.full(board)
    val cpuTurn = !over && board.count { it == 1 } + (if (youStart) 0 else 1) == board.count { it == 2 } + 1
    LaunchedEffect(board, cpuTurn) {
        if (cpuTurn) {
            delay(450)
            TicTacToe.computerMove(board, 2, hard)?.let { board = board.toMutableList().also { b -> b[it] = 2 } }
        }
    }
    LaunchedEffect(win) { when (win?.first) { 1 -> you++; 2 -> cpu++ } }
    SubScreen("Tic-tac-toe", onBack) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Chip("Easy", !hard) { hard = false }
                Box(Modifier.padding(4.dp))
                Chip("Unbeatable", hard) { hard = true }
            }
        }
        item {
            Score(you, cpu, when {
                win?.first == 1 -> "You win! 🎉"
                win?.first == 2 -> "Computer wins"
                over -> "Draw"
                cpuTurn -> "Computer is thinking…"
                else -> "Your move · you're ✕"
            })
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val i = r * 3 + c
                        val inLine = win?.second?.contains(i) == true
                        val bg by animateColorAsState(if (inLine) Extra.pineTint else MaterialTheme.colorScheme.surface, label = "cell")
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, Extra.line, RoundedCornerShape(18.dp))
                            .clickable(enabled = board[i] == 0 && !over && !cpuTurn) { board = board.toMutableList().also { it[i] = 1 } },
                            contentAlignment = Alignment.Center) {
                            Text(when (board[i]) { 1 -> "✕"; 2 -> "◯"; else -> "" }, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                                color = if (board[i] == 1) Palette.Pine else Extra.ember)
                        }
                    }
                }
            }
        }
        if (over) item {
            Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { youStart = !youStart; board = List(9) { 0 } }) { Text("Play again") }
            }
        }
        item {
            Text("Playing against the computer, right on this phone.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

@Composable
fun ConnectFourScreen(onBack: () -> Unit) {
    var board by rememberSaveable { mutableStateOf(ConnectFour.empty()) }
    var you by rememberSaveable { mutableIntStateOf(0) }
    var cpu by rememberSaveable { mutableIntStateOf(0) }
    var youStart by rememberSaveable { mutableStateOf(true) }
    val win = ConnectFour.winner(board)
    val over = win != null || ConnectFour.full(board)
    val cpuTurn = !over && board.count { it == 1 } + (if (youStart) 0 else 1) == board.count { it == 2 } + 1
    LaunchedEffect(board, cpuTurn) {
        if (cpuTurn) {
            delay(500)
            ConnectFour.computerMove(board, 2)?.let { col -> ConnectFour.drop(board, col, 2)?.let { board = it } }
        }
    }
    LaunchedEffect(win) { when (win?.first) { 1 -> you++; 2 -> cpu++ } }
    SubScreen("Connect 4", onBack) {
        item {
            Score(you, cpu, when {
                win?.first == 1 -> "You win! 🎉"
                win?.first == 2 -> "Computer wins"
                over -> "Draw"
                cpuTurn -> "Computer is thinking…"
                else -> "Your move · tap a column"
            })
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1F5FA0)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until ConnectFour.ROWS) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until ConnectFour.COLS) {
                        val i = r * ConnectFour.COLS + c
                        val inLine = win?.second?.contains(i) == true
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .background(when (board[i]) { 1 -> Extra.rose; 2 -> Color(0xFFF2C14E); else -> Color(0xFFEFF3EC) })
                            .then(if (inLine) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable(enabled = !over && !cpuTurn && ConnectFour.dropRow(board, c) != null) { ConnectFour.drop(board, c, 1)?.let { board = it } })
                    }
                }
            }
        }
        item {
            Text("You're red, the computer is yellow.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        if (over) item {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { youStart = !youStart; board = ConnectFour.empty() }) { Text("Play again") }
            }
        }
    }
}
