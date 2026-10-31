package com.bluemob.app.ui.profile

import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette

/** A row with a title, a line under it, and a switch; the whole row toggles. */
@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tile: androidx.compose.ui.graphics.Color, title: String, body: String, on: Boolean, divider: Boolean = false, onChange: (Boolean) -> Unit) {
    if (divider) HorizontalDivider(Modifier.padding(start = 64.dp), color = Extra.line)
    Row(Modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        com.bluemob.app.ui.components.IconTile(icon, tile)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        }
        Switch(on, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Emergency card: on or off, what it shows, whether Android lets it show, and Android's own emergency info. */
@Composable
fun EmergencyCardScreen(on: Boolean, onChange: (Boolean) -> Unit, cardText: () -> String, onBack: () -> Unit) {
    SubScreen("Emergency card", onBack) {
        item {
            Text("Whoever finds you (or a medic) can see your blood group and who to call, without unlocking your phone.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        item { Group { ToggleRow(androidx.compose.material.icons.Icons.Rounded.Badge, Extra.rose, "Show on the lock screen", if (on) "On" else "Off", on, onChange = onChange) } }
        // What it looks like on the lock screen.
        item { GroupLabel("What it shows") }
        item {
            val lines = cardText().lines()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.DangerDeep.copy(alpha = 0.12f)).padding(14.dp)) {
                lines.firstOrNull()?.let { com.bluemob.app.i18n.RawText(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
                lines.drop(1).forEach { com.bluemob.app.i18n.RawText(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (on) {
            item { GroupLabel("Making sure it shows") }
            item { EmergencyCardHelp(cardText) }
        }
    }
}

/** What people coming to help may do to this phone during your SOS, each on its own. */
@Composable
fun HelperSignalsScreen(on: Boolean, onChange: (Boolean) -> Unit, kinds: Set<String>, onKind: (String, Boolean) -> Unit, onBack: () -> Unit) {
    SubScreen("Helpers can signal my phone", onBack) {
        item {
            Text("During your SOS, people coming to help can make your phone easy to find. Only people in your rescue group can, and only while your SOS is on.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        item { Group { ToggleRow(androidx.compose.material.icons.Icons.Rounded.Campaign, Extra.ember, "Allow helpers to signal", if (on) "On" else "Off: helpers can't make your phone sound or flash", on, onChange = onChange) } }
        if (on) {
            item { GroupLabel("What they can use") }
            item {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.bluemob.app.ui.components.SwitchCard(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.VolumeUp, Extra.sky, "Sound", "A loud whistle sound, even on silent", "sound" in kinds) { onKind("sound", it) }
                    com.bluemob.app.ui.components.SwitchCard(androidx.compose.material.icons.Icons.Rounded.FlashlightOn, Extra.ember, "Flash", "The camera light blinks", "flash" in kinds) { onKind("flash", it) }
                    com.bluemob.app.ui.components.SwitchCard(androidx.compose.material.icons.Icons.Rounded.LightMode, Palette.Gold, "Screen light", "The screen lights up bright white", "screen" in kinds) { onKind("screen", it) }
                    com.bluemob.app.ui.components.SwitchCard(androidx.compose.material.icons.Icons.Rounded.Mic, Extra.rose, "Microphone", "A helper's call answers by itself after 3 seconds, so they can hear you", "mic" in kinds) { onKind("mic", it) }
                }
            }
            if ("mic" in kinds) item {
                Text("Useful if you can't reach your phone. Only voice calls, only from people in your rescue group, only during your SOS.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
