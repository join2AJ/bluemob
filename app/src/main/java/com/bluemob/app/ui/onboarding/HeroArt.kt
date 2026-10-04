package com.bluemob.app.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.components.avatarTint
import com.bluemob.app.ui.dashboard.RadarBlip
import com.bluemob.app.ui.dashboard.RadarSweep
import com.bluemob.app.ui.theme.Palette

enum class HeroKind { OFF_GRID, HOPS, BRIDGE, RADAR, BUDDY }

/** Animated illustrations for the intro carousel, drawn in code so they work offline and stay crisp. */
@Composable
fun HeroArt(kind: HeroKind, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (kind) {
            HeroKind.OFF_GRID -> OffGridArt()
            HeroKind.HOPS -> HopsArt()
            HeroKind.BRIDGE -> BridgeArt()
            HeroKind.RADAR -> RadarSweep(people = demoBlips, modifier = Modifier.fillMaxSize())
            HeroKind.BUDDY -> BuddyArt()
        }
    }
}

private val demoBlips = listOf(
    RadarBlip("demo1", "🦋", 40.0, 0.35f, Palette.Online),
    RadarBlip("demo2", "🐬", 150.0, 0.62f, Palette.Online),
    RadarBlip("demo3", "🌵", 250.0, 0.85f, Palette.Away),
)

@Composable
private fun EmojiNode(emoji: String, seed: String, size: Int = 64) {
    Box(
        Modifier.size(size.dp).background(avatarTint(seed).copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size * 0.5f).sp) }
}

@Composable
private fun OffGridArt() {
    val t = rememberInfiniteTransition(label = "ripples")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p")
    val ring = MaterialTheme.colorScheme.secondary
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            repeat(3) { i ->
                val p = (phase + i / 3f) % 1f
                drawCircle(ring.copy(alpha = (1 - p) * 0.5f), radius = maxR * (0.25f + 0.75f * p), style = Stroke(3.dp.toPx()))
            }
        }
        EmojiNode("📱", "me", 88)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
        ) {
            Icon(
                Icons.Filled.SignalCellularOff, contentDescription = null,
                modifier = Modifier.padding(12.dp).size(28.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** A message travels A → B → C. */
@Composable
private fun HopsArt() {
    val t = rememberInfiniteTransition(label = "hops")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "p")
    val line = MaterialTheme.colorScheme.outline
    val spark = MaterialTheme.colorScheme.tertiary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val nodes = listOf(Offset(0.15f, 0.7f), Offset(0.5f, 0.3f), Offset(0.85f, 0.7f))
        Canvas(Modifier.fillMaxSize()) {
            val pts = nodes.map { Offset(it.x * size.width, it.y * size.height) }
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
            drawLine(line, pts[0], pts[1], 4.dp.toPx(), pathEffect = dash)
            drawLine(line, pts[1], pts[2], 4.dp.toPx(), pathEffect = dash)
            val (a, b, f) = if (p < 0.5f) Triple(pts[0], pts[1], p * 2) else Triple(pts[1], pts[2], (p - 0.5f) * 2)
            val pos = a + (b - a) * f
            drawCircle(spark.copy(alpha = 0.3f), 18.dp.toPx(), pos)
            drawCircle(spark, 9.dp.toPx(), pos)
        }
        listOf("🦅" to "a", "🌊" to "b", "⛰️" to "c").forEachIndexed { i, (emoji, seed) ->
            Box(Modifier.offset(w * nodes[i].x - 32.dp, h * nodes[i].y - 32.dp)) { EmojiNode(emoji, seed) }
        }
    }
}

/** A circle of offline friends, one of whom links up to the globe. */
@Composable
private fun BridgeArt() {
    val t = rememberInfiniteTransition(label = "bridge")
    val glow by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "g")
    val line = MaterialTheme.colorScheme.outline
    val beam = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val friends = listOf(Offset(0.18f, 0.78f), Offset(0.45f, 0.86f), Offset(0.78f, 0.74f))
        val globe = Offset(0.5f, 0.2f)
        Canvas(Modifier.fillMaxSize()) {
            val pts = friends.map { Offset(it.x * size.width, it.y * size.height) }
            val g = Offset(globe.x * size.width, globe.y * size.height)
            drawLine(line, pts[0], pts[1], 3.dp.toPx())
            drawLine(line, pts[1], pts[2], 3.dp.toPx())
            drawLine(
                beam.copy(alpha = glow), pts[2], g, 6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)),
            )
        }
        listOf("🌻", "🍀", "📶").forEachIndexed { i, emoji ->
            Box(Modifier.offset(w * friends[i].x - 28.dp, h * friends[i].y - 28.dp)) { EmojiNode(emoji, "f$i", 56) }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.offset(w * globe.x - 40.dp, h * globe.y - 40.dp).size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Public, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BuddyArt() {
    val t = rememberInfiniteTransition(label = "buddy")
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")
    val fade by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "f")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Bubble("Hi Sky! 👋", mine = true)
        Box(Modifier.offset(y = bob.dp)) { Bubble("Hey! 🌤️ Ready to explore?", mine = false) }
        Box(Modifier.alpha(fade)) { Bubble("Let's go! 🚀", mine = true) }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 4.dp else 20.dp, if (mine) 20.dp else 4.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
