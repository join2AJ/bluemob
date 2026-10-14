package com.bluemob.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.bluemob.app.bridge.NewsItem
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** News from the BlueMob team: alerts, updates and tips, newest first. Readable offline once fetched. */
@Composable
fun NewsScreen(items: List<NewsItem>, seenAt: Long, onBack: () -> Unit, onSeen: () -> Unit) {
    LaunchedEffect(Unit) { onSeen() }
    val uri = LocalUriHandler.current
    val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    SubScreen("News from BlueMob", onBack) {
        if (items.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 48.dp)) {
                Text("📣", style = MaterialTheme.typography.displaySmall)
                Text("No news yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text("Alerts, updates and safety tips from the BlueMob team show up here, and as a notification.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items.forEach { n ->
            item(key = n.id) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (n.urgent) Extra.emberTint else MaterialTheme.colorScheme.surface)
                        .then(if (n.link.isNotBlank()) Modifier.clickable { runCatching { uri.openUri(n.link) } } else Modifier)
                        .padding(16.dp),
                ) {
                    Row {
                        Text((if (n.urgent) "⚠️ " else if (n.personal) "✉️ " else "") + n.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (n.at > seenAt) Text("NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text((if (n.personal) "To you from the BlueMob team · " else "") + fmt.format(Date(n.at)), style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.padding(top = 2.dp))
                    Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (n.link.isNotBlank()) Text("Open link ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
