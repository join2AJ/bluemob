package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.StatusTick
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.components.TypingDots
import com.bluemob.app.ui.dashboard.linkWords
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.TimeText
import com.bluemob.app.util.shortId
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun ChatScreen(
    nodeId: String,
    person: Person?,
    messages: List<MessageEntity>,
    typing: Boolean,
    meshEvents: SharedFlow<MeshEvent>,
    myName: String,
    myId: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPing: () -> Boolean,
    onInfo: (String) -> Unit,
    onPerson: () -> Unit,
    onAction: (String) -> Unit,
    /** Starts a voice (false) or video (true) call. */
    onCall: (Boolean) -> Unit = {},
    files: ChatFiles = ChatFiles(),
) {
    val isBot = nodeId == SkyBot.NODE_ID
    val name = if (isBot) SkyBot.NAME else person?.name ?: "Someone"
    val emoji = if (isBot) SkyBot.AVATAR else person?.avatar
    val presence = if (isBot) null else person?.presence ?: Presence.OFFLINE
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(messages.size, typing) {
        val count = messages.size + 2 + (if (typing) 1 else 0)
        listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(nodeId) {
        meshEvents.collect { e -> if (e is MeshEvent.PingResult && e.nodeId == nodeId) snackbar.showSnackbar("Link check: ${e.roundTripMs} ms round trip") }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Header
            Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = !isBot, onClick = onPerson).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(emoji, name, nodeId, 38.dp, presence, sos = person?.sos == true)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                if (!isBot && person != null) com.bluemob.app.ui.components.StarChip(person.stars, person.ratingCount, Modifier.padding(start = 6.dp))
                            }
                            Text(
                                when {
                                    typing -> "typing…"
                                    isBot -> "Lives on your phone · works offline"
                                    presence == Presence.ONLINE -> "Online nearby · " + linkWords(person?.quality)
                                    presence == Presence.IN_RANGE -> "In range · connecting…"
                                    person?.reach != null -> person.reach + " · calls work"
                                    (person?.lastSeen ?: 0L) == 0L -> "Not met yet · reached through phones nearby"
                                    else -> "Seen ${TimeText.ago(person?.lastSeen ?: 0)} · not in range, messages travel through the mesh"
                                },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                color = if (typing || presence == Presence.ONLINE || person?.reach != null) MaterialTheme.colorScheme.primary else Extra.ink2,
                            )
                        }
                    }
                    if (!isBot) {
                        IconButton(onClick = files.onMedia) { Icon(Icons.Outlined.PermMedia, "Photos, documents and voice notes") }
                        IconButton(onClick = { onCall(false) }) { Icon(Icons.Outlined.Call, "Voice call") }
                        IconButton(onClick = { onCall(true) }) { Icon(Icons.Outlined.Videocam, "Video call") }
                    }
                    if (presence == Presence.ONLINE) IconButton(onClick = { onPing() }) { Icon(Icons.Outlined.NetworkCheck, "Check link speed") }
                }
                if (!isBot) RouteStrip(messages.lastOrNull { it.fromMe }, myName, myId, name, nodeId, onInfo)
                HorizontalDivider(color = Extra.line)
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                item { Intro(isBot, nodeId, name, emoji, person) }
                item {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { Tag("Today") }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    val prev = messages.getOrNull(i - 1)
                    val next = messages.getOrNull(i + 1)
                    val withPrev = prev != null && prev.fromMe == m.fromMe && m.createdAt - prev.createdAt < 120_000
                    val withNext = next != null && next.fromMe == m.fromMe && next.createdAt - m.createdAt < 120_000
                    Bubble(m, isBot, withPrev, withNext, name, Modifier.animateItem(), onInfo, onAction, files)
                }
                if (typing) item(key = "typing") {
                    Box(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(Extra.bubbleThem).padding(horizontal = 16.dp, vertical = 14.dp)) { TypingDots() }
                }
            }

            if (isBot && !typing) {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SkyBot.suggestions) { s -> Chip(s) { onSend(s) } }
                }
            }
            Composer(draft, onDraft = { draft = it }, onSend = { onSend(draft); draft = "" }, files = if (isBot) null else files)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

@Composable
private fun RouteStrip(last: MessageEntity?, myName: String, myId: String, name: String, nodeId: String, onInfo: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Extra.sand).clickable(enabled = last != null) { last?.let { onInfo(it.id) } }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (last == null) "$myName ${shortId(myId)} → $name ${shortId(nodeId)} · phone to phone, no internet"
            else "Last message: " + when (last.status) {
                MessageStatus.READ -> "read ${clockFormat.format(Date(last.readAt ?: last.createdAt))} · over ${last.deliveredVia}"
                MessageStatus.DELIVERED -> "delivered ${clockFormat.format(Date(last.deliveredAt ?: last.createdAt))} · over ${last.deliveredVia}"
                MessageStatus.SENT -> "sent, waiting for the delivery receipt"
                else -> "waiting on your phone until $name is in range"
            },
            style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.weight(1f),
        )
        if (last != null) Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Intro(isBot: Boolean, nodeId: String, name: String, emoji: String?, person: Person?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(emoji, name, nodeId, 80.dp)
        Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        if (isBot) Tag("On this phone · offline", Extra.skyTint, Extra.sky) else Tag("Direct · by BlueMob ID", Extra.pineTint, MaterialTheme.colorScheme.primary)
        if (!isBot) Text("BM " + com.bluemob.app.util.formatId(nodeId), style = MaterialTheme.typography.labelMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (isBot) "Built into BlueMob, on your phone. No internet, no server: what you ask stays here. I know the app, the survival guide, and what's happening around you."
            else "Write any time, wherever ${person?.name ?: "they"} is. In range, it goes straight over Bluetooth or Wi-Fi. If not, phones nearby carry it " +
                "toward them, end-to-end encrypted so no one else can read it. Shown exactly once; the ticks tell you when it arrives.",
            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(
    m: MessageEntity, isBot: Boolean, withPrev: Boolean, withNext: Boolean, name: String, modifier: Modifier,
    onInfo: (String) -> Unit, onAction: (String) -> Unit, files: ChatFiles = ChatFiles(),
) {
    val mine = m.fromMe
    val att = remember(m.att) { com.bluemob.app.files.Attachment.fromJson(m.att) }
    val big = 20.dp
    val small = 6.dp
    val shape = if (mine) RoundedCornerShape(big, if (withPrev) small else big, if (withNext) small else big, if (withNext || !withPrev) small else big)
    else RoundedCornerShape(if (withPrev) small else big, big, big, if (withNext || !withPrev) small else big)
    val tappable = mine && m.status != MessageStatus.LOCAL
    Column(
        modifier.fillMaxWidth().padding(top = if (withPrev) 2.dp else 10.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) MaterialTheme.colorScheme.primary else Extra.bubbleThem)
                .clickable(enabled = tappable) { onInfo(m.id) }.padding(horizontal = if (att != null) 8.dp else 14.dp, vertical = if (att != null) 8.dp else 9.dp),
        ) {
            val onColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            if (att != null) AttachmentContent(m, att, name, files, onColor)
            else Text(m.text, style = MaterialTheme.typography.bodyLarge, color = onColor)
        }
        if (m.actions.isNotBlank()) {
            FlowRow(Modifier.padding(top = 8.dp).widthIn(max = 320.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.actions.lines().filter { '|' in it }.forEach { line ->
                    val (label, target) = line.split("|", limit = 2)
                    Chip(label) { onAction(target) }
                }
            }
        }
        if (mine && m.status == MessageStatus.PENDING) {
            Text("⏳ Waiting on your phone. Goes over Bluetooth or Wi-Fi the moment $name is in range",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.End, modifier = Modifier.padding(top = 4.dp, start = 40.dp))
        }
        if (!withNext || m.status == MessageStatus.PENDING) {
            Row(Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clockFormat.format(Date(m.createdAt)), style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                if (mine) {
                    Spacer(Modifier.width(5.dp))
                    if (m.status == MessageStatus.LOCAL || isBot) Text("on this phone", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                    else StatusTick(m.status, if (m.status == MessageStatus.DELIVERED || m.status == MessageStatus.READ) MaterialTheme.colorScheme.primary else Extra.ink3)
                }
            }
        }
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, files: ChatFiles? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val recording = files?.recordingMs
        if (recording != null) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Extra.emberTint).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                com.bluemob.app.ui.components.PulsingDot(Extra.rose, 10.dp)
                Text("  " + com.bluemob.app.files.Attachment.durationText(recording) + "  ·  release to send, slide away to cancel",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        } else Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Extra.sand).padding(start = if (files != null) 4.dp else 18.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (files != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.AttachFile, "Attach a photo or document", tint = Extra.ink2) }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("photo" to "📷  Photo", "video" to "🎬  Video", "doc" to "📄  Document").forEach { (k, label) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; files.onAttach(k) })
                    }
                }
            }
            Box(Modifier.weight(1f).padding(vertical = 13.dp)) {
                if (draft.isEmpty()) Text("Message", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(draft, onDraft, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(8.dp))
        if (files != null && draft.isBlank()) {
            HoldToRecord(files.onRecordStart, files.onRecordStop, Modifier.size(if (recording != null) 58.dp else 46.dp).clip(CircleShape)
                .background(if (recording != null) Extra.rose else MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Outlined.Mic, "Hold to record a voice note", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            return@Row
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (draft.isBlank()) Extra.sand else MaterialTheme.colorScheme.primary)
                .clickable(enabled = draft.isNotBlank(), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (draft.isBlank()) Extra.ink3 else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}
