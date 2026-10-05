package com.bluemob.app.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bluemob.app.bridge.BridgeStatus
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.TimeText

/** Set up and watch the internet bridge: how people far apart keep talking. */
@Composable
fun BridgeScreen(status: BridgeStatus, url: String, onBack: () -> Unit, onSave: (String) -> Unit) {
    var draft by rememberSaveable(url) { mutableStateOf(url) }
    val working = status.configured && status.online && status.lastError == null && status.lastSync != null
    SubScreen("Internet bridge", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Internet bridge", style = MaterialTheme.typography.headlineMedium)
                Text("Met someone over Bluetooth and now you're 1,000 km apart? When either phone has internet, messages go through the BlueMob relay. " +
                    "Your phone also carries messages for people near you who have no signal.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = if (working) Extra.pineTint else Extra.emberTint, modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            !status.configured -> "Not set up yet"
                            !status.online -> "No internet right now"
                            status.lastError != null -> "Can't reach the relay"
                            status.lastSync == null -> "Connecting…"
                            else -> "This phone is a bridge"
                        }, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            !status.configured -> "Add your relay's address below. Without it, messages still travel phone to phone over Bluetooth and Wi-Fi."
                            !status.online -> "Messages wait and go the moment this phone, or any phone near it, has internet."
                            status.lastError != null -> status.lastError
                            else -> "Last synced ${TimeText.ago(status.lastSync ?: 0)}."
                        }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item { GroupLabel("Since BlueMob started") }
        item {
            Group {
                SettingRow(null, Extra.sand, "Sent over the internet", "${status.uploaded} messages and receipts, yours and ones you carried")
                SettingRow(null, Extra.sand, "Received over the internet", "${status.downloaded}, for you and people near you", divider = true)
                SettingRow(null, Extra.sand, "Waiting to upload", "${status.queued}", divider = true)
            }
        }
        item { GroupLabel("Relay address") }
        item {
            Group {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
                        if (draft.isEmpty()) Text("https://relay.example.org", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                        BasicTextField(draft, { draft = it.take(200) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                    }
                    val valid = draft.isBlank() || draft.startsWith("https://") || draft.startsWith("http://")
                    if (!valid) Text("The address starts with https://", style = MaterialTheme.typography.bodySmall, color = Extra.rose)
                    Row { Button(onClick = { onSave(draft) }, enabled = valid && draft.trim() != url) { Text("Save") } }
                }
            }
        }
        item {
            Text("What the relay sees: encrypted messages, who they're for, and public keys. It can't read messages or fake them: every message is " +
                "end-to-end encrypted and signed by the sender's phone. There are no accounts or phone numbers. " +
                "Use https in real use. Anyone can run a relay: see server/README.md in the BlueMob project.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
        }
    }
}
