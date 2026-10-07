package com.bluemob.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.files.Attachment
import com.bluemob.app.guide.GuidePacks
import com.bluemob.app.guide.PackInfo
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import kotlinx.coroutines.launch

/** Download extra survival guides for the trip you're planning, while you have internet. They then work offline. */
@Composable
fun GuidePacksScreen(relay: String, online: Boolean, onBack: () -> Unit) {
    val installed by GuidePacks.installed.collectAsStateWithLifecycle()
    var available by remember { mutableStateOf<List<PackInfo>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(relay, online) {
        if (relay.isNotBlank() && online) { loading = true; available = GuidePacks.available(relay.trimEnd('/')); loading = false }
    }
    SubScreen("More guides", onBack) {
        item {
            Text("Pick the guides for your trip and download them while you have internet. They then work with no signal, and Sky can answer from them.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
            note?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }
        // One list: every pack once, with its size and what you can do with it.
        val all = ((available ?: emptyList()) + installed.filter { i -> available?.none { it.id == i.id } != false }).distinctBy { it.id }
        item {
            val status = when {
                relay.isBlank() -> "Downloads aren't available in this version."
                !online -> "You're offline: showing what's on this phone. Connect to download more."
                loading -> "Checking for guides…"
                available == null -> "Couldn't check for new guides right now."
                else -> null
            }
            status?.let {
                Column(Modifier.padding(top = 10.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                    if (available == null && online && relay.isNotBlank() && !loading)
                        OutlinedButton(onClick = { scope.launch { loading = true; available = GuidePacks.available(relay.trimEnd('/')); loading = false } }, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
                }
            }
        }
        items(all, key = { it.id }) { p ->
            val have = installed.firstOrNull { it.id == p.id }
            val isNew = have != null && System.currentTimeMillis() - (GuidePacks.installedAt[p.id] ?: 0L) < GuidePacks.NEW_FOR_MS
            val newer = available?.firstOrNull { it.id == p.id }?.let { have != null && it.version > have.version } == true
            PackRow(p, "${p.articles} guides · ${Attachment.sizeText(p.bytes)}" + when { have == null -> ""; isNew -> " · on this phone · NEW"; else -> " · on this phone" }) {
                Column(horizontalAlignment = Alignment.End) {
                    if (have == null || newer) Button(onClick = {
                        busy = p.id
                        scope.launch { note = GuidePacks.download(relay.trimEnd('/'), p.id) ?: "${p.title} is ready offline."; busy = null }
                    }, enabled = busy == null && online) { Text(if (busy == p.id) "…" else if (newer) "Update" else "Download") }
                    if (have != null) TextButton(onClick = { GuidePacks.remove(p.id); note = "${p.title} removed." }) { Text("Remove", color = Extra.rose) }
                }
            }
        }
        item {
            Text("These are general guidance, not medical advice. In an emergency, send an SOS and get help.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun PackRow(p: PackInfo, sub: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(p.emoji, fontSize = 28.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium)
            if (p.about.isNotBlank()) Text(p.about, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(top = 2.dp))
        }
        action()
    }
}
