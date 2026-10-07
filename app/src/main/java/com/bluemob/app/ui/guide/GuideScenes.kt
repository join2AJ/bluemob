package com.bluemob.app.ui.guide

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One animation per guide, so every guide has its own picture of what to do: hands pressing for CPR, water running
 * over a burn, a stick's shadow turning to show east and west, a rope tying itself into a knot. [t] runs 0..1 and
 * loops; [duration] says how long one loop takes (teaching scenes run slower).
 */
object GuideScenes {
    /** Milliseconds per loop: slower for scenes that teach a sequence. */
    fun duration(id: String): Int = when (id) {
        "north", "find-bluemob", "knots-reef", "knots-bowline", "knots-clove", "knots-taut", "firecraft-teepee", "shelters-debris" -> 7_000
        else -> 2_800
    }

    fun has(id: String) = id in scenes

    fun draw(scope: DrawScope, id: String, t: Float): Boolean {
        val f = scenes[id] ?: return false
        scope.f(t)
        return true
    }

    private val W = Color.White
    private val Soft = Color.White.copy(alpha = 0.35f)
    private val Faint = Color.White.copy(alpha = 0.18f)
    private val Warm = Color(0xFFFFD27A)
    private val Red = Color(0xFFFF6B6B)
    private val Ice = Color(0xFFBFE6FF)
    private val Dark = Color(0xFF1D2A24)

    private fun wave(t: Float, n: Float = 1f) = sin(t * 2 * PI * n).toFloat()
    private fun ping(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    // ---------- small building blocks ----------
    private fun DrawScope.ground(y: Float = size.height * 0.85f) = drawLine(Soft, Offset(0f, y), Offset(size.width, y), 4f)

    private fun DrawScope.person(x: Float, feet: Float, h: Float, color: Color = W, armL: Float = 30f, armR: Float = -30f, lean: Float = 0f) {
        val head = h * 0.14f
        val neck = Offset(x + lean, feet - h * 0.72f)
        val hip = Offset(x, feet - h * 0.38f)
        drawCircle(color, head, Offset(neck.x, neck.y - head * 1.1f))
        drawLine(color, neck, hip, h * 0.07f, StrokeCap.Round)
        drawLine(color, hip, Offset(x - h * 0.15f, feet), h * 0.06f, StrokeCap.Round)
        drawLine(color, hip, Offset(x + h * 0.15f, feet), h * 0.06f, StrokeCap.Round)
        val sh = Offset(neck.x, neck.y + h * 0.05f)
        fun arm(deg: Float, side: Float) {
            val a = Math.toRadians(deg.toDouble())
            drawLine(color, sh, Offset(sh.x + side * h * 0.3f * cos(a).toFloat(), sh.y + h * 0.3f * sin(a).toFloat()), h * 0.05f, StrokeCap.Round)
        }
        arm(armL, -1f); arm(armR, 1f)
    }

    private fun DrawScope.sun(c: Offset, r: Float, t: Float) {
        drawCircle(Warm, r, c)
        for (i in 0 until 8) rotate(i * 45f + t * 45f, c) { drawLine(Warm.copy(alpha = 0.7f), Offset(c.x, c.y - r * 1.4f), Offset(c.x, c.y - r * 1.9f), 4f, StrokeCap.Round) }
    }

    private fun DrawScope.drop(c: Offset, s: Float, color: Color = W) = drawPath(Path().apply {
        moveTo(c.x, c.y - s); quadraticTo(c.x + s * 0.7f, c.y + s * 0.3f, c.x, c.y + s * 0.6f); quadraticTo(c.x - s * 0.7f, c.y + s * 0.3f, c.x, c.y - s)
    }, color)

    private fun DrawScope.flame(c: Offset, h: Float, t: Float) {
        val f = wave(t, 3f)
        drawPath(Path().apply {
            moveTo(c.x - h * 0.3f, c.y); cubicTo(c.x - h * 0.4f, c.y - h * 0.5f, c.x + f * h * 0.1f, c.y - h * 0.7f, c.x + f * h * 0.12f, c.y - h)
            cubicTo(c.x + h * 0.1f, c.y - h * 0.6f, c.x + h * 0.4f, c.y - h * 0.4f, c.x + h * 0.3f, c.y); close()
        }, Warm)
        drawPath(Path().apply {
            moveTo(c.x - h * 0.15f, c.y); cubicTo(c.x - h * 0.2f, c.y - h * 0.3f, c.x, c.y - h * 0.4f, c.x - f * h * 0.05f, c.y - h * 0.55f)
            cubicTo(c.x + h * 0.05f, c.y - h * 0.3f, c.x + h * 0.2f, c.y - h * 0.2f, c.x + h * 0.15f, c.y); close()
        }, W)
    }

    private fun DrawScope.mountain(x: Float, base: Float, w: Float, h: Float, color: Color = Soft, snow: Boolean = false) {
        drawPath(Path().apply { moveTo(x - w / 2, base); lineTo(x, base - h); lineTo(x + w / 2, base); close() }, color)
        if (snow) drawPath(Path().apply { moveTo(x - w * 0.12f, base - h * 0.76f); lineTo(x, base - h); lineTo(x + w * 0.12f, base - h * 0.76f); close() }, W)
    }

    private fun DrawScope.tree(x: Float, base: Float, h: Float, color: Color = Soft) {
        drawLine(color, Offset(x, base), Offset(x, base - h * 0.3f), h * 0.08f)
        drawPath(Path().apply { moveTo(x - h * 0.3f, base - h * 0.25f); lineTo(x, base - h); lineTo(x + h * 0.3f, base - h * 0.25f); close() }, color)
    }

    private fun DrawScope.waves(y: Float, t: Float, color: Color = W, amp: Float = 8f, width: Float = 4f) {
        val p = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= size.width) { lineTo(x, y + amp * sin((x / size.width * 4 + t * 2) * PI).toFloat()); x += 8f }
        }
        drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round))
    }

    /** A rope drawn along [points], only the first [part] of it (0..1): knots "tie themselves". */
    private fun DrawScope.rope(points: List<Offset>, part: Float, color: Color = Warm, width: Float = 10f) {
        if (points.size < 2) return
        val segs = points.zipWithNext()
        val lens = segs.map { (a, b) -> (b - a).getDistance() }
        var left = lens.sum() * part
        val p = Path().apply { moveTo(points[0].x, points[0].y) }
        for ((i, s) in segs.withIndex()) {
            if (left <= 0) break
            val (a, b) = s
            val k = (left / lens[i]).coerceAtMost(1f)
            p.lineTo(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
            left -= lens[i]
        }
        drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    private fun DrawScope.at(fx: Float, fy: Float) = Offset(size.width * fx, size.height * fy)

    // ---------- the scenes ----------
    private val scenes: Map<String, DrawScope.(Float) -> Unit> = mapOf(
        // First aid
        "cpr" to { t ->
            val press = abs(wave(t, 4f))
            drawRoundRect(Soft, at(0.18f, 0.66f), Size(size.width * 0.64f, size.height * 0.12f), androidx.compose.ui.geometry.CornerRadius(20f))
            drawCircle(W, size.height * 0.07f, at(0.2f, 0.72f))
            val hands = at(0.5f, 0.42f + press * 0.12f)
            drawLine(W, at(0.42f, 0.2f), hands, 14f, StrokeCap.Round); drawLine(W, at(0.58f, 0.2f), hands, 14f, StrokeCap.Round)
            drawCircle(Red, 10f + press * 6f, at(0.5f, 0.66f))
            drawLine(Soft, at(0.75f, 0.3f), at(0.75f, 0.3f + 0.1f * press), 6f)
        },
        "bleed" to { t ->
            val p = ping(t, 0f, 0.6f)
            drawLine(W, at(0.1f, 0.6f), at(0.9f, 0.6f), size.height * 0.16f, StrokeCap.Round)
            drawCircle(Red.copy(alpha = 1f - p * 0.8f), size.height * (0.12f - p * 0.08f), at(0.5f, 0.6f))
            drawRoundRect(Soft, at(0.4f, 0.28f + 0.12f * p), Size(size.width * 0.2f, size.height * 0.16f), androidx.compose.ui.geometry.CornerRadius(14f))
            for (i in 0..2) drawLine(W, at(0.45f + i * 0.05f, 0.3f + 0.12f * p), at(0.45f + i * 0.05f, 0.18f + 0.12f * p), 8f, StrokeCap.Round)
        },
        "burns" to { t ->
            drawRect(Soft, at(0.42f, 0.08f), Size(size.width * 0.16f, size.height * 0.1f))
            for (i in 0 until 6) { val y = (t + i / 6f) % 1f; drop(at(0.5f + 0.02f * wave(y + i), 0.2f + y * 0.45f), 7f, Ice) }
            drawRoundRect(W, at(0.32f, 0.66f), Size(size.width * 0.36f, size.height * 0.12f), androidx.compose.ui.geometry.CornerRadius(30f))
            drawCircle(Red.copy(alpha = 0.6f - 0.4f * t), 12f, at(0.5f, 0.72f))
            drawArc(Soft, 0f, 360f * t, false, at(0.78f, 0.15f), Size(50f, 50f), style = Stroke(5f))
        },
        "choke" to { t ->
            val hit = ping(t % 0.5f, 0f, 0.15f)
            person(size.width * 0.4f, size.height * 0.88f, size.height * 0.7f, armL = 120f, armR = 120f, lean = 18f)
            person(size.width * 0.65f, size.height * 0.88f, size.height * 0.7f, armL = -10f - 40 * hit, armR = 20f)
            for (i in 0 until 5) drawCircle(if (i < (t * 10).toInt() % 6) W else Faint, 7f, at(0.1f + i * 0.06f, 0.12f))
        },
        "hypo" to { t ->
            drawRoundRect(W, at(0.45f, 0.12f), Size(size.width * 0.1f, size.height * 0.62f), androidx.compose.ui.geometry.CornerRadius(30f), style = Stroke(5f))
            val lvl = 0.6f - 0.4f * t
            drawRect(Ice, at(0.47f, 0.7f - lvl), Size(size.width * 0.06f, size.height * lvl))
            drawCircle(Ice, size.height * 0.08f, at(0.5f, 0.8f))
            for (i in 0 until 6) { val y = (t + i / 6f) % 1f; drawCircle(W, 4f, at(0.12f + i * 0.15f + 0.02f * wave(y), y)) }
        },
        "heat" to { t ->
            sun(at(0.25f, 0.3f), size.height * 0.12f, t)
            for (i in 0..2) waves(size.height * (0.6f + i * 0.08f), t + i * 0.2f, Soft, 5f, 3f)
            drawRoundRect(W, at(0.72f, 0.14f), Size(size.width * 0.08f, size.height * 0.56f), androidx.compose.ui.geometry.CornerRadius(30f), style = Stroke(5f))
            val lvl = 0.2f + 0.4f * t
            drawRect(Red, at(0.735f, 0.68f - lvl), Size(size.width * 0.05f, size.height * lvl))
        },
        "snake" to { t ->
            val p = Path().apply { var x = 0f; moveTo(0f, size.height * 0.6f); while (x < size.width * 0.8f) { x += 6f; lineTo(x, size.height * (0.6f + 0.1f * sin((x / size.width * 3 - t * 2) * PI).toFloat())) } }
            drawPath(p, W, style = Stroke(16f, cap = StrokeCap.Round))
            val hx = size.width * 0.8f; val hy = size.height * (0.6f + 0.1f * sin((0.8 * 3 - t * 2) * PI).toFloat())
            drawCircle(W, 14f, Offset(hx, hy)); drawLine(Red, Offset(hx + 12f, hy), Offset(hx + 30f, hy + 4f * wave(t, 8f)), 3f)
        },
        "fracture" to { t ->
            drawLine(W, at(0.1f, 0.55f), at(0.9f, 0.55f), size.height * 0.12f, StrokeCap.Round)
            drawLine(Soft, at(0.1f, 0.42f), at(0.9f, 0.42f), 10f, StrokeCap.Round); drawLine(Soft, at(0.1f, 0.68f), at(0.9f, 0.68f), 10f, StrokeCap.Round)
            for (i in 0 until 4) if (t > i * 0.22f) drawLine(Warm, at(0.2f + i * 0.2f, 0.36f), at(0.2f + i * 0.2f, 0.74f), 10f)
        },
        "find-water" to { t ->
            mountain(size.width * 0.25f, size.height * 0.9f, size.width * 0.6f, size.height * 0.6f)
            mountain(size.width * 0.75f, size.height * 0.9f, size.width * 0.6f, size.height * 0.55f)
            for (i in 0 until 5) { val k = (t + i / 5f) % 1f; drop(Offset(size.width * 0.5f, size.height * (0.4f + k * 0.45f)), 7f, Ice) }
            drawOval(Ice, at(0.38f, 0.84f), Size(size.width * 0.24f, size.height * 0.08f))
        },
        "purify" to { t ->
            flame(at(0.5f, 0.92f), size.height * 0.2f, t)
            drawRoundRect(W, at(0.3f, 0.45f), Size(size.width * 0.4f, size.height * 0.28f), androidx.compose.ui.geometry.CornerRadius(12f))
            for (i in 0 until 6) { val k = (t * 2 + i / 6f) % 1f; drawCircle(Ice.copy(alpha = 1f - k), 6f, at(0.35f + i * 0.06f, 0.42f - k * 0.3f)) }
            drawArc(W, -90f, 360f * t, false, at(0.8f, 0.1f), Size(46f, 46f), style = Stroke(5f))
        },
        "fire" to { t -> flame(at(0.5f, 0.82f), size.height * 0.55f, t); drawLine(Color(0xFF6B3F22), at(0.3f, 0.86f), at(0.7f, 0.8f), 14f, StrokeCap.Round); drawLine(Color(0xFF8A5A33), at(0.3f, 0.8f), at(0.7f, 0.86f), 14f, StrokeCap.Round) },
        "shelter" to { t ->
            ground()
            drawLine(Warm, at(0.2f, 0.45f), at(0.8f, 0.45f), 10f, StrokeCap.Round)
            for (i in 0 until 7) if (t > i / 8f) drawLine(Soft, at(0.22f + i * 0.09f, 0.45f), at(0.18f + i * 0.09f, 0.85f), 8f, StrokeCap.Round)
            for (i in 0 until 10) if (t > 0.7f) drawCircle(Color(0xFF9CCB7A), 6f, at(0.2f + i * 0.06f, 0.82f - (i % 3) * 0.04f))
        },
        "north" to { t -> shadowStick(t) },
        "lost" to { t ->
            val letters = listOf("S" to "Stop", "T" to "Think", "O" to "Observe", "P" to "Plan")
            val k = (t * 4).toInt().coerceAtMost(3)
            letters.forEachIndexed { i, (l, _) -> drawCircle(if (i <= k) W else Faint, size.height * 0.11f, at(0.15f + i * 0.23f, 0.4f)); if (i <= k) drawCircle(Dark, size.height * 0.05f, at(0.15f + i * 0.23f, 0.4f)) }
            person(size.width * (0.15f + k * 0.23f), size.height * 0.92f, size.height * 0.32f)
        },
        "signals" to { t ->
            ground()
            for (i in 0..2) { val on = ((t * 3).toInt() % 3) >= i; if (on) flame(at(0.25f + i * 0.25f, 0.84f), size.height * 0.35f, t + i * 0.3f) else drawCircle(Soft, 8f, at(0.25f + i * 0.25f, 0.82f)) }
        },
        "help-sos" to { t ->
            drawRoundRect(W, at(0.12f, 0.2f), Size(size.width * 0.18f, size.height * 0.6f), androidx.compose.ui.geometry.CornerRadius(16f))
            drawCircle(Red, size.height * 0.08f, at(0.21f, 0.5f))
            for (i in 0..2) { val k = (t + i / 3f) % 1f; drawCircle(Red.copy(alpha = 1f - k), size.height * (0.1f + k * 0.3f), at(0.21f, 0.5f), style = Stroke(4f)) }
            person(size.width * (0.85f - 0.35f * t), size.height * 0.9f, size.height * 0.5f, armL = 60f * wave(t, 4f), armR = -60f * wave(t, 4f), lean = -8f)
        },
        "lightning" to { t ->
            drawCircle(W, size.height * 0.14f, at(0.4f, 0.25f)); drawCircle(W, size.height * 0.18f, at(0.55f, 0.2f)); drawCircle(W, size.height * 0.13f, at(0.68f, 0.26f))
            if (t in 0.1f..0.18f || t in 0.6f..0.66f) drawPath(Path().apply { moveTo(size.width * 0.55f, size.height * 0.35f); lineTo(size.width * 0.48f, size.height * 0.6f); lineTo(size.width * 0.54f, size.height * 0.6f); lineTo(size.width * 0.47f, size.height * 0.9f); lineTo(size.width * 0.62f, size.height * 0.55f); lineTo(size.width * 0.56f, size.height * 0.55f); close() }, Warm)
            val n = (30 - (t * 30)).toInt(); drawCircle(Soft, 24f, at(0.15f, 0.75f)); drawArc(W, -90f, 360f * (n / 30f), false, at(0.15f, 0.75f) - Offset(24f, 24f), Size(48f, 48f), style = Stroke(5f))
        },
        "quake" to { t ->
            val shake = wave(t, 8f) * 6f * (if (t < 0.5f) 1f else 0.2f)
            drawRect(Soft, at(0.6f + shake / size.width, 0.25f), Size(size.width * 0.25f, size.height * 0.6f))
            drawRoundRect(W, at(0.12f, 0.6f), Size(size.width * 0.32f, size.height * 0.06f), androidx.compose.ui.geometry.CornerRadius(6f))
            drawLine(W, at(0.15f, 0.66f), at(0.15f, 0.85f), 6f); drawLine(W, at(0.41f, 0.66f), at(0.41f, 0.85f), 6f)
            drawCircle(W, size.height * 0.06f, at(0.25f, 0.76f)); drawOval(W, at(0.27f, 0.72f), Size(size.width * 0.1f, size.height * 0.1f))
            ground(size.height * 0.86f + shake)
        },
        "flood" to { t ->
            mountain(size.width * 0.8f, size.height * 0.95f, size.width * 0.5f, size.height * 0.7f)
            drawRect(W, at(0.2f, 0.45f), Size(size.width * 0.2f, size.height * 0.3f))
            drawPath(Path().apply { moveTo(size.width * 0.18f, size.height * 0.45f); lineTo(size.width * 0.3f, size.height * 0.32f); lineTo(size.width * 0.42f, size.height * 0.45f); close() }, W)
            val lvl = 0.9f - 0.3f * t
            drawRect(Ice.copy(alpha = 0.7f), Offset(0f, size.height * lvl), Size(size.width, size.height))
            person(size.width * (0.55f + 0.2f * t), size.height * (0.85f - 0.4f * t), size.height * 0.28f)
        },
        "battery-low" to { t ->
            drawRoundRect(W, at(0.25f, 0.3f), Size(size.width * 0.45f, size.height * 0.4f), androidx.compose.ui.geometry.CornerRadius(12f), style = Stroke(6f))
            drawRect(W, at(0.7f, 0.42f), Size(size.width * 0.04f, size.height * 0.16f))
            val lvl = 0.9f * (1f - t)
            drawRect(if (lvl < 0.25f) Red else W, at(0.27f, 0.33f), Size(size.width * 0.41f * lvl, size.height * 0.34f))
        },
        "recharge" to { t ->
            sun(at(0.2f, 0.25f), size.height * 0.1f, t)
            drawPath(Path().apply { moveTo(size.width * 0.12f, size.height * 0.85f); lineTo(size.width * 0.22f, size.height * 0.55f); lineTo(size.width * 0.48f, size.height * 0.55f); lineTo(size.width * 0.4f, size.height * 0.85f); close() }, Ice)
            for (i in 0 until 4) { val k = (t + i / 4f) % 1f; drawCircle(Warm, 5f, Offset(size.width * (0.45f + 0.25f * k), size.height * 0.7f)) }
            drawRoundRect(W, at(0.72f, 0.5f), Size(size.width * 0.18f, size.height * 0.36f), androidx.compose.ui.geometry.CornerRadius(10f), style = Stroke(5f))
            drawRect(Warm, at(0.735f, 0.84f - 0.32f * t), Size(size.width * 0.15f, size.height * 0.32f * t))
        },
        "threes" to { t ->
            listOf("3 min" to 0.2f, "3 h" to 0.4f, "3 d" to 0.65f, "3 wk" to 0.95f).forEachIndexed { i, (_, len) ->
                drawRoundRect(Faint, at(0.1f, 0.15f + i * 0.2f), Size(size.width * 0.8f, size.height * 0.1f), androidx.compose.ui.geometry.CornerRadius(20f))
                drawRoundRect(listOf(Ice, W, Ice, Warm)[i], at(0.1f, 0.15f + i * 0.2f), Size(size.width * 0.8f * len * ping(t, 0f, 0.8f), size.height * 0.1f), androidx.compose.ui.geometry.CornerRadius(20f))
            }
        },
        "find-bluemob" to { t -> findWithBlueMob(t) },
        // Pack: heat
        "heat-heatstroke" to { t -> sun(at(0.8f, 0.2f), size.height * 0.1f, t); drawRect(Faint, at(0.05f, 0.5f), Size(size.width * 0.5f, size.height * 0.08f)); drawRoundRect(W, at(0.1f, 0.62f), Size(size.width * 0.5f, size.height * 0.1f), androidx.compose.ui.geometry.CornerRadius(20f)); for (i in 0 until 6) { val k = (t + i / 6f) % 1f; drop(at(0.15f + i * 0.07f, 0.3f + k * 0.3f), 6f, Ice) } },
        "heat-heat-water" to { t -> sun(at(0.2f, 0.2f), size.height * 0.1f, t); drawRoundRect(W, at(0.55f, 0.2f), Size(size.width * 0.2f, size.height * 0.65f), androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(5f)); drawRect(Ice, at(0.565f, 0.83f - 0.55f * (1f - t)), Size(size.width * 0.17f, size.height * 0.55f * (1f - t))) },
        "heat-desert-travel" to { t -> drawCircle(W, size.height * 0.1f, at(0.8f, 0.2f)); for (i in 0..2) drawOval(Soft, at(-0.2f + i * 0.45f, 0.7f), Size(size.width * 0.7f, size.height * 0.4f)); person(size.width * (0.1f + 0.7f * t), size.height * 0.78f, size.height * 0.28f, armL = 40f * wave(t, 4f), armR = -40f * wave(t, 4f)); for (i in 0 until 8) drawCircle(W, 2.5f, at(0.05f + i * 0.12f, 0.1f + (i % 3) * 0.06f)) },
        // Pack: monsoon
        "monsoon-flash-flood" to { t -> drawRect(Soft, Offset(0f, 0f), Size(size.width * 0.2f, size.height)); drawRect(Soft, at(0.8f, 0f), Size(size.width * 0.2f, size.height)); val x = size.width * (1.2f - 1.4f * t); drawRect(Ice.copy(alpha = 0.8f), Offset(x, size.height * 0.5f), Size(size.width, size.height * 0.5f)); waves(size.height * 0.5f, t, W, 10f); person(size.width * 0.12f, size.height * 0.35f, size.height * 0.25f) },
        "monsoon-river-crossing" to { t -> drawRect(Ice.copy(alpha = 0.5f), at(0f, 0.45f), Size(size.width, size.height * 0.4f)); for (i in 0..2) waves(size.height * (0.52f + i * 0.1f), t + i * 0.3f, Soft, 4f, 3f); drawLine(Warm, at(0.05f, 0.4f), at(0.95f, 0.4f), 5f); for (i in 0..2) person(size.width * (0.25f + i * 0.18f + 0.02f * wave(t)), size.height * 0.75f, size.height * 0.45f, armL = -60f, armR = -60f) },
        "monsoon-landslide" to { t -> drawPath(Path().apply { moveTo(0f, size.height * 0.2f); lineTo(size.width, size.height * 0.9f); lineTo(0f, size.height * 0.9f); close() }, Soft); for (i in 0 until 6) { val k = ((t + i / 6f) % 1f); drawCircle(W, 8f + i % 3 * 3f, Offset(size.width * (0.05f + k * 0.7f), size.height * (0.25f + k * 0.55f))) }; person(size.width * 0.85f, size.height * 0.5f, size.height * 0.3f, armL = -70f, armR = -70f) },
        "monsoon-monsoon-health" to { t -> for (i in 0 until 12) { val k = (t * 2 + i / 12f) % 1f; drawLine(Ice, at(0.05f + i * 0.08f, k), at(0.03f + i * 0.08f, k + 0.08f), 3f) }; val m = at(0.5f + 0.25f * wave(t), 0.45f + 0.1f * wave(t, 2f)); drawCircle(W, 6f, m); drawLine(W, m, m + Offset(-14f, -10f * wave(t, 12f)), 3f); drawLine(W, m, m + Offset(14f, -10f * wave(t, 12f)), 3f); drawRoundRect(W, at(0.3f, 0.75f), Size(size.width * 0.4f, size.height * 0.08f), androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(4f)) },
        // Pack: mountains
        "mountains-altitude-sickness" to { t -> mountain(size.width * 0.5f, size.height * 0.95f, size.width * 0.9f, size.height * 0.8f, snow = true); val k = if (t < 0.6f) t / 0.6f else 1f - (t - 0.6f) / 0.4f; person(size.width * (0.2f + 0.25f * k), size.height * (0.9f - 0.55f * k), size.height * 0.2f); drawRect(W, at(0.85f, 0.15f), Size(size.width * 0.04f, size.height * 0.6f), style = Stroke(3f)); drawRect(Warm, at(0.85f, 0.75f - 0.6f * k), Size(size.width * 0.04f, size.height * 0.6f * k)) },
        "mountains-hypothermia" to { t -> for (i in 0 until 8) { val k = (t + i / 8f) % 1f; drawCircle(W, 3.5f, at(0.05f + i * 0.12f, k)) }; person(size.width * 0.5f + 3f * wave(t, 10f), size.height * 0.9f, size.height * 0.6f); drawPath(Path().apply { moveTo(size.width * 0.38f, size.height * 0.4f); lineTo(size.width * 0.62f, size.height * 0.4f); lineTo(size.width * 0.66f, size.height * 0.8f); lineTo(size.width * 0.34f, size.height * 0.8f); close() }, Warm.copy(alpha = ping(t, 0.3f, 0.7f))) },
        "mountains-frostbite" to { t -> val c = Color(lerp(0xFFE0F2FF.toInt(), 0xFFFFD9C7.toInt(), ping(t, 0.4f, 0.9f))); for (i in 0 until 4) drawRoundRect(c, at(0.3f + i * 0.1f, 0.25f), Size(size.width * 0.07f, size.height * 0.35f), androidx.compose.ui.geometry.CornerRadius(20f)); drawRoundRect(c, at(0.28f, 0.55f), Size(size.width * 0.44f, size.height * 0.3f), androidx.compose.ui.geometry.CornerRadius(30f)); if (t > 0.4f) for (i in 0..2) waves(size.height * (0.15f + i * 0.05f), t, Warm.copy(alpha = 0.5f), 3f, 2f) },
        "mountains-snow-safety" to { t -> mountain(size.width * 0.4f, size.height * 0.95f, size.width, size.height * 0.85f, snow = true); for (i in 0 until 10) { val k = ping(t, i * 0.05f, 0.6f + i * 0.04f); drawCircle(W, 10f, Offset(size.width * (0.3f + k * 0.5f + (i % 3) * 0.04f), size.height * (0.2f + k * 0.65f))) }; person(size.width * 0.88f, size.height * 0.9f, size.height * 0.3f, armL = -60f, armR = -60f) },
        // Pack: wildlife
        "wildlife-snakebite" to { t -> drawLine(W, at(0.15f, 0.6f), at(0.85f, 0.6f), size.height * 0.16f, StrokeCap.Round); drawCircle(Red, 5f, at(0.48f, 0.58f)); drawCircle(Red, 5f, at(0.54f, 0.58f)); drawPath(Path().apply { moveTo(size.width * 0.2f, size.height * 0.2f); quadraticTo(size.width * 0.5f, size.height * (0.45f + 0.05f * wave(t)), size.width * 0.8f, size.height * 0.2f) }, Warm, style = Stroke(10f, cap = StrokeCap.Round)); drawArc(W, -90f, 360f * t, false, at(0.82f, 0.72f), Size(40f, 40f), style = Stroke(4f)) },
        "wildlife-dog-bite" to { t -> for (i in 0 until 4) { val p = at(0.1f + i * 0.22f, 0.75f - i * 0.12f); if (t > i * 0.2f) { drawCircle(W, 12f, p); for (j in 0..2) drawCircle(W, 5f, p + Offset(-12f + j * 12f, -16f)) } }; for (i in 0 until 5) { val k = (t + i / 5f) % 1f; drop(at(0.85f, 0.1f + k * 0.4f), 6f, Ice) } },
        "wildlife-bees" to { t -> drawOval(Warm, at(0.1f, 0.3f), Size(size.width * 0.2f, size.height * 0.35f)); for (i in 0 until 5) { val a = t * 2 * PI + i; val c = at(0.5f + 0.3f * cos(a).toFloat(), 0.45f + 0.25f * sin(a * 2).toFloat()); drawOval(Warm, c - Offset(8f, 6f), Size(16f, 12f)); drawCircle(W.copy(alpha = 0.7f), 5f, c + Offset(0f, -8f)) }; person(size.width * (0.75f + 0.15f * t), size.height * 0.92f, size.height * 0.35f, armL = 40f * wave(t, 6f), armR = -40f * wave(t, 6f)) },
        "wildlife-large-animals" to { t -> drawOval(Soft, at(0.08f, 0.35f), Size(size.width * 0.35f, size.height * 0.35f)); drawCircle(Soft, size.height * 0.13f, at(0.42f, 0.42f)); drawLine(Soft, at(0.48f, 0.48f), at(0.5f, 0.8f), 12f, StrokeCap.Round); for (i in 0..3) drawLine(Soft, at(0.13f + i * 0.08f, 0.66f), at(0.13f + i * 0.08f, 0.88f), 14f); person(size.width * (0.7f + 0.2f * t), size.height * 0.9f, size.height * 0.4f, armL = 20f, armR = 20f) },
        // Pack: knots (rope ties itself)
        "knots-reef" to { t -> rope(listOf(at(0.05f, 0.5f), at(0.4f, 0.5f), at(0.5f, 0.35f), at(0.6f, 0.5f), at(0.5f, 0.65f), at(0.42f, 0.5f), at(0.58f, 0.5f), at(0.95f, 0.5f)), ping(t, 0f, 0.8f)); rope(listOf(at(0.95f, 0.55f), at(0.62f, 0.55f), at(0.5f, 0.42f), at(0.38f, 0.55f), at(0.05f, 0.55f)), ping(t, 0.2f, 0.9f), W, 8f) },
        "knots-bowline" to { t -> rope(listOf(at(0.5f, 0.05f), at(0.5f, 0.35f), at(0.38f, 0.45f), at(0.5f, 0.55f), at(0.62f, 0.45f), at(0.5f, 0.35f), at(0.3f, 0.75f), at(0.5f, 0.92f), at(0.7f, 0.75f), at(0.55f, 0.45f), at(0.45f, 0.3f), at(0.55f, 0.25f)), ping(t, 0f, 0.85f)) },
        "knots-clove" to { t -> drawRect(Color(0xFF8A5A33), at(0.46f, 0.02f), Size(size.width * 0.08f, size.height * 0.96f)); rope(listOf(at(0.1f, 0.3f), at(0.44f, 0.3f), at(0.56f, 0.38f), at(0.44f, 0.46f), at(0.56f, 0.54f), at(0.44f, 0.62f), at(0.56f, 0.62f), at(0.9f, 0.62f)), ping(t, 0f, 0.85f)) },
        "knots-taut" to { t -> drawCircle(Soft, 14f, at(0.08f, 0.5f)); drawLine(W, at(0.08f, 0.5f), at(0.8f, 0.3f), 3f); rope(listOf(at(0.92f, 0.85f), at(0.8f, 0.3f), at(0.7f, 0.42f), at(0.78f, 0.48f), at(0.68f, 0.52f), at(0.76f, 0.58f), at(0.66f, 0.62f), at(0.74f, 0.7f), at(0.92f, 0.7f)), ping(t, 0f, 0.85f)) },
        // Pack: fire craft
        "firecraft-teepee" to { t -> ground(); drawCircle(Color(0xFFC9B48A), 18f, at(0.5f, 0.8f)); for (i in 0 until 6) if (t > i * 0.1f) { val a = -60f + i * 24f; rotate(a, at(0.5f, 0.85f)) { drawLine(Color(0xFFB98A57), at(0.5f, 0.85f), at(0.5f, 0.35f), 8f, StrokeCap.Round) } }; if (t > 0.7f) flame(at(0.5f, 0.84f), size.height * 0.4f * ping(t, 0.7f, 1f), t) },
        "firecraft-sparks" to { t -> drawLine(Soft, at(0.2f, 0.25f), at(0.55f, 0.55f), 14f, StrokeCap.Round); drawLine(W, at(0.35f, 0.15f), at(0.62f, 0.55f), 8f, StrokeCap.Round); for (i in 0 until 10) { val k = (t * 2 + i / 10f) % 1f; drawCircle(Warm.copy(alpha = 1f - k), 4f, Offset(size.width * (0.6f + k * 0.2f + (i % 3) * 0.02f), size.height * (0.55f + k * 0.3f))) }; drawCircle(Color(0xFFC9B48A), 22f, at(0.75f, 0.88f)) },
        "firecraft-keep" to { t -> flame(at(0.5f, 0.85f), size.height * (0.35f + 0.15f * ping(t % 0.5f, 0f, 0.5f)), t); val k = ping(t % 0.5f, 0f, 0.4f); drawLine(Color(0xFF8A5A33), Offset(size.width * (0.9f - 0.3f * k), size.height * (0.4f + 0.4f * k)), Offset(size.width * (0.75f - 0.3f * k), size.height * (0.5f + 0.4f * k)), 12f, StrokeCap.Round) },
        // Pack: shelters
        "shelters-debris" to { t -> ground(); drawLine(Color(0xFFB98A57), at(0.1f, 0.85f), at(0.85f, 0.45f), 10f, StrokeCap.Round); for (i in 0 until 8) if (t > i * 0.08f) drawLine(Soft, at(0.15f + i * 0.09f, 0.85f - i * 0.05f), at(0.2f + i * 0.09f, 0.85f), 6f); if (t > 0.7f) for (i in 0 until 14) drawCircle(Color(0xFF9CCB7A), 7f, at(0.15f + i * 0.05f, 0.83f - i * 0.025f - (i % 2) * 0.03f)) },
        "shelters-tarp" to { t -> ground(); drawLine(W, at(0.1f, 0.4f), at(0.9f, 0.4f), 3f); drawPath(Path().apply { moveTo(size.width * 0.15f, size.height * 0.85f); lineTo(size.width * 0.5f, size.height * 0.4f); lineTo(size.width * 0.85f, size.height * 0.85f); close() }, Ice.copy(alpha = 0.8f)); for (i in 0 until 10) { val k = (t * 2 + i / 10f) % 1f; drawLine(Ice, at(0.05f + i * 0.1f, k * 0.4f), at(0.04f + i * 0.1f, k * 0.4f + 0.05f), 3f) } },
        "shelters-ground" to { t -> ground(size.height * 0.88f); for (i in 0 until 6) if (t > i * 0.12f) drawRoundRect(Color(0xFF9CCB7A), at(0.2f, 0.84f - i * 0.04f), Size(size.width * 0.6f, size.height * 0.035f), androidx.compose.ui.geometry.CornerRadius(10f)); for (i in 0 until 4) { val k = (t + i / 4f) % 1f; drawLine(Ice.copy(alpha = 1f - ping(t, 0.3f, 0.8f)), at(0.3f + i * 0.12f, 0.98f), at(0.3f + i * 0.12f, 0.98f - 0.1f * k), 3f) } },
    )

    private fun lerp(a: Int, b: Int, k: Float): Long {
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * k).toInt().coerceIn(0, 255)
        return (0xFF000000L or (mix(16).toLong() shl 16) or (mix(8).toLong() shl 8) or mix(0).toLong())
    }

    /**
     * Finding north with a stick: the sun moves, the shadow tip moves the other way; mark the first tip, wait, mark the
     * second; the line from first to second points east (west to east), and north is across it.
     */
    private fun DrawScope.shadowStick(t: Float) {
        val base = at(0.5f, 0.75f)
        drawOval(Faint, at(0.1f, 0.62f), Size(size.width * 0.8f, size.height * 0.26f))
        val sunA = (PI * (0.25 + 0.4 * t)).toFloat()
        val sun = Offset(size.width * (0.5f + 0.42f * cos(sunA)), size.height * (0.45f - 0.35f * sin(sunA)))
        sun(sun, size.height * 0.06f, t)
        drawLine(Color(0xFF8A5A33), base, base - Offset(0f, size.height * 0.28f), 8f, StrokeCap.Round)
        val tip = base + Offset(-cos(sunA) * size.width * 0.3f, size.height * 0.05f)
        drawLine(Dark.copy(alpha = 0.6f), base, tip, 6f, StrokeCap.Round)
        val first = base + Offset(-cos((PI * 0.25).toFloat()) * size.width * 0.3f, size.height * 0.05f)
        drawCircle(W, 8f, first)
        if (t > 0.6f) {
            val second = base + Offset(-cos((PI * (0.25 + 0.4 * 0.6)).toFloat()) * size.width * 0.3f, size.height * 0.05f)
            drawCircle(Warm, 8f, second)
            val k = ping(t, 0.65f, 0.85f)
            drawLine(W, first, first + (second - first) * (1f + k), 4f, StrokeCap.Round)
            if (t > 0.85f) {
                val mid = (first + second) / 2f
                drawLine(Red, mid, mid - Offset(0f, size.height * 0.25f), 5f, StrokeCap.Round)
                drawCircle(Red, 7f, mid - Offset(0f, size.height * 0.25f))
            }
        }
    }

    /** Finding someone with BlueMob: their SOS reaches you, the compass points, the distance counts down as you walk. */
    private fun DrawScope.findWithBlueMob(t: Float) {
        val them = at(0.82f, 0.3f)
        for (i in 0..2) { val k = (t * 2 + i / 3f) % 1f; drawCircle(Red.copy(alpha = 1f - k), size.height * (0.05f + k * 0.2f), them, style = Stroke(3f)) }
        drawCircle(Red, 9f, them)
        val me = Offset(size.width * (0.12f + 0.55f * t), size.height * (0.8f - 0.42f * t))
        person(me.x, me.y + size.height * 0.08f, size.height * 0.22f, armL = 30f * wave(t, 5f), armR = -30f * wave(t, 5f))
        val dir = (them - me).let { it / it.getDistance() }
        drawLine(W, me - Offset(0f, size.height * 0.2f), me - Offset(0f, size.height * 0.2f) + dir * 40f, 5f, StrokeCap.Round)
        val remaining = ((them - me).getDistance() / size.width * 200).toInt()
        for (i in 0 until (remaining / 25).coerceIn(0, 8)) drawCircle(W, 3f, Offset(size.width * (0.05f + i * 0.03f), size.height * 0.1f))
    }
}
