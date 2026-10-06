package com.bluemob.app.ui.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bluemob.app.settings.RadioPolicy
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

/** What BlueMob may switch on by itself. Nothing is on until the user chooses it here. */
@Composable
fun AutoStartScreen(
    meshAtStart: Boolean, bluetooth: RadioPolicy, wifi: RadioPolicy, background: Boolean, sharing: Boolean,
    onBack: () -> Unit, onMeshAtStart: (Boolean) -> Unit, onBluetooth: (RadioPolicy) -> Unit, onWifi: (RadioPolicy) -> Unit,
    onBackground: (Boolean) -> Unit, onSharing: (Boolean) -> Unit,
) {
    SubScreen("Turn on automatically", onBack) {
        item {
            Text("BlueMob turns nothing on unless you choose it here. Anything not chosen, it asks first.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
        }
        item { GroupLabel("When BlueMob opens") }
        item {
            Group {
                SwitchRow("Start the mesh", "Find and be found by people nearby as soon as you open BlueMob", meshAtStart, onMeshAtStart)
                SwitchRow("Stay on in the background", "SOS and messages reach you with the screen off (shows a notification)", background, onBackground)
                SwitchRow("Share my location", "People connected to you see how far you are", sharing, onSharing)
            }
        }
        item { GroupLabel("Bluetooth") }
        item {
            Text("The mesh needs Bluetooth. If it's off when the mesh starts:", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Ask me", bluetooth != RadioPolicy.ALLOW) { onBluetooth(RadioPolicy.ASK) }
                Chip("Turn it on without asking", bluetooth == RadioPolicy.ALLOW) { onBluetooth(RadioPolicy.ALLOW) }
            }
        }
        item { GroupLabel("Wi-Fi") }
        item {
            Text("Wi-Fi makes links with people nearby much faster (no network or internet needed). If it's off:", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Ask me", wifi == RadioPolicy.ASK) { onWifi(RadioPolicy.ASK) }
                Chip("Use it", wifi == RadioPolicy.ALLOW) { onWifi(RadioPolicy.ALLOW) }
                Chip("Bluetooth only", wifi == RadioPolicy.NEVER) { onWifi(RadioPolicy.NEVER) }
            }
        }
        item {
            Text("Why this matters: Android's Nearby service, which the mesh runs on, switches Bluetooth (and Wi-Fi, if allowed) on while the mesh is running. " +
                "If you switch Bluetooth off, BlueMob pauses the mesh and waits for you, instead of turning it back on.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        }
        Switch(on, onChange)
    }
}

/** Asks before the mesh starts with a radio switched off. */
@Composable
fun MeshAskDialog(ask: AppViewModel.MeshAsk, onAnswer: (turnOn: Boolean, remember: Boolean) -> Unit, onDismiss: () -> Unit) {
    var remember by remember { mutableStateOf(false) }
    val bt = ask == AppViewModel.MeshAsk.BLUETOOTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bt) "Turn on Bluetooth?" else "Use Wi-Fi too?") },
        text = {
            Column {
                Text(if (bt) "Bluetooth is off. The mesh needs it to find people nearby and pass on messages and SOS. Android will ask you to confirm."
                    else "Wi-Fi is off. The mesh works over Bluetooth alone, but Wi-Fi makes links much faster. No Wi-Fi network or internet is needed.")
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { remember = !remember }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(remember, { remember = it })
                    Text("Remember my choice", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, remember) }) { Text(if (bt) "Turn on Bluetooth" else "Turn on Wi-Fi") } },
        dismissButton = { TextButton(onClick = { onAnswer(false, remember) }) { Text(if (bt) "Not now" else "Bluetooth only") } },
    )
}
