package com.bluemob.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bluemob.app.games.DotsAndBoxes
import com.bluemob.app.games.FiveInARow
import com.bluemob.app.games.InfiniteTicTacToe
import com.bluemob.app.games.Match
import com.bluemob.app.games.SurvivalQuiz

/** Each game's colour: the icon's tile and accents. */
fun gameAccent(code: String): Color = when (code) {
    InfiniteTicTacToe.code, Match.TTT -> Color(0xFF4C6FFF)
    Match.C4 -> Color(0xFFE07A2E)
    FiveInARow.code -> Color(0xFF1F9D74)
    DotsAndBoxes.code -> Color(0xFF8C5BE0)
    SurvivalQuiz.code -> Color(0xFFE0525A)
    else -> Color(0xFF4C6FFF)
}

/**
 * A game's icon: a tiny drawing of its own board, in white on the game's colour, inside a rounded tile. Drawn, not
 * emoji, so it stays crisp and inside its tile on every phone.
 */
@Composable
fun GameIcon(code: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val accent = gameAccent(code)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(accent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).padding(size * 0.2f)) { drawGlyph(code, Color.White, accent) }
    }
}

private fun DrawScope.drawGlyph(code: String, ink: Color, accent: Color) {
    val w = size.width
    val s = w * 0.075f // stroke
    when (code) {
        InfiniteTicTacToe.code, Match.TTT -> {
            // A 3×3 grid with an X, an O and a fading X.
            for (i in 1..2) {
                drawLine(ink.copy(alpha = 0.55f), Offset(w * i / 3, 0f), Offset(w * i / 3, w), s * 0.7f, StrokeCap.Round)
                drawLine(ink.copy(alpha = 0.55f), Offset(0f, w * i / 3), Offset(w, w * i / 3), s * 0.7f, StrokeCap.Round)
            }
            fun x(c: Int, r: Int, a: Float) {
                val m = w / 3 * 0.25f; val x0 = c * w / 3 + m; val y0 = r * w / 3 + m; val d = w / 3 - 2 * m
                drawLine(ink.copy(alpha = a), Offset(x0, y0), Offset(x0 + d, y0 + d), s, StrokeCap.Round)
                drawLine(ink.copy(alpha = a), Offset(x0 + d, y0), Offset(x0, y0 + d), s, StrokeCap.Round)
            }
            x(0, 0, 1f); x(2, 2, 1f); x(2, 0, 0.35f)
            drawCircle(ink, w / 3 * 0.27f, Offset(w / 2, w / 2), style = Stroke(s))
        }
        Match.C4 -> {
            // A frame of holes, some filled.
            val n = 4; val gap = w / n
            drawRoundRect(ink.copy(alpha = 0.25f), Offset.Zero, Size(w, w), CornerRadius(w * 0.12f))
            val filled = mapOf(13 to 1f, 14 to 0.55f, 9 to 1f, 10 to 0.55f, 15 to 1f, 5 to 1f)
            for (i in 0 until n * n) {
                val c = Offset(gap * (i % n) + gap / 2, gap * (i / n) + gap / 2)
                val a = filled[i]
                if (a != null) drawCircle(ink.copy(alpha = a), gap * 0.36f, c) else drawCircle(accent.copy(alpha = 0.6f), gap * 0.3f, c)
            }
        }
        FiveInARow.code -> {
            // A go board with a diagonal of five stones.
            val n = 5; val gap = w / (n - 1)
            for (i in 0 until n) {
                drawLine(ink.copy(alpha = 0.45f), Offset(i * gap, 0f), Offset(i * gap, w), s * 0.5f)
                drawLine(ink.copy(alpha = 0.45f), Offset(0f, i * gap), Offset(w, i * gap), s * 0.5f)
            }
            for (i in 0 until n) drawCircle(ink, gap * 0.36f, Offset(i * gap, (n - 1 - i) * gap))
            drawCircle(Color.Black.copy(alpha = 0.55f), gap * 0.34f, Offset(gap, gap))
            drawCircle(Color.Black.copy(alpha = 0.55f), gap * 0.34f, Offset(3 * gap, 3 * gap))
        }
        DotsAndBoxes.code -> {
            // Dots, some lines, one closed box.
            val n = 3; val gap = w / (n - 1)
            drawRect(ink.copy(alpha = 0.3f), Offset(0f, 0f), Size(gap, gap))
            listOf(Offset(0f, 0f) to Offset(gap, 0f), Offset(0f, 0f) to Offset(0f, gap), Offset(gap, 0f) to Offset(gap, gap), Offset(0f, gap) to Offset(gap, gap),
                Offset(gap, gap) to Offset(2 * gap, gap), Offset(2 * gap, gap) to Offset(2 * gap, 2 * gap))
                .forEach { (a, b) -> drawLine(ink, a, b, s, StrokeCap.Round) }
            for (r in 0 until n) for (c in 0 until n) drawCircle(ink, s * 1.1f, Offset(c * gap, r * gap))
        }
        SurvivalQuiz.code -> {
            // A speech bubble with a question mark.
            val p = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, w * 0.78f, CornerRadius(w * 0.2f)))
                moveTo(w * 0.22f, w * 0.7f); lineTo(w * 0.18f, w); lineTo(w * 0.45f, w * 0.75f); close()
            }
            drawPath(p, ink.copy(alpha = 0.95f))
            val q = Path().apply {
                moveTo(w * 0.36f, w * 0.3f)
                cubicTo(w * 0.36f, w * 0.12f, w * 0.64f, w * 0.12f, w * 0.64f, w * 0.29f)
                cubicTo(w * 0.64f, w * 0.4f, w * 0.5f, w * 0.4f, w * 0.5f, w * 0.52f)
            }
            drawPath(q, accent, style = Stroke(s * 1.1f, cap = StrokeCap.Round))
            drawCircle(accent, s * 0.75f, Offset(w * 0.5f, w * 0.64f))
        }
        else -> drawCircle(ink, w / 3)
    }
}
