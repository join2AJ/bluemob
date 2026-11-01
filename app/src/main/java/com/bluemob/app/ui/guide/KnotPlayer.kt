package com.bluemob.app.ui.guide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.theme.Extra
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Knots, step by step. Each knot (assets/knots.json, made with tools/knots) is one or two ropes drawn on a 16 × 10 grid:
 * every control point says how high it lies (so a strand that passes under shows a gap) and in which step it is
 * drawn. Playing a step draws that part of the rope, end first, like hands tying it; some steps instead move the
 * whole rope into a new shape (the butterfly's twists).
 */
object KnotDiagrams {
    class Pt(val x: Float, val y: Float, val z: Int, val step: Int)
    class Rope(val blue: Boolean, val pts: List<Pt>, val work: Int, val width: Float?, val morph: Map<Int, List<Pt>>)
    class Prop(val type: String, val z: Int, val a: Float, val b: Float, val c: Float, val d: Float)
    class Knot(val id: String, val steps: List<String>, val ropes: List<Rope>, val props: List<Prop>, val pull: Int, val width: Float)

    @Volatile private var cache: Map<String, Knot>? = null

    fun get(context: android.content.Context, articleId: String): Knot? = all(context)[articleId.removePrefix("knots-")]

    fun all(context: android.content.Context): Map<String, Knot> = cache ?: runCatching {
        parse(context.assets.open("knots.json").bufferedReader().readText())
    }.getOrDefault(emptyMap()).also { cache = it }

    fun parse(json: String): Map<String, Knot> {
        val a = JSONArray(json)
        return (0 until a.length()).associate { i ->
            val k = a.getJSONObject(i)
            val ropes = k.getJSONArray("ropes").let { ra ->
                (0 until ra.length()).map { j ->
                    val r = ra.getJSONObject(j)
                    fun pts(arr: JSONArray) = (0 until arr.length()).map { n -> arr.getJSONArray(n).let { p -> Pt(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.getInt(2), if (p.length() > 3) p.getInt(3) else 0) } }
                    val morph = r.optJSONObject("morph")?.let { m -> m.keys().asSequence().associate { key -> key.toInt() to pts(m.getJSONArray(key)) } }.orEmpty()
                    Rope(r.optString("color", "blue") == "blue", pts(r.getJSONArray("pts")), r.optInt("work", Int.MAX_VALUE),
                        if (r.has("width")) r.getDouble("width").toFloat() else null, morph)
                }
            }
            val props = k.optJSONArray("props")?.let { pa ->
                (0 until pa.length()).map { j ->
                    val p = pa.getJSONObject(j)
                    when (val t = p.getString("type")) {
                        "post" -> Prop(t, p.optInt("z", 1), p.getDouble("x0").toFloat(), p.getDouble("y0").toFloat(), p.getDouble("x1").toFloat(), p.getDouble("y1").toFloat())
                        else -> Prop(t, p.optInt("z", 1), p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("r").toFloat(), 0f)
                    }
                }
            }.orEmpty()
            val steps = k.getJSONArray("steps").let { s -> (0 until s.length()).map { s.getString(it) } }
            k.getString("id") to Knot(k.getString("id"), steps, ropes, props, k.optInt("pull", steps.size - 1), k.optDouble("width", 0.5).toFloat())
        }
    }

    /** A smooth line through the control points; each piece keeps its height, step, and which control segment it came from. */
    class D(val x: Float, val y: Float, val z: Int, val step: Int, val seg: Int)

    fun dense(pts: List<Pt>, n: Int = 10): List<D> {
        if (pts.size < 2) return pts.map { D(it.x, it.y, it.z, it.step, 0) }
        val out = ArrayList<D>()
        for (i in 0 until pts.size - 1) {
            val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.size - 1)]
            for (k in 0 until n) {
                val t = k / n.toFloat(); val t2 = t * t; val t3 = t2 * t
                fun c(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
                out += D(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), p1.z, p2.step, i)
            }
        }
        pts.last().let { out += D(it.x, it.y, it.z, it.step, pts.size - 1) }
        return out
    }

    /** The rope's control points during [step] at [f] (0..1): morph steps move between shapes, others keep it still. */
    fun shape(r: Rope, step: Int, f: Float): List<Pt> {
        if (r.morph.isEmpty()) return r.pts
        val keys = r.morph.keys.sorted()
        fun at(s: Int) = r.morph[keys.lastOrNull { it <= s } ?: keys.first()]!!
        val a = if (step > 0) at(step - 1) else at(0); val b = at(step)
        return a.zip(b) { p, q -> Pt(p.x + (q.x - p.x) * f, p.y + (q.y - p.y) * f, if (f > 0.5f) q.z else p.z, 0) }
    }
}

private val Blue = Color(0xFF2F6FB0)
private val Orange = Color(0xFFE8892B)
private val Panel = Color(0xFFF3EEE4)
private val Wood = Color(0xFF9A6B3F)
private val WoodDark = Color(0xFF7A522E)

/** Draws [knot] at [step], [f] of the way through it. */
fun DrawScope.drawKnot(knot: KnotDiagrams.Knot, step: Int, f: Float, pulse: Float) {
    val s = minOf(size.width / 16f, size.height / 10f)
    val ox = (size.width - 16f * s) / 2; val oy = (size.height - 10f * s) / 2
    fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    class Run(val z: Int, val color: Color, val width: Float, val path: Path)
    val runs = ArrayList<Run>()
    val guides = ArrayList<Pair<Path, Float>>()
    val tips = ArrayList<Offset>()
    val ends = ArrayList<Pair<Offset, Offset>>()
    knot.ropes.forEach { r ->
        val w = (r.width ?: knot.width) * s
        val pts = KnotDiagrams.dense(KnotDiagrams.shape(r, step, f))
        var total = 0f
        for (i in 0 until pts.size - 1) if (pts[i].step == step) total += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        val limit = total * f
        var drawn = 0f
        var cur: Run? = null
        var guide: Path? = null
        var tip: Offset? = null
        var visibleFirst: KnotDiagrams.D? = null
        var visibleLast: KnotDiagrams.D? = null
        var beforeLast: KnotDiagrams.D? = null
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; var b = pts[i + 1]
            val st = a.step
            if (st > step) { cur = null; continue }
            if (st == step && r.morph.isEmpty()) {
                val len = hypot(b.x - a.x, b.y - a.y)
                if (drawn >= limit) {
                    if (guide == null) guide = Path().apply { o(a.x, a.y).let { moveTo(it.x, it.y) } }
                    o(b.x, b.y).let { guide.lineTo(it.x, it.y) }
                    cur = null; continue
                }
                if (drawn + len > limit) {
                    val k = (limit - drawn) / len
                    val cut = KnotDiagrams.D(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, b.z, b.step, b.seg)
                    guide = Path().apply { o(cut.x, cut.y).let { moveTo(it.x, it.y) }; o(b.x, b.y).let { lineTo(it.x, it.y) } }
                    b = cut
                }
                drawn += len
                tip = o(b.x, b.y)
            }
            val color = if (!r.blue || a.seg >= r.work) Orange else Blue
            if (cur == null || cur.z != a.z || cur.color != color) {
                cur = Run(a.z, color, w, Path().apply { o(a.x, a.y).let { moveTo(it.x, it.y) } }).also { runs += it }
            }
            o(b.x, b.y).let { cur.path.lineTo(it.x, it.y) }
            if (visibleFirst == null) visibleFirst = a
            beforeLast = a; visibleLast = b
        }
        guide?.let { guides += it to w }
        tip?.let { tips += it }
        if (step == knot.pull && f > 0.3f) {
            visibleFirst?.let { a -> pts.getOrNull(3)?.let { n -> ends += o(a.x, a.y) to o(a.x - (n.x - a.x), a.y - (n.y - a.y)) } }
            val last = visibleLast; val prev = beforeLast
            if (last != null && prev != null) ends += o(last.x, last.y) to o(last.x + (last.x - prev.x) * 3, last.y + (last.y - prev.y) * 3)
        }
    }
    // Ropes from the back up; posts, trees and poles at their own depth; what passes behind a post shows faintly through it.
    val zs = (runs.map { it.z } + knot.props.map { it.z }).distinct().sorted()
    val gap = 0.16f * s
    for (z in zs) {
        knot.props.filter { it.z == z }.forEach { p ->
            when (p.type) {
                "post" -> {
                    val tl = o(p.a, p.b); val br = o(p.c, p.d)
                    val vertical = (p.d - p.b) > (p.c - p.a)
                    drawRect(Brush.linearGradient(listOf(WoodDark, Wood, WoodDark), tl, if (vertical) Offset(br.x, tl.y) else Offset(tl.x, br.y)), tl, Size(br.x - tl.x, br.y - tl.y))
                    val grain = WoodDark.copy(alpha = 0.35f)
                    if (vertical) listOf(0.3f, 0.62f).forEach { k -> drawLine(grain, Offset(tl.x + (br.x - tl.x) * k, tl.y), Offset(tl.x + (br.x - tl.x) * k, br.y), 1.5f) }
                    else listOf(0.35f, 0.7f).forEach { k -> drawLine(grain, Offset(tl.x, tl.y + (br.y - tl.y) * k), Offset(br.x, tl.y + (br.y - tl.y) * k), 1.5f) }
                }
                "disc" -> {
                    val c = o(p.a, p.b)
                    drawCircle(Brush.radialGradient(listOf(Wood, WoodDark), c, p.c * s), p.c * s, c)
                    drawCircle(WoodDark.copy(alpha = 0.5f), p.c * s * 0.55f, c, style = Stroke(1.5f))
                    drawCircle(WoodDark.copy(alpha = 0.5f), p.c * s * 0.25f, c, style = Stroke(1.5f))
                }
                "ring" -> drawCircle(Color(0xFF8A8F99), p.c * s, o(p.a, p.b), style = Stroke(0.3f * s))
            }
            // Anything behind this prop: drawn faintly over it, so you can follow the rope round the back.
            runs.filter { it.z < p.z }.forEach { r -> drawPath(r.path, r.color.copy(alpha = 0.3f), style = Stroke(r.width * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r.width * 0.9f, r.width * 0.7f)))) }
        }
        val layer = runs.filter { it.z == z }
        layer.forEach { r -> drawPath(r.path, Panel, style = Stroke(r.width + 2 * gap, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        layer.forEach { r ->
            val round = Stroke(r.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(r.path, r.color, style = round)
            // Twisted-rope texture and a highlight along the top.
            drawPath(r.path, Color.Black.copy(alpha = 0.07f), style = Stroke(r.width * 0.7f, cap = StrokeCap.Butt, join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(r.width * 0.25f, r.width * 0.35f))))
            drawPath(r.path, Color.White.copy(alpha = 0.28f), style = Stroke(r.width * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    // Where the end goes next in this step, dotted.
    guides.forEach { (p, w) -> drawPath(p, Color(0xFF3A4A44).copy(alpha = 0.45f), style = Stroke(w * 0.22f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.15f, w * 0.55f)))) }
    // The moving end: a pulsing ring, like a fingertip.
    tips.forEach { t ->
        drawCircle(Color(0xFF1F2A26).copy(alpha = 0.75f), knot.width * s * (0.85f + 0.25f * pulse), t, style = Stroke(2.5f))
        drawCircle(Color(0xFF1F2A26).copy(alpha = 0.18f * (1 - pulse)), knot.width * s * (1.3f + 0.9f * pulse), t)
    }
    // "Pull tight": arrows at the ends.
    ends.forEach { (from, toward) ->
        val ang = atan2(toward.y - from.y, toward.x - from.x)
        val start = Offset(from.x + cos(ang) * s * (0.5f + 0.35f * pulse), from.y + sin(ang) * s * (0.5f + 0.35f * pulse))
        val end = Offset(start.x + cos(ang) * s * 1.1f, start.y + sin(ang) * s * 1.1f)
        val col = Color(0xFFC0392B)
        drawLine(col, start, end, 0.18f * s, StrokeCap.Round)
        val h = 0.45f * s
        drawLine(col, end, Offset(end.x - cos(ang - 0.6f) * h, end.y - sin(ang - 0.6f) * h), 0.18f * s, StrokeCap.Round)
        drawLine(col, end, Offset(end.x - cos(ang + 0.6f) * h, end.y - sin(ang + 0.6f) * h), 0.18f * s, StrokeCap.Round)
    }
}

/**
 * The knot player: the drawing, the step's instruction under it, and play / pause / back / next / slow. It plays each
 * step, holds the result a moment, then goes on; [step] is shared with the steps list so tapping a step jumps there.
 */
@Composable
fun KnotPlayer(knot: KnotDiagrams.Knot, step: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val still = LocalInspectionMode.current
    var playing by rememberSaveable(knot.id) { mutableStateOf(true) }
    var slow by rememberSaveable { mutableStateOf(false) }
    val progress = remember(knot.id) { Animatable(if (still) 1f else 0f) }
    val last = knot.steps.size - 1
    LaunchedEffect(knot.id, step, playing, slow) {
        if (still) return@LaunchedEffect
        if (!playing) { progress.snapTo(1f); return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(((if (step == knot.pull) 1_500 else 2_600) * (if (slow) 2 else 1)), easing = LinearEasing))
        kotlinx.coroutines.delay(if (step == last) 2_400 else 1_100)
        onStep(if (step >= last) 0 else step + 1)
    }
    val pulse = if (still) 0.5f else rememberInfiniteTransition(label = "knot").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(20.dp)).background(Panel)
            .semantics { contentDescription = "Step ${step + 1}: ${knot.steps.getOrNull(step).orEmpty()}" }) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).padding(10.dp)) { drawKnot(knot, step, progress.value, pulse) }
            Text("${step + 1} / ${knot.steps.size}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1F2A26).copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        // The step's instruction, big, right under the drawing.
        Text(knot.steps.getOrNull(step).orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp).height(72.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(0); playing = true }) { Icon(Icons.Rounded.Replay, "From the start") }
            IconButton(onClick = { onStep((step - 1).coerceAtLeast(0)) }, enabled = step > 0) { Icon(Icons.Rounded.SkipPrevious, "Previous step") }
            FilledIconButton(onClick = { playing = !playing }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
            IconButton(onClick = { onStep((step + 1).coerceAtMost(last)) }, enabled = step < last) { Icon(Icons.Rounded.SkipNext, "Next step") }
            Box(Modifier.weight(1f))
            Text(if (slow) "Slow" else "Normal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).clickable { slow = !slow }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            knot.steps.indices.forEach { i ->
                Box(Modifier.padding(3.dp).size(if (i == step) 10.dp else 7.dp).clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else Extra.line).clickable { onStep(i) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Blue)); Text(" Standing part   ", style = MaterialTheme.typography.labelSmall, color = Extra.ink2)
            Box(Modifier.size(10.dp).clip(CircleShape).background(Orange)); Text(" The end you move   ", style = MaterialTheme.typography.labelSmall, color = Extra.ink2)
            Box(Modifier.width(14.dp).height(2.dp).background(Color(0xFF3A4A44).copy(alpha = 0.45f))); Text(" Where it goes", style = MaterialTheme.typography.labelSmall, color = Extra.ink2)
        }
    }
}

/** The knot for a guide, if it has a step-by-step animation. */
@Composable
fun rememberKnot(articleId: String): KnotDiagrams.Knot? {
    val context = LocalContext.current
    return remember(articleId) { KnotDiagrams.get(context, articleId) }
}
