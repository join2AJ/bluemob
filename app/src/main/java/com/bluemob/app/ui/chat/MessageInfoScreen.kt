package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.MessageRepository
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.data.PathState
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.StatusTick
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.formatId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val secs = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/** Everything about one message: who to whom, receipts, both delivery paths, and its full history. */
@Composable
fun MessageInfoScreen(m: MessageEntity, myName: String, myId: String, toName: String, onBack: () -> Unit) {
    SubScreen("Message info", onBack) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Party("From", myName, "BM · " + formatId(myId), "This phone", Modifier.weight(1f))
                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                Party("To", toName, "BM · " + formatId(m.peer), "By BlueMob ID", Modifier.weight(1f))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(m.text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        item {
            val ok = m.status == MessageStatus.DELIVERED || m.status == MessageStatus.READ
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusTick(m.status, if (ok) MaterialTheme.colorScheme.primary else Extra.ink3, Modifier.size(width = 30.dp, height = 22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(when (m.status) {
                        MessageStatus.READ -> "Read"; MessageStatus.DELIVERED -> "Delivered"; MessageStatus.SENT -> "Sent, waiting for a receipt"
                        else -> "Waiting to be delivered"
                    }, style = MaterialTheme.typography.titleLarge)
                    Text(if (ok) "over ${m.deliveredVia}" else "Goes over Bluetooth or Wi-Fi the moment $toName is in range",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
            }
        }
        item { GroupLabel("Receipts") }
        item {
            Group {
                if (m.deliveredAt == null && m.readAt == null) SettingRow(null, Color.Transparent, "No receipt yet",
                    "You'll get one the moment it's delivered, and another when it's read.")
                m.deliveredAt?.let { SettingRow(Icons.Outlined.Done, MaterialTheme.colorScheme.primary, "Delivered · ${secs.format(Date(it))}", "Receipt came back over ${m.deliveredVia}") }
                m.readAt?.let { SettingRow(Icons.Outlined.Visibility, MaterialTheme.colorScheme.primary, "Read · ${secs.format(Date(it))}", "Receipt came back over ${m.deliveredVia}", divider = m.deliveredAt != null) }
            }
        }
        item { GroupLabel("Delivery paths · first one wins") }
        item {
            Group {
                SettingRow(Icons.Outlined.Hub, if (m.directState == PathState.DELIVERED) MaterialTheme.colorScheme.primary else Extra.sky, "Bluetooth / Wi-Fi",
                    when (m.directState) {
                        PathState.WAITING -> "Waiting for $toName to come in range"
                        PathState.TRYING -> "Sent ${m.attempts} time${if (m.attempts == 1) "" else "s"}, waiting for a receipt"
                        PathState.DELIVERED -> "Delivered" + (m.deliveredAt?.let { " · " + secs.format(Date(it)) } ?: "")
                        else -> "Not used"
                    }) { if (m.directState == PathState.DELIVERED) Tag("Used", Extra.pineTint, MaterialTheme.colorScheme.primary) }
                SettingRow(Icons.Outlined.Language, Extra.ink3, "Internet", "Through a bridge and the BlueMob relay. Coming in the next update.", divider = true)
            }
        }
        item { GroupLabel("History") }
        item {
            Group(Modifier) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val events = MessageRepository.historyOf(m)
                    events.forEachIndexed { i, (t, text) ->
                        Row {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.padding(top = 3.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                if (i < events.lastIndex) Box(Modifier.width(2.dp).height(38.dp).background(MaterialTheme.colorScheme.primary))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                                Text(secs.format(Date(t)), style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                            }
                        }
                    }
                }
            }
        }
        item { GroupLabel("Duplicate protection") }
        item {
            Group {
                Text("This message's ID is ${m.id}. Every copy carries it. If it's sent more than once (for example, the link dropped before the receipt came back), $toName's phone recognises the ID and discards the extra copy. So it's shown exactly once.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun Party(title: String, name: String, id: String, sub: String, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(id, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Extra.ink3)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
    }
}
