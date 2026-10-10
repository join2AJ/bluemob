package com.bluemob.app.ui.compass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
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

/**
 * One trip: its trail drawn to scale (pinch to zoom, drag to move), played back over time with the speed at each
 * moment, how they were probably travelling, and export of a picture plus the full data with timestamps.
 */
@Composable
fun TripScreen(trip: Trip, points: List<TrailPoint>, spots: List<Spot>, recording: Boolean, onBack: () -> Unit,
    onRename: (String) -> Unit, onDelete: () -> Unit, onShareGpx: () -> Unit, onExport: () -> Unit = onShareGpx) {
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
    val analysis = remember(points.size, points.lastOrNull()?.time) { com.bluemob.app.trail.TripAnalysis.of(points) }
    // Playback: a moment in the trip, moved by the slider or the play button.
    val start = points.firstOrNull()?.time ?: trip.startedAt
    val end = points.lastOrNull()?.time ?: start
    var at by rememberSaveable(trip.id) { mutableStateOf(end) }
    var playing by remember { mutableStateOf(false) }
    var speedUp by rememberSaveable { mutableStateOf(60) }
    androidx.compose.runtime.LaunchedEffect(playing, speedUp) {
        if (!playing) return@LaunchedEffect
        if (at >= end) at = start
        while (playing && at < end) {
            kotlinx.coroutines.delay(50)
            at = (at + 50L * speedUp).coerceAtMost(end)
        }
        playing = false
    }
    val here = com.bluemob.app.trail.TripAnalysis.positionAt(points, at)
    val legNow = analysis.legs.lastOrNull { it.from.time <= at } ?: analysis.legs.firstOrNull()
    SubScreen(trip.name, onBack) {
        item {
            Text(dayFormat.format(Date(trip.startedAt)) + (if (recording) " · recording now" else ""), style = MaterialTheme.typography.bodyMedium,
                color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
        }
        item { TrailMap(points, spots, Modifier.padding(top = 12.dp), marker = here, legs = analysis.legs) }
        // Playback.
        if (points.size > 1) item {
            Group(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeFormat.format(Date(at)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        legNow?.let { l -> Text("${l.mode.emoji} ${"%.1f".format(l.kmh)} km/h", style = MaterialTheme.typography.titleMedium, color = modeColor(l.mode)) }
                    }
                    androidx.compose.material3.Slider(value = (at - start).toFloat(), onValueChange = { playing = false; at = start + it.toLong() },
                        valueRange = 0f..(end - start).coerceAtLeast(1).toFloat())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { playing = !playing }) { Text(if (playing) "⏸ Pause" else "▶ Play") }
                        Spacer(Modifier.width(8.dp))
                        listOf(10, 60, 300).forEach { x ->
                            com.bluemob.app.ui.components.Chip("${x}×", speedUp == x) { speedUp = x }
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
        item {
            Group(Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Distance", Geo.formatDistance(analysis.distanceM))
                    Stat("Time", durationText(trip.copy(endedAt = trip.endedAt ?: points.lastOrNull()?.time)) ?: "–")
                    Stat("Avg moving", "%.1f km/h".format(analysis.avgMovingKmh))
                    Stat("Top", "%.0f km/h".format(analysis.maxKmh))
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Points", "${points.size}")
                    Stat("GPS", if (points.isEmpty()) "–" else "${points.count { !it.estimated } * 100 / points.size}%")
                    Stat("Climb", if (analysis.climbM > 0) "${analysis.climbM.toInt()} m" else "–")
                    Stat("Moving", com.bluemob.app.activity.Usage.words(analysis.movingSeconds.toLong()))
                }
            }
        }
        // How they travelled.
        if (analysis.legs.isNotEmpty()) {
            item { GroupLabel("How they travelled") }
            item {
                Group {
                    Column(Modifier.padding(16.dp)) {
                        analysis.likelyMode?.let { m -> Text("Probably ${m.emoji} ${m.label.lowercase()}", style = MaterialTheme.typography.titleMedium) }
                        Text("Judged from speed: on foot up to 7 km/h, cycle up to 25, motorbike up to 60, car or bus above.",
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 2.dp))
                        SpeedChart(analysis, start, end, at, Modifier.padding(top = 10.dp).fillMaxWidth().height(110.dp))
                        val total = analysis.timeByMode.values.sum().coerceAtLeast(1.0)
                        com.bluemob.app.trail.TravelMode.entries.forEach { m ->
                            val sec = analysis.timeByMode[m] ?: return@forEach
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(modeColor(m)))
                                Text("  ${m.emoji} ${m.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${com.bluemob.app.activity.Usage.words(sec.toLong())} · ${(sec * 100 / total).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Use it") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = points.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Export picture + data") }
                Text("Shares a picture of the trail with its numbers, a spreadsheet (CSV) of every point with date, time, position, accuracy, speed, " +
                    "altitude and travel mode, and a GPX file for Organic Maps, OsmAnd, Google Earth and other map apps.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onShareGpx, enabled = points.isNotEmpty()) { Text("GPX only") }
                    TextButton(onClick = { renaming = true }) { Text("Rename") }
                    TextButton(onClick = { deleting = true }) { Text("Delete trip", color = Extra.rose) }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("d MMM, h:mm:ss a", Locale.getDefault())

/** Each travel mode's colour, on the map and the chart. */
fun modeColor(m: com.bluemob.app.trail.TravelMode): Color = when (m) {
    com.bluemob.app.trail.TravelMode.STILL -> Color(0xFF9AA5A0)
    com.bluemob.app.trail.TravelMode.FOOT -> Palette.Pine
    com.bluemob.app.trail.TravelMode.CYCLE -> Color(0xFF2F8FD8)
    com.bluemob.app.trail.TravelMode.BIKE -> Color(0xFFE08A2E)
    com.bluemob.app.trail.TravelMode.CAR -> Color(0xFFD94F55)
}

/** Speed over the trip, coloured by mode, with a line at the playback moment. */
@Composable
private fun SpeedChart(a: com.bluemob.app.trail.TripAnalysis, start: Long, end: Long, at: Long, modifier: Modifier) {
    val ink3 = Extra.ink3
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val span = (end - start).coerceAtLeast(1).toFloat()
        val top = (a.maxKmh.coerceAtLeast(10.0) * 1.1).toFloat()
        a.legs.forEach { l ->
            val x0 = (l.from.time - start) / span * size.width
            val x1 = (l.to.time - start) / span * size.width
            val h = (l.kmh / top * size.height).toFloat()
            drawRect(modeColor(l.mode), Offset(x0, size.height - h), Size((x1 - x0).coerceAtLeast(1f), h))
        }
        drawLine(ink3, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        val x = (at - start) / span * size.width
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 3f)
        drawText(measurer, "${top.toInt()} km/h", Offset(4f, 0f), TextStyle(color = ink3, fontSize = 10.sp))
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
fun TrailMap(points: List<TrailPoint>, spots: List<Spot>, modifier: Modifier = Modifier,
    /** Playback position to mark. */
    marker: Pair<Double, Double>? = null,
    /** Colour the trail by travel mode. */
    legs: List<com.bluemob.app.trail.Leg> = emptyList()) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = Extra.line
    val surface = MaterialTheme.colorScheme.surface
    val pine = Palette.Pine
    val rose = Extra.rose
    // Pinch to zoom (up to 12×), drag to move; double-tap resets.
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val state = androidx.compose.foundation.gestures.rememberTransformableState { z, p, _ -> zoom = (zoom * z).coerceIn(1f, 12f); pan += p }
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(surface)) {
    Canvas(Modifier.fillMaxSize().transformable(state).pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) }) {
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
        val scale = (size.width - 2 * pad) / span * zoom
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        fun screen(p: Pair<Double, Double>) = Offset((size.width / 2 + (p.first - cx) * scale).toFloat() + pan.x, (size.height / 2 - (p.second - cy) * scale).toFloat() + pan.y)
        // Grid at a round distance.
        val viewM = span / zoom
        val gridM = listOf(5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10_000.0).firstOrNull { viewM / it <= 6 } ?: 10.0.pow(5)
        val gridPx = (gridM * scale).toFloat()
        var g = ((size.width / 2 + pan.x) % gridPx + gridPx) % gridPx
        while (g < size.width) { drawLine(grid, Offset(g, 0f), Offset(g, size.height), 1f); g += gridPx }
        g = ((size.height / 2 + pan.y) % gridPx + gridPx) % gridPx
        while (g < size.height) { drawLine(grid, Offset(0f, g), Offset(size.width, g), 1f); g += gridPx }
        drawLine(ink, Offset(pad, size.height - pad / 2), Offset(pad + gridPx, size.height - pad / 2), 4f, cap = StrokeCap.Round)
        drawText(measurer, if (gridM >= 1000) "${(gridM / 1000).toInt()} km" else "${gridM.toInt()} m",
            Offset(pad, size.height - pad / 2 - 40), TextStyle(color = ink, fontSize = 11.sp))
        drawText(measurer, "N ↑", Offset(size.width - pad, pad / 3), TextStyle(color = ink, fontSize = 12.sp))
        // The trail: coloured by travel mode when known; estimated stretches dashed.
        val modeAt = legs.associate { it.to.time to it.mode }
        points.zipWithNext().forEach { (a, b) ->
            val c = modeAt[b.time]?.let { modeColor(it) } ?: pine
            drawLine(c, screen(xy(a.lat, a.lon)), screen(xy(b.lat, b.lon)), 7f, cap = StrokeCap.Round,
                pathEffect = if (b.estimated) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null)
        }
        drawCircle(pine, 14f, screen(xy(points.first().lat, points.first().lon)))
        drawCircle(rose, 14f, screen(xy(points.last().lat, points.last().lon)))
        drawCircle(surface, 6f, screen(xy(points.last().lat, points.last().lon)))
        spots.forEach { s -> drawText(measurer, if (s.isBaseCamp) "⛺" else "📍", screen(xy(s.lat, s.lon)) - Offset(18f, 30f), TextStyle(fontSize = 16.sp)) }
        marker?.let { (la, lo) ->
            val p = screen(xy(la, lo))
            drawCircle(Color.White, 18f, p); drawCircle(Color(0xFF2F8FD8), 13f, p)
        }
    }
    if (zoom > 1f) Text("${"%.1f".format(zoom)}× · double-tap to reset", style = MaterialTheme.typography.labelSmall, color = Extra.ink3,
        modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}

/** A GPX file for the trip, which map apps can open. */
fun tripGpx(trip: Trip, points: List<TrailPoint>): String {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val name = trip.name.replace("&", "&amp;").replace("<", "&lt;")
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"BlueMob\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<trk><name>$name</name><trkseg>\n")
        points.forEach { p ->
            append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
            p.altitudeM?.let { append("<ele>${"%.1f".format(Locale.US, it)}</ele>") }
            append("<time>${iso.format(Date(p.time))}</time>")
            p.speedMps?.let { append("<extensions><speed>${"%.2f".format(Locale.US, it)}</speed></extensions>") }
            append("</trkpt>\n")
        }
        append("</trkseg></trk>\n</gpx>\n")
    }
}

private val dayFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

private fun durationText(t: Trip): String? {
    val end = t.endedAt ?: return null
    val m = (end - t.startedAt) / 60_000
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}


/** Every point as a spreadsheet row: local date and time, position, accuracy, speed, altitude, travel mode. */
fun tripCsv(points: List<TrailPoint>): String {
    val a = com.bluemob.app.trail.TripAnalysis.of(points)
    val modeAt = a.legs.associate { it.to.time to it }
    val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
        append("date_time,latitude,longitude,accuracy_m,speed_kmh,altitude_m,travel_mode,position_source\n")
        points.forEach { p ->
            val leg = modeAt[p.time]
            append(f.format(Date(p.time))).append(',').append(p.lat).append(',').append(p.lon).append(',').append("%.0f".format(Locale.US, p.accuracyM)).append(',')
            append(leg?.let { "%.1f".format(Locale.US, it.kmh) } ?: "").append(',')
            append(p.altitudeM?.let { "%.0f".format(Locale.US, it) } ?: "").append(',')
            append(leg?.mode?.label ?: "").append(',').append(if (p.estimated) "estimated" else "gps").append('\n')
        }
    }
}
