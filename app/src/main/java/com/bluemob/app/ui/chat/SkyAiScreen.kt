package com.bluemob.app.ui.chat

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.bot.AiModel
import com.bluemob.app.bot.AiModels
import com.bluemob.app.bot.OfflineAi
import com.bluemob.app.ui.AppViewModel
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

@Composable
fun SkyAiScreen(vm: AppViewModel, onBack: () -> Unit) {
    val status by vm.aiStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            vm.importAi(uri, name)
        }
    }
    val ai = vm.offlineAi
    SkyAiContent(
        status, remember { ai.ramGb() }, remember(status.installed, status.download) { ai.freeBytes() },
        onDownload = ai::download, onCancel = ai::cancelDownload, onImport = { pick.launch(arrayOf("*/*")) },
        onEnabled = ai::setEnabled, onDelete = vm::deleteAi, onBack = onBack,
    )
}

/** Sky's offline AI: what it is, the two models to choose from, the download, and managing it afterwards. */
@Composable
fun SkyAiContent(
    status: OfflineAi.Status,
    ramGb: Int,
    freeBytes: Long,
    onDownload: (AiModel, Boolean) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var chosen by rememberSaveable { mutableStateOf(if (ramGb >= AiModels.STANDARD.minRamGb) AiModels.STANDARD.id else AiModels.LITE.id) }
    var confirmDelete by remember { mutableStateOf(false) }
    SubScreen("Sky's offline AI", onBack) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Extra.skyTint).padding(16.dp)) {
                Text("🧠  Ask Sky anything, with no internet", style = MaterialTheme.typography.titleMedium, color = Extra.sky)
                Text(
                    "Download an AI brain once. After that, Sky can answer any question, help you write, plan, explain and work things out, " +
                        "right on your phone. Nothing you ask is sent anywhere.\n\n" +
                        "It's a small AI, so it can be wrong. For first aid, Sky also points you to the checked survival guide. SOS never depends on it.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        val dl = status.download
        when {
            dl != null -> {
                item { GroupLabel(if (dl.checking) "Getting it ready" else "Downloading") }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        Text(dl.model.title + if (dl.total > 0) " · ${gb(dl.total)}" else "", style = MaterialTheme.typography.titleSmall)
                        if (dl.checking || dl.total <= 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 10.dp))
                        else LinearProgressIndicator(progress = { (dl.done.toFloat() / dl.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                        Text(
                            when {
                                dl.checking -> "Checking the file and setting it up…"
                                dl.waitingForWifi -> "Waiting for Wi-Fi. It starts by itself when you're on Wi-Fi."
                                else -> "${gb(dl.done)} of ${gb(dl.total)} · ${(100 * dl.done / dl.total.coerceAtLeast(1))}%. You can leave this screen; it keeps going."
                            },
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
                        )
                        if (!dl.checking) OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 10.dp)) { Text("Cancel download") }
                    }
                }
            }
            status.installed != null -> {
                item { GroupLabel("On this phone") }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Use the AI for Sky's answers", style = MaterialTheme.typography.titleSmall)
                                Text("${status.installed} · ${gb(status.installedBytes)}" + if (status.busy) " · thinking…" else "",
                                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                            }
                            Switch(checked = status.enabled, onCheckedChange = onEnabled)
                        }
                        Text(if (status.enabled) "On: ask Sky anything in the Sky chat." else "Off: Sky answers from its built-in guide only, as before.",
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Delete the AI and free ${gb(status.installedBytes)}", color = Extra.rose) }
                    }
                }
            }
            else -> {
                item { GroupLabel("Choose one") }
                AiModels.ALL.forEach { m ->
                    item(key = m.id) {
                        ModelCard(m, chosen == m.id, ramGb, best = m == (if (ramGb >= AiModels.STANDARD.minRamGb) AiModels.STANDARD else AiModels.LITE)) { chosen = m.id }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi only", style = MaterialTheme.typography.titleSmall)
                            Text("Don't use mobile data for this big download", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        }
                        Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                    }
                }
                item {
                    val m = AiModels.ALL.first { it.id == chosen }
                    val roomy = freeBytes <= 0 || freeBytes > m.bytes + 200_000_000
                    Button(onClick = { onDownload(m, wifiOnly) }, enabled = roomy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Download ${m.title} · ${gb(m.bytes)}")
                    }
                    Text(
                        if (roomy) "Free space: ${gb(freeBytes)}. Downloaded once, used offline after that." else "Not enough free space: ${gb(freeBytes)} free, needs ${gb(m.bytes)}.",
                        style = MaterialTheme.typography.bodySmall, color = if (roomy) Extra.ink3 else Extra.rose, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item {
                    TextButton(onClick = onImport, modifier = Modifier.padding(top = 6.dp)) { Text("I already have a model file (.task), e.g. Gemma") }
                }
            }
        }
        status.error?.let { e -> item { Text(e, style = MaterialTheme.typography.bodySmall, color = Extra.rose, modifier = Modifier.padding(top = 8.dp)) } }
        item {
            Text(
                "The AI models are Qwen 2.5 by Alibaba (Apache 2.0 licence), downloaded from Hugging Face. " +
                    "Gemma models need a free Hugging Face account to download; you can bring one in with the button above.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete the offline AI?") },
        text = { Text("Sky goes back to its built-in answers. You can download the AI again later.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Extra.rose) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

@Composable
private fun ModelCard(m: AiModel, selected: Boolean, ramGb: Int, best: Boolean, onClick: () -> Unit) {
    val fits = ramGb <= 0 || ramGb >= m.minRamGb
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Extra.line, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = MaterialTheme.typography.titleSmall)
                Text(gb(m.bytes), style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                if (best) Text("Best for your phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(m.blurb, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            Text(if (fits) "Needs ${m.minRamGb} GB memory · your phone has $ramGb GB" else "Your phone has $ramGb GB memory; this needs ${m.minRamGb} GB and may be slow or close",
                style = MaterialTheme.typography.bodySmall, color = if (fits) Extra.ink3 else Extra.ember)
        }
    }
}

private fun gb(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    else -> "${bytes / 1_000_000} MB"
}
