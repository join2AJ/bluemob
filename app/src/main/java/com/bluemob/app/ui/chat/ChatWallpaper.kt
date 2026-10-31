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
    val hour = currentHour()
    val bits = remember(myId, hour) {
        if (myId.length == 16) Watermark.bits(myId, hour * Watermark.HOUR) else BooleanArray(Watermark.COLS * Watermark.ROWS) { (it * 7) % 3 == 0 }
    }
    DotPattern(bits, Watermark.COLS, Watermark.ROWS, ink, modifier)
}

/**
 * A rescue screen's background: the same kind of dots in warm orange, in blocks of 16 × 9 that hide [RescueMark]
 * (this phone's ID and the person in need's), so a rescue screenshot can be traced, and told apart from a chat's.
 */
@Composable
fun RescueWallpaper(myId: String, victimId: String, modifier: Modifier = Modifier) {
    val hour = currentHour()
    val bits = remember(myId, victimId, hour) {
        if (myId.length == 16 && victimId.length == 16) com.bluemob.app.chat.RescueMark.bits(myId, victimId, hour * Watermark.HOUR)
        else BooleanArray(com.bluemob.app.chat.RescueMark.COLS * com.bluemob.app.chat.RescueMark.ROWS) { (it * 5) % 3 == 0 }
    }
    DotPattern(bits, com.bluemob.app.chat.RescueMark.COLS, com.bluemob.app.chat.RescueMark.ROWS, Color(0xFFF2994A), modifier, strength = 1.5f)
}

/** The hour now, updated every minute. */
@Composable
private fun currentHour(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    return now / Watermark.HOUR
}

/** Blocks of [cols] × [rows] dots, each starting at a small ring: big dot 1, small dot 0. */
@Composable
private fun DotPattern(bits: BooleanArray, cols: Int, rows: Int, ink: Color, modifier: Modifier, strength: Float = 1f) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val step = 18.dp.toPx()
        val small = 1.6.dp.toPx()
        val big = 3.dp.toPx()
        val dot = ink.copy(alpha = 0.09f * strength)
        val ring = ink.copy(alpha = 0.12f * strength)
        // A block is 10 × 8 dots plus one empty column and row around it, so blocks read apart.
        val blockW = (cols + 1) * step
        val blockH = (rows + 1) * step
        var by = step
        while (by < size.height) {
            var bx = step
            while (bx < size.width) {
                drawCircle(ring, big + 1.dp.toPx(), Offset(bx - step / 2, by - step / 2), style = Stroke(1.2.dp.toPx()))
                for (r in 0 until rows) for (c in 0 until cols) {
                    drawCircle(dot, if (bits[r * cols + c]) big else small, Offset(bx + c * step, by + r * step))
                }
                bx += blockW
            }
            by += blockH
        }
    }
}
