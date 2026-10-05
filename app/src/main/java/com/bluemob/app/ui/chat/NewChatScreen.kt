package com.bluemob.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bluemob.app.crypto.Crypto
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.dashboard.statusLine
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.formatId

/** Start a chat with anyone by their BlueMob ID, share your own, or pick someone you've met. */
@Composable
fun NewChatScreen(
    myId: String,
    people: List<Person>,
    onBack: () -> Unit,
    onStart: (id: String, name: String) -> Unit,
    onOpen: (String) -> Unit,
    onShareId: () -> Unit,
) {
    var idText by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable { mutableStateOf(false) }
    val parsed = Crypto.normalizeId(idText)
    val error = when {
        idText.isBlank() -> null
        parsed == null -> "A BlueMob ID has 16 letters and numbers, like BM 3F9A 1C2B 7D4E 8A01"
        parsed == myId -> "That's your own ID"
        else -> null
    }

    SubScreen("New message", onBack) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("YOUR BLUEMOB ID", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Text("BM " + formatId(myId), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Give it to anyone, at home or on the trail. They can message you with it: no phone number, no internet. It can't be copied by another phone.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboard.setText(AnnotatedString("BM " + formatId(myId))); copied = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) {
                            Text(if (copied) "Copied ✓" else "Copy")
                        }
                        OutlinedButton(onClick = onShareId, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))) {
                            Text("Share", color = Color.White)
                        }
                    }
                }
            }
        }
        item { GroupLabel("Message someone by their ID") }
        item {
            Group {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("BlueMob ID", idText, "BM 3F9A 1C2B 7D4E 8A01", mono = true) { idText = it.take(30) }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Extra.rose) }
                    Field("Their name (optional)", name, "e.g. Kabir from the bus", mono = false) { name = it.take(24) }
                    Button(onClick = { parsed?.let { onStart(it, name.trim()) } }, enabled = parsed != null && parsed != myId, modifier = Modifier.fillMaxWidth()) {
                        Text("Start chat")
                    }
                }
            }
        }
        item {
            Text("If they're not nearby, your message is handed, encrypted, to phones around you. They carry it and pass it on as people move, " +
                "until it reaches them. The more BlueMob phones around, the faster it gets there. The ticks show when it arrives.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        }
        if (people.isNotEmpty()) {
            item { GroupLabel("People you know (${people.size})") }
            item {
                Group {
                    people.forEachIndexed { i, p ->
                        Column {
                            if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 66.dp), color = Extra.line)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.avatar, p.name, p.nodeId, 40.dp, p.presence)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text("BM ${formatId(p.nodeId)} · ${statusLine(p)}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
                                }
                                TextButton(onClick = { onOpen(p.nodeId) }) { Text("Message") }
                            }
                        }
                    }
                }
            }
        }
        item { Gap(8.dp) }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, mono: Boolean, onChange: (String) -> Unit) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.sand).padding(12.dp)) {
            val style = MaterialTheme.typography.bodyLarge.let { if (mono) it.copy(fontFamily = FontFamily.Monospace) else it }
            if (value.isEmpty()) Text(hint, color = Extra.ink3, style = style)
            BasicTextField(value, onChange, singleLine = true, textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(capitalization = if (mono) KeyboardCapitalization.Characters else KeyboardCapitalization.Words),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
        }
    }
}
