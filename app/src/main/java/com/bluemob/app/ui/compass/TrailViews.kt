package com.bluemob.app.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.settings.Spot
import com.bluemob.app.trail.Course
import com.bluemob.app.trail.PositionEstimate
import com.bluemob.app.trail.TrailMath
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.util.Geo
import com.bluemob.app.util.TimeText
import com.bluemob.app.util.cardinal
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the compass shows about the trail and lost mode. */
data class TrailUi(
    val on: Boolean = false,
    val points: List<TrailPoint> = emptyList(),
    val estimate: PositionEstimate? = null,
    val lost: Boolean = false,
    val countsSteps: Boolean = true,
    val canCountSteps: Boolean = true,
)

class TrailActions(
    val onTrail: (Boolean) -> Unit = {},
    val onLost: (Boolean) -> Unit = {},
    val onBaseCamp: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onAllowSteps: () -> Unit = {},
)

/** Trail off: explain it, and let the user opt in. */
@Composable
fun TrailOptIn(onTrail: (Boolean) -> Unit) {
    Group {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Record my trail", style = MaterialTheme.typography.titleMedium)
                Text("Off until you want it. Draws where you've walked, tells you if you're keeping a straight line, and keeps your last position " +
                    "and base camp. It keeps GPS on, so it uses more battery. Turn it on when you're unsure of the way.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = false, onCheckedChange = onTrail)
        }
    }
}

/** Trail on: the map, the "am I walking straight?" check, and where the last GPS fix was. */
@Composable
fun TrailCard(ui: TrailUi, spots: List<Spot>, me: GeoPoint?, actions: TrailActions) {
    val geo = ui.points.map { GeoPoint(it.lat, it.lon, it.accuracyM, it.time) }
    val stats = TrailMath.analyse(geo)
    val base = spots.firstOrNull { it.isBaseCamp }
    Group {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your trail", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Recording", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Switch(checked = true, onCheckedChange = actions.onTrail)
        }
        TrailMap(ui.points, spots, me, ui.estimate, Modifier.padding(12.dp).fillMaxWidth().height(250.dp).clip(MaterialTheme.shapes.medium))
        val color = when (stats.course) {
            Course.STRAIGHT -> MaterialTheme.colorScheme.primary
            Course.CIRCLING -> Extra.rose
            Course.TOO_SHORT -> Extra.ink3
            else -> Extra.ember
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
                Spacer(Modifier.width(8.dp))
                Text(stats.course.title, style = MaterialTheme.typography.titleMedium, color = color)
                stats.bearingDeg?.let { Text("  · heading ${it.roundToInt()}° ${cardinal(it)}", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2) }
            }
            Text(stats.course.tip + if (stats.course != Course.TOO_SHORT) " (last ${Geo.formatDistance(stats.pathM)}, ${(stats.straightness * 100).roundToInt()}% straight)" else "",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        }
        val lastFix = ui.points.lastOrNull { !it.estimated }
        SettingRow(null, Color.Transparent, "Last GPS fix",
            lastFix?.let { "${TimeText.ago(it.time)} · ${Geo.formatLatLon(it.lat, it.lon)} · ±${it.accuracyM.roundToInt()} m" } ?: "Waiting for GPS… step into the open", divider = true)
        SettingRow(null, Color.Transparent, "Base camp",
            base?.let { b -> me?.let { val p = GeoPoint(b.lat, b.lon, 0f, 0); "${Geo.formatDistance(Geo.distanceM(it, p))} away, ${Geo.bearingDeg(it, p).roundToInt()}° ${cardinal(Geo.bearingDeg(it, p))}" } ?: "Saved" }
                ?: "Not set. Set it where you start, to walk back to it", divider = true) {
            TextButton(onClick = actions.onBaseCamp) { Text(if (base == null) "Set here" else "Move here") }
        }
        if (!ui.countsSteps) SettingRow(null, Color.Transparent, "Count steps when GPS drops out",
            if (ui.canCountSteps) "Allow physical activity, so BlueMob can estimate your position from steps and the compass" else "This phone has no step counter, so only GPS is used",
            divider = true) {
            if (ui.canCountSteps) TextButton(onClick = actions.onAllowSteps) { Text("Allow") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions.onClear) { Text("Clear trail", color = Extra.ink3) }
        }
    }
}

/** "I'm lost": shares the position estimate with everyone nearby, so they can pinpoint you. */
@Composable
fun LostCard(ui: TrailUi, onLost: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = if (ui.lost) Extra.skyTint else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (ui.lost) Extra.sky else Extra.line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(if (ui.lost) "Lost mode is on" else "Lost?", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ui.lost) "Your position goes to everyone nearby every minute, and each phone passes it on. With no GPS, BlueMob sends your last fix plus the steps and direction since."
                else "Lost mode records your trail and shares where you are with everyone nearby: GPS if there is any, otherwise your last fix plus how far and which way you've walked since.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp),
            )
            if (ui.lost) ui.estimate?.let { pos ->
                Text("BEING SHARED", style = MaterialTheme.typography.labelSmall, color = Extra.sky, modifier = Modifier.padding(top = 10.dp))
                Text(pos.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(12.dp))
            if (ui.lost) OutlinedButton(onClick = { onLost(false) }) { Text("I've found my way") }
            else Button(onClick = { onLost(true) }, colors = ButtonDefaults.buttonColors(containerColor = Extra.sky, contentColor = Color.White)) { Text("I'm lost") }
        }
    }
}

/** North-up map of the trail: solid where GPS was used, dashed where estimated. */
@Composable
fun TrailMap(points: List<TrailPoint>, spots: List<Spot>, me: GeoPoint?, estimate: PositionEstimate?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val pine = MaterialTheme.colorScheme.primary
    val sky = Extra.sky
    val ink3 = Extra.ink3
    val ember = Extra.ember
    val rose = Extra.rose
    Canvas(modifier.background(Color(0xFFEFF3EC))) {
        val all = points.map { it.lat to it.lon } + spots.map { it.lat to it.lon } + listOfNotNull(me?.let { it.lat to it.lon }, estimate?.let { it.lat to it.lon })
        // Grid.
        val step = 32.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color(0x14000000), Offset(gx, 0f), Offset(gx, size.height)); gx += step }
        var gy = 0f
        while (gy < size.height) { drawLine(Color(0x14000000), Offset(0f, gy), Offset(size.width, gy)); gy += step }
        val n = measurer.measure("N ↑", TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rose))
        drawText(n, topLeft = Offset(10.dp.toPx(), 8.dp.toPx()))
        if (all.isEmpty()) {
            val t = measurer.measure("Your trail appears here as you walk", TextStyle(fontSize = 13.sp, color = ink3))
            drawText(t, topLeft = Offset((size.width - t.size.width) / 2, (size.height - t.size.height) / 2))
            return@Canvas
        }
        val lat0 = all.map { it.first }.average()
        val lon0 = all.map { it.second }.average()
        val k = 111_320.0
        fun xy(lat: Double, lon: Double) = ((lon - lon0) * k * cos(Math.toRadians(lat0))) to ((lat - lat0) * k)
        val m = all.map { xy(it.first, it.second) }
        val spanX = max(m.maxOf { it.first } - m.minOf { it.first }, 60.0)
        val spanY = max(m.maxOf { it.second } - m.minOf { it.second }, 60.0)
        val pad = 28.dp.toPx()
        val scale = minOf((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        val cx = (m.maxOf { it.first } + m.minOf { it.first }) / 2
        val cy = (m.maxOf { it.second } + m.minOf { it.second }) / 2
        fun px(lat: Double, lon: Double): Offset {
            val (x, y) = xy(lat, lon)
            return Offset((size.width / 2 + (x - cx) * scale).toFloat(), (size.height / 2 - (y - cy) * scale).toFloat())
        }
        // Path, in runs of GPS (solid) and estimated (dashed) points.
        points.zipWithNext().forEach { (a, b) ->
            drawLine(if (b.estimated) sky else pine, px(a.lat, a.lon), px(b.lat, b.lon), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (b.estimated) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null)
        }
        points.firstOrNull()?.let { drawCircle(pine, 5.dp.toPx(), px(it.lat, it.lon), style = Stroke(2.dp.toPx())) }
        spots.forEach { s ->
            val p = px(s.lat, s.lon)
            val label = measurer.measure(if (s.isBaseCamp) "⛺" else "📍", TextStyle(fontSize = 18.sp))
            drawText(label, topLeft = Offset(p.x - label.size.width / 2, p.y - label.size.height + 4.dp.toPx()))
        }
        estimate?.takeIf { !it.gps }?.let { e ->
            val p = px(e.lat, e.lon)
            drawCircle(sky.copy(alpha = 0.15f), (e.uncertaintyM * scale).toFloat().coerceIn(8.dp.toPx(), size.minDimension), p)
        }
        (me ?: estimate?.let { GeoPoint(it.lat, it.lon, 0f, 0) })?.let {
            val p = px(it.lat, it.lon)
            drawCircle(Color.White, 8.dp.toPx(), p)
            drawCircle(if (estimate?.gps == false) sky else ember, 6.dp.toPx(), p)
        }
        // Scale bar.
        val target = (size.width / 4) / scale
        val nice = listOf(10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10_000, 20_000).lastOrNull { it <= target } ?: 10
        val len = (nice * scale).toFloat()
        val y = size.height - 12.dp.toPx()
        val x0 = size.width - len - 12.dp.toPx()
        drawLine(Palette.Ink, Offset(x0, y), Offset(x0 + len, y), strokeWidth = 2.dp.toPx())
        val t = measurer.measure(if (nice >= 1000) "${nice / 1000} km" else "$nice m", TextStyle(fontSize = 11.sp, color = Palette.Ink))
        drawText(t, topLeft = Offset(x0 + len - t.size.width, y - t.size.height - 2.dp.toPx()))
    }
}

