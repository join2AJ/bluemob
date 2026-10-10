package com.bluemob.app.ui.rescue

import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.data.RescueMessage
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.rescue.Helper
import com.bluemob.app.rescue.HelperStatus
import com.bluemob.app.rescue.RescueRoom
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.Geo
import com.bluemob.app.util.TimeText
import com.bluemob.app.util.cardinal
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

class RescueActions(
    val onBack: () -> Unit = {},
    val onJoin: () -> Unit = {},
    val onSend: (String) -> Unit = {},
    val onArrived: () -> Unit = {},
    val onLeave: () -> Unit = {},
    /** Opens the compass pointed at the person. */
    val onNavigate: () -> Unit = {},
    val onGuide: (String) -> Unit = {},
    /** For the person in need: flash and beep so helpers can find them. */
    val onSignal: () -> Unit = {},
    val onSafe: () -> Unit = {},
    /** Rate someone in this rescue: (their ID, what). */
    val onRate: (String, com.bluemob.app.trust.RatingKind) -> Unit = { _, _ -> },
    /** For the person in need: what's on that helps them be found ("location", "bluetooth", "mesh", "internet"), and turning one on. */
    val readiness: Map<String, Boolean> = emptyMap(),
    val onTurnOn: (String) -> Unit = {},
    /** For helpers: make their phone sound, flash its light or light up its screen ("sound", "flash", "screen", "all"). */
    val onSignalThem: (String) -> Unit = {},
)

private val HELPER_REPLIES = listOf("On my way 🏃", "Stay where you are", "Can you hear my whistle?", "Shine your light", "I see you!", "Need more people")
private val VICTIM_REPLIES = listOf("I can hear you!", "I can see your light", "Please hurry", "I'm OK, take care", "I'm by the water", "Battery is low")

/**
 * The rescue group around one SOS. Helpers see how to reach the person, who else is coming, and a shared chat.
 * The person in need sees who's coming and how far away they are.
 */
@Composable
fun RescueScreen(room: RescueRoom, myId: String, me: GeoPoint?, headings: Flow<Float>, actions: RescueActions, rated: Set<String> = emptySet(),
    /** The person in trouble's blood group and age, from their SOS, e.g. "🩸 B+ · age 34". */
    medical: String? = null) {
    val heading by remember(headings) { headings }.collectAsStateWithLifecycle(initialValue = 0f)
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(room.chat.size) { if (room.chat.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    val guideId = remember(room.note) { SkyBot.guideIdFor(room.note) }

    Column(Modifier.fillMaxSize().background(Extra.sand).imePadding()) {
        // Top bar.
        Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(if (room.mine) "Your rescue" else "Helping ${room.victimName}", style = MaterialTheme.typography.titleMedium)
                    Text("${room.coming.size + 1} in this group · " + (listOf(if (room.mine) "you" else room.victimName) + room.coming.map { if (it.nodeId == myId) "you" else it.name }).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
                    Text("Started ${rescueStamp(room.startedAt)}" + (if (room.ended) room.chat.lastOrNull()?.let { " · ended ${rescueStamp(it.at).substringAfter(", ")}" } ?: " · ended" else ""),
                        style = MaterialTheme.typography.labelSmall, color = Extra.ink3, maxLines = 1)
                }
                Box(Modifier.padding(end = 12.dp)) {
                    when {
                        room.ended -> Tag("ENDED", Extra.sand2, Extra.ink2)
                        room.myStatus == HelperStatus.ARRIVED -> Tag("ARRIVED", MaterialTheme.colorScheme.primary, Color.White)
                        room.myStatus == HelperStatus.COMING -> Tag("ON THE WAY", Extra.sky, Color.White)
                        else -> Tag("SOS", Extra.rose, Color.White, dot = Color.White)
                    }
                }
            }
            HorizontalDivider(color = Extra.line)
        }

        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp)) {
            if (room.ended) item { RateRescue(room, myId, rated, actions) }
            item {
                if (room.mine) VictimCard(room, actions) else TargetCard(room, me, heading, guideId, actions, medical)
            }
            item { GroupLabel(if (room.mine) "Who's coming" else "Who's helping") }
            item { HelpersCard(room, myId) }
            // Helpers close by: make their phone call out, so you find them even if they can't answer.
            if (!room.mine && !room.ended) item {
                Surface(shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.padding(top = 12.dp)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth()) {
                        Text("Find ${room.victimName}", style = MaterialTheme.typography.titleMedium)
                        Text("Close but can't see them? Make their phone call out for 20 seconds. Works even if they can't answer.",
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("sound" to "🔊 Sound", "flash" to "🔦 Flash", "screen" to "📱 Screen", "all" to "All").forEach { (k, l) ->
                                OutlinedButton(onClick = { actions.onSignalThem(k) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                                    Text(l, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (!room.mine && room.myStatus == HelperStatus.COMING) item {
                GroupLabel("Before you set off")
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line)) {
                    Text(
                        "• Tell someone where you're going, or post it here\n• Take water, a light, a warm layer and any first-aid kit\n" +
                            "• Don't become a second casualty: check for danger before you get close\n• Call out and use your whistle as you get near",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    )
                }
            }
            item { GroupLabel("Group chat") }
            if (room.chat.isEmpty()) item {
                Text("No messages yet. Everyone in this group sees what you write here.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
            }
            items(room.chat, key = { it.id }) { m -> ChatLine(m, myId, room.victimId) }
        }

        // Bottom: join, chat input, or ended.
        Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
            HorizontalDivider(color = Extra.line)
            when {
                room.ended -> Text("${room.victimName.takeUnless { room.mine } ?: "You"} ${if (room.mine) "are" else "is"} safe. This rescue has ended. Thank you, everyone 💚",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(18.dp))
                !room.iAmIn -> Column(Modifier.padding(14.dp)) {
                    Text("Join to tell ${room.victimName} you're coming. Your position is shared with this group so they and others can see where you are.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    Button(onClick = actions.onJoin, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)) { Text("I'm coming", style = MaterialTheme.typography.titleMedium) }
                }
                else -> {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(if (room.mine) VICTIM_REPLIES else HELPER_REPLIES) { r -> Chip(r) { actions.onSend(r) } }
                    }
                    Row(Modifier.padding(start = 12.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Extra.sand).padding(horizontal = 18.dp, vertical = 13.dp)) {
                            if (draft.isEmpty()) Text("Message the group", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(draft, { draft = it.take(300) }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                maxLines = 4, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                        }
                        IconButton(onClick = { actions.onSend(draft); draft = "" }, enabled = draft.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else Extra.ink3)
                        }
                    }
                }
            }
        }
    }
}

/** For helpers: where the person is, how to get there, and what to bring. */
@Composable
private fun TargetCard(room: RescueRoom, me: GeoPoint?, heading: Float, guideId: String?, actions: RescueActions, medical: String? = null) {
    val target = room.victimPos
    val there = target?.let { GeoPoint(it.lat, it.lon, it.uncertaintyM.toFloat(), it.at) }
    val dist = if (me != null && there != null) Geo.distanceM(me, there) else null
    val bearing = if (me != null && there != null) Geo.bearingDeg(me, there) else null
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(null, room.victimName, room.victimId, 52.dp, sos = !room.ended)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("${room.victimName} needs help", style = MaterialTheme.typography.titleLarge)
                    Text("SOS ${TimeText.ago(room.startedAt)}" + (room.battery?.let { " · their battery $it%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    medical?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Extra.rose, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            if (room.note.isNotBlank()) Text("“${room.note}”", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp))

            // How to get there.
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Arrow(bearing?.let { (it - heading).toFloat() }, Modifier.size(64.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(dist?.let { Geo.formatDistance(it) } ?: "Distance unknown", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when {
                            dist != null && bearing != null -> "${bearing.roundToInt()}° ${cardinal(bearing)} · about ${RescueRoom.walkMinutes(dist)} min walk"
                            target == null -> "They haven't shared a position. Ask in the chat."
                            else -> "Waiting for your GPS… step into the open"
                        }, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                    )
                }
            }
            target?.let {
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.skyTint).padding(12.dp)) {
                    Text(if (it.gps) "WHERE THEY ARE · GPS" else "WHERE THEY ARE · ESTIMATE", style = MaterialTheme.typography.labelSmall, color = Extra.sky)
                    Text(it.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target != null) Button(onClick = actions.onNavigate) { Text("Navigate") }
                guideId?.let { id -> GuideContent.byId(id) }?.let { a -> OutlinedButton(onClick = { actions.onGuide(a.id) }) { Text(a.title, maxLines = 1) } }
                    ?: OutlinedButton(onClick = { actions.onGuide("help-sos") }) { Text("How to help") }
            }
            if (room.iAmIn && !room.ended) Row(Modifier.padding(top = 4.dp)) {
                if (room.myStatus == HelperStatus.COMING) TextButton(onClick = actions.onArrived) { Text("I'm here ✓") }
                TextButton(onClick = actions.onLeave) { Text("I can't come", color = Extra.ink3) }
            }
        }
    }
}

/** For the person in need: who's coming, and what to do while waiting. */
@Composable
private fun VictimCard(room: RescueRoom, actions: RescueActions) {
    val nearest = room.coming.mapNotNull { h -> room.distanceM(h)?.let { h to it } }.minByOrNull { it.second }
    Surface(shape = MaterialTheme.shapes.large, color = if (room.coming.isEmpty()) Extra.emberTint else Extra.pineTint) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                when {
                    room.ended -> "You're safe"
                    room.coming.isEmpty() -> "Your SOS is out. Waiting for someone to answer"
                    room.coming.size == 1 -> "${room.coming[0].name} is coming to help"
                    else -> "${room.coming.size} people are coming to help"
                }, style = MaterialTheme.typography.titleLarge,
            )
            nearest?.let { (h, d) ->
                Text("Nearest: ${h.name}, ${Geo.formatDistance(d)} away · about ${RescueRoom.walkMinutes(d)} min", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            }
            Text("Stay where you are if you can. Keep warm and save battery. When they're close, use the SOS signal so they can find you.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
            // What helps them find you: anything off gets a button.
            if (!room.ended && actions.readiness.isNotEmpty()) Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "location" to ("📍 Location" to "so helpers know where you are"),
                    "bluetooth" to ("📶 Bluetooth" to "so phones nearby find you without signal"),
                    "mesh" to ("📡 Hunting mode" to "keeps scanning for phones nearby, so your SOS reaches everyone in range"),
                    "internet" to ("🌐 Internet" to "reaches your SOS contacts anywhere"),
                ).forEach { (k, v) ->
                    val on = actions.readiness[k] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.first + if (on) "  ✓ on" else "  · off", style = MaterialTheme.typography.titleSmall, color = if (on) MaterialTheme.colorScheme.primary else Extra.rose)
                            if (!on) Text(v.second, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        if (!on) TextButton(onClick = { actions.onTurnOn(k) }) { Text("Turn on") }
                    }
                }
            }
            if (!room.ended) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onSignal, colors = ButtonDefaults.buttonColors(containerColor = Extra.rose, contentColor = Color.White)) { Text("Signal: light & sound") }
                OutlinedButton(onClick = actions.onSafe) { Text("I'm safe now") }
            }
        }
    }
}

@Composable
private fun HelpersCard(room: RescueRoom, myId: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line)) {
        Column(Modifier.fillMaxWidth()) {
            if (room.helpers.isEmpty()) Text(if (room.mine) "No one has answered yet. Your SOS keeps going out to every phone that comes into range." else "No one yet. Be the first.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(14.dp))
            room.helpers.forEachIndexed { i, h -> HelperRow(room, h, h.nodeId == myId, divider = i > 0) }
        }
    }
}

@Composable
private fun HelperRow(room: RescueRoom, h: Helper, isMe: Boolean, divider: Boolean) {
    if (divider) HorizontalDivider(Modifier.padding(start = 64.dp), color = Extra.line)
    val d = room.distanceM(h)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(null, h.name, h.nodeId, 38.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (isMe) "You" else h.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when (h.status) {
                    HelperStatus.ARRIVED -> "With ${if (room.mine) "you" else room.victimName}"
                    HelperStatus.LEFT -> "Can't come"
                    HelperStatus.COMING -> d?.let { "${Geo.formatDistance(it)} from ${if (room.mine) "you" else room.victimName} · about ${RescueRoom.walkMinutes(it)} min" } ?: "On the way · position not shared yet"
                } + " · updated ${TimeText.ago(h.updatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
            )
        }
        Tag(h.status.label, when (h.status) { HelperStatus.COMING -> Extra.skyTint; HelperStatus.ARRIVED -> Extra.pineTint; HelperStatus.LEFT -> Extra.sand2 },
            when (h.status) { HelperStatus.COMING -> Extra.sky; HelperStatus.ARRIVED -> MaterialTheme.colorScheme.primary; HelperStatus.LEFT -> Extra.ink3 })
    }
}

@Composable
private fun ChatLine(m: RescueMessage, myId: String, victimId: String) {
    if (m.kind != RescueRoom.TEXT) {
        val text = when (m.kind) {
            RescueRoom.JOIN -> "${if (m.fromNodeId == myId) "You" else m.fromName} joined: ${m.text}"
            RescueRoom.ARRIVED -> "${if (m.fromNodeId == myId) "You" else m.fromName} arrived"
            RescueRoom.LEAVE -> "${if (m.fromNodeId == myId) "You" else m.fromName} can't come after all"
            else -> m.text
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Extra.ink2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        return
    }
    val mine = m.fromNodeId == myId
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) Text(m.fromName + if (m.fromNodeId == victimId) " · needs help" else "", style = MaterialTheme.typography.labelSmall,
            color = if (m.fromNodeId == victimId) Extra.rose else Extra.ink3, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
        Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp)).background(
            when { mine -> MaterialTheme.colorScheme.primary; m.fromNodeId == victimId -> Extra.rose.copy(alpha = 0.14f); else -> Extra.bubbleThem },
        ).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(m.text, style = MaterialTheme.typography.bodyLarge, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
        }
        Text(rescueStamp(m.at), style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
}

/** "7 Oct 2026, 13:05": rescues are records, so they keep exact times, not "2 h ago". */
fun rescueStamp(at: Long): String = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

/** An arrow pointing at the person, relative to where the phone faces. */
@Composable
private fun Arrow(relative: Float?, modifier: Modifier) {
    val angle by animateFloatAsState(relative ?: 0f, label = "arrow")
    val pine = MaterialTheme.colorScheme.primary
    val ring = Extra.line
    val ink3 = Extra.ink3
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(angle)) {
            drawCircle(ring, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            val c = center
            val r = size.minDimension / 2
            val p = Path().apply {
                moveTo(c.x, c.y - r * 0.72f)
                lineTo(c.x + r * 0.38f, c.y + r * 0.42f)
                lineTo(c.x, c.y + r * 0.2f)
                lineTo(c.x - r * 0.38f, c.y + r * 0.42f)
                close()
            }
            drawPath(p, if (relative == null) ink3 else pine)
        }
    }
}

/** After a rescue: helpers say whether the SOS was real; the person who was helped thanks the people who came. */
@Composable
private fun RateRescue(room: RescueRoom, myId: String, rated: Set<String>, actions: RescueActions) {
    Surface(shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (room.mine) {
                Text("Thank the people who came", style = MaterialTheme.typography.titleMedium)
                Text("Each thank-you adds to their stars, so others know they can be trusted.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                room.helpers.filter { it.status != HelperStatus.LEFT }.forEach { h ->
                    val done = "${h.nodeId}|thanks" in rated
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (done) Text("Thanked ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        else Button(onClick = { actions.onRate(h.nodeId, com.bluemob.app.trust.RatingKind.THANKS) }) { Text("👏 Thank") }
                    }
                }
            } else if (room.iAmIn || room.helpers.any { it.nodeId == myId }) {
                val real = "${room.victimId}|genuine_sos" in rated
                val fake = "${room.victimId}|fake_sos" in rated
                Text("Was this SOS real?", style = MaterialTheme.typography.titleMedium)
                Text("Your answer goes into ${room.victimName}'s stars, so people can tell a genuine SOS from a prank next time.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onRate(room.victimId, com.bluemob.app.trust.RatingKind.GENUINE_SOS) }, enabled = !real) { Text(if (real) "Real ✓" else "✅ It was real") }
                    OutlinedButton(onClick = { actions.onRate(room.victimId, com.bluemob.app.trust.RatingKind.FAKE_SOS) }, enabled = !fake) { Text(if (fake) "Fake ✓" else "⚠ Fake") }
                }
                if (!room.mine) TextButton(onClick = { actions.onRate(room.victimId, com.bluemob.app.trust.RatingKind.THANKS) }) { Text("Or just say thanks to ${room.victimName}") }
            } else {
                Text("${room.victimName} is safe now.", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}


/** Every rescue on this phone, in two lists: when you asked for help, and when you helped. Newest first. */
@Composable
fun RescuesScreen(rescues: List<com.bluemob.app.rescue.RescueRoom>, onBack: () -> Unit, onOpen: (String) -> Unit, mySosId: String? = null) {
    // Ongoing: our own SOS that's on right now, or one we're on the way to (or at) that hasn't ended. These go first.
    val now = System.currentTimeMillis()
    val ongoing = { r: com.bluemob.app.rescue.RescueRoom ->
        !r.ended && (if (r.mine) r.id == mySosId else (r.myStatus == HelperStatus.COMING || r.myStatus == HelperStatus.ARRIVED) && now - r.startedAt < 24 * 3_600_000L)
    }
    com.bluemob.app.ui.components.SubScreen("Rescues", onBack) {
        val asked = rescues.filter { it.mine }.sortedWith(compareByDescending<com.bluemob.app.rescue.RescueRoom> { ongoing(it) }.thenByDescending { it.startedAt })
        val helped = rescues.filter { !it.mine }.sortedWith(compareByDescending<com.bluemob.app.rescue.RescueRoom> { ongoing(it) }.thenByDescending { it.startedAt })
        if (rescues.isEmpty()) item { Text("No rescues yet.", color = Extra.ink2, modifier = Modifier.padding(top = 16.dp)) }
        listOf("You asked for help" to asked, "You helped" to helped).forEach { (label, list) ->
            if (list.isNotEmpty()) {
                item { com.bluemob.app.ui.components.GroupLabel("$label · ${list.size}") }
                list.forEach { r ->
                    item(key = r.id) {
                        val live = ongoing(r)
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (live) Extra.rose.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                            .then(if (live) Modifier.border(1.5.dp, Extra.rose, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { onOpen(r.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.ended) "✅" else "🆘", fontSize = 24.sp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (r.mine) "Your SOS" + (r.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") else "Helping ${r.victimName}",
                                        style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                    if (live) Text("ONGOING", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(Extra.rose).padding(horizontal = 7.dp, vertical = 2.dp))
                                }
                                Text(rescueStamp(r.startedAt) + " · " + (if (r.ended) "ended" else if (live) "happening now" else "no news") + " · ${r.coming.size} came to help",
                                    style = MaterialTheme.typography.bodySmall, color = if (live) Extra.rose else Extra.ink2)
                            }
                        }
                    }
                }
            }
        }
    }
}
