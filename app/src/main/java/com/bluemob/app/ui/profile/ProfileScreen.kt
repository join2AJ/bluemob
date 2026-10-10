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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SettingsInputAntenna
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
    onBackup: () -> Unit = {},
    onGames: () -> Unit = {},
    sosContactCount: Int = 0,
    background: Boolean = true,
    onBackground: (Boolean) -> Unit = {},
    onStorage: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onActivity: () -> Unit = {},
    activitySummary: String = "",
    /** Badges earned / all, and opening them. */
    badges: Pair<Int, Int> = 0 to 0,
    badgeEmojis: String = "",
    onBadges: () -> Unit = {},
    theme: String = "auto",
    onTheme: (String) -> Unit = {},
    emergencyCard: Boolean = false,
    onEmergencyCard: (Boolean) -> Unit = {},
    remoteSignal: Boolean = true,
    onRemoteSignal: (Boolean) -> Unit = {},
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
        "avatar" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Pick your icon") },
            text = { AvatarPicker(selected = avatar, onSelect = { onAvatar(it); editing = null }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Done") } },
        )
    }
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
                // Your rating, right under your name: tap it to see each category and what people said.
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).clickable(onClick = onMyRating).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.ui.components.QuarterStars(myStars, 18.dp)
                    Text(
                        if (myRatingCount == 0) "  New · no ratings yet" else "  %.2f · %d %s".format(com.bluemob.app.trust.Trust.quarter(myStars), myRatingCount, if (myRatingCount == 1) "person" else "people"),
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                    )
                    Text("  ›", color = Extra.ink3)
                }
                // Badges: what you've done that makes you (and others) safer.
                Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onBadges)
                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏅  ${badges.first} of ${badges.second} badges  " + badgeEmojis, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text("  ›", color = Extra.ink3)
                }
                // Your activity at a glance: opens the dashboard with graphs.
                Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onActivity)
                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📊  " + activitySummary.ifBlank { "Your activity" }, style = MaterialTheme.typography.bodyMedium)
                    Text("  ›", color = Extra.ink3)
                }
            }
        }

        item { GroupLabel("Staying reachable") }
        item {
            Group {
                SettingRow(Icons.Outlined.Hub, MaterialTheme.colorScheme.primary, "Connections & power",
                    (if (running) "Mesh on" else "Mesh off") + " · " + if (background && keepsRunning) "reachable when closed" else "only while open",
                    onClick = onAutoStart) { chevron() }
            }
        }

        item { GroupLabel("Safety") }
        item {
            Group {
                SettingRow(Icons.Outlined.WarningAmber, Extra.rose, "SOS",
                    "Signal ${signalDefault.emoji} ${signalDefault.label} · " + if (sosContactCount == 0) "no SOS contacts yet" else "$sosContactCount SOS contact${if (sosContactCount == 1) "" else "s"}",
                    onClick = onSos) { chevron() }
                SettingRow(Icons.Outlined.WarningAmber, Extra.rose, "Emergency card on lock screen",
                    if (emergencyCard) "On: blood group and SOS contacts show without unlocking" else "Off: let whoever finds you see who to call",
                    divider = true) { androidx.compose.material3.Switch(emergencyCard, onEmergencyCard) }
                SettingRow(Icons.Outlined.WarningAmber, Extra.ember, "Helpers can signal my phone",
                    if (remoteSignal) "During your SOS, people coming to help can make it sound, flash or light up, to find you" else "Off: helpers can't make your phone sound or flash",
                    divider = true) { androidx.compose.material3.Switch(remoteSignal, onRemoteSignal) }
            }
        }

        item { GroupLabel("Account") }
        item {
            Group {
                SettingRow(Icons.Outlined.Key, Color(0xFF2F6F62), "Account & backup", "Number, blood group, PIN, recovery code, backups", onClick = onAccount) { chevron() }
                SettingRow(Icons.Outlined.DeleteOutline, Extra.ember, "Storage & data", "What's kept on this phone, and clearing it", divider = true, onClick = onStorage) { chevron() }
            }
        }

        item { GroupLabel("More") }
        item {
            Group {
                SettingRow(com.bluemob.app.ui.components.BlueMobIcons.Games, Extra.ember, "Games", "5 games, against the computer or people nearby", onClick = onGames) { chevron() }
                // Appearance: follow the phone, or always light / dark.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🌗", fontSize = 20.sp)
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                        Text(l, style = MaterialTheme.typography.labelLarge, color = if (theme == k) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (theme == k) MaterialTheme.colorScheme.primary else Extra.sand)
                                .clickable { onTheme(k) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                SettingRow(Icons.Outlined.Refresh, Color(0xFF7C6BD6), "Replay the intro", "How BlueMob works, in a minute", divider = true, onClick = onReplayIntro) { chevron() }
                SettingRow(Icons.Outlined.Terminal, Color(0xFF3A4A44), "Diagnostics", "Audit trail, internet connection, logs, report a problem", divider = true, onClick = onDiagnostics) { chevron() }
            }
        }
        item {
            Text("BlueMob ${com.bluemob.app.BuildConfig.VERSION_NAME} · made for the open sky", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
    }
}

private val logTime = SimpleDateFormat("HH:mm:ss", Locale.US)

