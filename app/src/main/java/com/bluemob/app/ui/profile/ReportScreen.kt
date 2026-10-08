package com.bluemob.app.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.ReportTopics
import kotlinx.coroutines.launch

/** A photo picked for a report: a small preview, and the compressed JPEG that gets sent. */
class ReportPhoto(val preview: Bitmap, val jpeg: ByteArray)

/**
 * Report a problem: pick what it's about (category, then what exactly), say what happened, add up to 3 screenshots
 * or photos, and choose whether to include technical details. Never messages, contacts or keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onAddPhoto: ((ReportPhoto?) -> Unit) -> Unit,
    onSend: suspend (category: String, sub: String, text: String, details: Boolean, photos: List<ByteArray>) -> String?,
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(true) }
    var photos by remember { mutableStateOf(listOf<ReportPhoto>()) }
    var sending by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SubScreen("Report a problem", onBack) {
        if (sent) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = MaterialTheme.typography.displayMedium)
                    Text("Thanks! Your report reached the BlueMob team.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text("Done") }
                }
            }
            return@SubScreen
        }
        item { GroupLabel("1 · What's it about?") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTopics.ALL.forEach { (c, _) -> Chip(c, category == c) { if (category != c) { category = c; sub = null } } }
            }
        }
        val subs = ReportTopics.ALL.firstOrNull { it.first == category }?.second
        if (subs != null) {
            item { GroupLabel("2 · What exactly?") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subs.forEach { s -> Chip(s, sub == s) { sub = s } }
                }
            }
        }
        item { GroupLabel("3 · What happened?") }
        item {
            OutlinedTextField(text, { text = it.take(4000) }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What did you do, what happened, and what did you expect? When was it?") })
        }
        item { GroupLabel("4 · Screenshots or photos (optional, up to 3)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photos.forEachIndexed { i, p ->
                    Box {
                        Image(p.preview.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)))
                        Text("✕", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).clickable { photos = photos.filterIndexed { j, _ -> j != i } }.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                if (photos.size < 3) Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(Extra.sand)
                    .clickable { onAddPhoto { p -> if (p != null) photos = photos + p else note = "That picture couldn't be read." } },
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", style = MaterialTheme.typography.titleLarge)
                        Text("Add", style = MaterialTheme.typography.labelMedium, color = Extra.ink2)
                    }
                }
            }
            Text("Tip: take a screenshot of the problem first (power + volume down), then add it here.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(Modifier.padding(top = 12.dp).clickable { details = !details }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(details, { details = it })
                Text("Include technical details (phone model, BlueMob version, recent errors and connection log). Never your messages, contacts or keys.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        note?.let { item { Text(it, color = Extra.rose, modifier = Modifier.padding(top = 8.dp)) } }
        item {
            Button(enabled = category != null && sub != null && text.isNotBlank() && !sending, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), onClick = {
                sending = true; note = null
                scope.launch {
                    val r = onSend(category!!, sub!!, text, details, photos.map { it.jpeg })
                    sending = false
                    if (r == null) sent = true else note = r
                }
            }) { Text(if (sending) "Sending…" else "Send report") }
            if (category == null || sub == null) Text("Pick what it's about first: it helps the team find and fix it faster.", style = MaterialTheme.typography.bodySmall,
                color = Extra.ink3, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
