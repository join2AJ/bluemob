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
fun BridgeScreen(status: BridgeStatus, url: String, onBack: () -> Unit, onSave: (String) -> Unit, builtIn: String = "",
    /** Signed in to the relay's live link, so calls work with people far away. */
    liveConnected: Boolean = false) {
    var draft by rememberSaveable(url) { mutableStateOf(url) }
    var advanced by rememberSaveable { mutableStateOf(builtIn.isEmpty() || url != builtIn) }
    val working = status.configured && status.online && status.lastError == null && status.lastSync != null
    SubScreen("Internet bridge", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Internet bridge", style = MaterialTheme.typography.headlineMedium)
                Text("Met someone over Bluetooth and now you're 1,000 km apart? When either phone has internet, messages go through BlueMob over the internet. " +
                    "Your phone also carries messages for people near you who have no signal.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = if (working) Extra.pineTint else Extra.emberTint, modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            !status.configured -> "Not available in this version"
                            !status.online -> "No internet right now"
                            status.lastError != null -> "Can't connect right now"
                            status.lastSync == null -> "Connecting…"
                            else -> "This phone is a bridge"
                        }, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            !status.configured -> "This test version has no relay built in yet. Messages still travel phone to phone over Bluetooth and Wi-Fi."
                            !status.online -> "Messages wait and go the moment this phone, or any phone near it, has internet."
                            status.lastError != null -> "BlueMob keeps trying. Messages wait on this phone and go as soon as it connects."
                            else -> "Connected automatically. Last synced ${TimeText.ago(status.lastSync ?: 0)}."
                        }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = if (liveConnected) com.bluemob.app.ui.theme.Extra.pineTint else com.bluemob.app.ui.theme.Extra.sand2, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (liveConnected) "📞 Internet calls: ready" else "📞 Internet calls: not connected", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(when {
                        liveConnected -> "You can call people who are far away, and they can call you, as long as both phones have internet."
                        url.isBlank() -> "Internet calls aren't available in this version."
                        !status.online -> "This phone has no internet right now. Calls with people nearby still work."
                        else -> "Connecting… this can take up to a minute."
                    }, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
        item {
            Text("Messages, calls and files over the internet are end-to-end encrypted: only you and the person you're talking to can read or hear them, not even BlueMob.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
        }
    }
}
