package com.bluemob.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bluemob.app.mesh.LogLine
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What's kept on this phone, and clearing it. */
@Composable
fun StorageScreen(messages: Int, files: Int, people: Int, onBack: () -> Unit, onForgetPeople: () -> Unit, onClearMessages: () -> Unit, onBackup: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    when (confirm) {
        "messages" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete all messages?") },
            text = { Text("Every chat on this phone is deleted, with its photos, documents and voice notes. You won't be able to get them back unless you have a backup.") },
            confirmButton = { TextButton(onClick = { onClearMessages(); confirm = null }) { Text("Delete", color = Extra.rose) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep them") } },
        )
        "people" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Forget people you've met?") },
            text = { Text("Clears the list of people and when you last saw them. Chats stay; people come back as you meet them again.") },
            confirmButton = { TextButton(onClick = { onForgetPeople(); confirm = null }) { Text("Forget", color = Extra.rose) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    SubScreen("Storage & data", onBack) {
        item {
            Text("Everything BlueMob keeps is on this phone, encrypted. Back it up before clearing anything.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
        }
        item { GroupLabel("On this phone") }
        item {
            Group {
                Info("Messages", "$messages")
                Info("Photos, documents & voice notes", "$files")
                Info("People you've met", "$people")
            }
        }
        item {
            Text("Back up first", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onBackup).padding(vertical = 6.dp))
        }
        item { GroupLabel("Clear") }
        item {
            Group {
                SettingRow(Icons.Outlined.DeleteOutline, Extra.ember, "Forget people I've met", "Clears the list and last-seen history", onClick = { confirm = "people" })
                SettingRow(Icons.Outlined.DeleteOutline, Extra.rose, "Delete all messages", "From this phone only. The audit trail is kept", divider = true, onClick = { confirm = "messages" })
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** For testers and the curious: technical details, the relay, the audit trail and logs. */
@Composable
fun DiagnosticsScreen(
    techDetails: Boolean, onTechDetails: (Boolean) -> Unit,
    log: List<LogLine>, lastError: String?,
    onBack: () -> Unit, onBridge: () -> Unit, onAudit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showLog by rememberSaveable { mutableStateOf(false) }
    val chevron: @Composable () -> Unit = { Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3) }
    SubScreen("Diagnostics", onBack) {
        item {
            Text("Nothing here is needed day to day. It helps when something goes wrong, and when testing.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
        }
        item { GroupLabel("Show") }
        item {
            Group {
                SettingRow(Icons.Outlined.Tune, MaterialTheme.colorScheme.primary, "Technical details in chats",
                    "Link speed check and how each message travelled") { Switch(techDetails, onTechDetails) }
            }
        }
        item { GroupLabel("Security") }
        item {
            Group {
                SettingRow(Icons.Outlined.Lock, Color(0xFF3A4A44), "Audit trail", "Read-only, tamper-evident record of SOS, messages and positions", onClick = onAudit) { chevron() }
            }
        }
        item { GroupLabel("Advanced") }
        item {
            Group {
                SettingRow(Icons.Outlined.Public, Extra.sky, "Internet relay", "Built in. Change it only if you run your own BlueMob relay", onClick = onBridge) { chevron() }
            }
        }
        item { GroupLabel("Logs") }
        item {
            Group {
                if (lastError != null) {
                    var copied by remember { mutableStateOf(false) }
                    SettingRow(Icons.Outlined.Terminal, Extra.rose, "Last background error", if (copied) "Copied ✓ Send it to the BlueMob team" else lastError.lines().getOrNull(3)?.take(80) ?: "Tap to copy",
                        onClick = { clipboard.setText(AnnotatedString(lastError)); copied = true })
                }
                SettingRow(Icons.Outlined.Terminal, Color(0xFF3A4A44), "Mesh activity log", "What the mesh is doing, step by step", divider = lastError != null, onClick = { showLog = !showLog }) {
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
            Text("BlueMob ${com.bluemob.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

private val logTime = SimpleDateFormat("HH:mm:ss", Locale.US)
