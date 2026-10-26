package com.bluemob.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.Watermark
import kotlinx.coroutines.delay

/**
 * The chat background: a soft dotted pattern. It's made of blocks of 10 × 8 dots, each block starting at a small
 * ring; inside a block a big dot is a 1 and a small dot a 0, spelling the hourly [Watermark] for this phone. To
 * anyone else it's just dots. [ink] is the dot colour.
 */
@Composable
fun ChatWallpaper(myId: String, ink: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val bits = remember(myId, now / Watermark.HOUR) {
        if (myId.length == 16) Watermark.bits(myId, now) else BooleanArray(Watermark.COLS * Watermark.ROWS) { (it * 7) % 3 == 0 }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val step = 18.dp.toPx()
        val small = 1.6.dp.toPx()
        val big = 3.dp.toPx()
        val dot = ink.copy(alpha = 0.09f)
        val ring = ink.copy(alpha = 0.12f)
        // A block is 10 × 8 dots plus one empty column and row around it, so blocks read apart.
        val blockW = (Watermark.COLS + 1) * step
        val blockH = (Watermark.ROWS + 1) * step
        var by = step
        while (by < size.height) {
            var bx = step
            while (bx < size.width) {
                drawCircle(ring, big + 1.dp.toPx(), Offset(bx - step / 2, by - step / 2), style = Stroke(1.2.dp.toPx()))
                for (r in 0 until Watermark.ROWS) for (c in 0 until Watermark.COLS) {
                    drawCircle(dot, if (bits[r * Watermark.COLS + c]) big else small, Offset(bx + c * step, by + r * step))
                }
                bx += blockW
            }
            by += blockH
        }
    }
}
