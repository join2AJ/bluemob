package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.SportsEsports
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
    onConnections: () -> Unit = {},
    onBridge: () -> Unit = {},
    onMyRating: () -> Unit = {},
    myStars: Double = com.bluemob.app.trust.Trust.START,
    myRatingCount: Int = 0,
    lastError: String? = null,
    onSosContacts: () -> Unit = {},
    onAudit: () -> Unit = {},
    onAccount: () -> Unit = {},
    onAutoStart: () -> Unit = {},
    onGames: () -> Unit = {},
    sosContactCount: Int = 0,
    background: Boolean = true,
    onBackground: (Boolean) -> Unit = {},
) {
    var draft by remember(name) { mutableStateOf(name) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    when (editing) {
        "name" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Your name") },
            text = {
                androidx.compose.material3.OutlinedTextField(draft, { draft = it.take(Identity.MAX_NAME_LENGTH) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), supportingText = { Text("People nearby see this") })
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { if (draft.isNotBlank()) onName(draft.trim()); editing = null }, enabled = draft.isNotBlank()) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
        "clear" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Delete all messages?") },
            text = { Text("Every chat on this phone is deleted, with its photos, documents and voice notes. You won't be able to get them back unless you have a backup (You → Backup).") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onClearMessages(); editing = null }) { Text("Delete", color = Extra.rose) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Keep them") } },
        )
        "avatar" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Pick your icon") },
            text = { AvatarPicker(selected = avatar, onSelect = { onAvatar(it); editing = null }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Done") } },
        )
    }
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
                // Tap the picture to change it, tap the name to rename: no big editor taking up the screen.
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.size(88.dp).rotate(-4f).clip(RoundedCornerShape(30.dp)).background(Gradients.horizon()).clickable { editing = "avatar" },
                        contentAlignment = Alignment.Center) { Text(avatar, fontSize = 44.sp) }
                    Text("✎", fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 7.dp, vertical = 3.dp))
                }
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { draft = name; editing = "name" }.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    Text("  ✎", style = MaterialTheme.typography.bodyMedium, color = Extra.ink3)
                }
                Text(
                    "BM · ${formatId(nodeId)}" + if (copied) "  ✓ copied" else "  ⧉",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Extra.ink2,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                        .clickable { clipboard.setText(AnnotatedString("BM-$nodeId")); copied = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        item { GroupLabel("Connections") }
        item {
            Group {
                SettingRow(Icons.Outlined.Hub, MaterialTheme.colorScheme.primary, "Mesh", "Find and be found by nearby phones") { Switch(running, onToggleMesh) }
                SettingRow(Icons.Outlined.LocationOn, Extra.sky, "Share my location", "Connected people see how far you are. GPS, no internet", divider = true) {
                    Switch(sharingLocation, onToggleLocation)
                }
                SettingRow(Icons.Outlined.ToggleOn, Extra.sky, "Turn on automatically", "Choose what BlueMob may switch on: mesh, Bluetooth, Wi-Fi", divider = true, onClick = onAutoStart) { chevron() }
                SettingRow(Icons.Outlined.NotificationsActive, Extra.rose, "Stay on in the background",
                    if (background) "SOS and messages reach you with the screen off" else "Off: SOS and messages only arrive while BlueMob is open", divider = true) {
                    Switch(background, onBackground)
                }
                SettingRow(Icons.Outlined.Public, Extra.sky, "Internet bridge", "Keep talking when you're far apart, through the BlueMob relay",
                    divider = true, onClick = onBridge) { chevron() }
                SettingRow(Icons.Outlined.SettingsInputAntenna, Extra.ember, "Bluetooth, Wi-Fi, GPS, internet", "See what's on and switch it, in one place",
                    divider = true, onClick = onConnections) { chevron() }
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
                SettingRow(Icons.Outlined.Contacts, Extra.sky, "SOS contacts",
                    if (sosContactCount == 0) "None yet. Add family to text when you send an SOS" else "$sosContactCount saved", divider = true, onClick = onSosContacts) { chevron() }
            }
        }

        item { GroupLabel("Your standing") }
        item {
            Group {
                SettingRow(Icons.Outlined.StarOutline, com.bluemob.app.ui.components.StarGold, "Your rating",
                    if (myRatingCount == 0) "4 stars to start. Help people and they can thank you" else "%.1f out of 5 · %d rating%s".format(myStars, myRatingCount, if (myRatingCount == 1) "" else "s"),
                    onClick = onMyRating) { com.bluemob.app.ui.components.StarRow(myStars, 14.dp) }
            }
        }

        item { GroupLabel("Account") }
        item {
            Group {
                SettingRow(Icons.Outlined.Key, Color(0xFF2F6F62), "Account", "Number, age, blood group, PIN lock, recovery code", onClick = onAccount) { chevron() }
            }
        }

        item { GroupLabel("Records") }
        item {
            Group {
                SettingRow(Icons.Outlined.Lock, Color(0xFF3A4A44), "Audit trail", "Read-only, tamper-evident record of SOS, messages and positions", onClick = onAudit) { chevron() }
            }
        }

        item { GroupLabel("Play") }
        item {
            Group {
                SettingRow(Icons.Outlined.SportsEsports, Extra.ember, "Games", "Tic-tac-toe and Connect 4, against the computer", onClick = onGames) { chevron() }
            }
        }

        item { GroupLabel("App") }
        item {
            Group {
                SettingRow(Icons.Outlined.Refresh, Color(0xFF7C6BD6), "Replay the intro", onClick = onReplayIntro) { chevron() }
                SettingRow(Icons.Outlined.DeleteOutline, Extra.ember, "Forget people I've met", "Clears the list and last-seen history", divider = true, onClick = onForgetPeople)
                SettingRow(Icons.Outlined.DeleteOutline, Extra.rose, "Delete all messages", "From this phone only. The audit trail is kept", divider = true, onClick = { editing = "clear" })
            }
        }

        item { GroupLabel("For testers") }
        item {
            Group {
                if (lastError != null) {
                    var errCopied by remember { mutableStateOf(false) }
                    SettingRow(Icons.Outlined.Terminal, Extra.rose, "Last background error", if (errCopied) "Copied ✓ Send it to the BlueMob team" else lastError.lines().getOrNull(3)?.take(80) ?: "Tap to copy",
                        onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(lastError)); errCopied = true })
                }
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

