package com.bluemob.app.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.components.avatarTint
import com.bluemob.app.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One person on the radar.
 * [bearingDeg] is the compass direction (0 = up/north), [radius] goes from 0 (you) to 1 (edge).
 */
data class RadarBlip(
    val id: String,
    val emoji: String?,
    val bearingDeg: Double,
    val radius: Float,
    val color: Color,
    val faded: Boolean = false,
    val sos: Boolean = false,
)

private val Mist = Color(0xFFEAF5F0)

/** An animated radar drawn on a night sky: twinkling stars, rings, a mint sweep, you in the middle. */
@Composable
fun RadarSweep(people: List<RadarBlip>, modifier: Modifier = Modifier, active: Boolean = true, onTap: ((String) -> Unit)? = null) {
    val t = rememberInfiniteTransition(label = "radar")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val stars = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.size(side)) {
            val c = center
            val r = size.minDimension / 2 * 0.92f
            stars.forEach { (x, y, s) ->
                drawCircle(Mist.copy(alpha = 0.12f + 0.35f * pulse * s), (0.6f + s) * 1.2.dp.toPx(), Offset(x * size.width, y * size.height))
            }
            for (i in 1..3) drawCircle(Mist.copy(alpha = 0.10f), r * i / 3f, c, style = Stroke(1.dp.toPx()))
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 10f))
            drawLine(Mist.copy(alpha = 0.10f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx(), pathEffect = dash)
            drawLine(Mist.copy(alpha = 0.10f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx(), pathEffect = dash)
            if (active) {
                rotate(angle, c) {
                    drawCircle(
                        Brush.sweepGradient(0f to Palette.Mint.copy(alpha = 0.5f), 0.18f to Color.Transparent, 1f to Color.Transparent, center = c),
                        r, c,
                    )
                    drawLine(Palette.Mint.copy(alpha = 0.8f), c, Offset(c.x + r, c.y), 1.5.dp.toPx())
                }
            }
            drawCircle(Color(0x477DB7EA), (10 + 12 * pulse) * density, c)
            drawCircle(Color(0xFF7DB7EA), 6.5.dp.toPx(), c)
            drawCircle(Color.White, 6.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
        val half = side / 2
        people.forEach { b ->
            val rad = Math.toRadians(b.bearingDeg)
            val x = half * b.radius * sin(rad).toFloat()
            val y = -half * b.radius * cos(rad).toFloat()
            Box(
                Modifier.offset(x, y).size(40.dp).alpha(if (b.faded) 0.45f else 1f)
                    .then(if (onTap != null) Modifier.clip(CircleShape).clickable { onTap(b.id) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(38.dp).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape).clip(CircleShape).background(avatarTint(b.id)),
                    contentAlignment = Alignment.Center,
                ) { Text(b.emoji ?: "•", fontSize = 18.sp) }
                Box(Modifier.align(Alignment.BottomEnd).size(12.dp).border(2.dp, Palette.Night1, CircleShape).background(if (b.sos) Palette.Rose else b.color, CircleShape))
            }
        }
    }
}
