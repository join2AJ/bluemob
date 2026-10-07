package com.bluemob.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.guide.Article
import com.bluemob.app.guide.GuideCategory
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.InsetDivider
import com.bluemob.app.ui.components.LargeTitle
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Space

private enum class GuideSort(val label: String) { RELEVANT("Suggested"), AZ("A–Z"), QUICK("Quickest"), UNREAD("Not read yet"), NEW("New first") }

@Composable
fun GuideScreen(bookmarks: Set<String>, contentPadding: PaddingValues, onOpen: (String) -> Unit, onSos: () -> Unit, onMore: () -> Unit = {}, onAskSky: () -> Unit = {}) {
    // Recompose when packs are downloaded or removed, and as guides are read.
    val packs by com.bluemob.app.guide.GuidePacks.installed.collectAsStateWithLifecycle()
    val read by com.bluemob.app.guide.GuidePacks.read.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<GuideCategory?>(null) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var newOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(GuideSort.RELEVANT) }
    val all = GuideContent.all()
    val isNew = { id: String -> com.bluemob.app.guide.GuidePacks.isNew(id) }
    val dash = com.bluemob.app.guide.GuideDashboard.of(all, bookmarks, read, isNew, packs.size)
    val q = query.trim().lowercase()
    val list = all.filter { a ->
        (!savedOnly || a.id in bookmarks) && (!newOnly || isNew(a.id)) && (category == null || a.category == category) &&
            (q.isEmpty() || (a.title + " " + a.intro + " " + a.steps.joinToString(" ")).lowercase().contains(q))
    }.let { l ->
        when (sort) {
            GuideSort.RELEVANT -> l.sortedByDescending { isNew(it.id) }
            GuideSort.AZ -> l.sortedBy { it.title }
            GuideSort.QUICK -> l.sortedBy { it.minutes }
            GuideSort.UNREAD -> l.sortedBy { it.id in read }
            GuideSort.NEW -> l.sortedByDescending { com.bluemob.app.guide.GuidePacks.packOf(it.id)?.let { p -> com.bluemob.app.guide.GuidePacks.installedAt[p] } ?: 0L }
        }
    }
    val filtering = q.isNotEmpty() || category != null || savedOnly || newOnly

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Survival guide", over = "${dash.total} guides · stored on your phone, work offline") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("SOS", "Nearby now", Extra.rose, Color.White, Icons.Outlined.WarningAmber, Modifier.weight(1f), onSos)
                QuickCard("I'm lost", "Stop, think, plan", Extra.emberTint, MaterialTheme.colorScheme.onSurface, Icons.Outlined.Explore, Modifier.weight(1f)) { onOpen("lost") }
            }
        }
        // Sky lives here now: ask in your own words, it answers from these guides, offline.
        item {
            androidx.compose.material3.Surface(onClick = onAskSky, shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.bluemob.app.bot.SkyBot.AVATAR, fontSize = 26.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Ask Sky", style = MaterialTheme.typography.titleSmall)
                        Text("\"No water, what do I do?\" · answers from these guides, offline", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("›", color = Extra.ink3, fontSize = 20.sp)
                }
            }
        }
        // Dashboard: how much is here, what's new, what you've read.
        item {
            Group(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${dash.total}", "Guides", Modifier.weight(1f)) { category = null; savedOnly = false; newOnly = false }
                        StatTile("${dash.downloaded}", if (dash.packs == 1) "From 1 pack" else "From ${dash.packs} packs", Modifier.weight(1f), onClick = onMore)
                        StatTile("${dash.newCount}", "New today", Modifier.weight(1f), highlight = dash.newCount > 0) { newOnly = dash.newCount > 0; savedOnly = false; category = null }
                        StatTile("${dash.saved}", "Saved", Modifier.weight(1f)) { savedOnly = true; newOnly = false; category = null }
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Read ${dash.readCount} of ${dash.total}", style = MaterialTheme.typography.labelMedium, color = Extra.ink2, modifier = Modifier.width(110.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { dash.readShare }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = Extra.sand)
                    }
                }
            }
        }
        if (!filtering) {
            dash.lastRead?.let { id -> all.firstOrNull { it.id == id } }?.let { a ->
                item { GroupLabel("Continue reading") }
                item { GuideRow(a, a.id in bookmarks, true, isNew(a.id)) { onOpen(a.id) } }
            }
            com.bluemob.app.guide.GuideDashboard.ofTheDay(all)?.let { a ->
                item { GroupLabel("Guide of the day") }
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.large).clickable { onOpen(a.id) }) {
                        GuideArt(a.category, Modifier.fillMaxSize(), articleId = a.id)
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))).padding(14.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.category.label} · ${a.minutes} min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
        item {
            androidx.compose.material3.Surface(onClick = onMore, shape = MaterialTheme.shapes.large, color = Extra.pineTint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⬇️", fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Download more guides", style = MaterialTheme.typography.titleSmall)
                        Text(if (packs.isEmpty()) "Mountains, monsoon, heat, wildlife… for your trip" else "${packs.size} pack${if (packs.size == 1) "" else "s"} on this phone · get more",
                            style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                }
            }
        }
        if (!filtering) {
            item { GroupLabel("Topics") }
            item {
                // Three across, each with how many guides it has.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideCategory.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { c ->
                                Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(c.color.copy(alpha = 0.12f)).clickable { category = c; savedOnly = false; newOnly = false }.padding(12.dp)) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.color), contentAlignment = Alignment.Center) {
                                        Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Text(c.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
                                    Text("${dash.byCategory[c] ?: 0} guides", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(MaterialTheme.shapes.small).background(Extra.sand).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = Extra.ink3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search bleeding, water, fire…", color = Extra.ink3, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", !filtering) { category = null; savedOnly = false; newOnly = false; query = "" } }
                if (dash.newCount > 0) item { Chip("New ${dash.newCount}", newOnly) { newOnly = !newOnly; savedOnly = false } }
                item { Chip("★ Saved" + if (dash.saved > 0) " ${dash.saved}" else "", savedOnly) { savedOnly = !savedOnly; newOnly = false; category = null } }
                GuideCategory.entries.forEach { c -> item { Chip(c.label, category == c) { category = if (category == c) null else c; savedOnly = false } } }
            }
        }
        item {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (savedOnly) "Saved" else if (newOnly) "New" else category?.label ?: if (q.isNotEmpty()) "Results" else "All guides",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp).weight(1f))
                var open by remember { mutableStateOf(false) }
                Box {
                    Chip("⇅ ${sort.label}", false) { open = true }
                    androidx.compose.material3.DropdownMenu(open, { open = false }) {
                        GuideSort.entries.forEach { s -> androidx.compose.material3.DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; open = false }) }
                    }
                }
            }
        }
        itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
            if (i > 0) InsetDivider(60.dp)
            GuideRow(a, a.id in bookmarks, a.id in read, isNew(a.id)) { onOpen(a.id) }
        }
        if (list.isEmpty()) item { Text("Nothing found. Try a simpler word.", color = Extra.ink2, modifier = Modifier.padding(24.dp)) }
        item {
            Text("General guidance, not a substitute for trained medical help. Reach emergency services whenever you can.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun GuideRow(a: Article, saved: Boolean, read: Boolean, new: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(a.category.color), contentAlignment = Alignment.Center) {
            Icon(a.category.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.intro, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Downloaded in the last day: a small "NEW" tab under it.
            if (new) Text("NEW", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(5.dp)).background(Extra.rose).padding(horizontal = 6.dp, vertical = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (saved) "★ " else "") + "${a.minutes} min", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
            if (read) Text("✓ read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(if (highlight) Extra.rose.copy(alpha = 0.12f) else Extra.sand).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (highlight) Extra.rose else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickCard(title: String, sub: String, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(MaterialTheme.shapes.medium).background(bg).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
        }
    }
}

@Composable
fun ArticleScreen(article: Article, saved: Boolean, onBack: () -> Unit, onToggleSaved: () -> Unit, onSos: () -> Unit, onOpen: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(article.id) { com.bluemob.app.guide.GuidePacks.markRead(article.id) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var done by rememberSaveable(article.id) { mutableStateOf(setOf<Int>()) }
    var stepMode by rememberSaveable { mutableStateOf(false) }
    val speaker = rememberSpeaker()
    val body = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) }
    if (stepMode) { StepByStep(article, speaker, scale, onClose = { stepMode = false; speaker.stop() }); return }

    SubScreen(article.title, onBack, actions = {
        IconButton(onClick = onToggleSaved) { Text(if (saved) "★" else "☆", fontSize = 22.sp, color = if (saved) Extra.ember else MaterialTheme.colorScheme.onSurface) }
    }) {
        item { GuideArt(article.category, Modifier.padding(top = 8.dp).fillMaxWidth().height(160.dp), articleId = article.id) }
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(article.category.label, article.category.color, Color.White)
                    Text("  ${article.minutes} min · ${article.steps.size} steps", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                    if (com.bluemob.app.guide.GuidePacks.isNew(article.id)) Text("  NEW", style = MaterialTheme.typography.labelSmall, color = Extra.rose)
                }
                Text(article.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
                Text(article.intro, style = body, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
            }
        }
        // Tools: step through it, listen to it, bigger text, share it.
        item {
            LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("▶ Step by step", true) { stepMode = true } }
                if (speaker.available) item {
                    Chip(if (speaker.speaking) "⏹ Stop" else "🔊 Read aloud", speaker.speaking) {
                        if (speaker.speaking) speaker.stop() else speaker.speak(spoken(article))
                    }
                }
                item { Chip("A−", false) { scale = (scale - 0.15f).coerceAtLeast(0.85f) } }
                item { Chip("A+", false) { scale = (scale + 0.15f).coerceAtMost(1.6f) } }
                item {
                    Chip("↗ Share", false) {
                        val text = article.title + "\n\n" + article.intro + "\n\n" + article.steps.mapIndexed { i, st -> "${i + 1}. $st" }.joinToString("\n") +
                            (if (article.avoid.isNotEmpty()) "\n\nAvoid:\n" + article.avoid.joinToString("\n") { "✕ $it" } else "") + "\n\n(from BlueMob's offline survival guide)"
                        runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Share guide")) }
                    }
                }
            }
        }
        if (article.id == "cpr") item { CprBeat() }
        item {
            Group(Modifier.padding(top = 4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${done.size} of ${article.steps.size} done · tap a step to tick it", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                    article.steps.forEachIndexed { i, s ->
                        val ticked = i in done
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { done = if (ticked) done - i else done + i }) {
                            Box(Modifier.size(28.dp).background(if (ticked) MaterialTheme.colorScheme.primary else Extra.pineTint, CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (ticked) "✓" else "${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (ticked) Color.White else MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = body, modifier = Modifier.weight(1f), color = if (ticked) Extra.ink3 else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (article.avoid.isNotEmpty()) {
            item { GroupLabel("Avoid") }
            item {
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        article.avoid.forEach { d -> Row { Text("✕  ", color = Extra.rose); Text(d, style = body) } }
                    }
                }
            }
        }
        // More from the same topic.
        val related = GuideContent.all().filter { it.category == article.category && it.id != article.id }.take(3)
        if (related.isNotEmpty()) {
            item { GroupLabel("More on ${article.category.label.lowercase()}") }
            item {
                Column { related.forEach { r -> Text("${r.title} · ${r.minutes} min", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(r.id) }.padding(vertical = 8.dp)) } }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.emberTint).padding(14.dp)) {
                Text("General guidance, not medical advice. Reach emergency services when you can. BlueMob's SOS reaches everyone nearby.", style = MaterialTheme.typography.bodyMedium)
                Text("Send SOS", style = MaterialTheme.typography.labelLarge, color = Extra.rose,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSos).border(1.dp, Extra.rose, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

private fun spoken(a: Article) = a.title + ". " + a.intro + ". " + a.steps.mapIndexed { i, s -> "Step ${i + 1}. $s" }.joinToString(" ") +
    if (a.avoid.isNotEmpty()) " Avoid: " + a.avoid.joinToString(". ") else ""

/** One step at a time in big text, for when your hands are busy: Next and Back, and it can read each step out. */
@Composable
private fun StepByStep(a: Article, speaker: Speaker, scale: Float, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    var i by rememberSaveable { mutableStateOf(0) }
    var auto by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(i, auto) { if (auto) speaker.speak("Step ${i + 1}. ${a.steps[i]}") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Close", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp))
        }
        androidx.compose.material3.LinearProgressIndicator(progress = { (i + 1f) / a.steps.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        GuideArt(a.category, Modifier.fillMaxWidth().height(150.dp), articleId = a.id)
        Text("Step ${i + 1} of ${a.steps.size}", style = MaterialTheme.typography.labelLarge, color = Extra.ink3, modifier = Modifier.padding(top = 18.dp))
        Text(a.steps[i], style = MaterialTheme.typography.headlineSmall.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) },
            modifier = Modifier.padding(top = 8.dp).weight(1f))
        if (speaker.available) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { auto = !auto; if (!auto) speaker.stop() }.padding(vertical = 8.dp)) {
            androidx.compose.material3.Switch(auto, { auto = it; if (!it) speaker.stop() })
            Text("  Read each step aloud", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { if (i > 0) i-- }, enabled = i > 0, modifier = Modifier.weight(1f).height(56.dp)) { Text("Back") }
            androidx.compose.material3.Button(onClick = { if (i < a.steps.lastIndex) i++ else onClose() }, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(if (i < a.steps.lastIndex) "Next" else "Done")
            }
        }
    }
}

/** For CPR: a beat at 110 a minute to push to, with a count of compressions (30, then 2 breaths). */
@Composable
private fun CprBeat() {
    var on by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80) }.getOrNull()
        try {
            count = 0
            while (true) {
                tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 60)
                count = count % 30 + 1
                kotlinx.coroutines.delay(545) // 110 a minute
            }
        } finally { tone?.release() }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(MaterialTheme.shapes.medium).background(Extra.rose.copy(alpha = 0.12f)).clickable { on = !on }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "⏹" else "🫀", fontSize = 26.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (on) "Push on each beat · $count of 30" else "Compression beat (110 a minute)", style = MaterialTheme.typography.titleSmall)
            Text(if (on) "After 30, give 2 breaths if you're trained, then carry on. Tap to stop." else "Tap to start a beat to push to: hard and fast, 5–6 cm deep.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
        }
    }
}

/** Reading guides aloud with the phone's own text-to-speech (works offline with the voices most phones have). */
class Speaker(private val tts: android.speech.tts.TextToSpeech?) {
    fun shutdown() { runCatching { tts?.stop(); tts?.shutdown() } }
    var available by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    fun speak(text: String) {
        val t = tts ?: return
        speaking = true
        t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "guide")
    }
    fun stop() { tts?.stop(); speaking = false }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val holder = remember { arrayOfNulls<Speaker>(1) }
    val speaker = remember {
        if (preview) Speaker(null) else {
            var created: android.speech.tts.TextToSpeech? = null
            created = runCatching {
                android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                    holder[0]?.available = status == android.speech.tts.TextToSpeech.SUCCESS
                }
            }.getOrNull()
            Speaker(created).also { sp ->
                runCatching {
                    created?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { sp.speaking = false }
                        @Deprecated("Deprecated in Java") override fun onError(id: String?) { sp.speaking = false }
                    })
                }
            }
        }
    }
    holder[0] = speaker
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    return speaker
}
