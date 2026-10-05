package com.bluemob.app.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bluemob.app.system.Radio
import com.bluemob.app.system.RadioState
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette

fun Radio.icon(): ImageVector = when (this) {
    Radio.BLUETOOTH -> Icons.Outlined.Bluetooth
    Radio.WIFI -> Icons.Outlined.Wifi
    Radio.LOCATION -> Icons.Outlined.LocationOn
    Radio.INTERNET -> Icons.Outlined.Language
}

fun RadioState.isOn(r: Radio, online: Boolean): Boolean = when (r) {
    Radio.BLUETOOTH -> bluetooth
    Radio.WIFI -> wifi
    Radio.LOCATION -> location
    Radio.INTERNET -> online
}

/** The most important thing that's off, with a reason, or null when everything BlueMob needs is on. */
fun RadioState.firstProblem(): Pair<Radio, String>? = when {
    hasBluetooth && !bluetooth -> Radio.BLUETOOTH to "Bluetooth is off, so BlueMob can't find anyone or receive an SOS."
    !location -> Radio.LOCATION to "Location is off, so the compass, your trail and SOS position won't work."
    !wifi -> Radio.WIFI to "Wi-Fi is off. BlueMob still works over Bluetooth, just slower."
    else -> null
}

/** One place to see and switch every radio BlueMob uses. */
@Composable
fun ConnectionsScreen(state: RadioState, online: Boolean, onBack: () -> Unit, onSwitch: (Radio, Boolean) -> Unit) {
    SubScreen("Connections", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Connections", style = MaterialTheme.typography.headlineMedium)
                Text("Everything BlueMob uses, in one place. Android doesn't let apps flip these by themselves, so a switch opens the right Android panel and you come straight back.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Gap(14.dp) }
        item {
            val good = state.bluetooth && state.location
            Surface(shape = MaterialTheme.shapes.large, color = if (good) Extra.pineTint else Extra.emberTint, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (good) "Ready for the outdoors" else "Something BlueMob needs is off", style = MaterialTheme.typography.titleMedium)
                    Text("Best off-grid setup: airplane mode on to save battery, then Bluetooth, Wi-Fi and Location back on. BlueMob needs no SIM or internet.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { GroupLabel("Radios") }
        item {
            Group {
                Radio.entries.forEachIndexed { i, r ->
                    val on = state.isOn(r, online)
                    RadioRow(r, on, divider = i > 0, onSwitch = { onSwitch(r, it) },
                        status = when {
                            r == Radio.BLUETOOTH && !state.hasBluetooth -> "This phone has no Bluetooth"
                            r == Radio.INTERNET && !on && state.airplane -> "Off · airplane mode"
                            r == Radio.INTERNET -> if (on) "On · this phone can bridge" else "No internet here"
                            else -> if (on) "On" else "Off"
                        })
                }
            }
        }
        if (state.airplane) item {
            Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AirplanemodeActive, null, tint = Extra.ink3, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Airplane mode is on. That's fine: Bluetooth, Wi-Fi and GPS can all be on in airplane mode.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
        }
    }
}

@Composable
private fun RadioRow(r: Radio, on: Boolean, status: String, divider: Boolean, onSwitch: (Boolean) -> Unit) {
    Column {
        if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 62.dp), color = Extra.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.Pine else Extra.ink3), contentAlignment = Alignment.Center) {
                Icon(r.icon(), null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.primary else Extra.ember)
                Text(r.why, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = onSwitch)
        }
    }
}

/** A compact row of the four radios, for the top of Nearby. Tap to open [ConnectionsScreen]. */
@Composable
fun RadioStrip(state: RadioState, online: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen)
        .padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Radio.entries.forEach { r ->
            val on = state.isOn(r, online)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(r.icon(), null, tint = if (on) MaterialTheme.colorScheme.primary else Extra.ink3, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(when (r) { Radio.LOCATION -> "GPS"; Radio.INTERNET -> "Net"; else -> r.title }, style = MaterialTheme.typography.labelMedium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else Extra.ink3)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Palette.Mint else Extra.line))
            }
        }
    }
}

/** Asks the user to switch on the most important radio that's off. */
@Composable
fun RadioBanner(problem: Pair<Radio, String>, onFix: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = Extra.emberTint, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(problem.first.icon(), null, tint = Extra.ember, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(problem.second, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("All connections") }
            }
            Button(onClick = onFix) { Text("Turn on") }
        }
    }
}
