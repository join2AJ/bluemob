package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bluemob.app.bot.SkyBot
import com.bluemob.app.chat.Conversation
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Pill
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.TimeText

@Composable
fun ChatsScreen(
    people: List<Person>,
    conversations: Map<String, Conversation>,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg, end = Space.lg,
            top = contentPadding.calculateTopPadding() + Space.lg,
            bottom = contentPadding.calculateBottomPadding() + Space.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = Space.sm))
        }
        item {
            ChatRow(
                avatar = SkyBot.AVATAR, name = SkyBot.NAME, seed = SkyBot.NODE_ID,
                presence = Presence.ONLINE,
                conversation = conversations[SkyBot.NODE_ID],
                fallback = "Your practice buddy, always here",
                badge = "PRACTICE",
                onClick = { onOpen(SkyBot.NODE_ID) },
            )
        }
        item {
            Text(
                "People you've met",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
            )
        }
        if (people.isEmpty()) {
            item {
                Text(
                    "No one yet. Turn on the mesh on the Radar tab, and people nearby will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(people, key = { it.nodeId }) { p ->
            ChatRow(
                avatar = p.avatar, name = p.name, seed = p.nodeId,
                presence = p.presence,
                conversation = conversations[p.nodeId],
                fallback = when (p.presence) {
                    Presence.ONLINE -> "Online now: say hello!"
                    Presence.IN_RANGE -> "In range"
                    Presence.OFFLINE -> "Last seen ${TimeText.ago(p.lastSeen)}"
                },
                onClick = { onOpen(p.nodeId) },
            )
        }
    }
}

@Composable
private fun ChatRow(
    avatar: String?,
    name: String,
    seed: String,
    presence: Presence,
    conversation: Conversation?,
    fallback: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val last = conversation?.messages?.lastOrNull()
    val unread = conversation?.unread ?: 0
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, name, seed, 52.dp, presence)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(Space.sm))
                        Pill(badge, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Text(
                    when {
                        conversation?.typing == true -> "typing…"
                        last != null -> (if (last.fromMe) "You: " else "") + last.text
                        else -> fallback
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation?.typing == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (last != null) {
                    Text(
                        TimeText.ago(last.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (unread > 0) {
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$unread", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
