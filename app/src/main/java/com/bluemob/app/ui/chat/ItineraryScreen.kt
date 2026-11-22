package com.bluemob.app.ui.chat

import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import com.bluemob.app.ui.components.GroupLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.chat.Stop
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayTime = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

/** A place someone can pick for a stop: a saved spot, a peak kept on the phone, or an earlier stop's place. */
data class PlacePick(val name: String, val lat: Double, val lon: Double, val kind: String)

/**
 * The trip's plan: stops in time order, by day, with where each one is. Checkpoints show who has passed them; a phone
 * checks in by itself near a checkpoint's place (when its location is on), or with "I'm here". The admin plans the
 * main group, a captain plans their team; everyone else reads.
 */
@Composable
fun ItineraryScreen(
    g: ChatGroup,
    myId: String,
    nameOf: (String) -> String,
    here: () -> GeoPoint?,
    onSave: (List<Stop>) -> Unit,
    onCheckIn: (String) -> Unit,
    onBack: () -> Unit,
    now: Long = System.currentTimeMillis(),
    /** Places to pick from when setting a stop's location. */
    places: List<PlacePick> = emptyList(),
    /** Previews only: the stop editor open. */
    startEditing: Stop? = null,
) {
    val canPlan = g.canPlan(myId) && !g.isClosed(now)
    var stops by remember(g.itineraryAt) { mutableStateOf(g.itinerary) }
    var editing by remember { mutableStateOf(startEditing) }
    val changed = stops != g.itinerary
    val everyone = g.members.size + 1
    val me = remember { here() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val openMap = { st: Stop -> openInMaps(context, st.lat!!, st.lon!!, st.title) }
    val day0 = remember(g.createdAt) { startOfDay(g.createdAt) }
    editing?.let { s ->
        StopEditor(s, day0, here, places + stops.filter { it.hasPlace && it.id != s.id }.map { PlacePick(it.title, it.lat!!, it.lon!!, "Stop") }, onDone = { done ->
            stops = (stops.filter { it.id != done.id } + done).sortedBy { it.at }; editing = null
        }, onDelete = { stops = stops.filter { it.id != s.id }; editing = null }, onCancel = { editing = null })
    }
    SubScreen(if (g.isTeam) "Team itinerary" else "Itinerary", onBack) {
        item {
            Text(when {
                canPlan && stops.isEmpty() -> "Add the plan: where you'll be and when. Give each stop its place, and mark the ones everyone must pass as checkpoints."
                canPlan -> "You plan this trip. Everyone sees the plan; checkpoints show who has passed them."
                g.isTeam -> "Set by the team's captain."
                else -> "Set by the group's admin."
            }, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        if (stops.count { it.hasPlace } >= 1) item { RouteSketch(stops, me, Stop.next(stops, now)?.id) }
        if (stops.isEmpty() && !canPlan) item {
            Text("No plan yet.", style = MaterialTheme.typography.bodyLarge, color = Extra.ink3, modifier = Modifier.padding(vertical = 24.dp))
        }
        val next = Stop.next(stops, now)
        var lastDay = -1
        stops.forEachIndexed { i, s ->
            val d = ((startOfDay(s.at) - day0) / 86_400_000L).toInt()
            if (d != lastDay) {
                lastDay = d
                item(key = "day-$d-${s.id}") {
                    Row(Modifier.padding(top = if (i == 0) 6.dp else 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Day ${d + 1}", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(Palette.BrandGreen).padding(horizontal = 10.dp, vertical = 3.dp))
                        RawText(dayName.format(Date(s.at)), style = MaterialTheme.typography.labelLarge, color = Extra.ink2, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            item(key = s.id) {
                StopRow(i + 1, s, i == stops.lastIndex, s.id == next?.id && s.at >= now, g.checkins[s.id].orEmpty(), everyone, myId, nameOf, me,
                    onCheckIn = if (s.checkpoint && !g.isClosed(now) && g.checkins[s.id]?.containsKey(myId) != true && !changed) ({ onCheckIn(s.id) }) else null,
                    onEdit = if (canPlan) ({ editing = s }) else null, onMap = { openMap(s) })
            }
        }
        if (canPlan) {
            item {
                OutlinedButton(onClick = {
                    val last = stops.lastOrNull()?.at ?: (now + 3_600_000L)
                    editing = Stop("s-" + java.util.UUID.randomUUID().toString().take(8), last + 3_600_000L, "")
                }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("+ Add a stop") }
            }
            if (changed) item {
                Button(onClick = { onSave(stops) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save and send to everyone") }
            }
        }
        if (stops.any { it.checkpoint && it.hasPlace }) item {
            Text("Phones check in by themselves within the circle around a checkpoint, when their location is on. Check-ins reach the group as soon as any phone can carry them.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp))
        }
    }
}

/** The route drawn offline: each stop with a place as a numbered dot, joined in order, and you on it. North is up. */
@Composable
private fun RouteSketch(stops: List<Stop>, me: GeoPoint?, nextId: String?) {
    val pts = stops.withIndex().filter { it.value.hasPlace }
    val all = pts.map { it.value.lat!! to it.value.lon!! } + listOfNotNull(me?.let { it.lat to it.lon })
    val line = Extra.ink3; val green = Palette.BrandGreen; val gold = Palette.Warning; val blue = Palette.BrandBlue
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(180.dp).background(Palette.BrandGreen.copy(alpha = 0.06f))) {
            val minLat = all.minOf { it.first }; val maxLat = all.maxOf { it.first }
            val minLon = all.minOf { it.second }; val maxLon = all.maxOf { it.second }
            val k = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = ((maxLon - minLon) * k).coerceAtLeast(0.002); val spanY = (maxLat - minLat).coerceAtLeast(0.002)
            val pad = 28.dp.toPx()
            val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
            val ox = (size.width - spanX * scale) / 2; val oy = (size.height - spanY * scale) / 2
            fun at(lat: Double, lon: Double) = androidx.compose.ui.geometry.Offset((ox + (lon - minLon) * k * scale).toFloat(), (size.height - oy - (lat - minLat) * scale).toFloat())
            // a faint grid, like a map
            for (f in 1..3) {
                drawLine(line.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(size.width * f / 4, 0f), androidx.compose.ui.geometry.Offset(size.width * f / 4, size.height))
                drawLine(line.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, size.height * f / 4), androidx.compose.ui.geometry.Offset(size.width, size.height * f / 4))
            }
            val path = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { n, (_, s) -> val o = at(s.lat!!, s.lon!!); if (n == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, green.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            pts.forEach { (idx, s) ->
                val o = at(s.lat!!, s.lon!!)
                val c = if (s.id == nextId) green else if (s.checkpoint) gold else line
                drawCircle(c, 12.dp.toPx(), o)
                val t = measurer.measure("${idx + 1}", labelStyle)
                drawText(t, topLeft = androidx.compose.ui.geometry.Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
            }
            me?.let { val o = at(it.lat, it.lon); drawCircle(blue.copy(alpha = 0.25f), 14.dp.toPx(), o); drawCircle(blue, 6.dp.toPx(), o); drawCircle(Color.White, 6.dp.toPx(), o, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }
            val n = measurer.measure("N ↑", labelStyle.copy(color = line))
            drawText(n, topLeft = androidx.compose.ui.geometry.Offset(size.width - n.size.width - 10.dp.toPx(), 8.dp.toPx()))
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Palette.BrandGreen, "Next"); Legend(Palette.Warning, "Checkpoint"); if (me != null) Legend(Palette.BrandBlue, "You")
            Text("${pts.size} of ${stops.size} stops placed", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(label, style = MaterialTheme.typography.labelSmall, color = Extra.ink2, modifier = Modifier.padding(start = 5.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopRow(
    n: Int, s: Stop, last: Boolean, isNext: Boolean, checked: Map<String, Long>, everyone: Int, myId: String, nameOf: (String) -> String, me: GeoPoint?,
    onCheckIn: (() -> Unit)?, onEdit: (() -> Unit)?, onMap: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // The timeline: a numbered dot per stop (gold for checkpoints), joined by a line.
        Column(Modifier.width(34.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (isNext) Palette.BrandGreen else if (s.checkpoint) Palette.Warning else Extra.line),
                contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelLarge, color = if (isNext || s.checkpoint) Color.White else Extra.ink2) }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(Extra.line))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 12.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
            .then(if (isNext) Modifier.border(1.5.dp, Palette.BrandGreen, MaterialTheme.shapes.large) else Modifier)
            .clickable(enabled = onEdit != null || checked.isNotEmpty()) { if (onEdit != null) onEdit() else open = !open }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RawText(clock.format(Date(s.at)), style = MaterialTheme.typography.labelLarge, color = if (isNext) Palette.BrandGreen else Extra.ink3)
                if (isNext) Text("NEXT", style = MaterialTheme.typography.labelSmall, color = Palette.BrandGreen, modifier = Modifier.padding(start = 8.dp)
                    .clip(RoundedCornerShape(50)).background(Palette.BrandGreen.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
                if (s.checkpoint) Text("CHECKPOINT", style = MaterialTheme.typography.labelSmall, color = Palette.Warning, modifier = Modifier.padding(start = 6.dp)
                    .clip(RoundedCornerShape(50)).background(Palette.Warning.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.weight(1f))
                if (onEdit != null) Icon(Icons.Rounded.Edit, "Change", tint = Extra.ink3, modifier = Modifier.size(16.dp))
            }
            RawText(s.title.ifBlank { "Stop" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
            if (s.note.isNotBlank()) RawText(s.note, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            if (s.hasPlace) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = Palette.BrandBlue, modifier = Modifier.size(16.dp))
                RawText(coord(s.lat!!, s.lon!!) + (me?.let { " · " + km(com.bluemob.app.peaks.Peaks.distanceM(it.lat, it.lon, s.lat, s.lon)) + " away" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(start = 4.dp).weight(1f), maxLines = 1)
                Text("Map", style = MaterialTheme.typography.labelLarge, color = Palette.BrandBlue, modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(Palette.BrandBlue.copy(alpha = 0.12f)).clickable(onClick = onMap).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            if (s.checkpoint) {
                Text("${checked.size} of $everyone reached" + if (s.hasPlace) " · checks in within ${s.radiusM} m" else "",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Warning, modifier = Modifier.padding(top = 4.dp))
                if (myId in checked) Text("✓ You checked in at ${clock.format(Date(checked.getValue(myId)))}", style = MaterialTheme.typography.bodySmall, color = Palette.Success)
                if (open) checked.entries.sortedBy { it.value }.forEach { (id, at) ->
                    RawText("${nameOf(id)} · ${clock.format(Date(at))}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                onCheckIn?.let { Chip("📍 I'm here", true, it) }
            }
        }
    }
}

/** Add or change one stop, on a full screen: what, when, where (your position, typed, or a saved place), and whether it's a checkpoint. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopEditor(s: Stop, day0: Long, here: () -> GeoPoint?, places: List<PlacePick>, onDone: (Stop) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        StopEditorPage(s, day0, here, places, onDone, onDelete, onCancel)
    }
}

/** The stop editor's page itself (also drawn on its own in previews). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StopEditorPage(s: Stop, day0: Long, here: () -> GeoPoint?, places: List<PlacePick>, onDone: (Stop) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(s.title) }
    var note by rememberSaveable { mutableStateOf(s.note) }
    var day by rememberSaveable { mutableStateOf(((startOfDay(s.at) - day0) / 86_400_000L).toInt().coerceIn(0, 13)) }
    var time by rememberSaveable { mutableStateOf(clock.format(Date(s.at))) }
    var checkpoint by rememberSaveable { mutableStateOf(s.checkpoint) }
    var lat by rememberSaveable { mutableStateOf(s.lat?.let { "%.5f".format(Locale.US, it) } ?: "") }
    var lon by rememberSaveable { mutableStateOf(s.lon?.let { "%.5f".format(Locale.US, it) } ?: "") }
    var radius by rememberSaveable { mutableStateOf(s.radiusM) }
    var noFix by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(places.isNotEmpty() && s.title.isBlank() && s.lat == null && s.id == "preview") }
    var filter by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val la = com.bluemob.app.peaks.PeakLibrary.parseCoord(lat)?.takeIf { it in -90.0..90.0 }
    val lo = com.bluemob.app.peaks.PeakLibrary.parseCoord(lon)?.takeIf { it in -180.0..180.0 }
    val placeOk = (lat.isBlank() && lon.isBlank()) || (la != null && lo != null)
    val minutes = Regex("""^(\d{1,2})[:.](\d{2})$""").find(time.trim())?.let { m -> m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt() }?.takeIf { it < 24 * 60 }
    run {
        SubScreen(if (s.title.isBlank()) "New stop" else "Change the stop", onCancel) {
            item {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("What and where") }, placeholder = { Text("e.g. Base camp, lunch at the temple") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            item { GroupLabel("When") }
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(14) { d ->
                        val on = day == d
                        Column(Modifier.clip(MaterialTheme.shapes.medium).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable { day = d }.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Day ${d + 1}", style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            RawText(shortDay.format(Date(day0 + d * 86_400_000L)), style = MaterialTheme.typography.labelSmall,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else Extra.ink3)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(time, { time = it.take(5) }, label = { Text("Time (24 h)") }, placeholder = { Text("07:30") }, singleLine = true, isError = minutes == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(130.dp))
                    androidx.compose.foundation.lazy.LazyRow(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("06:00", "08:00", "12:00", "15:00", "18:00")) { t -> Chip(t, time == t) { time = t } }
                    }
                }
            }
            item { GroupLabel("Where") }
            item {
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.bluemob.app.ui.components.IconBadge(Icons.Rounded.Place, if (la != null && lo != null) Palette.BrandBlue else Extra.ink3, size = 36.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(if (la != null && lo != null) "Place set" else "No place yet", style = MaterialTheme.typography.titleSmall)
                            Text(if (la != null && lo != null) coord(la, lo) else "Optional. With a place, everyone can find it on a map, and checkpoints check people in by themselves.",
                                style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        if (la != null && lo != null) TextButton(onClick = { openInMaps(context, la, lo, title) }) { Text("Map") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { here()?.let { lat = "%.5f".format(Locale.US, it.lat); lon = "%.5f".format(Locale.US, it.lon); noFix = false } ?: run { noFix = true } }) {
                            Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)); Text("  Where I am")
                        }
                        if (places.isNotEmpty()) OutlinedButton(onClick = { picking = !picking }) { Icon(Icons.Rounded.Bookmarks, null, Modifier.size(18.dp)); Text("  Saved places") }
                        if (lat.isNotBlank() || lon.isNotBlank()) TextButton(onClick = { lat = ""; lon = "" }) { Text("Remove", color = Extra.rose) }
                    }
                    if (noFix) Text("No position yet. Turn on location (Compass tab), then try again.", style = MaterialTheme.typography.bodySmall, color = Extra.rose)
                    if (picking) {
                        OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search saved places and peaks") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) })
                        places.filter { filter.isBlank() || filter.trim().lowercase() in it.name.lowercase() }.take(12).forEach { p ->
                            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                                lat = "%.5f".format(Locale.US, p.lat); lon = "%.5f".format(Locale.US, p.lon); if (title.isBlank()) title = p.name; picking = false
                            }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (p.kind == "Peak") Icons.Rounded.Landscape else Icons.Rounded.Place, null, tint = Palette.BrandGreen, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    RawText(p.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${p.kind} · ${coord(p.lat, p.lon)}", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(lat, { lat = it.take(24) }, Modifier.weight(1f), singleLine = true, label = { Text("Latitude") }, placeholder = { Text("30.8612") },
                            isError = lat.isNotBlank() && la == null)
                        OutlinedTextField(lon, { lon = it.take(24) }, Modifier.weight(1f), singleLine = true, label = { Text("Longitude") }, placeholder = { Text("79.0634") },
                            isError = lon.isNotBlank() && lo == null)
                    }
                    if (!placeOk) Text("Give both, as 30.8612 or 30° 51' 40\" N.", style = MaterialTheme.typography.bodySmall, color = Extra.rose)
                }
            }
            item { GroupLabel("More") }
            item {
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.bluemob.app.ui.components.IconBadge(Icons.Rounded.Flag, Palette.Warning, size = 36.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Checkpoint", style = MaterialTheme.typography.titleSmall)
                            Text("Everyone should pass it; the group sees who has", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        Switch(checkpoint, { checkpoint = it })
                    }
                    if (checkpoint && la != null && lo != null) {
                        Text("Phones check in by themselves within", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 10.dp))
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(50, 150, 500).forEach { r -> Chip("$r m", radius == r) { radius = r } } }
                    }
                    OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
            item {
                Button(enabled = title.isNotBlank() && minutes != null && placeOk, onClick = {
                    onDone(s.copy(title = title.trim(), note = note.trim(), at = day0 + day * 86_400_000L + (minutes ?: 0) * 60_000L,
                        checkpoint = checkpoint, lat = la, lon = lo, radiusM = radius))
                }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text(if (s.title.isBlank()) "Add the stop" else "Save the stop") }
            }
            if (s.title.isNotBlank()) item {
                TextButton(onClick = onDelete, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)) { Text("Delete this stop", color = Extra.rose) }
            }
        }
    }
}

private val dayName = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val shortDay = SimpleDateFormat("EEE d", Locale.getDefault())
private fun startOfDay(t: Long) = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun coord(lat: Double, lon: Double) = "%.5f, %.5f".format(Locale.US, lat, lon)
private fun km(m: Double) = if (m < 1000) "${m.toInt()} m" else if (m < 10_000) "%.1f km".format(m / 1000) else "${(m / 1000).toInt()} km"

/** Opens the place in Google Maps (or any map app). */
fun openInMaps(context: android.content.Context, lat: Double, lon: Double, label: String) {
    val q = "%.6f,%.6f".format(Locale.US, lat, lon)
    val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$q?q=$q(" + android.net.Uri.encode(label.ifBlank { "Stop" }) + ")"))
    runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=$q")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The itinerary in one card on the trip group screen: the next stop, and opening the whole plan. */
@Composable
fun ItineraryCard(g: ChatGroup, myId: String, now: Long, onOpen: () -> Unit) {
    val next = Stop.next(g.itinerary, now)
    val cps = g.itinerary.filter { it.checkpoint }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.bluemob.app.ui.components.IconBadge(androidx.compose.material.icons.Icons.Rounded.Map, com.bluemob.app.ui.theme.Palette.BrandGreen)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (g.isTeam) "Team itinerary" else "Itinerary", style = MaterialTheme.typography.titleMedium)
                if (g.itinerary.isEmpty()) Text(if (g.canPlan(myId)) "Add the plan so everyone knows what's coming" else "No plan yet",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                else {
                    next?.let { RawText((if (it.at >= now) "Next: " else "Last: ") + "${it.title} · ${dayTime.format(Date(it.at))}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2) }
                    if (cps.isNotEmpty()) Text("${cps.count { g.checkins[it.id]?.containsKey(myId) == true }} of ${cps.size} checkpoints passed by you",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Warning)
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
