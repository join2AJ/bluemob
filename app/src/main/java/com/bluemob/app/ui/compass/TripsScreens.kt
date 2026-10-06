package com.bluemob.app.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.TrailPoint
import com.bluemob.app.data.Trip
import com.bluemob.app.settings.Spot
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.util.Geo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

/** Every trip recorded on this phone: kept here, never uploaded (backups are encrypted files you choose where to keep). */
@Composable
fun TripsScreen(trips: List<Trip>, current: String?, onBack: () -> Unit, onOpen: (String) -> Unit, onNewTrip: (String) -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (naming) AlertDialog(
        onDismissRequest = { naming = false },
        title = { Text("Start a new trip") },
        text = { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name (optional)") }, placeholder = { Text("e.g. Kedarkantha day 2") }, singleLine = true) },
        confirmButton = { Button(onClick = { onNewTrip(name); naming = false; name = "" }) { Text("Start") } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
    )
    SubScreen("Your trips", onBack) {
        item {
            Text("Each trip's trail is kept on this phone. Back them up from You → Backup.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            Button(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) { Text(if (current != null) "End this trip and start a new one" else "Start a new trip") }
        }
        if (trips.isEmpty()) item {
            Text("No trips yet. Turn on the trail in the Compass tab, or start one above.", style = MaterialTheme.typography.bodyMedium,
                color = Extra.ink2, modifier = Modifier.padding(top = 20.dp))
        }
        items(trips, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { onOpen(t.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.id == current) "🟢" else "🥾", fontSize = 24.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(dayFormat.format(Date(t.startedAt)), durationText(t),
                        t.distanceM.takeIf { it > 0 }?.let { Geo.formatDistance(it) }, if (t.id == current) "recording now" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                Text("›", color = Extra.ink3)
            }
        }
    }
}

/** One trip: its trail drawn to scale, the numbers, and rename / delete / share as GPX. */
@Composable
fun TripScreen(trip: Trip, points: List<TrailPoint>, spots: List<Spot>, recording: Boolean, onBack: () -> Unit,
    onRename: (String) -> Unit, onDelete: () -> Unit, onShareGpx: () -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(trip.id) { mutableStateOf(trip.name) }
    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false }, title = { Text("Rename trip") },
        text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true) },
        confirmButton = { Button(onClick = { onRename(name); renaming = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text("Delete this trip?") },
        text = { Text("Its trail is removed from this phone. You can only get it back from a backup.") },
        confirmButton = { TextButton(onClick = { onDelete(); deleting = false; onBack() }) { Text("Delete", color = Extra.rose) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep") } },
    )
    val distance = points.zipWithNext { a, b -> Geo.distanceM(GeoPoint(a.lat, a.lon, 0f, 0), GeoPoint(b.lat, b.lon, 0f, 0)) }.sum()
    SubScreen(trip.name, onBack) {
        item {
            Text(dayFormat.format(Date(trip.startedAt)) + (if (recording) " · recording now" else ""), style = MaterialTheme.typography.bodyMedium,
                color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
        }
        item { TrailMap(points, spots, Modifier.padding(top = 12.dp)) }
        item {
            Group(Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Distance", Geo.formatDistance(distance))
                    Stat("Time", durationText(trip.copy(endedAt = trip.endedAt ?: points.lastOrNull()?.time)) ?: "–")
                    Stat("Points", "${points.size}")
                    Stat("GPS", if (points.isEmpty()) "–" else "${points.count { !it.estimated } * 100 / points.size}%")
                }
            }
        }
        item { GroupLabel("Use it") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShareGpx, enabled = points.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Share as GPX (open in offline map apps)") }
                Text("GPX works with Organic Maps, OsmAnd, Google Earth and most GPS apps, so you can see this trail over a map you downloaded for offline use.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { renaming = true }) { Text("Rename") }
                    TextButton(onClick = { deleting = true }) { Text("Delete trip", color = Extra.rose) }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Extra.ink2)
    }
}

/**
 * The trail drawn to scale with no map tiles (it works anywhere): a grid with a scale bar, solid line where GPS was
 * used, dashed where the position was estimated from steps, green start, red end, and saved spots.
 */
@Composable
fun TrailMap(points: List<TrailPoint>, spots: List<Spot>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = Extra.line
    val surface = MaterialTheme.colorScheme.surface
    val pine = Palette.Pine
    val rose = Extra.rose
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(surface)) {
        if (points.isEmpty()) {
            drawText(measurer, "No points yet", Offset(size.width / 2 - 120, size.height / 2), TextStyle(color = ink, fontSize = 14.sp))
            return@Canvas
        }
        val all = points.map { it.lat to it.lon } + spots.map { it.lat to it.lon }
        val lat0 = all.map { it.first }.average()
        val k = cos(Math.toRadians(lat0))
        // Metres east/north of the first point.
        fun xy(lat: Double, lon: Double) = ((lon - points[0].lon) * 111_320 * k) to ((lat - points[0].lat) * 110_540)
        val pts = all.map { xy(it.first, it.second) }
        val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
        val span = max(max(maxX - minX, maxY - minY), 50.0)
        val pad = size.width * 0.1f
        val scale = (size.width - 2 * pad) / span
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        fun screen(p: Pair<Double, Double>) = Offset((size.width / 2 + (p.first - cx) * scale).toFloat(), (size.height / 2 - (p.second - cy) * scale).toFloat())
        // Grid at a round distance.
        val gridM = listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10_000.0).firstOrNull { span / it <= 6 } ?: 10.0.pow(5)
        val gridPx = (gridM * scale).toFloat()
        var g = size.width / 2 % gridPx
        while (g < size.width) { drawLine(grid, Offset(g, 0f), Offset(g, size.height), 1f); g += gridPx }
        g = size.height / 2 % gridPx
        while (g < size.height) { drawLine(grid, Offset(0f, g), Offset(size.width, g), 1f); g += gridPx }
        drawLine(ink, Offset(pad, size.height - pad / 2), Offset(pad + gridPx, size.height - pad / 2), 4f, cap = StrokeCap.Round)
        drawText(measurer, if (gridM >= 1000) "${(gridM / 1000).toInt()} km" else "${gridM.toInt()} m",
            Offset(pad, size.height - pad / 2 - 40), TextStyle(color = ink, fontSize = 11.sp))
        drawText(measurer, "N ↑", Offset(size.width - pad, pad / 3), TextStyle(color = ink, fontSize = 12.sp))
        // The trail: GPS stretches solid, estimated stretches dashed.
        points.zipWithNext().forEach { (a, b) ->
            drawLine(pine, screen(xy(a.lat, a.lon)), screen(xy(b.lat, b.lon)), 7f, cap = StrokeCap.Round,
                pathEffect = if (b.estimated) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null)
        }
        drawCircle(pine, 14f, screen(xy(points.first().lat, points.first().lon)))
        drawCircle(rose, 14f, screen(xy(points.last().lat, points.last().lon)))
        drawCircle(surface, 6f, screen(xy(points.last().lat, points.last().lon)))
        spots.forEach { s -> drawText(measurer, if (s.isBaseCamp) "⛺" else "📍", screen(xy(s.lat, s.lon)) - Offset(18f, 30f), TextStyle(fontSize = 16.sp)) }
    }
}

/** A GPX file for the trip, which map apps can open. */
fun tripGpx(trip: Trip, points: List<TrailPoint>): String {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val name = trip.name.replace("&", "&amp;").replace("<", "&lt;")
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"BlueMob\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<trk><name>$name</name><trkseg>\n")
        points.forEach { p -> append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${iso.format(Date(p.time))}</time></trkpt>\n") }
        append("</trkseg></trk>\n</gpx>\n")
    }
}

private val dayFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

private fun durationText(t: Trip): String? {
    val end = t.endedAt ?: return null
    val m = (end - t.startedAt) / 60_000
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}
