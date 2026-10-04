package com.bluemob.app.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.components.avatarTint
import kotlin.math.cos
import kotlin.math.sin

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
)

/** An animated radar: rings, a rotating sweep, you in the middle and people around you. */
@Composable
fun RadarSweep(people: List<RadarBlip>, modifier: Modifier = Modifier, active: Boolean = true) {
    val t = rememberInfiniteTransition(label = "radar")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    val ring = MaterialTheme.colorScheme.outlineVariant
    val sweep = MaterialTheme.colorScheme.primary
    val me = MaterialTheme.colorScheme.secondary

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.size(side)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(sweep.copy(alpha = 0.05f), r, c)
            for (i in 1..3) drawCircle(ring, r * i / 3f, c, style = Stroke(1.5.dp.toPx()))
            drawLine(ring, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(ring, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            if (active) {
                rotate(angle, c) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to sweep.copy(alpha = 0.35f),
                            center = c,
                        ),
                        r, c,
                    )
                }
            }
            drawCircle(me.copy(alpha = 0.25f * pulse), 18.dp.toPx() * pulse, c)
            drawCircle(me, 7.dp.toPx(), c)
        }
        val half = side / 2
        people.forEach { b ->
            val rad = Math.toRadians(b.bearingDeg)
            val x = half * b.radius * sin(rad).toFloat()
            val y = -half * b.radius * cos(rad).toFloat()
            val dot = 30.dp
            Box(
                Modifier
                    .offset(x, y)
                    .size(dot)
                    .alpha(if (b.faded) 0.45f else 1f)
                    .background(avatarTint(b.id).copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(b.emoji ?: "•", fontSize = 15.sp)
            }
            Box(
                Modifier
                    .offset(x + dot * 0.38f, y + dot * 0.38f)
                    .size(9.dp)
                    .alpha(if (b.faded) 0.45f else 1f)
                    .background(b.color, CircleShape)
            )
        }
    }
}

