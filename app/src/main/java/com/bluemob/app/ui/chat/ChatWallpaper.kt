package com.bluemob.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bluemob.app.chat.Watermark
import kotlinx.coroutines.delay

/**
 * The chat background: a quiet outdoor pattern (peaks, stars, compass rings, footprints) with the hourly
 * [Watermark] code woven faintly through it. [ink] is the pattern colour.
 */
@Composable
fun ChatWallpaper(myId: String, ink: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val code = remember(myId, now / Watermark.HOUR) { if (myId.length == 16) Watermark.code(myId, now) else "" }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true } }
    Canvas(modifier.clearAndSetSemantics { }) {
        val tile = 220f
        val pattern = ink.copy(alpha = 0.07f)
        val cols = (size.width / tile).toInt() + 2
        val rows = (size.height / tile).toInt() + 2
        for (r in 0 until rows) for (c in 0 until cols) {
            val ox = c * tile + (if (r % 2 == 1) tile / 2 else 0f) - tile / 2
            val oy = r * tile
            when ((r * 7 + c * 3) % 4) {
                0 -> { // two peaks
                    val p = Path().apply { moveTo(ox + 40, oy + 120); lineTo(ox + 80, oy + 60); lineTo(ox + 105, oy + 95); lineTo(ox + 130, oy + 70); lineTo(ox + 170, oy + 120) }
                    drawPath(p, pattern, style = Stroke(4f))
                }
                1 -> { // compass ring
                    drawCircle(pattern, 30f, Offset(ox + 110, oy + 90), style = Stroke(4f))
                    drawLine(pattern, Offset(ox + 110, oy + 66), Offset(ox + 110, oy + 114), 4f)
                    drawLine(pattern, Offset(ox + 86, oy + 90), Offset(ox + 134, oy + 90), 2f)
                }
                2 -> { // stars
                    listOf(60f to 60f, 140f to 90f, 95f to 140f).forEach { (x, y) -> drawCircle(pattern, 4f, Offset(ox + x, oy + y)) }
                }
                else -> { // footprints
                    drawOval(pattern, Offset(ox + 80, oy + 70), androidx.compose.ui.geometry.Size(18f, 28f))
                    drawOval(pattern, Offset(ox + 112, oy + 104), androidx.compose.ui.geometry.Size(18f, 28f))
                }
            }
        }
        if (code.isNotEmpty()) {
            // The code, tiled diagonally and very faint: readable when you look for it in a screenshot.
            paint.color = ink.copy(alpha = 0.06f).toArgb()
            rotate(-28f) {
                var y = -size.height
                var line = 0
                while (y < size.height * 2) {
                    var x = -size.width + (line % 2) * 160f
                    while (x < size.width * 2) { drawContext.canvas.nativeCanvas.drawText(code, x, y, paint); x += 420f }
                    y += 150f; line++
                }
            }
        }
    }
}
