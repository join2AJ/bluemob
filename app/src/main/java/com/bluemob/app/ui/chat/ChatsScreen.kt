package com.bluemob.app.ui.chat

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
    onOpen: (String) -> Unit,
) {
    var showCalls by rememberSaveable { mutableStateOf(false) }
    if (showCalls) {
        CallsList(calls, people, contentPadding, onBack = { showCalls = false }, onCallBack = onCallBack, onClear = onClearCalls)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }
    val lastTime = { id: String -> conversations[id]?.lastOrNull()?.createdAt ?: 0L }
    val entries = buildList {
        add(Entry(SkyBot.NODE_ID, SkyBot.NAME, SkyBot.AVATAR, Presence.ONLINE, false, "Lives on your phone · works offline", true))
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
        item { ChatsCallsTabs(false, calls.count { it.outcome == "MISSED" && it.startedAt > System.currentTimeMillis() - 86_400_000 }) { showCalls = it } }
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
        val groups = rescues.filter { it.iAmIn || !it.ended }
        if (groups.isNotEmpty() && query.isBlank()) {
            item { Text("RESCUE GROUPS", style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
            groups.forEach { r ->
                item(key = "rescue-" + r.id) {
                    val last = r.chat.lastOrNull()
                    Row(Modifier.fillMaxWidth().clickable { onOpenRescue(r.id) }.padding(horizontal = Space.lg, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (r.ended) Extra.sand2 else Extra.rose), contentAlignment = Alignment.Center) {
                            Text("🆘", fontSize = 22.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(if (r.mine) "Your rescue" else "Help ${r.victimName}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (r.ended) "Ended · " else "${r.coming.size} coming · ") + (last?.let { "${it.fromName}: ${it.text}" } ?: r.note.ifBlank { "SOS" }),
                                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
            ChatRow(e, conversations[e.id].orEmpty(), e.id in typing) { onOpen(e.id) }
        }
        if (entries.isEmpty()) item {
            Text(if (people.isEmpty()) "People you meet appear here. Turn on the mesh in the Nearby tab." else "No chats match.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(24.dp))
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun ChatRow(e: Entry, messages: List<MessageEntity>, typing: Boolean, onClick: () -> Unit) {
    val last = messages.lastOrNull()
    val unread = messages.count { !it.fromMe && it.status == MessageStatus.RECEIVED }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.lg, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
@Composable
private fun ChatsCallsTabs(calls: Boolean, missed: Int, onCalls: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = Space.lg).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Extra.sand).padding(4.dp)) {
        listOf(false to "Chats", true to (if (missed > 0) "Calls · $missed missed" else "Calls")).forEach { (isCalls, label) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (calls == isCalls) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { onCalls(isCalls) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (isCalls && missed > 0 && !calls) Extra.rose else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Call history, newest first, like a phone's: who, in or out, answered or missed, when and how long. Tap to call back. */
@Composable
private fun CallsList(calls: List<com.bluemob.app.data.CallLogEntry>, people: List<Person>, contentPadding: PaddingValues,
    onBack: () -> Unit, onCallBack: (String, String, Boolean) -> Unit, onClear: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Calls", modifier = Modifier.padding(horizontal = Space.lg)) }
        item { ChatsCallsTabs(true, 0) { if (!it) onBack() } }
        if (calls.isEmpty()) item {
            Text("No calls yet. Open a chat with someone nearby and tap 📞 or 🎥.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = 24.dp))
        }
        items(calls, key = { it.id }) { c ->
            val person = people.firstOrNull { it.nodeId == c.peer }
            val missed = c.outcome == "MISSED"
            Row(Modifier.fillMaxWidth().clickable { onCallBack(c.peer, person?.name ?: c.name, c.video) }.padding(horizontal = Space.lg, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(person?.avatar, person?.name ?: c.name, c.peer, 48.dp, person?.presence)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(person?.name ?: c.name, style = MaterialTheme.typography.titleMedium, color = if (missed) Extra.rose else MaterialTheme.colorScheme.onSurface)
                    Text(
                        (if (c.outgoing) "↗ " else "↙ ") + when (c.outcome) {
                            "ANSWERED" -> (if (c.outgoing) "Outgoing" else "Incoming") + " · " + durationWords(c.durationS)
                            "MISSED" -> "Missed"
                            "DECLINED" -> if (c.outgoing) "Declined by them" else "Declined"
                            "NO_ANSWER" -> "No answer"
                            "BUSY" -> "Busy"
                            "CANCELLED" -> "Cancelled"
                            else -> "Connection lost"
                        } + " · " + callTime(c.startedAt),
                        style = MaterialTheme.typography.bodySmall, color = if (missed) Extra.rose else Extra.ink2,
                    )
                }
                Text(if (c.video) "🎥" else "📞", fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp))
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
