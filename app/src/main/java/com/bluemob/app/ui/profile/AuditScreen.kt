package com.bluemob.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bluemob.app.audit.AuditKind
import com.bluemob.app.audit.AuditVerification
import com.bluemob.app.audit.Witness
import com.bluemob.app.ui.SecurityStatus
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import com.bluemob.app.data.AuditEntry
import com.bluemob.app.ui.components.Gap
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault())

/** Read-only view of the audit trail. There's nothing to edit or delete here, by design. */
@Composable
fun AuditScreen(
    entries: List<AuditEntry>,
    check: AuditVerification,
    onBack: () -> Unit,
    witnesses: List<Witness> = emptyList(),
    publicKey: String = "",
    security: SecurityStatus? = null,
) {
    val broken = check.brokenAt ?: check.badSignatureAt ?: check.missingAfter
    val clipboard = LocalClipboardManager.current
    SubScreen("Audit trail", onBack) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Audit trail", style = MaterialTheme.typography.headlineMedium)
                Text("Everything important that happened on this phone, in order: SOS calls, messages, receipts and shared positions. Entries can't be edited or deleted.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Gap(14.dp) }
        item {
            val ok = check.ok && witnesses.none { !it.matches }
            Surface(shape = MaterialTheme.shapes.large, color = if (ok) Extra.pineTint else Extra.emberTint, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Outlined.Lock else Icons.Outlined.GppBad, null, tint = if (ok) MaterialTheme.colorScheme.primary else Extra.rose, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (ok) "Verified · ${entries.size} entries" else "Tampering detected", style = MaterialTheme.typography.titleMedium)
                        Text(if (ok) "Every check below passed just now." else "Something in this trail was changed or removed. Details below.",
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                }
            }
        }
        item { GroupLabel("Checks") }
        item {
            Group {
                Check("Chain of fingerprints", check.brokenAt == null,
                    if (check.brokenAt == null) "Each entry holds the SHA-256 of the one before. All ${check.count} link up." else "Entry ${check.brokenAt} doesn't match the one before it.")
                Check("Signed by this phone", check.badSignatureAt == null,
                    if (check.badSignatureAt == null) "Each entry is signed with this phone's private key, which never leaves it. Recomputing the hashes can't fake a signature." +
                        (if (check.unsigned > 0) " ${check.unsigned} early entries are from before signing." else "")
                    else "Entry ${check.badSignatureAt} has no valid signature.", divider = true)
                Check("Nothing removed from the end", check.missingAfter == null,
                    if (check.missingAfter == null) "A protected checkpoint remembers the newest entry. It's still here." else "Entries after ${check.missingAfter} were deleted.", divider = true)
                Check("Witnessed by other phones", witnesses.none { !it.matches },
                    when {
                        witnesses.isEmpty() -> "Phones you meet keep a signed copy of your newest entry and hand it back next time. None yet."
                        witnesses.any { !it.matches } -> "${witnesses.first { !it.matches }.name}'s phone holds entry ${witnesses.first { !it.matches }.seq}, and this trail no longer matches it."
                        else -> "${witnesses.size} phone${if (witnesses.size == 1) "" else "s"} confirmed it: " + witnesses.take(3).joinToString(", ") { "${it.name} (#${it.seq}, ${com.bluemob.app.util.TimeText.ago(it.at)})" }
                    }, divider = true)
                security?.let { sec ->
                    val encrypted = sec.databaseEncrypted && sec.plainSettingsFiles.isEmpty()
                    Check("Encrypted on this phone", encrypted,
                        if (encrypted) "Messages, contacts, keys and this trail are stored with AES-256. The keys live in Android Keystore, so copied files can't be read."
                        else "Not encrypted yet: " + (if (!sec.databaseEncrypted) "database " else "") + sec.plainSettingsFiles.joinToString(), divider = true)
                }
            }
        }
        item { GroupLabel("Newest first") }
        item {
            Group {
                if (entries.isEmpty()) Text("Nothing yet.", color = Extra.ink2, modifier = Modifier.padding(16.dp))
                entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 14.dp), color = Extra.line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        val kind = runCatching { AuditKind.valueOf(e.kind) }.getOrNull()
                        Box(Modifier.width(78.dp)) { Tag(kind?.label ?: e.kind, kindColor(kind), Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(e.text, style = MaterialTheme.typography.bodyMedium, color = if (broken != null && e.seq >= broken) Extra.rose else MaterialTheme.colorScheme.onSurface)
                            Text("#${e.seq} · ${stamp.format(Date(e.time))} · ${e.hash.take(12)}…", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Extra.ink3, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString("BlueMob audit trail\npublic key (X.509, base64): $publicKey\n" +
                        "hash = SHA-256(prev + seq|time|kind|text); sig = ECDSA-P256-SHA256(hash)\n\n" +
                        entries.asReversed().joinToString("\n") { "#${it.seq}\t${it.time}\t${stamp.format(Date(it.time))}\t${it.kind}\t${it.text}\tprev=${it.prev}\thash=${it.hash}\tsig=${it.sig}" }))
                }) { Text("Copy the full trail, with signatures") }
            }
        }
    }
}

@Composable
private fun kindColor(k: AuditKind?): Color = when (k) {
    AuditKind.SOS -> Extra.rose
    AuditKind.MESSAGE -> MaterialTheme.colorScheme.primary
    AuditKind.RECEIPT -> Color(0xFF3A9A5B)
    AuditKind.POSITION -> Extra.sky
    AuditKind.MESH -> Color(0xFF2F7BC0)
    AuditKind.TRUST -> Color(0xFFC2621A)
    AuditKind.APP, null -> Extra.ink3
}

@Composable
private fun Check(title: String, ok: Boolean, detail: String, divider: Boolean = false) {
    Column {
        if (divider) HorizontalDivider(Modifier.padding(start = 50.dp), color = Extra.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) MaterialTheme.colorScheme.primary else Extra.rose, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
        }
    }
}
