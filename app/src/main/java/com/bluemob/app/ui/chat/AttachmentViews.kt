package com.bluemob.app.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.data.MessageEntity
import com.bluemob.app.files.AttKind
import com.bluemob.app.files.AttState
import com.bluemob.app.files.Attachment
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What chats need to show and send photos, documents and voice notes. */
class ChatFiles(
    val progress: Map<String, Float> = emptyMap(),
    /** The voice note playing (file ID) and how far through, 0..1. */
    val playing: Pair<String, Float>? = null,
    /** Length of the voice note being recorded, or null. */
    val recordingMs: Long? = null,
    val thumbnail: suspend (MessageEntity) -> Bitmap? = { null },
    val onOpen: (MessageEntity) -> Unit = {},
    val onPlay: (MessageEntity) -> Unit = {},
    /** "photo", "video" or "doc". */
    val onAttach: (String) -> Unit = {},
    val onRecordStart: () -> Unit = {},
    /** True: send the recording. False: throw it away. */
    val onRecordStop: (Boolean) -> Unit = {},
    val onMedia: () -> Unit = {},
)

/** A short label for chat lists and notifications, e.g. "📷 Photo". */
fun MessageEntity.preview(): String = Attachment.fromJson(att)?.let { "${it.kind.emoji} ${if (it.kind == AttKind.DOC) it.name else it.kind.label}" } ?: text

/** The inside of a chat bubble that carries a file. */
@Composable
fun AttachmentContent(m: MessageEntity, att: Attachment, name: String, files: ChatFiles, onColor: Color) {
    val progress = files.progress[att.fid]
    val ready = m.attState == AttState.DONE || (m.fromMe && m.attPath != null)
    val status = when {
        progress != null -> (if (m.fromMe) "Sending " else "Receiving ") + "${(progress * 100).toInt()}%"
        m.fromMe && m.attState == AttState.DONE -> "Delivered"
        m.fromMe && m.attState == AttState.SENDING -> "Sending…"
        m.fromMe -> "Goes the next time you're in range of $name"
        m.attState != AttState.DONE -> "Arrives when you're in range of $name"
        else -> null
    }
    Column(Modifier.width(240.dp)) {
        when (att.kind) {
            AttKind.IMAGE -> {
                val thumb = rememberLoaded(m.attPath, m.attState) { if (ready) files.thumbnail(m) else null }
                Box(Modifier.fillMaxWidth().aspectRatio(thumb?.let { it.width.toFloat() / it.height }?.coerceIn(0.6f, 1.8f) ?: 1.33f)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.12f)).clickable(enabled = ready) { files.onOpen(m) },
                    contentAlignment = Alignment.Center) {
                    thumb?.let { Image(it.asImageBitmap(), att.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        ?: Text("📷", fontSize = 36.sp)
                }
            }
            AttKind.AUDIO -> {
                val playing = files.playing?.takeIf { it.first == att.fid }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = ready) { files.onPlay(m) }.padding(vertical = 4.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(onColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text(if (playing != null) "■" else "▶", color = onColor, fontSize = 16.sp)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        LinearProgressIndicator(progress = { playing?.second ?: 0f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = onColor, trackColor = onColor.copy(alpha = 0.25f))
                        Text("🎤 " + Attachment.durationText(att.durationMs), color = onColor.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = ready) { files.onOpen(m) }.padding(vertical = 4.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(onColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(if (att.kind == AttKind.VIDEO) "🎬" else docEmoji(att.name), fontSize = 22.sp)
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(att.name, color = onColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Attachment.sizeText(att.size) + " · " + att.name.substringAfterLast('.', "file").uppercase(), color = onColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = onColor, trackColor = onColor.copy(alpha = 0.25f))
        status?.let { Text(it, color = onColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}

private fun docEmoji(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "📕"; "doc", "docx", "txt", "rtf" -> "📝"; "xls", "xlsx", "csv" -> "📊"; "ppt", "pptx" -> "📽"; "zip", "rar", "7z" -> "🗜"; "apk" -> "📦"
    else -> "📄"
}

/** Mic button: hold to record a voice note, release to send, slide away (or lift off the button) to cancel. */
@Composable
fun HoldToRecord(onStart: () -> Unit, onStop: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            onStart()
            val up = waitForUpOrCancellation()
            // Released on the button: send. Dragged off or cancelled: discard.
            val inside = up != null && up.position.x in -60f..(size.width + 60f) && up.position.y in -60f..(size.height + 60f)
            onStop(inside)
            down.consume()
        }
    }, contentAlignment = Alignment.Center) { content() }
}

/** Every photo, video, document and voice note in a chat, filtered by type. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(name: String, messages: List<MessageEntity>, files: ChatFiles, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val withFiles = messages.mapNotNull { m -> Attachment.fromJson(m.att)?.let { m to it } }.sortedByDescending { it.first.createdAt }
    val shown = withFiles.filter { (_, a) ->
        when (filter) { "photo" -> a.kind == AttKind.IMAGE; "video" -> a.kind == AttKind.VIDEO; "doc" -> a.kind == AttKind.DOC; "voice" -> a.kind == AttKind.AUDIO; else -> true }
    }
    val counts = withFiles.groupingBy { it.second.kind }.eachCount()
    SubScreen("Media, docs & voice", onBack) {
        item {
            Text("Shared with $name", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf("all" to "All ${withFiles.size}", "photo" to "Photos ${counts[AttKind.IMAGE] ?: 0}", "video" to "Videos ${counts[AttKind.VIDEO] ?: 0}",
                    "doc" to "Documents ${counts[AttKind.DOC] ?: 0}", "voice" to "Voice ${counts[AttKind.AUDIO] ?: 0}")
                items(tabs) { (k, label) -> Chip(label, filter == k) { filter = k } }
            }
        }
        if (shown.isEmpty()) item {
            Text(if (withFiles.isEmpty()) "Nothing shared yet. Tap 📎 in the chat to send a photo or document, or hold 🎤 for a voice note." else "Nothing of this type yet.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 24.dp))
        }
        val photos = shown.filter { it.second.kind == AttKind.IMAGE }
        if (photos.isNotEmpty() && (filter == "photo" || filter == "all")) item {
            // A fixed-height grid inside the list: 3 across.
            val rows = (photos.size + 2) / 3
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().height((rows * 118).dp).padding(top = 12.dp), userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(photos, key = { it.second.fid }) { (m, a) ->
                    val thumb = rememberLoaded(m.attPath, m.attState) { files.thumbnail(m) }
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Extra.sand).clickable { files.onOpen(m) }, contentAlignment = Alignment.Center) {
                        thumb?.let { Image(it.asImageBitmap(), a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("📷")
                    }
                }
            }
        }
        items(shown.filter { it.second.kind != AttKind.IMAGE }, key = { it.second.fid }) { (m, a) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (a.kind == AttKind.AUDIO) files.onPlay(m) else files.onOpen(m) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Extra.sand), contentAlignment = Alignment.Center) {
                    Text(when (a.kind) { AttKind.AUDIO -> "🎤"; AttKind.VIDEO -> "🎬"; else -> docEmoji(a.name) }, fontSize = 22.sp)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(if (a.kind == AttKind.AUDIO) "Voice note · ${Attachment.durationText(a.durationMs)}" else a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (m.fromMe) "You" else name} · ${dateFormat.format(Date(m.createdAt))} · ${Attachment.sizeText(a.size)}" +
                        if (m.attState != AttState.DONE && !(m.fromMe && m.attPath != null)) " · not here yet" else "",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

/** Loads something in the background (e.g. decrypting a photo), again whenever a key changes. Null until it's ready. */
@Composable
fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T?): T? {
    var result by remember { mutableStateOf<T?>(null) }
    androidx.compose.runtime.LaunchedEffect(*keys) { result = load() }
    return result
}
