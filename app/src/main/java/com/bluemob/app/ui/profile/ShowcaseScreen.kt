package com.bluemob.app.ui.profile

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.HeroCard
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Palette

/** What "Try it" opens for each feature. */
class ShowcaseActions(
    val onSky: () -> Unit = {}, val onSos: () -> Unit = {}, val onDemoTrip: () -> Unit = {}, val onCompass: () -> Unit = {},
    val onPeaks: () -> Unit = {}, val onKnots: () -> Unit = {}, val onShare: (String) -> Unit = {},
)

const val PITCH = "BlueMob lets a group talk, find each other and call for help where there's no mobile signal. Phones pass messages, " +
    "calls and SOS to each other over Bluetooth and Wi-Fi, and one phone with internet can carry everyone's. Free, no ads, no account needed to start."

/**
 * "Show someone BlueMob": the one-line idea, an animation of a message hopping phone to phone to the internet, and
 * each feature with a demo that works with just this phone (no one else needed).
 */
@Composable
fun ShowcaseScreen(a: ShowcaseActions, onBack: () -> Unit) {
    SubScreen("Show someone BlueMob", onBack) {
        item {
            HeroCard(Gradients.brand(), Modifier.padding(top = 12.dp)) {
                Text("No signal? Still connected.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(PITCH, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { MeshDemo(Modifier.padding(top = 14.dp)) }
        item { Text("TRY IT ON THIS PHONE", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp)) }
        val features = listOf(
            Feature(Icons.Rounded.Hiking, Palette.BrandBlue, "Trip groups", "Everyone joins by scanning one code. Roll call checks in one tap that every phone can be reached, even with no signal.", "Try a demo trip", a.onDemoTrip),
            Feature(Icons.Rounded.Sos, Palette.Danger, "SOS that hops", "One button tells every phone nearby, and each passes it on, until one with internet sends it to family and rescuers.", "See what helpers see", a.onSos),
            Feature(Icons.Rounded.Explore, Palette.Violet, "Find each other", "A compass arrow to your friend's last position, and a button that makes their phone whistle and flash.", "Open the compass", a.onCompass),
            Feature(Icons.Rounded.Landscape, Color(0xFF2E8C5A), "Peak finder", "Point the phone at the mountains: their names, height and distance appear over the camera.", "Open the peak finder", a.onPeaks),
            Feature(Icons.Rounded.MenuBook, Color(0xFFE07F2E), "Survival guide, offline", "First aid, water, shelter, weather, and knots you learn by watching them tie themselves.", "Watch a knot", a.onKnots),
            Feature(Icons.Rounded.Smartphone, Color(0xFF3D8FD9), "Sky, the offline helper", "Ask what to do: snake bite, lost, storm. Answers come from the phone itself, no internet.", "Ask Sky", a.onSky),
        )
        features.forEach { f -> item { FeatureCard(f) } }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text("No internet to install it?", style = MaterialTheme.typography.titleSmall)
                Text("Give it phone to phone: open the Play Store, tap your profile picture → Manage apps & device → Share → Send, and pick BlueMob. They tap Receive. It works with no internet, and their copy updates normally later.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Button(onClick = { a.onShare(PITCH + "\n\nAsk me to show you, or get BlueMob from the person who shared this.") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
                Icon(Icons.Rounded.Share, null); Text("Tell a friend about BlueMob", Modifier.padding(start = 8.dp))
            }
        }
    }
}

private class Feature(val icon: ImageVector, val tint: Color, val title: String, val body: String, val action: String, val onClick: () -> Unit)

@Composable
private fun FeatureCard(f: Feature) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(f.icon, f.tint, size = 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(f.title, style = MaterialTheme.typography.titleMedium)
                    Text(f.body, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
            }
            OutlinedButton(onClick = f.onClick, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) { Text(f.action) }
        }
    }
}

/** A message hopping from a phone with no signal, through two phones, to one with internet, and on to home. */
@Composable
fun MeshDemo(modifier: Modifier = Modifier) {
    val still = LocalInspectionMode.current
    val t = if (still) 0.55f else rememberInfiniteTransition(label = "mesh").animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    val nodes = listOf(
        Triple(Icons.Rounded.SignalCellularOff, "You, no signal", Palette.Danger),
        Triple(Icons.Rounded.Smartphone, "A friend", Palette.BrandBlue),
        Triple(Icons.Rounded.Smartphone, "Another hiker", Palette.BrandBlue),
        Triple(Icons.Rounded.Wifi, "Phone with internet", Palette.BrandGreen),
        Triple(Icons.Rounded.Home, "Family at home", Palette.Violet),
    )
    // Where the message is: 0..4 between the five, with a short stop at each.
    val pos = (t * 5f).coerceAtMost(4.6f)
    val at = pos.toInt().coerceAtMost(4)
    val caption = when (at) {
        0 -> "You write, or press SOS. There's no signal here."
        1 -> "Your phone hands it to a friend's, over Bluetooth or Wi-Fi."
        2 -> "Their phone passes it on to the next. Each hop can be 50–100 m."
        3 -> "This phone has internet, so it sends it on for everyone."
        else -> "Delivered, end-to-end encrypted. Calls work the same way."
    }
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text("How a message gets through", style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(92.dp)) {
            val line = Extra.line; val dot = Palette.Gold
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val step = size.width / 5f
                val y = 22.dp.toPx()
                for (i in 0 until 4) {
                    val x0 = step * (i + 0.5f) + 22.dp.toPx(); val x1 = step * (i + 1.5f) - 22.dp.toPx()
                    drawLine(if (i < at) Palette.BrandGreen else line, Offset(x0, y), Offset(x1, y), 4f, pathEffect = if (i == 3) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                val frac = (pos - at).coerceIn(0f, 1f)
                val moving = frac > 0.35f && at < 4
                val k = if (moving) ((frac - 0.35f) / 0.65f) else 0f
                val x = step * (at + 0.5f + k)
                drawCircle(dot.copy(alpha = 0.3f), 14.dp.toPx(), Offset(x, y))
                drawCircle(dot, 7.dp.toPx(), Offset(x, y))
            }
            Row(Modifier.fillMaxWidth()) {
                nodes.forEachIndexed { i, (icon, label, tint) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(icon, if (i <= at) tint else Extra.ink3, size = 44.dp)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (i == at) MaterialTheme.colorScheme.onSurface else Extra.ink3,
                            maxLines = 2, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).height(44.dp))
    }
}

/** Trip groups in four steps, with a demo button. */
@Composable
fun TripHowItWorks(onDemo: (() -> Unit)?, modifier: Modifier = Modifier) {
    val steps = listOf(
        Icons.Rounded.Hiking to "The leader makes a trip group and shows its QR code.",
        Icons.Rounded.Smartphone to "Everyone scans it. Now all the phones know each other.",
        Icons.Rounded.SignalCellularAlt to "Roll call: one tap, and each phone answers with a tick: reached directly, through other phones, or over the internet.",
        Icons.Rounded.Cloud to "On the way: a plan with checkpoints, a headcount at the bus, alerts if someone falls behind. The chat stays after the trip.",
    )
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How a trip group works", style = MaterialTheme.typography.titleSmall)
        steps.forEachIndexed { i, (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) { IconBadge(icon, Palette.BrandBlue, size = 36.dp) }
                Text("${i + 1}. $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (onDemo != null) Button(onClick = onDemo, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))) { Text("Try a demo trip: nothing is sent") }
    }
}
