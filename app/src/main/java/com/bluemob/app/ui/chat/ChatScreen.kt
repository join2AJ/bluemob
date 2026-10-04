package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.chat.ChatMessage
import com.bluemob.app.chat.Conversation
import com.bluemob.app.chat.MessageStatus
import com.bluemob.app.mesh.MeshEvent
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.TypingDots
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.TimeText
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nodeId: String,
    person: Person?,
    conversation: Conversation?,
    meshEvents: SharedFlow<MeshEvent>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPing: () -> Boolean,
) {
    val isBot = nodeId == SkyBot.NODE_ID
    val name = if (isBot) SkyBot.NAME else person?.name ?: "Unknown"
    val avatar = if (isBot) SkyBot.AVATAR else person?.avatar
    val presence = if (isBot) Presence.ONLINE else person?.presence ?: Presence.OFFLINE
    val canSend = isBot || presence == Presence.ONLINE
    val messages = conversation?.messages.orEmpty()
    val typing = conversation?.typing == true

    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(messages.size, typing) {
        val count = messages.size + (if (typing) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(nodeId) {
        meshEvents.collect { e ->
            if (e is MeshEvent.PingResult && e.nodeId == nodeId) {
                snackbar.showSnackbar("Link check: ${e.roundTripMs} ms round trip")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(avatar, name, nodeId, 40.dp, presence)
                        Spacer(Modifier.width(Space.md))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    typing -> "typing…"
                                    isBot -> "Practice bot · always online"
                                    presence == Presence.ONLINE -> "Online · nearby"
                                    presence == Presence.IN_RANGE -> "In range · connecting…"
                                    else -> "Last seen ${TimeText.ago(person?.lastSeen ?: 0)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (typing || presence == Presence.ONLINE) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!isBot && presence == Presence.ONLINE) {
                        IconButton(onClick = { onPing() }) { Icon(Icons.Filled.NetworkCheck, "Check link speed") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                if (isBot) item { BotBanner() }
                items(messages, key = { it.id }) { MessageBubble(it, Modifier.animateItem()) }
                if (typing) item(key = "typing") { TypingBubble(Modifier.animateItem()) }
            }

            if (isBot && !typing) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(SkyBot.suggestions) { s -> SuggestionChip(onClick = { onSend(s) }, label = { Text(s) }) }
                }
            }
            if (!canSend) {
                Text(
                    "${name} is out of range. Messages will be able to hop through others soon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                )
            }
            Composer(
                draft = draft,
                enabled = canSend,
                onDraft = { draft = it },
                onSend = {
                    onSend(draft)
                    draft = ""
                },
            )
        }
    }
}

@Composable
private fun BotBanner() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.md),
    ) {
        Text(
            "🌱 This is a practice chat. Sky is a friendly bot on your phone that shows how real BlueMob chats feel: " +
                "ticks, typing and replies. No signal needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(Space.md),
        )
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun MessageBubble(m: ChatMessage, modifier: Modifier = Modifier) {
    val mine = m.fromMe
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 4.dp else 20.dp, if (mine) 20.dp else 4.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp)) {
                val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(m.text, style = MaterialTheme.typography.bodyLarge, color = fg)
                Row(
                    Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(clockFormat.format(Date(m.time)), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        StatusTick(m.status, fg)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTick(status: MessageStatus, color: Color) {
    val (icon, tint) = when (status) {
        MessageStatus.SENDING -> Icons.Filled.Schedule to color.copy(alpha = 0.7f)
        MessageStatus.SENT -> Icons.Filled.Done to color.copy(alpha = 0.8f)
        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to Palette.SunLight
        MessageStatus.FAILED -> Icons.Filled.ErrorOutline to Palette.Coral
    }
    Icon(icon, contentDescription = status.name.lowercase(), tint = tint, modifier = Modifier.size(14.dp))
}

@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { TypingDots() }
        }
    }
}

@Composable
private fun Composer(draft: String, enabled: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = draft,
            onValueChange = onDraft,
            enabled = enabled,
            placeholder = { Text(if (enabled) "Message" else "Out of range") },
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.sm))
        FilledIconButton(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
