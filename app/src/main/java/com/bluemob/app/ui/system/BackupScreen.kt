package com.bluemob.app.ui.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bluemob.app.backup.BackupEvery
import com.bluemob.app.backup.BackupFile
import com.bluemob.app.backup.BackupManager
import com.bluemob.app.backup.BackupStatus
import com.bluemob.app.files.Attachment
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.TimeText
import kotlinx.coroutines.launch

/** What the backup screen needs from the activity: Android's pickers for files and folders. */
class BackupActions(
    /** Pick a folder for automatic backups (phone, SD card, or a cloud app that offers folders). */
    val pickFolder: ((String?) -> Unit) -> Unit,
    /** "Save to…" a new file anywhere (Google Drive, OneDrive, phone…). */
    val createFile: (String, (android.net.Uri?) -> Unit) -> Unit,
    /** Open a backup file to restore. */
    val openFile: ((android.net.Uri?) -> Unit) -> Unit,
    val restart: () -> Unit,
)

@Composable
fun BackupScreen(m: BackupManager, status: BackupStatus, every: BackupEvery, folder: String?, actions: BackupActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var askPassword by rememberSaveable { mutableStateOf<String?>(null) } // "set", "save", "open"
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preview by remember { mutableStateOf<BackupManager.Preview.Ready?>(null) }
    var hasPassword by remember { mutableStateOf(m.hasPassword) }

    askPassword?.let { mode ->
        PasswordDialog(mode, onDone = { pw ->
            askPassword = null
            when (mode) {
                "set" -> { m.setPassword(pw); hasPassword = true; note = "Backup password saved." }
                "save" -> pendingUri?.let { uri -> busy = true; scope.launch { note = m.backupTo(uri, pw); busy = false } }
                "open" -> pendingUri?.let { uri -> busy = true; scope.launch {
                    when (val p = m.open(uri, pw)) { is BackupManager.Preview.Ready -> preview = p; is BackupManager.Preview.Problem -> note = p.message }
                    busy = false
                } }
            }
        }, onCancel = { askPassword = null })
    }
    preview?.let { p ->
        var restoreId by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text("From ${TimeText.ago(p.data.createdAt)} (BlueMob ${p.data.app}): ${p.data.messages.size} messages, ${p.data.trips.size} trips, " +
                        "${p.data.calls.size} calls, ${p.data.contacts.size} contacts, ${p.attachments.size} files. It's added to what's on this phone; nothing is deleted.")
                    if (p.otherId && p.data.recovery != null) Row(Modifier.padding(top = 12.dp).clickable { restoreId = !restoreId }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(restoreId, { restoreId = it })
                        Text("Also bring back the BlueMob ID from this backup (BM ${com.bluemob.app.util.formatId(p.data.nodeId).take(9)}…). BlueMob restarts.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = {
                val chosen = p; preview = null; busy = true
                scope.launch { note = m.restore(chosen, restoreId); busy = false; if (restoreId) actions.restart() }
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }

    SubScreen("Backup", onBack) {
        item {
            Text("Your messages, files, trips, call history, contacts and saved spots in one compressed file, locked with your backup password. " +
                "It stays where you put it: BlueMob has no copy.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
            (note ?: if (busy) "Working…" else null)?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)) }
        }
        item { GroupLabel("Last backup") }
        item {
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text(if (status.lastAt == 0L) "Never backed up" else "${TimeText.ago(status.lastAt)} · ${Attachment.sizeText(status.lastSize)}", style = MaterialTheme.typography.titleMedium)
                    status.lastResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        item { GroupLabel("Password") }
        item {
            Column {
                OutlinedButton(onClick = { askPassword = "set" }, modifier = Modifier.fillMaxWidth()) { Text(if (hasPassword) "Change backup password" else "Set a backup password") }
                Text("You need it to open a backup, even on this phone. BlueMob can't recover a forgotten one. It's kept encrypted on this phone so automatic backups can run.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { GroupLabel("Automatic backups") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupEvery.entries.forEach { e -> Chip(e.label, every == e) { if (e != BackupEvery.OFF && !hasPassword) askPassword = "set" else m.setEvery(e) } }
            }
        }
        item {
            Group(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Saved to", style = MaterialTheme.typography.labelMedium, color = Extra.ink2)
                    Text(m.folderName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Chip("This phone", folder == null) { m.setFolder(null) }
                        Chip("Pick a folder…", folder != null) { actions.pickFolder { uri -> if (uri != null) m.setFolder(uri) } }
                    }
                    Text("Pick a folder on this phone or SD card, or in a cloud app that offers folders (OneDrive, Dropbox and others). The newest $KEEP backups are kept there.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Button(onClick = { if (!hasPassword) askPassword = "set" else { busy = true; scope.launch { note = m.backupNow(); busy = false } } },
                enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Back up now") }
        }
        item { GroupLabel("Copy to Google Drive, OneDrive…") }
        item {
            OutlinedButton(onClick = { actions.createFile(m.suggestedName()) { uri -> if (uri != null) { pendingUri = uri; askPassword = "save" } } },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save a backup to…") }
            Text("Opens Android's \"Save to\" screen: choose Drive, OneDrive, or any folder.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
        }
        item { GroupLabel("Restore") }
        item {
            OutlinedButton(onClick = { actions.openFile { uri -> if (uri != null) { pendingUri = uri; askPassword = "open" } } },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from a backup…") }
            Text("Works on a new phone too. What's on this phone is kept; the backup is added to it.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        }
    }
}

private const val KEEP = BackupManager.KEEP

@Composable
private fun PasswordDialog(mode: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val setting = mode == "set"
    val ok = pw.length >= BackupFile.MIN_PASSWORD && (!setting || pw == again)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(when (mode) { "set" -> "Backup password"; "save" -> "Password for this backup"; else -> "Backup password" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pw, { pw = it }, label = { Text("Password (at least ${BackupFile.MIN_PASSWORD} characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (setting) OutlinedTextField(again, { again = it }, label = { Text("Type it again") }, singleLine = true, isError = again.isNotEmpty() && again != pw,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (setting) Text("Write it down. Without it, no one (not even BlueMob) can open your backups.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
        },
        confirmButton = { Button(onClick = { onDone(pw) }, enabled = ok) { Text(if (mode == "open") "Open" else "OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
