package com.bluemob.app.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bluemob.app.activity.Activity
import com.bluemob.app.activity.ActivityEvent
import com.bluemob.app.activity.ActivityRange
import com.bluemob.app.activity.ActivitySummary
import com.bluemob.app.activity.ActivityType
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Your activity: messages, calls and SOS over time, with a date filter and simple bar charts. */
@Composable
fun ActivityScreen(events: List<ActivityEvent>, onBack: () -> Unit, now: Long = System.currentTimeMillis()) {
    var range by rememberSaveable { mutableStateOf(ActivityRange.WEEK) }
    val s = remember(events, range) { Activity.summarize(events, range, now) }
    val sent = MaterialTheme.colorScheme.primary
    val received = Extra.sky
    val missed = Extra.rose
    val dialled = Extra.ember
    SubScreen("Your activity", onBack) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityRange.entries.forEach { r -> FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Messages", s.count(ActivityType.MSG_SENT) + s.count(ActivityType.MSG_RECEIVED), "💬", Modifier.weight(1f))
                Tile("Calls", s.count(ActivityType.CALL_DIALLED) + s.count(ActivityType.CALL_RECEIVED) + s.count(ActivityType.CALL_MISSED), "📞", Modifier.weight(1f))
                Tile("People", s.people, "👥", Modifier.weight(1f))
            }
        }
        item { GroupLabel("Messages") }
        item {
            ChartCard(s, listOf(ActivityType.MSG_SENT to sent, ActivityType.MSG_RECEIVED to received))
        }
        item { GroupLabel("Calls") }
        item {
            ChartCard(s, listOf(ActivityType.CALL_DIALLED to dialled, ActivityType.CALL_RECEIVED to received, ActivityType.CALL_MISSED to missed),
                footer = "Talk time ${talk(s.talkSeconds)}")
        }
        item { GroupLabel("Safety") }
        item {
            Group {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SafetyRow("🆘", "SOS you sent", s.count(ActivityType.SOS_SENT))
                    SafetyRow("📡", "SOS you received", s.count(ActivityType.SOS_RECEIVED))
                    SafetyRow("🏃", "Rescues you joined", s.count(ActivityType.HELPED))
                }
            }
        }
        item {
            Text("Counted from what's on this phone. Deleting chats or calls removes them here too.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

private fun talk(sec: Long) = when {
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60} min"
    else -> "${sec / 3600} h ${sec % 3600 / 60} min"
}

@Composable
private fun Tile(label: String, value: Int, emoji: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
    }
}

@Composable
private fun SafetyRow(emoji: String, label: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji); Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("$n", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Stacked bars, one per day (or week), with a legend of totals underneath. Bars grow in when the range changes. */
@Composable
private fun ChartCard(s: ActivitySummary, series: List<Pair<ActivityType, Color>>, footer: String? = null) {
    // Fully drawn at first; bars grow in again whenever the date filter changes.
    val grow = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(s.bins.size) }
    LaunchedEffect(s.bins.size) { if (s.bins.size != shown) { shown = s.bins.size; grow.snapTo(0f); grow.animateTo(1f, tween(500)) } }
    val max = s.bins.maxOfOrNull { b -> series.sumOf { b.counts[it.first] ?: 0 } }?.coerceAtLeast(1) ?: 1
    val grid = Extra.line
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Group {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$max", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Spacer(Modifier.weight(1f))
                Text(if (s.binDays == 1) "per day" else "per week", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
            }
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 4.dp)) {
                val n = s.bins.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = (slot * 0.62f).coerceAtLeast(2f)
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                s.bins.forEachIndexed { i, b ->
                    var top = size.height
                    series.forEach { (t, color) ->
                        val v = b.counts[t] ?: 0
                        if (v > 0) {
                            val h = size.height * v / max * grow.value
                            top -= h
                            drawRoundRect(color, Offset(i * slot + (slot - barW) / 2, top), Size(barW, h), CornerRadius(barW / 4))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(s.bins.firstOrNull()?.let { fmt.format(Date(it.start)) } ?: "", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Spacer(Modifier.weight(1f))
                Text("Today", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                series.forEach { (t, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(" ${t.label} ${s.count(t)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
