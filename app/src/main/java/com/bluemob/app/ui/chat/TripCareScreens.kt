package com.bluemob.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.AltitudeCheck
import com.bluemob.app.chat.AmsCheck
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.SwitchCard
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette

fun levelColor(l: AltitudeCheck.Level): Color = when (l) {
    AltitudeCheck.Level.WELL -> Palette.BrandGreen
    AltitudeCheck.Level.MILD -> Palette.Gold
    AltitudeCheck.Level.MODERATE -> Color(0xFFE07F2E)
    AltitudeCheck.Level.SEVERE -> Palette.Danger
}

/** The daily altitude check: four questions, the danger signs, and what to do. */
@Composable
fun AltitudeCheckScreen(groupName: String, altitudeM: Double?, doneToday: Boolean, onBack: () -> Unit, onSend: (AltitudeCheck.Answers, Int) -> Unit) {
    var answers by remember { mutableStateOf(listOf(-1, -1, -1, -1)) }
    var danger by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    val complete = answers.all { it >= 0 }
    val a = AltitudeCheck.Answers(answers[0].coerceAtLeast(0), answers[1].coerceAtLeast(0), answers[2].coerceAtLeast(0), answers[3].coerceAtLeast(0), danger)
    val level = AltitudeCheck.level(a)
    SubScreen("Altitude check", onBack) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Landscape, Palette.BrandBlue, size = 48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    RawText(groupName, style = MaterialTheme.typography.titleMedium)
                    Text(altitudeM?.let { "You're at about ${it.toInt()} m" } ?: "Altitude unknown: turn on location", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
            }
            Text(if (doneToday && !sent) "You did today's check. You can do it again if you feel different." else "How do you feel right now? It goes to your trip admin and captain only.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 10.dp))
        }
        AltitudeCheck.QUESTIONS.forEachIndexed { qi, (q, options) ->
            item { GroupLabel(q) }
            item {
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                    options.forEachIndexed { oi, label ->
                        val on = answers[qi] == oi
                        Row(Modifier.fillMaxWidth().clickable { answers = answers.toMutableList().also { it[qi] = oi }; sent = false }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(on, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            Text("$oi", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                        }
                    }
                }
            }
        }
        item { GroupLabel("Danger signs") }
        item {
            SwitchCard(Icons.Rounded.WarningAmber, Palette.Danger, "Any of these?", AltitudeCheck.DANGER, danger) { danger = it; sent = false }
        }
        if (complete) item {
            val c = levelColor(level)
            Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(MaterialTheme.shapes.large).background(c.copy(alpha = 0.14f))
                .border(1.dp, c.copy(alpha = 0.5f), MaterialTheme.shapes.large).padding(16.dp)) {
                Text(level.label, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c)
                Text("Score ${a.score} of 12" + if (danger) " · danger signs" else "", style = MaterialTheme.typography.labelLarge, color = Extra.ink2)
                Text(level.advice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Button(onClick = { onSend(a, altitudeM?.toInt() ?: 0); sent = true }, enabled = complete && !sent, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).padding(bottom = 4.dp)) {
                Text(if (sent) "Sent to your trip admin ✓" else "Send to my trip admin")
            }
            Text("Lake Louise score. A guide, not a diagnosis: if in doubt, go down. It's erased from their phone when the trip ends.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        }
    }
}

/** Headcount at the bus or camp: tick who's here (phones nearby are ticked for you), then tell the group who's missing. */
@Composable
fun HeadcountScreen(g: ChatGroup, myId: String, nameOf: (String) -> String, nearby: Set<String>, onBack: () -> Unit, onRollCall: () -> Unit, onSave: (List<String>, List<String>) -> Unit) {
    val everyone = remember(g.members) { listOf(myId) + g.members.keys.filter { it != myId }.sortedBy { nameOf(it) } }
    var here by remember { mutableStateOf(setOf(myId) + nearby.filter { it in g.members }) }
    var saved by remember { mutableStateOf(false) }
    val missing = everyone.filter { it !in here }
    SubScreen("Headcount", onBack) {
        item {
            val c = if (missing.isEmpty()) Palette.BrandGreen else Palette.Gold
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(c.copy(alpha = 0.14f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Rounded.DirectionsBus, c, size = 52.dp)
                Text("${here.size} / ${everyone.size}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 6.dp))
                Text(if (missing.isEmpty()) "Everyone is here" else "${missing.size} missing", style = MaterialTheme.typography.titleMedium, color = c)
                Text("Phones nearby are ticked for you. Tick the others when you see them.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { here = here + nearby.filter { it in g.members } }, modifier = Modifier.weight(1f)) { Text("Tick phones nearby", maxLines = 1) }
                OutlinedButton(onClick = onRollCall, modifier = Modifier.weight(1f)) { Text("Roll call", maxLines = 1) }
            }
        }
        item { GroupLabel("People · ${everyone.size}") }
        item {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                everyone.forEach { id ->
                    val on = id in here
                    Row(Modifier.fillMaxWidth().clickable { here = if (on) here - id else here + id; saved = false }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, onCheckedChange = null, modifier = Modifier.padding(end = 6.dp))
                        RawText(nameOf(id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (id in nearby) Text("Phone nearby", style = MaterialTheme.typography.labelSmall, color = Palette.BrandGreen,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.BrandGreen.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
        item {
            Button(onClick = { onSave(everyone.filter { it in here }, missing); saved = true }, enabled = !saved, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(if (saved) "Told the group ✓" else if (missing.isEmpty()) "Save: everyone's here" else "Save and tell the group who's missing")
            }
            g.headcounts.lastOrNull()?.let { h ->
                Text("Last headcount ${java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(h.at))}: ${h.here.size} of ${h.here.size + h.missing.size}",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
            }
        }
    }
}

/** Every photo and video shared in the trip group and its teams, as one album. */
@Composable
fun TripPhotosScreen(title: String, photos: List<MessageEntity>, thumbnail: suspend (MessageEntity) -> android.graphics.Bitmap?, nameOf: (MessageEntity) -> String,
    onBack: () -> Unit, onOpen: (MessageEntity) -> Unit, onAdd: () -> Unit) {
    SubScreen("Trip photos", onBack) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RawText(title, style = MaterialTheme.typography.titleMedium)
                    Text("${photos.size} photos and videos from everyone in the trip", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                Button(onClick = onAdd) { Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(18.dp)); Text("Add", Modifier.padding(start = 6.dp)) }
            }
        }
        if (photos.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Rounded.PhotoLibrary, Palette.Violet, size = 72.dp)
                Text("No photos yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Photos anyone sends in the group chat or a team chat show up here.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        photos.sortedByDescending { it.createdAt }.chunked(3).forEach { row ->
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { m ->
                        var bmp by remember(m.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(m.id) { bmp = runCatching { thumbnail(m) }.getOrNull() }
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Extra.sand).clickable { onOpen(m) }) {
                            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                ?: Icon(Icons.Rounded.PhotoLibrary, null, tint = Extra.ink3, modifier = Modifier.align(Alignment.Center))
                            RawText(nameOf(m), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The trip group's Health tab: the daily altitude check, and health cards shared with the admin. */
fun LazyListScope.tripHealthTab(g: ChatGroup, myId: String, now: Long, nameOf: (String) -> String, myCard: String, a: TripActions) {
    val closed = g.isClosed(now)
    val manage = g.canManage(myId)
    item { GroupLabel("Daily altitude check") }
    item {
        val mine = g.amsChecks[myId]
        val today = g.myAmsAt > 0 && com.bluemob.app.chat.TripWatch.localDay(g.myAmsAt) == com.bluemob.app.chat.TripWatch.localDay(now)
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(enabled = !closed, onClick = a.onAltitude).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Landscape, Palette.BrandBlue)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (today) "Done today" else "How do you feel at altitude?", style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        mine != null && today -> "${mine.level.label} · score ${mine.score}"
                        g.amsDaily -> "Your admin asks everyone each morning. Four questions."
                        else -> "Four questions about headache, stomach, tiredness and dizziness."
                    }, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                if (!closed) Text(if (today) "Again" else "Check", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (g.isOwner(myId) && !g.isTeam && !closed) item {
        SwitchCard(Icons.Rounded.Landscape, Palette.BrandBlue, "Ask everyone every morning", "A reminder at 7:00 each day. Answers come to you and their captain.",
            g.amsDaily, Modifier.padding(top = 8.dp), a.onAmsDaily)
    }
    if (manage && g.amsChecks.isNotEmpty()) {
        item { GroupLabel("Latest checks · ${g.amsChecks.size}") }
        item {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                g.amsChecks.entries.sortedByDescending { it.value.score + if (it.value.danger) 20 else 0 }.forEach { (id, c) -> AmsRow(nameOf(id), c, now) }
            }
        }
    }
    item { GroupLabel("Health card") }
    item {
        SwitchCard(Icons.Rounded.MedicalInformation, Palette.Danger, "Share mine with the trip admin",
            "Blood group, allergies, conditions and who to call, so they can help fast. Only the admin and your captain see it, and it's erased from their phone when the trip ends.",
            g.shareHealth, onChange = a.onShareHealth)
        if (myCard.isNotBlank()) RawText(myCard, style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp))
    }
    if (manage) {
        item { GroupLabel("Shared with you · ${g.health.size}") }
        item {
            if (g.health.isEmpty()) Text("No one has shared their health card yet. Each person chooses in this tab.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
            else Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                g.health.entries.sortedBy { nameOf(it.key) }.forEach { (id, card) ->
                    var open by remember { mutableStateOf(false) }
                    SettingRow(Icons.Rounded.MedicalInformation, Palette.Danger, nameOf(id), card.lines().firstOrNull { it.isNotBlank() }?.take(60), onClick = { open = !open })
                    if (open) RawText(card, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 62.dp, end = 14.dp, bottom = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun AmsRow(name: String, c: AmsCheck, now: Long) {
    val col = levelColor(c.level)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(col))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            RawText(name, style = MaterialTheme.typography.bodyLarge)
            Text("${c.level.label} · score ${c.score}" + (if (c.altM > 0) " · ${c.altM} m" else "") + " · " + com.bluemob.app.util.TimeText.ago(c.at, now),
                style = MaterialTheme.typography.bodySmall, color = if (c.level >= AltitudeCheck.Level.MODERATE) col else Extra.ink2)
        }
    }
}

/** Trip extras in the Plan tab: the photo album and the PDF report. */
fun LazyListScope.tripExtras(g: ChatGroup, myId: String, photoCount: Int, a: TripActions) {
    item { GroupLabel("Memories and records") }
    item {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
            SettingRow(Icons.Rounded.PhotoLibrary, Palette.Violet, "Trip photos", if (photoCount == 0) "Photos from the group and team chats, in one album" else "$photoCount photos and videos", onClick = a.onPhotos)
            if (g.canManage(myId)) SettingRow(Icons.Rounded.PictureAsPdf, Palette.Danger, "Trip report (PDF)", "People, plan, check-ins, headcounts, roll calls. No health cards", divider = true, onClick = a.onReport)
        }
    }
}
