package com.bluemob.app.ui.chat

import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Reply
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
import com.bluemob.app.i18n.Text
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
    /** Link-speed check and the route strip: for testers (You → Diagnostics). */
    techDetails: Boolean = false,
    /** A group chat: its name and members. */
    group: com.bluemob.app.chat.ChatGroup? = null,
    /** Replies and reactions. */
    onReply: (String, MessageEntity) -> Unit = { t, _ -> onSend(t) },
    onReact: (MessageEntity, String) -> Unit = { _, _ -> },
    /** Group info: members, adding people, leaving. */
    onGroupInfo: () -> Unit = {},
    /** Sky only: whether the offline AI is downloaded and on, and where to set it up. */
    aiReady: Boolean = false,
    onAiSetup: (() -> Unit)? = null,
    /** Checked in person with a QR code: a tick by their name. */
    verified: Boolean = false,
    /** Deletes a message from this phone only. */
    onDelete: (MessageEntity) -> Unit = {},
    /** Previews only: a message picked as if long-pressed. */
    startSelected: String? = null,
) {
    val isBot = nodeId == SkyBot.NODE_ID
    // Long-press picks a message: the header turns into its actions, and its reactions show above it.
    var selected by remember { mutableStateOf(startSelected?.let { id -> messages.firstOrNull { it.id == id } }) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }
    val isGroup = group != null
    val name = if (isBot) SkyBot.NAME else group?.name ?: person?.name ?: "Someone"
    val emoji = if (isBot) SkyBot.AVATAR else if (isGroup) (if (group?.trip == true && !group.isTeam) "🧭" else "👥") else person?.avatar
    val presence = if (isBot || isGroup) null else person?.presence ?: Presence.OFFLINE
    var draft by rememberSaveable { mutableStateOf("") }
    var replying by remember { mutableStateOf<MessageEntity?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
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
        ChatWallpaper(myId, MaterialTheme.colorScheme.onBackground, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().imePadding()) {
            // Header (or, with a message picked, what you can do with it)
            Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                selected?.let { sel ->
                    val clipboardBar = LocalClipboardManager.current
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = null }) { Icon(Icons.Rounded.Close, "Done") }
                        Text("1", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        if (!isBot) IconButton(onClick = { replying = sel; selected = null }) { Icon(Icons.AutoMirrored.Rounded.Reply, "Reply") }
                        if (sel.text.isNotBlank()) IconButton(onClick = { clipboardBar.setText(androidx.compose.ui.text.AnnotatedString(sel.text)); selected = null }) {
                            Icon(Icons.Rounded.ContentCopy, "Copy")
                        }
                        if (sel.fromMe && sel.status != MessageStatus.LOCAL) IconButton(onClick = { onInfo(sel.id); selected = null }) { Icon(Icons.Rounded.Info, "Message info") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                    }
                    if (confirmDelete) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Delete this message?") },
                        text = { Text("It's deleted from this phone only. Anyone who already got it keeps their copy.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDelete(sel); confirmDelete = false; selected = null }) { Text("Delete", color = Extra.rose) } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                    )
                }
                if (selected == null) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = !isBot, onClick = if (isGroup) onGroupInfo else onPerson).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(emoji, name, nodeId, 38.dp, presence, sos = person?.sos == true)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                if (verified) Text(" ✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.semantics { contentDescription = "Verified" })
                                if (!isBot && !isGroup && person != null) com.bluemob.app.ui.components.StarChip(person.stars, person.ratingCount, Modifier.padding(start = 6.dp))
                            }
                            Text(
                                when {
                                    group != null -> DayLabel.people(group.members.size + 1) + " · " + (listOf("You") + group.members.values).joinToString(", ")
                                    typing -> "typing…"
                                    isBot && aiReady -> "🧠 AI on your phone · ask anything, offline"
                                    isBot -> "Lives on your phone · works offline"
                                    presence == Presence.ONLINE -> "Online nearby · " + linkWords(person?.quality)
                                    presence == Presence.IN_RANGE -> "In range · connecting…"
                                    person?.reach != null -> person.reach + " · calls work"
                                    (person?.lastSeen ?: 0L) == 0L && (person?.lastOnline ?: 0L) == 0L -> "Not met yet · reached through phones nearby"
                                    (person?.lastOnline ?: 0L) > (person?.lastSeen ?: 0L) -> "Online ${TimeText.ago(person?.lastOnline ?: 0)} on the internet · messages wait for them"
                                    else -> "Seen ${TimeText.ago(person?.lastSeen ?: 0)} · not in range, messages travel through the mesh"
                                },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                color = if (typing || presence == Presence.ONLINE || person?.reach != null) MaterialTheme.colorScheme.primary else Extra.ink2,
                            )
                        }
                    }
                    if (!isBot && !isGroup) {
                        IconButton(onClick = files.onMedia) { Icon(Icons.Outlined.PermMedia, "Photos, documents and voice notes") }
                        IconButton(onClick = { onCall(false) }) { Icon(Icons.Outlined.Call, "Voice call") }
                        IconButton(onClick = { onCall(true) }) { Icon(Icons.Outlined.Videocam, "Video call") }
                    }
                    if (techDetails && presence == Presence.ONLINE) IconButton(onClick = { onPing() }) { Icon(Icons.Outlined.NetworkCheck, "Check link speed") }
                }
                if (!isBot && !isGroup && techDetails) RouteStrip(messages.lastOrNull { it.fromMe }, myName, myId, name, nodeId, onInfo)
                HorizontalDivider(color = Extra.line)
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                item { if (group != null) GroupIntro(group) else Intro(isBot, nodeId, name, emoji, person, aiReady) }
                if (isBot && onAiSetup != null) item(key = "ai") { AiCard(aiReady, onAiSetup) }
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    val prev = messages.getOrNull(i - 1)
                    val next = messages.getOrNull(i + 1)
                    // A day label above the first message of each day: "Today", "Yesterday", "Mon 26 Oct", "26 Oct 2025".
                    val newDay = prev == null || !DayLabel.sameDay(prev.createdAt, m.createdAt)
                    if (newDay) Box(Modifier.fillMaxWidth().padding(top = if (prev == null) 0.dp else 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                        Tag(DayLabel.of(m.createdAt, System.currentTimeMillis()))
                    }
                    val withPrev = !newDay && prev != null && prev.fromMe == m.fromMe && prev.sender == m.sender && m.createdAt - prev.createdAt < 120_000
                    val withNext = next != null && DayLabel.sameDay(m.createdAt, next.createdAt) && next.fromMe == m.fromMe && next.sender == m.sender && next.createdAt - m.createdAt < 120_000
                    Bubble(m, isBot, withPrev, withNext, name, Modifier.animateItem(), onInfo, onAction, files, myId = myId,
                        showSender = isGroup && !m.fromMe && !withPrev,
                        onReply = if (isBot) null else ({ replying = m }), onReact = if (isBot) null else ({ e -> onReact(m, e); selected = null }),
                        onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text)) },
                        selected = selected?.id == m.id, selecting = selected != null, onSelect = { selected = if (selected?.id == m.id) null else m })
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
            replying?.let { r ->
                // What you're replying to: a card above the message box, with the accent bar and its first words.
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.padding(start = 12.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
                        Text("Replying to " + if (r.fromMe) "yourself" else r.senderName.ifBlank { name }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        com.bluemob.app.i18n.RawText(r.text, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
                    }
                    IconButton(onClick = { replying = null }) { Icon(Icons.Rounded.Close, "Cancel reply", tint = Extra.ink3) }
                }
            }
            val closedAt = group?.closedTime(System.currentTimeMillis()) ?: 0L
            // The trip is over, but the group stays: everyone can keep in touch here.
            if (closedAt > 0) Text("🏁 The trip ended ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(Date(closedAt))}. The group stays, so you can keep in touch.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Extra.sand).padding(horizontal = 16.dp, vertical = 8.dp))
            Composer(draft, onDraft = { draft = it }, onSend = { val r = replying; if (r != null) onReply(draft, r) else onSend(draft); draft = ""; replying = null },
                files = if (isBot || isGroup) null else files)
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
private fun Intro(isBot: Boolean, nodeId: String, name: String, emoji: String?, person: Person?, smart: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(emoji, name, nodeId, 80.dp)
        Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        if (isBot) Tag(if (smart) "🧠 AI on this phone · offline" else "On this phone · offline", Extra.skyTint, Extra.sky) else Tag("Direct · by BlueMob ID", Extra.pineTint, MaterialTheme.colorScheme.primary)
        if (!isBot) Text("BM " + com.bluemob.app.util.formatId(nodeId), style = MaterialTheme.typography.labelMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (isBot && smart) "Ask me anything: questions, writing, planning, maths, learning, first aid. My AI runs on your phone, so it works with no internet and nothing you ask leaves the phone. I can be wrong, so check what matters."
            else if (isBot) "Built into BlueMob, on your phone. No internet, no server: what you ask stays here. I know the app, the survival guide, and what's happening around you."
            else "Write any time, wherever ${person?.name ?: "they"} is. In range, it goes straight over Bluetooth or Wi-Fi. If not, phones nearby carry it " +
                "toward them, end-to-end encrypted so no one else can read it. Shown exactly once; the ticks tell you when it arrives.",
            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun GroupIntro(g: com.bluemob.app.chat.ChatGroup) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(Extra.skyTint), contentAlignment = Alignment.Center) {
            Text(if (g.trip && !g.isTeam) "🧭" else "👥", style = MaterialTheme.typography.headlineLarge)
        }
        com.bluemob.app.i18n.RawText(g.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        Tag((if (g.isTeam) "Team · " else if (g.trip) "Trip group · " else "Group · ") + DayLabel.people(g.members.size + 1), Extra.skyTint, Extra.sky)
        Text("Each message goes to every member separately, end-to-end encrypted, over Bluetooth, Wi-Fi, other phones or the internet: whatever reaches them.",
            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: MessageEntity, isBot: Boolean, withPrev: Boolean, withNext: Boolean, name: String, modifier: Modifier,
    onInfo: (String) -> Unit, onAction: (String) -> Unit, files: ChatFiles = ChatFiles(),
    myId: String = "", showSender: Boolean = false,
    onReply: (() -> Unit)? = null, onReact: ((String) -> Unit)? = null, onCopy: () -> Unit = {},
    selected: Boolean = false, selecting: Boolean = false, onSelect: () -> Unit = {},
) {
    val mine = m.fromMe
    val reactions = remember(m.reactions) { com.bluemob.app.chat.Rich.reactionsOf(m.reactions) }
    val att = remember(m.att) { com.bluemob.app.files.Attachment.fromJson(m.att) }
    val big = 20.dp
    val small = 6.dp
    val shape = if (mine) RoundedCornerShape(big, if (withPrev) small else big, if (withNext) small else big, if (withNext || !withPrev) small else big)
    else RoundedCornerShape(if (withPrev) small else big, big, big, if (withNext || !withPrev) small else big)
    val tappable = mine && m.status != MessageStatus.LOCAL
    Column(
        modifier.fillMaxWidth().padding(top = if (withPrev) 2.dp else 10.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp)),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (showSender && m.senderName.isNotBlank()) Text(m.senderName, style = MaterialTheme.typography.labelMedium, color = Extra.sky, modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
        // Picked: the reactions, in a pill above the message.
        if (selected && onReact != null) Row(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)) {
            com.bluemob.app.chat.Rich.QUICK_REACTIONS.forEach { e ->
                val chosen = reactions[myId] == e
                Text(e, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(CircleShape)
                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onReact(if (chosen) "" else e) }.padding(7.dp))
            }
        }
        Box {
        Box(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) MaterialTheme.colorScheme.primary else Extra.bubbleThem)
                .combinedClickable(onClick = { if (selecting || !tappable) onSelect() else onInfo(m.id) }, onLongClick = onSelect)
                .padding(horizontal = if (att != null) 8.dp else 14.dp, vertical = if (att != null) 8.dp else 9.dp),
        ) {
            val onColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column {
                com.bluemob.app.chat.Rich.replyParts(m.replyTo)?.let { (_, who, quote) ->
                    // The quoted message: an accent bar, who wrote it, and its first words, on a slightly darker panel.
                    val accent = if (mine) onColor else Extra.sky
                    Row(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (mine) 0.12f else 0.18f))
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                        Column(Modifier.padding(start = 9.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
                            com.bluemob.app.i18n.RawText(who.ifBlank { "Message" }, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
                            com.bluemob.app.i18n.RawText(quote, style = MaterialTheme.typography.bodySmall, color = onColor.copy(alpha = 0.8f), maxLines = 2)
                        }
                    }
                }
                if (att != null) AttachmentContent(m, att, name, files, onColor)
                // People's own words stay as they wrote them; only Sky's replies follow the app language.
                else if (m.peer == com.bluemob.app.bot.SkyBot.NODE_ID && !mine) Text(m.text, style = MaterialTheme.typography.bodyLarge, color = onColor)
                else com.bluemob.app.i18n.RawText(m.text, style = MaterialTheme.typography.bodyLarge, color = onColor)
            }
        }
        }
        if (reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = onReact != null) { onSelect() }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                reactions.values.groupingBy { it }.eachCount().forEach { (e, n) -> Text(if (n > 1) "$e $n " else "$e ", style = MaterialTheme.typography.bodyMedium) }
            }
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
    androidx.activity.compose.BackHandler(enabled = menu) { menu = false }
    Column {
    // The attach panel: round icons in a grid, like a share sheet, above the message box.
    androidx.compose.animation.AnimatedVisibility(menu && files != null) {
        if (files != null) AttachPanel { k -> menu = false; files.onAttach(k) }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val recording = files?.recordingMs
        if (recording != null) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Extra.emberTint).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                com.bluemob.app.ui.components.PulsingDot(Extra.rose, 10.dp)
                Text("  " + com.bluemob.app.files.Attachment.durationText(recording) + "  ·  release to send, slide away to cancel",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        } else Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Extra.sand).padding(start = if (files != null) 4.dp else 18.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (files != null) Box {
                IconButton(onClick = { menu = !menu }) {
                    Icon(if (menu) Icons.Rounded.Close else Icons.Outlined.AttachFile, if (menu) "Close" else "Attach", tint = Extra.ink2)
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
}

/** What can be attached: photo or video from the gallery, a document, your position, or your BlueMob contact card. */
@Composable
internal fun AttachPanel(onPick: (String) -> Unit) {
    val items = listOf(
        Triple("photo", "Gallery", Icons.Rounded.Image to com.bluemob.app.ui.theme.Palette.BrandBlue),
        Triple("video", "Video", Icons.Rounded.Videocam to com.bluemob.app.ui.theme.Palette.Sunset),
        Triple("doc", "Document", Icons.Rounded.Description to com.bluemob.app.ui.theme.Palette.Violet),
        Triple("location", "Location", Icons.Rounded.LocationOn to com.bluemob.app.ui.theme.Palette.BrandGreen),
        Triple("contact", "My contact", Icons.Rounded.Person to com.bluemob.app.ui.theme.Palette.Gold),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(top = 10.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Extra.ink3.copy(alpha = 0.5f)))
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                row.forEach { (k, label, look) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onPick(k) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(look.second.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(look.first, null, Modifier.size(26.dp), tint = look.second)
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** At the top of the Sky chat: get the offline AI, or see that it's on. */
@Composable
private fun AiCard(ready: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(Extra.skyTint).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.bluemob.app.ui.components.IconBadge(androidx.compose.material.icons.Icons.Rounded.Psychology, com.bluemob.app.ui.theme.Palette.Violet)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(if (ready) "Offline AI is on" else "Make Sky smarter, offline", style = MaterialTheme.typography.titleSmall, color = Extra.sky)
            Text(
                if (ready) "Answers are written on your phone. Tap to manage" else "Download an AI brain once, then ask anything with no internet",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
            )
        }
        Text(if (ready) "Manage" else "Get", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Day labels between messages, and "1 member" / "5 members". */
object DayLabel {
    fun sameDay(a: Long, b: Long): Boolean {
        val x = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val y = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR) && x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun of(at: Long, now: Long): String = when {
        sameDay(at, now) -> "Today"
        sameDay(at, now - 86_400_000L) -> "Yesterday"
        now - at < 6 * 86_400_000L && at < now -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(at))
        sameYear(at, now) -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(at))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    }

    private fun sameYear(a: Long, b: Long) =
        java.util.Calendar.getInstance().apply { timeInMillis = a }.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().apply { timeInMillis = b }.get(java.util.Calendar.YEAR)

    fun people(n: Int) = if (n == 1) "1 member" else "$n members"
}
