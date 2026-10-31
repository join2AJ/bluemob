package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.files.Attachment
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.IconBadge
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.util.TimeText

/** Starred messages from every chat, newest first: tap one to open its chat. */
@Composable
fun StarredScreen(messages: List<MessageEntity>, chatName: (String) -> String, onBack: () -> Unit, onOpen: (String) -> Unit, onUnstar: (String) -> Unit) {
    SubScreen("Starred messages", onBack) {
        if (messages.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Rounded.Star, Palette.Gold, size = 72.dp)
                Text("No starred messages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                Text("Hold a message and tap the star to keep it here: meeting points, passcodes, plans.", style = MaterialTheme.typography.bodyMedium,
                    color = Extra.ink2, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
            }
        }
        if (messages.isNotEmpty()) item {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) {
                messages.sortedByDescending { it.createdAt }.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Extra.line)
                    Row(Modifier.fillMaxWidth().clickable { onOpen(m.peer) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RawText((if (m.fromMe) "You" else m.senderName.ifBlank { chatName(m.peer) }) + " · " + chatName(m.peer),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Text("  " + TimeText.ago(m.createdAt), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                            }
                            RawText(m.text.ifBlank { Attachment.fromJson(m.att)?.let { it.kind.label + " · " + it.name } ?: "" },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
                        }
                        IconButton(onClick = { onUnstar(m.id) }) { Icon(Icons.Rounded.Star, "Unstar", tint = Palette.Gold, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
