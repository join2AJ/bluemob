package com.bluemob.app.ui.guide

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.bluemob.app.guide.GuideCategory
import kotlin.math.PI
import kotlin.math.sin

/**
 * A small looping illustration for a guide's category, drawn in code (no image files, works offline, tiny):
 * a heartbeat for first aid, falling drops for water, a flickering flame, a tent under twinkling stars, a compass
 * needle settling north, a signal pulse, rain and lightning, shaking ground, a swaying leaf.
 */
@Composable
fun GuideArt(category: GuideCategory, modifier: Modifier = Modifier, animate: Boolean = true) {
    // Screenshot tests and previews get a still frame.
    val still = LocalInspectionMode.current || !animate
    val t = if (still) 0.35f else {
        val loop = rememberInfiniteTransition(label = "guide-art")
        val v by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    }
    val base = category.color
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(base.copy(alpha = 0.95f), base.copy(alpha = 0.65f))))) {
        Canvas(Modifier.matchParentSize()) {
            when (category) {
                GuideCategory.FIRST_AID -> heartbeat(t)
                GuideCategory.WATER -> drops(t)
                GuideCategory.FIRE -> flame(t)
                GuideCategory.SHELTER -> tent(t)
                GuideCategory.NAVIGATION -> compass(t)
                GuideCategory.SIGNALS -> signal(t)
                GuideCategory.WEATHER -> storm(t)
                GuideCategory.DISASTERS -> quake(t)
                GuideCategory.BASICS -> leaf(t)
            }
        }
    }
}

private val White = Color.White
private val Soft = Color.White.copy(alpha = 0.35f)

private fun DrawScope.heartbeat(t: Float) {
    val w = size.width; val h = size.height; val mid = h * 0.55f
    val path = Path().apply {
        moveTo(0f, mid)
        lineTo(w * 0.30f, mid); lineTo(w * 0.36f, mid - h * 0.10f); lineTo(w * 0.42f, mid + h * 0.08f)
        lineTo(w * 0.48f, mid - h * 0.32f); lineTo(w * 0.54f, mid + h * 0.18f); lineTo(w * 0.60f, mid); lineTo(w, mid)
    }
    drawPath(path, Soft, style = Stroke(5f, cap = StrokeCap.Round))
    // A bright dot runs along the line, like a monitor.
    val x = w * t
    val y = when {
        x < w * 0.30f || x > w * 0.60f -> mid
        x < w * 0.36f -> mid - h * 0.10f * ((x - w * 0.30f) / (w * 0.06f))
        x < w * 0.42f -> mid - h * 0.10f + h * 0.18f * ((x - w * 0.36f) / (w * 0.06f))
        x < w * 0.48f -> mid + h * 0.08f - h * 0.40f * ((x - w * 0.42f) / (w * 0.06f))
        x < w * 0.54f -> mid - h * 0.32f + h * 0.50f * ((x - w * 0.48f) / (w * 0.06f))
        else -> mid + h * 0.18f - h * 0.18f * ((x - w * 0.54f) / (w * 0.06f))
    }
    drawCircle(White, 9f, Offset(x, y))
    // Heart that beats.
    val s = 1f + 0.12f * sin(t * 4 * PI).toFloat().coerceAtLeast(0f)
    val cx = w * 0.84f; val cy = h * 0.28f; val r = h * 0.09f * s
    drawCircle(White, r, Offset(cx - r * 0.7f, cy))
    drawCircle(White, r, Offset(cx + r * 0.7f, cy))
    drawPath(Path().apply { moveTo(cx - r * 1.65f, cy + r * 0.3f); lineTo(cx, cy + r * 2.2f); lineTo(cx + r * 1.65f, cy + r * 0.3f); close() }, White)
}

private fun DrawScope.drops(t: Float) {
    val w = size.width; val h = size.height
    val ground = h * 0.80f
    for (i in 0 until 5) {
        val phase = (t + i * 0.2f) % 1f
        val x = w * (0.15f + i * 0.17f)
        if (phase < 0.7f) {
            val y = h * 0.05f + (ground - h * 0.05f) * (phase / 0.7f)
            drawPath(Path().apply { moveTo(x, y - 14f); quadraticTo(x + 9f, y + 4f, x, y + 8f); quadraticTo(x - 9f, y + 4f, x, y - 14f) }, White)
        } else {
            val k = (phase - 0.7f) / 0.3f
            drawOval(White.copy(alpha = 1f - k), Offset(x - 30f * k, ground - 6f * k), Size(60f * k, 12f * k), style = Stroke(3f))
        }
    }
    drawLine(Soft, Offset(0f, ground + 8f), Offset(w, ground + 8f), 4f)
}

private fun DrawScope.flame(t: Float) {
    val w = size.width; val h = size.height
    val cx = w / 2; val bottom = h * 0.82f
    val flick = sin(t * 6 * PI).toFloat()
    val height = h * (0.55f + 0.06f * flick)
    fun tongue(scale: Float, color: Color, sway: Float) = drawPath(Path().apply {
        moveTo(cx - w * 0.12f * scale, bottom)
        cubicTo(cx - w * 0.16f * scale, bottom - height * scale * 0.5f, cx + sway, bottom - height * scale * 0.7f, cx + sway * 1.5f, bottom - height * scale)
        cubicTo(cx + w * 0.02f, bottom - height * scale * 0.6f, cx + w * 0.16f * scale, bottom - height * scale * 0.4f, cx + w * 0.12f * scale, bottom)
        close()
    }, color)
    tongue(1f, Color(0xFFFFD27A), w * 0.03f * flick)
    tongue(0.62f, White, -w * 0.02f * flick)
    // Logs.
    drawLine(Color(0xFF6B3F22), Offset(cx - w * 0.22f, bottom + 6f), Offset(cx + w * 0.2f, bottom - 10f), 16f, StrokeCap.Round)
    drawLine(Color(0xFF8A5A33), Offset(cx - w * 0.2f, bottom - 10f), Offset(cx + w * 0.22f, bottom + 6f), 16f, StrokeCap.Round)
    // Sparks rising.
    for (i in 0 until 4) {
        val p = (t + i * 0.25f) % 1f
        drawCircle(Color(0xFFFFE3A3).copy(alpha = 1f - p), 4f, Offset(cx + sin((p + i) * 5).toFloat() * w * 0.08f, bottom - height - p * h * 0.25f))
    }
}

private fun DrawScope.tent(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 7) {
        val tw = 0.4f + 0.6f * ((sin((t * 2 + i * 0.37f) * 2 * PI).toFloat() + 1f) / 2f)
        drawCircle(White.copy(alpha = tw), 3.5f, Offset(w * (0.08f + i * 0.13f), h * (0.12f + (i % 3) * 0.08f)))
    }
    val ground = h * 0.85f
    drawLine(Soft, Offset(0f, ground), Offset(w, ground), 4f)
    drawPath(Path().apply { moveTo(w * 0.3f, ground); lineTo(w * 0.5f, h * 0.38f); lineTo(w * 0.7f, ground); close() }, White)
    drawPath(Path().apply { moveTo(w * 0.46f, ground); lineTo(w * 0.5f, h * 0.55f); lineTo(w * 0.54f, ground); close() }, Color(0xFF11694E).copy(alpha = 0.7f))
    drawCircle(White.copy(alpha = 0.9f), h * 0.07f, Offset(w * 0.84f, h * 0.22f))
}

private fun DrawScope.compass(t: Float) {
    val c = center; val r = size.minDimension * 0.36f
    drawCircle(White, r, c, style = Stroke(5f))
    for (i in 0 until 8) rotate(i * 45f, c) { drawLine(Soft, Offset(c.x, c.y - r), Offset(c.x, c.y - r + if (i % 2 == 0) 16f else 9f), 4f) }
    // The needle swings and settles north, then again.
    val swing = 40f * sin(t * 4 * PI).toFloat() * (1f - t)
    rotate(swing, c) {
        drawPath(Path().apply { moveTo(c.x, c.y - r * 0.8f); lineTo(c.x + 10f, c.y); lineTo(c.x - 10f, c.y); close() }, Color(0xFFFF6B6B))
        drawPath(Path().apply { moveTo(c.x, c.y + r * 0.8f); lineTo(c.x + 10f, c.y); lineTo(c.x - 10f, c.y); close() }, White)
    }
    drawCircle(White, 6f, c)
}

private fun DrawScope.signal(t: Float) {
    val w = size.width; val h = size.height
    val base = Offset(w * 0.3f, h * 0.8f)
    drawLine(White, base, Offset(base.x, h * 0.2f), 6f, StrokeCap.Round)
    val wave = sin(t * 2 * PI).toFloat() * 10f
    drawPath(Path().apply {
        moveTo(base.x, h * 0.2f); quadraticTo(base.x + w * 0.12f, h * 0.2f + wave, base.x + w * 0.24f, h * 0.24f)
        lineTo(base.x + w * 0.24f, h * 0.42f); quadraticTo(base.x + w * 0.12f, h * 0.38f + wave, base.x, h * 0.42f); close()
    }, Color(0xFFFFD27A))
    for (i in 0 until 3) {
        val p = (t + i / 3f) % 1f
        drawCircle(White.copy(alpha = 1f - p), h * 0.1f + p * h * 0.4f, Offset(w * 0.72f, h * 0.5f), style = Stroke(4f))
    }
    drawCircle(White, 8f, Offset(w * 0.72f, h * 0.5f))
}

private fun DrawScope.storm(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 9) {
        val p = (t * 2 + i * 0.11f) % 1f
        val x = w * (0.18f + i * 0.08f)
        val y = h * 0.42f + p * h * 0.5f
        drawLine(White.copy(alpha = 0.7f), Offset(x, y), Offset(x - 6f, y + 16f), 3f, StrokeCap.Round)
    }
    val cloud = Color.White.copy(alpha = 0.95f)
    drawCircle(cloud, h * 0.14f, Offset(w * 0.38f, h * 0.3f))
    drawCircle(cloud, h * 0.18f, Offset(w * 0.52f, h * 0.24f))
    drawCircle(cloud, h * 0.13f, Offset(w * 0.66f, h * 0.31f))
    drawRect(cloud, Offset(w * 0.38f, h * 0.3f), Size(w * 0.28f, h * 0.14f))
    if (t in 0.55f..0.68f) drawPath(Path().apply {
        moveTo(w * 0.55f, h * 0.42f); lineTo(w * 0.48f, h * 0.62f); lineTo(w * 0.54f, h * 0.62f); lineTo(w * 0.47f, h * 0.86f); lineTo(w * 0.62f, h * 0.56f); lineTo(w * 0.56f, h * 0.56f); close()
    }, Color(0xFFFFE066))
}

private fun DrawScope.quake(t: Float) {
    val w = size.width; val h = size.height
    val shake = sin(t * 16 * PI).toFloat() * 6f * (if (t < 0.5f) 1f else 0.2f)
    drawRect(White, Offset(w * 0.36f + shake, h * 0.3f), Size(w * 0.28f, h * 0.45f))
    for (r in 0 until 3) for (c in 0 until 2) drawRect(Color.Black.copy(alpha = 0.25f), Offset(w * (0.40f + c * 0.12f) + shake, h * (0.35f + r * 0.13f)), Size(w * 0.07f, h * 0.07f))
    val ground = h * 0.78f
    drawPath(Path().apply {
        moveTo(0f, ground); for (i in 1..12) lineTo(w * i / 12f, ground + (if (i % 2 == 0) -6f else 6f) * (if (t < 0.5f) 1f else 0.3f))
    }, White, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(Path().apply { moveTo(w * 0.2f, ground); lineTo(w * 0.24f, ground + h * 0.08f); lineTo(w * 0.2f, ground + h * 0.14f) }, Soft, style = Stroke(4f))
}

private fun DrawScope.leaf(t: Float) {
    val c = Offset(size.width / 2, size.height * 0.52f)
    val sway = 14f * sin(t * 2 * PI).toFloat()
    rotate(sway, Offset(c.x, size.height * 0.9f)) {
        drawLine(White, Offset(c.x, size.height * 0.9f), c, 5f, StrokeCap.Round)
        val leafPath = Path().apply {
            moveTo(c.x, c.y + 10f)
            cubicTo(c.x - size.width * 0.22f, c.y - size.height * 0.1f, c.x - size.width * 0.05f, c.y - size.height * 0.4f, c.x, c.y - size.height * 0.42f)
            cubicTo(c.x + size.width * 0.05f, c.y - size.height * 0.4f, c.x + size.width * 0.22f, c.y - size.height * 0.1f, c.x, c.y + 10f)
            close()
        }
        drawPath(leafPath, White)
        drawLine(Color(0xFF3A4A44).copy(alpha = 0.4f), Offset(c.x, c.y + 6f), Offset(c.x, c.y - size.height * 0.38f), 3f)
    }
}
