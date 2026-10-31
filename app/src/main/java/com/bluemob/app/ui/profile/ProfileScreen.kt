package com.bluemob.app.ui.profile

import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.PersonalInjury
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Badge
import com.bluemob.app.ui.theme.Palette
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.bluemob.app.i18n.Text
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    game: GameProgress? = null,
    onBadges: () -> Unit = {},
    theme: String = "auto",
    onTheme: (String) -> Unit = {},
    language: String = "",
    onLanguage: (String) -> Unit = {},
    emergencyCard: Boolean = false,
    onEmergencyCard: (Boolean) -> Unit = {},
    /** The card's words, to copy into Android's own emergency information. */
    emergencyCardText: () -> String = { "" },
    powerSos: Boolean = false,
    onPowerSos: (Boolean) -> Unit = {},
    helperKinds: Set<String> = setOf("sound", "flash", "screen"),
    onHelperKind: (String, Boolean) -> Unit = { _, _ -> },
    onEmergencyCardOpen: () -> Unit = {},
    onHelperSignalsOpen: () -> Unit = {},
    checkInDue: Long = 0L,
    onCheckIn: () -> Unit = {},
    fallDetect: Boolean = false,
    onFallDetect: ((Boolean) -> Unit)? = null,
    remoteSignal: Boolean = true,
    onRemoteSignal: (Boolean) -> Unit = {},
    /** Do not disturb: until when (0 = off), and whether SOS contacts' calls still ring. */
    dndUntil: Long = 0L,
    onDndUntil: (Long) -> Unit = {},
    /** Full silence: every BlueMob sound off, even SOS alarms; turning it on asks a sum. */
    fullSilence: Boolean = false,
    onFullSilence: (Boolean) -> Unit = {},
    dndSosCalls: Boolean = true,
    onDndSosCalls: (Boolean) -> Unit = {},
    /** News from the BlueMob team: how many are unread, and opening the list. */
    newsUnread: Int = 0,
    onNews: () -> Unit = {},
) {
    var dndPicker by remember { mutableStateOf(false) }
    var silenceAsk by remember { mutableStateOf(false) }
    if (silenceAsk) SilenceDialog(onConfirm = { onFullSilence(true); silenceAsk = false }) { silenceAsk = false }
    if (dndPicker) DndDialog(dndSosCalls, onDndSosCalls, { onDndUntil(it); dndPicker = false }) { dndPicker = false }
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
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding() + 8.dp, bottom = 24.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Tap the picture to change it, tap the name to rename: no big editor taking up the screen.
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.size(88.dp).rotate(-4f).clip(RoundedCornerShape(30.dp)).background(Gradients.horizon()).clickable { editing = "avatar" },
                        contentAlignment = Alignment.Center) { Text(avatar, fontSize = 44.sp) }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Edit, "Change picture", Modifier.size(15.dp), tint = Extra.ink2)
                    }
                }
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { draft = name; editing = "name" }.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.i18n.RawText(name, style = MaterialTheme.typography.headlineSmall)
                    Icon(Icons.Outlined.Edit, "Change name", Modifier.padding(start = 6.dp).size(16.dp), tint = Extra.ink3)
                }
                Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                    .clickable { clipboard.setText(AnnotatedString("BM-$nodeId")); copied = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.i18n.RawText("BM · ${formatId(nodeId)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Extra.ink2)
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Copied" else "Copy ID", Modifier.padding(start = 8.dp).size(15.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else Extra.ink3)
                }
                // Level, rank and medals: what you've done that makes you (and others) safer.
                game?.takeIf { com.bluemob.app.Features.BADGES }?.let { LevelPill(it, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBadges)) }
                // Your rating and your week, as two tiles: tap one for the details.
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Modifier.weight(1f), Icons.Filled.Star, Palette.Gold, "Trust",
                        if (myRatingCount == 0) "New" else "%.1f".format(com.bluemob.app.trust.Trust.quarter(myStars)),
                        if (myRatingCount == 0) "No ratings yet" else "$myRatingCount ${if (myRatingCount == 1) "rating" else "ratings"}", onMyRating) {
                        if (myRatingCount > 0) com.bluemob.app.ui.components.QuarterStars(myStars, 12.dp)
                    }
                    val week = activitySummary.removePrefix("This week: ").split(" · ").filter { it.isNotBlank() }
                    StatTile(Modifier.weight(1f), Icons.Outlined.Insights, Palette.BrandBlue, "This week",
                        week.firstOrNull() ?: "–", week.drop(1).joinToString(" · ").ifBlank { "Your activity" }, onActivity)
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

        item { GroupLabel("From BlueMob") }
        item {
            Group {
                SettingRow(Icons.Outlined.NotificationsActive, Extra.sky, "News from BlueMob",
                    if (newsUnread > 0) "$newsUnread new · alerts, updates and safety tips" else "Alerts, updates and safety tips from the team",
                    onClick = onNews) { chevron() }
            }
        }

        item { GroupLabel("Do not disturb") }
        item {
            val on = dndUntil > System.currentTimeMillis()
            Group {
                SettingRow(Icons.Outlined.DoNotDisturbOn, Extra.sky, "Do not disturb",
                    if (on) "On ${dndText(dndUntil)} · SOS and rescue alerts still come through" + (if (dndSosCalls) ", and calls from your SOS contacts" else "")
                    else "Off · messages and calls make sound as usual",
                    onClick = { dndPicker = true }) { androidx.compose.material3.Switch(on, { if (it) dndPicker = true else onDndUntil(0L) }) }
                SettingRow(Icons.Outlined.NotificationsOff, Extra.rose, "Full silence",
                    if (fullSilence) "On · every BlueMob sound is off, even SOS alarms and trip alerts. Your own SOS still works"
                    else "Off · turn on to silence BlueMob completely",
                    divider = true, onClick = { if (fullSilence) onFullSilence(false) else silenceAsk = true }) {
                    androidx.compose.material3.Switch(fullSilence, { if (it) silenceAsk = true else onFullSilence(false) })
                }
            }
        }

        item { GroupLabel("Safety") }
        item {
            Group {
                SettingRow(androidx.compose.material.icons.Icons.Outlined.Sos, Extra.rose, "SOS",
                    "Signal ${signalDefault.emoji} ${signalDefault.label} · " + if (sosContactCount == 0) "no SOS contacts yet" else "$sosContactCount SOS contact${if (sosContactCount == 1) "" else "s"}",
                    onClick = onSos) { chevron() }
                SettingRow(androidx.compose.material.icons.Icons.Outlined.Timer, Extra.ember, "Check-in timer",
                    if (checkInDue > 0L) "On: back by " + com.bluemob.app.safety.CheckInTimer.time(checkInDue) else "Going alone? An SOS if you don't check in by a time you set",
                    divider = true, onClick = onCheckIn) { chevron() }
                if (onFallDetect != null) SettingRow(androidx.compose.material.icons.Icons.Outlined.PersonalInjury, Extra.rose, "Fall detection",
                    if (fallDetect) "On: after a hard fall and no movement, 30 seconds to cancel, then SOS" else "Off: send an SOS by itself after a hard fall",
                    divider = true) { androidx.compose.material3.Switch(fallDetect, onFallDetect) }
                SettingRow(androidx.compose.material.icons.Icons.Outlined.PowerSettingsNew, Extra.rose, "Power button SOS",
                    if (powerSos) "On: press the power button 5 times quickly, even locked. 5 seconds to cancel" else "Off: press the power button 5 times to send an SOS",
                    divider = true) { androidx.compose.material3.Switch(powerSos, onPowerSos) }
                SettingRow(androidx.compose.material.icons.Icons.Outlined.Badge, Extra.rose, "Emergency card on lock screen",
                    if (emergencyCard) "On: blood group and SOS contacts show without unlocking" else "Off: let whoever finds you see who to call",
                    divider = true, onClick = onEmergencyCardOpen) { chevron() }
                SettingRow(androidx.compose.material.icons.Icons.Outlined.Campaign, Extra.ember, "Helpers can signal my phone",
                    if (!remoteSignal) "Off: helpers can't make your phone sound or flash"
                    else "On: " + listOf("sound" to "sound", "flash" to "flash", "screen" to "screen light", "mic" to "microphone")
                        .filter { it.first in helperKinds }.joinToString(", ") { it.second }.ifBlank { "nothing chosen" },
                    divider = true, onClick = onHelperSignalsOpen) { chevron() }
            }
        }

        item { GroupLabel("Account") }
        item {
            Group {
                SettingRow(Icons.Outlined.Key, Color(0xFF2F6F62), "Account & backup", "Number, blood group, PIN, recovery code, backups", onClick = onAccount) { chevron() }
                SettingRow(androidx.compose.material.icons.Icons.Outlined.Storage, Extra.ember, "Storage & data", "What's kept on this phone, and clearing it", divider = true, onClick = onStorage) { chevron() }
            }
        }

        item { GroupLabel("More") }
        item {
            Group {
                if (com.bluemob.app.Features.GAMES) SettingRow(com.bluemob.app.ui.components.BlueMobIcons.Games, Extra.ember, "Games", "5 games, against the computer or people nearby", onClick = onGames) { chevron() }
                // Appearance: follow the phone, or always light / dark.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.ui.components.IconTile(androidx.compose.material.icons.Icons.Rounded.Contrast, com.bluemob.app.ui.theme.Palette.Violet)
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                        Text(l, style = MaterialTheme.typography.labelLarge, color = if (theme == k) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (theme == k) MaterialTheme.colorScheme.primary else Extra.sand)
                                .clickable { onTheme(k) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                // Language: English or Hindi, or whatever the phone uses.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.bluemob.app.ui.components.IconTile(androidx.compose.material.icons.Icons.Rounded.Language, Extra.sky)
                    Text("Language", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    (listOf("" to "Auto") + com.bluemob.app.i18n.Lang.SUPPORTED).forEach { (k, l) ->
                        Text(l, style = MaterialTheme.typography.labelLarge, color = if (language == k) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (language == k) MaterialTheme.colorScheme.primary else Extra.sand)
                                .clickable { onLanguage(k) }.padding(horizontal = 10.dp, vertical = 6.dp))
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



/** "until 7:00 am", "until you turn it off". */
fun dndText(until: Long): String = if (until == Long.MAX_VALUE) "until you turn it off"
    else "until " + java.text.SimpleDateFormat(if (until - System.currentTimeMillis() > 20 * 3_600_000L) "EEE h:mm a" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(until))

/** The next 7 am (tomorrow if it's already past 7 am). */
private fun nextMorning(): Long = java.util.Calendar.getInstance().apply {
    val past = get(java.util.Calendar.HOUR_OF_DAY) >= 7
    set(java.util.Calendar.HOUR_OF_DAY, 7); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    if (past) add(java.util.Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

@Composable
private fun DndDialog(sosCalls: Boolean, onSosCalls: (Boolean) -> Unit, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = System.currentTimeMillis()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Do not disturb") },
        text = {
            Column {
                Text("Messages, calls and invites arrive silently. SOS alerts and rescue groups always sound, so you never miss someone who needs help.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                listOf("For 1 hour" to now + 3_600_000L, "For 8 hours" to now + 8 * 3_600_000L, "Until 7 am" to nextMorning(), "Until I turn it off" to Long.MAX_VALUE).forEach { (label, until) ->
                    androidx.compose.material3.TextButton(onClick = { onPick(until) }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Let calls from my SOS contacts ring", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(sosCalls, onSosCalls)
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Turning on full silence: it also mutes SOS alarms from people nearby, so a simple sum makes sure it's on purpose.
 * Turning it off never asks.
 */
@Composable
private fun SilenceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (x, y) = remember { (2..9).random() to (2..9).random() }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Full silence?") },
        text = {
            Column {
                Text("Every BlueMob sound stops: messages, calls, trip alerts, and SOS alarms from people nearby. Your own SOS still goes out. To turn it on, answer:",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                com.bluemob.app.i18n.RawText("$x + $y = ?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                androidx.compose.material3.OutlinedTextField(answer, { answer = it.filter(Char::isDigit).take(3); wrong = false }, singleLine = true, isError = wrong,
                    label = { Text("Answer") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp))
                if (wrong) Text("Not quite. Try again.", style = MaterialTheme.typography.bodySmall, color = Extra.rose)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (answer.toIntOrNull() == x + y) onConfirm() else wrong = true }, enabled = answer.isNotBlank()) { Text("Turn on") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A small tile under your name: icon, label, a big value and a line under it. */
@Composable
private fun StatTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, label: String, value: String, sub: String,
    onClick: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(15.dp), tint = tint)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = Extra.ink3, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Extra.ink3)
        }
        com.bluemob.app.i18n.RawText(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
        extra()
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
    }
}
