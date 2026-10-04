package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.identity.Identity
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.settings.SignalMode
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.onboarding.AvatarPicker
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.formatId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    name: String,
    avatar: String,
    nodeId: String,
    running: Boolean,
    sharingLocation: Boolean,
    keepsRunning: Boolean,
    signalDefault: SignalMode,
    log: List<LogLine>,
    contentPadding: PaddingValues,
    onName: (String) -> Unit,
    onAvatar: (String) -> Unit,
    onToggleMesh: (Boolean) -> Unit,
    onToggleLocation: (Boolean) -> Unit,
    onBatterySaver: () -> Unit,
    onKeepRunning: () -> Unit,
    onSos: () -> Unit,
    onReplayIntro: () -> Unit,
    onForgetPeople: () -> Unit,
    onClearMessages: () -> Unit,
) {
    var draft by remember(name) { mutableStateOf(name) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val chevron: @Composable () -> Unit = { Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3) }

    LazyColumn(
        Modifier.fillMaxSize().background(Extra.sand),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding() + 8.dp, bottom = 120.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(104.dp).rotate(-4f).clip(RoundedCornerShape(34.dp)).background(Gradients.horizon()), contentAlignment = Alignment.Center) {
                    Text(avatar, fontSize = 52.sp)
                }
                Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "BM · ${formatId(nodeId)}" + if (copied) "  ✓ copied" else "  ⧉",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Extra.ink2,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                        .clickable { clipboard.setText(AnnotatedString("BM-$nodeId")); copied = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text("Your unique ID, given automatically to this phone", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item { GroupLabel("Profile") }
        item {
            Group {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("NAME", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(14.dp)) {
                        BasicTextField(draft, {
                            draft = it.take(Identity.MAX_NAME_LENGTH)
                            if (draft.isNotBlank()) onName(draft)
                        }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth())
                    }
                    AvatarPicker(selected = avatar, onSelect = onAvatar)
                }
            }
        }

        item { GroupLabel("Connections") }
        item {
            Group {
                SettingRow(Icons.Outlined.Hub, MaterialTheme.colorScheme.primary, "Mesh", "Find and be found by nearby phones") { Switch(running, onToggleMesh) }
                SettingRow(Icons.Outlined.LocationOn, Extra.sky, "Share my location", "Connected people see how far you are. GPS, no internet", divider = true) {
                    Switch(sharingLocation, onToggleLocation)
                }
            }
        }

        item { GroupLabel("Battery") }
        item {
            Group {
                SettingRow(Icons.Outlined.BatterySaver, Color(0xFF3A9A5B), "Phone Battery Saver", "Slows every other app. Opens Android settings", onClick = onBatterySaver) { chevron() }
                SettingRow(Icons.Outlined.PowerSettingsNew, MaterialTheme.colorScheme.primary, "Keep BlueMob running",
                    if (keepsRunning) "Done: BlueMob stays awake while Battery Saver is on" else "So messages and SOS still reach you with Battery Saver on",
                    divider = true, onClick = if (keepsRunning) null else onKeepRunning) { if (!keepsRunning) chevron() else Text("✓", color = MaterialTheme.colorScheme.primary) }
            }
        }

        item { GroupLabel("Safety") }
        item {
            Group {
                SettingRow(Icons.Outlined.WarningAmber, Extra.rose, "SOS", "Default signal: ${signalDefault.emoji} ${signalDefault.label}", onClick = onSos) { chevron() }
            }
        }

        item { GroupLabel("App") }
        item {
            Group {
                SettingRow(Icons.Outlined.Refresh, Color(0xFF7C6BD6), "Replay the intro", onClick = onReplayIntro) { chevron() }
                SettingRow(Icons.Outlined.DeleteOutline, Extra.ember, "Forget people I've met", "Clears the list and last-seen history", divider = true, onClick = onForgetPeople)
                SettingRow(Icons.Outlined.DeleteOutline, Extra.rose, "Delete all messages", "From this phone only", divider = true, onClick = onClearMessages)
            }
        }

        item { GroupLabel("For testers") }
        item {
            Group {
                SettingRow(Icons.Outlined.Terminal, Color(0xFF3A4A44), "Mesh activity log", "What the mesh is doing, step by step", onClick = { showLog = !showLog }) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3, modifier = Modifier.rotate(if (showLog) 90f else 0f))
                }
                if (showLog) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (log.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
                    log.take(80).forEach {
                        Text("${logTime.format(Date(it.timeMillis))}  ${it.text}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Extra.ink2)
                    }
                }
            }
        }
        item {
            Text("BlueMob 0.3 · made for the open sky", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
    }
}

private val logTime = SimpleDateFormat("HH:mm:ss", Locale.US)

