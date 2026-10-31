package com.bluemob.app.ui.chat

import androidx.compose.material.icons.rounded.Map
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

/**
 * The trip's plan: stops in time order, so everyone knows what's coming. Checkpoints show who has passed them; a phone
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
) {
    val canPlan = g.canPlan(myId) && !g.isClosed(now)
    var stops by remember(g.itineraryAt) { mutableStateOf(g.itinerary) }
    var editing by remember { mutableStateOf<Stop?>(null) }
    val changed = stops != g.itinerary
    val everyone = g.members.size + 1
    editing?.let { s ->
        StopEditor(s, tripStart = g.createdAt, here = here, onDone = { done ->
            stops = (stops.filter { it.id != done.id } + done).sortedBy { it.at }; editing = null
        }, onDelete = { stops = stops.filter { it.id != s.id }; editing = null }, onCancel = { editing = null })
    }
    SubScreen(if (g.isTeam) "Team itinerary" else "Itinerary", onBack) {
        item {
            Text(when {
                canPlan && stops.isEmpty() -> "Add the plan: where you'll be and when. Mark the places everyone must pass as checkpoints."
                canPlan -> "You plan this trip. Everyone sees the plan; checkpoints show who has passed them."
                g.isTeam -> "Set by the team's captain."
                else -> "Set by the group's admin."
            }, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        if (stops.isEmpty() && !canPlan) item {
            Text("No plan yet.", style = MaterialTheme.typography.bodyLarge, color = Extra.ink3, modifier = Modifier.padding(vertical = 24.dp))
        }
        val next = Stop.next(stops, now)
        stops.forEachIndexed { i, s ->
            item(key = s.id) {
                StopRow(s, i == stops.lastIndex, s.id == next?.id && s.at >= now, g.checkins[s.id].orEmpty(), everyone, myId, nameOf,
                    onCheckIn = if (s.checkpoint && !g.isClosed(now) && g.checkins[s.id]?.containsKey(myId) != true && !changed) ({ onCheckIn(s.id) }) else null,
                    onEdit = if (canPlan) ({ editing = s }) else null)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopRow(
    s: Stop, last: Boolean, isNext: Boolean, checked: Map<String, Long>, everyone: Int, myId: String, nameOf: (String) -> String,
    onCheckIn: (() -> Unit)?, onEdit: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        // The timeline: a dot per stop (a flag for checkpoints), joined by a line.
        Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (s.checkpoint) Palette.Warning else if (isNext) Palette.BrandGreen else Extra.sand),
                contentAlignment = Alignment.Center) { Text(if (s.checkpoint) "🚩" else "•", style = MaterialTheme.typography.labelMedium) }
            if (!last) Box(Modifier.width(2.dp).height(86.dp).background(Extra.line))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 14.dp).clip(MaterialTheme.shapes.medium).clickable(enabled = onEdit != null || checked.isNotEmpty()) {
            if (onEdit != null) onEdit() else open = !open
        }.padding(4.dp)) {
            RawText(dayTime.format(Date(s.at)), style = MaterialTheme.typography.labelMedium, color = if (isNext) Palette.BrandGreen else Extra.ink3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RawText(s.title.ifBlank { "Stop" }, style = MaterialTheme.typography.titleMedium)
                if (isNext) Text("Next", style = MaterialTheme.typography.labelSmall, color = Palette.BrandGreen, modifier = Modifier.padding(start = 8.dp)
                    .clip(RoundedCornerShape(50)).background(Palette.BrandGreen.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            if (s.note.isNotBlank()) RawText(s.note, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            if (s.checkpoint) {
                Text("Checkpoint · ${checked.size} of $everyone reached" + if (s.hasPlace) " · ${s.radiusM} m circle" else "",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Warning, modifier = Modifier.padding(top = 2.dp))
                if (myId in checked) Text("✓ You checked in at ${clock.format(Date(checked.getValue(myId)))}", style = MaterialTheme.typography.bodySmall, color = Palette.Success)
                if (open) checked.entries.sortedBy { it.value }.forEach { (id, at) ->
                    RawText("${nameOf(id)} · ${clock.format(Date(at))}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                onCheckIn?.let { Chip("📍 I'm here", true, it) }
            }
        }
    }
}

/** Add or change one stop: what, which day and time, a note, and whether it's a checkpoint (with a place). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopEditor(s: Stop, tripStart: Long, here: () -> GeoPoint?, onDone: (Stop) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val day0 = remember(tripStart) { Calendar.getInstance().apply { timeInMillis = tripStart; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    var title by rememberSaveable { mutableStateOf(s.title) }
    var note by rememberSaveable { mutableStateOf(s.note) }
    var day by rememberSaveable { mutableStateOf(((s.at - day0) / 86_400_000L).toInt().coerceIn(0, 13)) }
    var time by rememberSaveable { mutableStateOf(clock.format(Date(s.at))) }
    var checkpoint by rememberSaveable { mutableStateOf(s.checkpoint) }
    var place by remember { mutableStateOf(if (s.hasPlace) s.lat!! to s.lon!! else null) }
    var radius by rememberSaveable { mutableStateOf(s.radiusM) }
    var noFix by remember { mutableStateOf(false) }
    val minutes = Regex("""^(\d{1,2})[:.](\d{2})$""").find(time.trim())?.let { m -> m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt() }?.takeIf { it < 24 * 60 }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (s.title.isBlank()) "New stop" else "Change the stop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("What and where") }, placeholder = { Text("e.g. Base camp, lunch at the temple") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { d -> Chip("Day ${d + 1}", day == d) { day = d } }
                }
                OutlinedTextField(time, { time = it.take(5) }, label = { Text("Time (24 h)") }, placeholder = { Text("07:30") }, singleLine = true, isError = minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Checkpoint", style = MaterialTheme.typography.titleSmall)
                        Text("Everyone should pass it; it shows who has", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    Switch(checkpoint, { checkpoint = it })
                }
                if (checkpoint) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { here()?.let { place = it.lat to it.lon; noFix = false } ?: run { noFix = true } }) {
                            Text(if (place == null) "📍 Use where I am" else "📍 Place set")
                        }
                        if (place != null) TextButton(onClick = { place = null }) { Text("Remove") }
                    }
                    if (noFix) Text("No position yet. Turn on location (Compass tab), then try again.", style = MaterialTheme.typography.bodySmall, color = Extra.rose)
                    if (place != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 150, 500).forEach { r -> Chip("$r m", radius == r) { radius = r } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && minutes != null, onClick = {
                onDone(s.copy(title = title.trim(), note = note.trim(), at = day0 + day * 86_400_000L + (minutes ?: 0) * 60_000L,
                    checkpoint = checkpoint, lat = place?.first.takeIf { checkpoint }, lon = place?.second.takeIf { checkpoint }, radiusM = radius))
            }) { Text("Done") }
        },
        dismissButton = {
            Row {
                if (s.title.isNotBlank()) TextButton(onClick = onDelete) { Text("Delete", color = Extra.rose) }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
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
