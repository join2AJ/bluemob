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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun GuideScreen(bookmarks: Set<String>, contentPadding: PaddingValues, onOpen: (String) -> Unit, onSos: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<GuideCategory?>(null) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val list = GuideContent.articles.filter { a ->
        (!savedOnly || a.id in bookmarks) && (category == null || a.category == category) &&
            (q.isEmpty() || (a.title + " " + a.intro + " " + a.steps.joinToString(" ")).lowercase().contains(q))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Survival guide", over = "${GuideContent.articles.size} guides · stored on your phone") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("SOS", "Nearby now", Extra.rose, Color.White, Icons.Outlined.WarningAmber, Modifier.weight(1f), onSos)
                QuickCard("I'm lost", "Stop, think, plan", Extra.emberTint, MaterialTheme.colorScheme.onSurface, Icons.Outlined.Explore, Modifier.weight(1f)) { onOpen("lost") }
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
            LazyRow(contentPadding = PaddingValues(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", category == null && !savedOnly) { category = null; savedOnly = false } }
                item { Chip("★ Saved" + if (bookmarks.isNotEmpty()) " ${bookmarks.size}" else "", savedOnly) { savedOnly = !savedOnly; category = null } }
                GuideCategory.entries.forEach { c -> item { Chip(c.label, category == c) { category = if (category == c) null else c; savedOnly = false } } }
            }
        }
        item {
            Text(if (savedOnly) "Saved" else category?.label ?: if (q.isNotEmpty()) "Results" else "All guides",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
            if (i > 0) InsetDivider(60.dp)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(a.category.color), contentAlignment = Alignment.Center) {
                    Icon(a.category.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.intro, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text((if (a.id in bookmarks) "★ " else "") + "${a.minutes} min", style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
            }
        }
        if (list.isEmpty()) item { Text("Nothing found. Try a simpler word.", color = Extra.ink2, modifier = Modifier.padding(24.dp)) }
        item {
            Text("General guidance, not a substitute for trained medical help. Reach emergency services whenever you can.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 20.dp))
        }
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
fun ArticleScreen(article: Article, saved: Boolean, onBack: () -> Unit, onToggleSaved: () -> Unit, onSos: () -> Unit) {
    SubScreen(article.title, onBack, actions = {
        IconButton(onClick = onToggleSaved) { Text(if (saved) "★" else "☆", fontSize = 22.sp, color = if (saved) Extra.ember else MaterialTheme.colorScheme.onSurface) }
    }) {
        item {
            Column(Modifier.padding(top = 10.dp)) {
                Tag(article.category.label, article.category.color, Color.White)
                Text(article.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
                Text(article.intro, style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Group(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    article.steps.forEachIndexed { i, s ->
                        Row {
                            Box(Modifier.size(28.dp).background(Extra.pineTint, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                        article.avoid.forEach { d -> Row { Text("✕  ", color = Extra.rose); Text(d, style = MaterialTheme.typography.bodyLarge) } }
                    }
                }
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
