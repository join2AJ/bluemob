package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.InsetDivider
import com.bluemob.app.ui.components.LargeTitle
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.dashboard.statusLine
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.shortId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChatFilter(val label: String) { ALL("All"), UNREAD("Unread"), ONLINE("Online"), WAITING("Waiting") }

private data class Entry(val id: String, val name: String, val emoji: String?, val presence: Presence, val sharesName: Boolean, val status: String, val isBot: Boolean)

@Composable
fun ChatsScreen(
    people: List<Person>,
    conversations: Map<String, List<MessageEntity>>,
    typing: Set<String>,
    contentPadding: PaddingValues,
    rescues: List<com.bluemob.app.rescue.RescueRoom> = emptyList(),
    onOpenRescue: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    calls: List<com.bluemob.app.data.CallLogEntry> = emptyList(),
    onCallBack: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onClearCalls: () -> Unit = {},
    onDeleteCall: (String) -> Unit = {},
    /** Long-press a chat to delete it (Sky's can't be). */
    onDeleteChat: (String) -> Unit = {},
    /** File actions (open, play, thumbnails) for a chat, for the Files tab. */
    filesFor: (String) -> ChatFiles = { ChatFiles() },
    onOpen: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ChatsTab.CHATS) }
    val missedToday = calls.count { !it.outgoing && it.outcome == "MISSED" && it.startedAt > System.currentTimeMillis() - 86_400_000 }
    when (tab) {
        ChatsTab.CALLS -> { CallsList(calls, people, contentPadding, missedToday, onTab = { tab = it }, onCallBack = onCallBack, onClear = onClearCalls, onDelete = onDeleteCall); return }
        ChatsTab.FILES -> { FilesList(conversations, people, contentPadding, missedToday, onTab = { tab = it }, filesFor = filesFor); return }
        ChatsTab.CHATS -> Unit
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    deleting?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat with ${d.name}?") },
            text = { Text("Messages, photos and files in this chat are deleted from this phone. You can't get them back unless you have a backup.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onDeleteChat(d.id); deleting = null }) { Text("Delete", color = Extra.rose) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
    val lastTime = { id: String -> conversations[id]?.lastOrNull()?.createdAt ?: 0L }
    val entries = buildList {
        // Sky lives in the Guide tab now; its chat shows here only once you've talked to it.
        if (conversations[SkyBot.NODE_ID].orEmpty().isNotEmpty()) add(Entry(SkyBot.NODE_ID, SkyBot.NAME, SkyBot.AVATAR, Presence.ONLINE, false, "Lives on your phone · works offline", true))
        people.sortedByDescending { lastTime(it.nodeId) }.forEach { add(Entry(it.nodeId, it.name, it.avatar, it.presence, it.sharesName, statusLine(it), false)) }
    }.filter { e ->
        val msgs = conversations[e.id].orEmpty()
        (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
            ChatFilter.ALL -> true
            ChatFilter.UNREAD -> msgs.any { !it.fromMe && it.status == MessageStatus.RECEIVED }
            ChatFilter.ONLINE -> e.presence == Presence.ONLINE && !e.isBot
            ChatFilter.WAITING -> msgs.any { it.fromMe && (it.status == MessageStatus.PENDING || it.status == MessageStatus.SENT) }
        }
    }
    val onlineNow = people.filter { it.presence == Presence.ONLINE }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Chats", modifier = Modifier.padding(horizontal = Space.lg)) }
        item { ChatsCallsTabs(ChatsTab.CHATS, missedToday) { tab = it } }
        item {
            Row(
                Modifier.padding(horizontal = Space.lg).fillMaxWidth().clip(MaterialTheme.shapes.small).background(Extra.sand).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = Extra.ink3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            androidx.compose.material3.Surface(onClick = onNewChat, shape = MaterialTheme.shapes.large, color = Extra.pineTint,
                modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = 14.dp).fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✉️", fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Message anyone by BlueMob ID", style = MaterialTheme.typography.titleMedium)
                        Text("Even if they're not nearby. Share your own ID too", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // All rescues in one box: the ones you asked for and the ones you helped, open or ended.
        if (rescues.isNotEmpty() && query.isBlank()) item {
            val active = rescues.count { !it.ended }
            val asked = rescues.count { it.mine }
            val helped = rescues.count { !it.mine && it.iAmIn }
            Row(Modifier.padding(start = Space.lg, end = Space.lg, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (active > 0) Extra.rose.copy(alpha = 0.14f) else Extra.sand).clickable { onOpenRescue("") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (active > 0) Extra.rose else Extra.sand2), contentAlignment = Alignment.Center) {
                    Text("🆘", fontSize = 22.sp)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Rescues" + if (active > 0) " · $active active" else "", style = MaterialTheme.typography.titleMedium)
                    Text("You asked for help $asked × · you helped $helped ×", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (onlineNow.isNotEmpty() && query.isBlank()) {
            item { Text("ONLINE NEARBY", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.lg), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(onlineNow, key = { it.nodeId }) { p ->
                        Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(p.nodeId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(p.avatar, p.name, p.nodeId, 60.dp, p.presence)
                            Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.lg), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChatFilter.entries) { f -> Chip(f.label, filter == f) { filter = f } }
            }
        }
        itemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
            if (i > 0) InsetDivider()
            ChatRow(e, conversations[e.id].orEmpty(), e.id in typing, onDelete = if (e.isBot) null else ({ deleting = e })) { onOpen(e.id) }
        }
        if (entries.isEmpty()) item {
            Text(if (people.isEmpty()) "People you meet appear here. Turn on the mesh in the Nearby tab." else "No chats match.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(24.dp))
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ChatRow(e: Entry, messages: List<MessageEntity>, typing: Boolean, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    val last = messages.lastOrNull()
    val unread = messages.count { !it.fromMe && it.status == MessageStatus.RECEIVED }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onDelete).padding(horizontal = Space.lg, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(e.emoji, e.name, e.id, 54.dp, if (e.isBot) null else e.presence)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.sharesName) Text(shortId(e.id), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                if (e.isBot) Tag("On this phone", Extra.skyTint, Extra.sky)
            }
            Text(
                when {
                    typing -> "typing…"
                    last != null -> (if (last.fromMe) "You: " else "") + last.preview().lineSequence().first()
                    else -> e.status
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal),
                color = if (typing) MaterialTheme.colorScheme.primary else if (unread > 0) MaterialTheme.colorScheme.onSurface else Extra.ink2,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (last != null) Text(timeFormat.format(Date(last.createdAt)), style = MaterialTheme.typography.bodySmall,
                color = if (unread > 0) MaterialTheme.colorScheme.primary else Extra.ink3)
            if (unread > 0) Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("$unread", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** "Chats | Calls" switch at the top of the Chats tab. [missed] is today's missed calls. */
enum class ChatsTab(val label: String) { CHATS("Chats"), CALLS("Calls"), FILES("Files") }

@Composable
private fun ChatsCallsTabs(current: ChatsTab, missed: Int, onTab: (ChatsTab) -> Unit) {
    Row(Modifier.padding(horizontal = Space.lg).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Extra.sand).padding(4.dp)) {
        ChatsTab.entries.forEach { t ->
            val label = if (t == ChatsTab.CALLS && missed > 0 && current != ChatsTab.CALLS) "Calls · $missed missed" else t.label
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (current == t) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { onTab(t) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (t == ChatsTab.CALLS && missed > 0 && current != ChatsTab.CALLS) Extra.rose else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Every photo, document and voice note from all chats, with the same filters as one chat's media. */
@Composable
private fun FilesList(conversations: Map<String, List<MessageEntity>>, people: List<Person>, contentPadding: PaddingValues, missed: Int,
    onTab: (ChatsTab) -> Unit, filesFor: (String) -> ChatFiles) {
    androidx.activity.compose.BackHandler { onTab(ChatsTab.CHATS) }
    val state = rememberFileBrowserState()
    val all = remember(conversations) { sharedFiles(conversations.values.flatten()) }
    val names = people.associate { it.nodeId to it.name }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Files", modifier = Modifier.padding(horizontal = Space.lg)) }
        item { ChatsCallsTabs(ChatsTab.FILES, missed, onTab) }
        fileBrowser(state, all, nameOf = { names[it] ?: "Someone" }, filesFor = filesFor, showPeople = true, sidePadding = Space.lg)
    }
}

/**
 * Call history like a phone's: filter by missed, received, dialled, video or voice and by time, search by name,
 * grouped by day. Tap a call for the same kind of call back, or the other icon for the other kind.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CallsList(calls: List<com.bluemob.app.data.CallLogEntry>, people: List<Person>, contentPadding: PaddingValues, missedToday: Int,
    onTab: (ChatsTab) -> Unit, onCallBack: (String, String, Boolean) -> Unit, onClear: () -> Unit, onDelete: (String) -> Unit) {
    androidx.activity.compose.BackHandler { onTab(ChatsTab.CHATS) }
    var confirmClear by remember { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(CallKind.ALL) }
    var period by rememberSaveable { mutableStateOf(Period.ANY) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val shown = CallFilter.apply(calls, kind, period, query)
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Calls", modifier = Modifier.padding(horizontal = Space.lg)) }
        item { ChatsCallsTabs(ChatsTab.CALLS, missedToday, onTab) }
        if (calls.isNotEmpty()) item {
            Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.lg), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CallKind.entries.toList()) { k ->
                        val n = calls.count { k.matches(it) && period.matches(it.startedAt, now) }
                        Chip("${k.label} $n", kind == k) { kind = k }
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = Space.lg), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Period.entries.toList()) { p -> Chip(p.label, period == p) { period = p } }
                }
                androidx.compose.material3.OutlinedTextField(query, { query = it }, placeholder = { Text("Search by name") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.padding(horizontal = Space.lg).fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
                Text("${shown.size} calls · talk time ${CallFilter.talkTime(shown)}", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(horizontal = Space.lg))
            }
        }
        if (calls.isEmpty()) item {
            Text("No calls yet. Open a chat and tap 📞 or 🎥: it works with people nearby, through friends, or over the internet.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(horizontal = Space.lg, vertical = 24.dp))
        } else if (shown.isEmpty()) item {
            Text("No calls match these filters.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(horizontal = Space.lg, vertical = 24.dp))
        }
        shown.groupBy { CallFilter.group(it.startedAt, now) }.forEach { (heading, group) ->
            item(key = "h-$heading") {
                Text(heading.uppercase(), style = MaterialTheme.typography.labelMedium, color = Extra.ink3, modifier = Modifier.padding(start = Space.lg, top = 14.dp, bottom = 2.dp))
            }
            items(group, key = { it.id }) { c ->
                val person = people.firstOrNull { it.nodeId == c.peer }
                val missed = !c.outgoing && c.outcome == "MISSED"
                val name = person?.name ?: c.name
                Box {
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onCallBack(c.peer, name, c.video) }, onLongClick = { menuFor = c.id })
                        .padding(horizontal = Space.lg, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person?.avatar, name, c.peer, 46.dp, person?.presence)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, color = if (missed) Extra.rose else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (c.outgoing) "↗" else "↙", color = if (missed) Extra.rose else if (c.outcome == "ANSWERED") MaterialTheme.colorScheme.primary else Extra.ink3, fontWeight = FontWeight.Bold)
                                Text(" " + outcomeWords(c) + " · " + callTime(c.startedAt), style = MaterialTheme.typography.bodySmall,
                                    color = if (missed) Extra.rose else Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // Call back either way: the same kind is the big tap, the other is this small one.
                        Text(if (c.video) "📞" else "🎥", fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable { onCallBack(c.peer, name, !c.video) }.padding(8.dp))
                        Text(if (c.video) "🎥" else "📞", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { onCallBack(c.peer, name, c.video) }.padding(8.dp))
                    }
                    androidx.compose.material3.DropdownMenu(menuFor == c.id, { menuFor = null }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Voice call") }, onClick = { menuFor = null; onCallBack(c.peer, name, false) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Video call") }, onClick = { menuFor = null; onCallBack(c.peer, name, true) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Remove from history", color = Extra.rose) }, onClick = { menuFor = null; onDelete(c.id) })
                    }
                }
            }
        }
        if (calls.isNotEmpty()) item {
            if (confirmClear) Row(Modifier.padding(horizontal = Space.lg, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Clear all call history?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Keep") }
                androidx.compose.material3.TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear", color = Extra.rose) }
            } else androidx.compose.material3.TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = Space.lg, vertical = 8.dp)) { Text("Clear call history") }
        }
    }
}

private fun outcomeWords(c: com.bluemob.app.data.CallLogEntry) = when (c.outcome) {
    "ANSWERED" -> (if (c.outgoing) "Outgoing" else "Incoming") + " · " + durationWords(c.durationS)
    "MISSED" -> "Missed"
    "DECLINED" -> if (c.outgoing) "Declined by them" else "Declined"
    "NO_ANSWER" -> "No answer"
    "BUSY" -> "Busy"
    "CANCELLED" -> "Cancelled"
    else -> "Connection lost"
} + if (c.video) " · video" else ""

private fun durationWords(s: Long) = if (s < 60) "${s}s" else "${s / 60} min ${s % 60}s"

private fun callTime(at: Long): String {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(at))
    return when {
        now - at < day && java.util.Calendar.getInstance().apply { timeInMillis = at }.get(java.util.Calendar.DAY_OF_YEAR) ==
            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) -> "Today $time"
        now - at < 2 * day -> "Yesterday $time"
        else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(java.util.Date(at))
    }
}
