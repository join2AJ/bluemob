package com.bluemob.app.ui.profile

import androidx.compose.material.icons.rounded.CheckCircle
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
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
import com.bluemob.app.i18n.Text
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
                    com.bluemob.app.ui.components.IconBadge(androidx.compose.material.icons.Icons.Rounded.CheckCircle, com.bluemob.app.ui.theme.Palette.BrandGreen, size = 80.dp)
                    Text("Thanks! Your report reached the BlueMob team.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text("Done") }
                }
            }
            return@SubScreen
        }
        item {
            Text("Tell us what went wrong. The team reads every report.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
        item { Step(1, "What's it about?", done = category != null) }
        item {
            // Three tiles a row: an icon in a soft circle of its colour, and a short name. Hidden features aren't offered.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTopics.ALL.filter { com.bluemob.app.Features.GAMES || !it.first.endsWith("Games") }.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (c, _) ->
                            val on = category == c
                            val (icon, tint) = topicLook(c.substringAfter(' '))
                            Column(
                                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (on) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                                    .border(if (on) 2.dp else 1.dp, if (on) tint else Extra.line, RoundedCornerShape(18.dp))
                                    .clickable { if (category != c) { category = c; sub = null } }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = if (on) 0.32f else 0.16f)), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, Modifier.size(20.dp), tint = tint)
                                }
                                Text(c.substringAfter(' '), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
                                    color = if (on) tint else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        val subs = ReportTopics.ALL.firstOrNull { it.first == category }?.second
        // Steps are numbered as shown: "What exactly?" appears once a topic is picked.
        val n = if (subs != null) 1 else 0
        if (subs != null) {
            item { Step(2, "What exactly?", done = sub != null) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subs.forEach { s -> Chip(s, sub == s) { sub = s } }
                }
            }
        }
        item { Step(2 + n, "What happened?", done = text.isNotBlank()) }
        item {
            OutlinedTextField(text, { text = it.take(4000) }, minLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                placeholder = { Text("What did you do, what happened, and what did you expect? When was it?") },
                supportingText = { Text("${text.length} / 4000", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) })
        }
        item { Step(3 + n, "Screenshots or photos", done = photos.isNotEmpty(), optional = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photos.forEachIndexed { i, p ->
                    Box(Modifier.weight(1f).aspectRatio(0.8f)) {
                        Image(p.preview.asImageBitmap(), "Photo ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)).clickable { photos = photos.filterIndexed { j, _ -> j != i } },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Remove photo", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                repeat(3 - photos.size) { slot ->
                    if (slot == 0) {
                        Column(
                            Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .dashedBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                .clickable { onAddPhoto { p -> if (p != null) photos = photos + p else note = "That picture couldn't be read." } },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                            }
                            Text("Add photo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                            Text("${photos.size} of 3", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                        }
                    } else Box(Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).border(1.dp, Extra.line, RoundedCornerShape(16.dp)))
                }
            }
            Text("Tip: take a screenshot of the problem first (power + volume down), then add it here.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { details = !details }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include technical details", style = MaterialTheme.typography.titleSmall)
                    Text("Phone model, BlueMob version, recent errors, connection log, and your BlueMob ID so the team can reply to you. Never your messages, contacts or keys.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Switch(details, { details = it })
            }
        }
        note?.let { item { Text(it, color = Extra.rose, modifier = Modifier.padding(top = 10.dp)) } }
        item {
            val ready = category != null && sub != null && text.isNotBlank()
            Button(enabled = ready && !sending, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(54.dp), shape = RoundedCornerShape(28.dp), onClick = {
                sending = true; note = null
                scope.launch {
                    val r = onSend(category!!, sub!!, text, details, photos.map { it.jpeg })
                    sending = false
                    if (r == null) sent = true else note = r
                }
            }) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (sending) "Sending…" else "Send report")
            }
            if (!ready) Text(
                when { category == null -> "Pick what it's about first: it helps the team find and fix it faster."; sub == null -> "Now pick what exactly."; else -> "Say what happened." },
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp))
        }
    }
}

/** A numbered step heading: the number turns into a tick once the step is done. */
@Composable
private fun Step(n: Int, title: String, done: Boolean, optional: Boolean = false) {
    Row(Modifier.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else Extra.line), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            else Text("$n", style = MaterialTheme.typography.labelLarge, color = Extra.ink2)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (optional) Text("  · optional", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
    }
}

/** A dashed rounded outline, for "add" tiles. */
private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color) = drawBehind {
    drawRoundRect(color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
}

/** Each topic's icon and colour. */
private fun topicLook(name: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> = when (name) {
    "Messages & chats" -> Icons.Rounded.ChatBubbleOutline to com.bluemob.app.ui.theme.Palette.BrandBlue
    "Calls" -> Icons.Rounded.Call to com.bluemob.app.ui.theme.Palette.BrandGreen
    "SOS & rescue" -> Icons.Rounded.Sos to com.bluemob.app.ui.theme.Palette.Danger
    "Nearby & connection" -> Icons.Rounded.Hub to com.bluemob.app.ui.theme.Palette.Violet
    "Compass & map" -> Icons.Rounded.Explore to com.bluemob.app.ui.theme.Palette.Sunset
    "Guides & Sky" -> Icons.Rounded.MenuBook to com.bluemob.app.ui.theme.Palette.Gold
    "Trip groups" -> Icons.Rounded.Groups to com.bluemob.app.ui.theme.Palette.BrandGreen
    "Games" -> Icons.Rounded.SportsEsports to com.bluemob.app.ui.theme.Palette.Violet
    "Account & backup" -> Icons.Rounded.Person to com.bluemob.app.ui.theme.Palette.BrandBlue
    else -> Icons.Rounded.PhoneAndroid to com.bluemob.app.ui.theme.Palette.Warning
}
