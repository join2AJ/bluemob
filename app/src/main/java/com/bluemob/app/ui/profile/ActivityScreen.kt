package com.bluemob.app.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.activity.Activity
import com.bluemob.app.activity.ActivityEvent
import com.bluemob.app.activity.ActivityRange
import com.bluemob.app.activity.ActivitySummary
import com.bluemob.app.activity.ActivityType
import com.bluemob.app.activity.AppArea
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Colours for the charts: distinct in both themes, never red/green alone for meaning. */
private object ChartColors {
    val sent = Color(0xFF1C7FD6)
    val received = Color(0xFF12A97A)
    val missed = Color(0xFFE5484D)
    val dialled = Color(0xFFF59E0B)
    val areas = listOf(Color(0xFF1C7FD6), Color(0xFF12A97A), Color(0xFFF59E0B), Color(0xFF7B4DDB), Color(0xFFE5484D),
        Color(0xFF0EA5B7), Color(0xFFEC4899), Color(0xFF84CC16), Color(0xFF8B5E3C), Color(0xFF64748B))
}

/** Your activity: how you used BlueMob over a period, compared with the period before, in charts. */
@Composable
fun ActivityScreen(
    events: List<ActivityEvent>, onBack: () -> Unit, now: Long = System.currentTimeMillis(),
    /** Names for people (node ID → name), for "who you talk to most". */
    names: Map<String, String> = emptyMap(),
    /** Seconds in each part of the app by day ("yyyy-MM-dd"). */
    usage: Map<String, Map<AppArea, Long>> = emptyMap(),
    /** Guides read, quizzes passed, reading streak. */
    learning: Triple<Int, Int, Int> = Triple(0, 0, 0),
) {
    var range by rememberSaveable { mutableStateOf(ActivityRange.WEEK) }
    val s = remember(events, range) { Activity.summarize(events, range, now) }
    // The same length of time just before, to say "up 20%".
    val before = remember(events, range) { range.days?.let { Activity.summarize(events, range, now - it * 86_400_000L) } }
    val ins = remember(events, range) { com.bluemob.app.activity.Insights.of(events, range, now) }
    SubScreen("Your activity", onBack) {
        item { RangePicker(range) { range = it } }
        item { HeroCard(s, before, range) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("💬", "Messages", s.messages(), before?.messages(), s.bins.map { it.msgs() }, ChartColors.sent, Modifier.weight(1f))
                Kpi("📞", "Calls", s.calls(), before?.calls(), s.bins.map { it.calls() }, ChartColors.dialled, Modifier.weight(1f))
                Kpi("👥", "People", s.people, before?.people, null, ChartColors.received, Modifier.weight(1f))
            }
        }
        item { GroupLabel("Messages") }
        item {
            BarChart(s, listOf(ActivityType.MSG_SENT to ChartColors.sent, ActivityType.MSG_RECEIVED to ChartColors.received))
        }
        if (ins.delivery.isNotEmpty() || ins.waiting > 0) item { DeliveryDonut(ins) }
        item { GroupLabel("Calls") }
        item {
            BarChart(s, listOf(ActivityType.CALL_DIALLED to ChartColors.dialled, ActivityType.CALL_RECEIVED to ChartColors.received, ActivityType.CALL_MISSED to ChartColors.missed),
                footer = "Talk time ${talk(s.talkSeconds)}" + (ins.answerRate?.let { " · answered ${(it * 100).toInt()}%" } ?: ""))
        }
        item { GroupLabel("When you're active") }
        item { Heatmap(events, range, now) }
        item { GroupLabel("Time in BlueMob") }
        item { UsageDonut(usage, range, now) }
        item { GroupLabel("Insights") }
        item { InsightCards(ins, names) }
        item { GroupLabel("Safety & learning") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("🆘", "SOS sent", s.count(ActivityType.SOS_SENT), Modifier.weight(1f))
                MiniStat("📡", "SOS received", s.count(ActivityType.SOS_RECEIVED), Modifier.weight(1f))
                MiniStat("🏃", "Rescues joined", s.count(ActivityType.HELPED), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("📖", "Guides read", learning.first, Modifier.weight(1f))
                MiniStat("🧠", "Quizzes passed", learning.second, Modifier.weight(1f))
                MiniStat("🔥", "days in a row", learning.third, Modifier.weight(1f))
            }
        }
        item {
            Text("Counted from what's on this phone. Deleting chats or calls removes them here too.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

private fun ActivitySummary.messages() = count(ActivityType.MSG_SENT) + count(ActivityType.MSG_RECEIVED)
private fun ActivitySummary.calls() = count(ActivityType.CALL_DIALLED) + count(ActivityType.CALL_RECEIVED) + count(ActivityType.CALL_MISSED)
private fun com.bluemob.app.activity.ActivityBin.msgs() = (counts[ActivityType.MSG_SENT] ?: 0) + (counts[ActivityType.MSG_RECEIVED] ?: 0)
private fun com.bluemob.app.activity.ActivityBin.calls() =
    (counts[ActivityType.CALL_DIALLED] ?: 0) + (counts[ActivityType.CALL_RECEIVED] ?: 0) + (counts[ActivityType.CALL_MISSED] ?: 0)

private fun talk(sec: Long) = when {
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60} min"
    else -> "${sec / 3600} h ${sec % 3600 / 60} min"
}

/** "▲ 20%" against the period before, or "new" when there was nothing before. */
private fun delta(now: Int, before: Int?): Pair<String, Boolean>? = when {
    before == null -> null
    before == 0 && now == 0 -> null
    before == 0 -> "new" to true
    else -> {
        val pct = (now - before) * 100 / before
        (if (pct >= 0) "▲ $pct%" else "▼ ${-pct}%") to (pct >= 0)
    }
}

@Composable
private fun RangePicker(range: ActivityRange, onRange: (ActivityRange) -> Unit) {
    Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Extra.sand).padding(4.dp)) {
        ActivityRange.entries.forEach { r ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (r == range) MaterialTheme.colorScheme.surface else Color.Transparent)
                .clickable { onRange(r) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(r.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (r == range) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** The headline: everything you did in the period, and how it compares. */
@Composable
private fun HeroCard(s: ActivitySummary, before: ActivitySummary?, range: ActivityRange) {
    val total = s.messages() + s.calls()
    val d = delta(total, before?.let { it.messages() + it.calls() })
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1C7FD6), Color(0xFF0E9468)))).padding(18.dp)) {
        Text(when (range) { ActivityRange.WEEK -> "Last 7 days"; ActivityRange.MONTH -> "Last 30 days"; ActivityRange.QUARTER -> "Last 90 days"; ActivityRange.ALL -> "Since BlueMob started" },
            style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("  messages & calls", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
        }
        d?.let { (t, up) ->
            Text("$t vs the ${range.label.lowercase()} before", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (up) 0.22f else 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Text("Talk time ${talk(s.talkSeconds)} · ${s.people} ${if (s.people == 1) "person" else "people"}", style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 8.dp))
    }
}

/** A number with its change and a small trend line. */
@Composable
private fun Kpi(emoji: String, label: String, value: Int, before: Int?, trend: List<Int>?, color: Color, modifier: Modifier) {
    val d = delta(value, before)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$label: $value" + (d?.first?.let { ", $it" } ?: "") }) {
        Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = Extra.ink2, maxLines = 1)
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        d?.let { (t, up) -> Text(t, style = MaterialTheme.typography.labelSmall, color = if (up) ChartColors.received else ChartColors.missed, fontWeight = FontWeight.Bold) }
        if (trend != null && trend.size > 1 && trend.any { it > 0 }) Canvas(Modifier.padding(top = 6.dp).fillMaxWidth().height(24.dp)) {
            val max = trend.max().coerceAtLeast(1)
            val step = size.width / (trend.size - 1)
            val path = Path()
            trend.forEachIndexed { i, v -> val p = Offset(i * step, size.height - size.height * v / max); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}

/** Stacked bars per day (or week) with gridlines, labels, today highlighted, and tap for the numbers. */
@Composable
private fun BarChart(s: ActivitySummary, series: List<Pair<ActivityType, Color>>, footer: String? = null) {
    val grow = rememberRegrow(s.bins.size, s.bins.sumOf { b -> series.sumOf { b.counts[it.first] ?: 0 } })
    val max = s.bins.maxOfOrNull { b -> series.sumOf { b.counts[it.first] ?: 0 } }?.coerceAtLeast(1) ?: 1
    var picked by remember(s.bins.size) { mutableStateOf<Int?>(null) }
    val grid = Extra.line
    val day = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val date = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        // Totals as the legend.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEach { (t, c) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(" ${t.label}", style = MaterialTheme.typography.labelMedium, color = Extra.ink2)
                    }
                    Text("${s.count(t)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        val pickedBin = picked?.let { s.bins.getOrNull(it) }
        Text(pickedBin?.let { b -> (if (s.binDays == 1) date.format(Date(b.start)) else "Week of " + date.format(Date(b.start))) + ": " +
            series.joinToString(" · ") { (t, _) -> "${b.counts[t] ?: 0} ${t.label.lowercase()}" } } ?: "Tap a bar for details",
            style = MaterialTheme.typography.labelMedium, color = if (pickedBin != null) MaterialTheme.colorScheme.primary else Extra.ink3, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 8.dp)) {
            Column(Modifier.height(140.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("$max", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Text("${max / 2}", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Text("0", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
            }
            Canvas(Modifier.weight(1f).height(140.dp).pointerInput(s.bins.size) {
                detectTapGestures { o -> val n = s.bins.size.coerceAtLeast(1); val i = (o.x / (size.width / n)).toInt().coerceIn(0, n - 1); picked = if (picked == i) null else i }
            }) {
                val n = s.bins.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = (slot * 0.64f).coerceIn(2f, 46f)
                listOf(0f, 0.5f, 1f).forEach { f -> drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), if (f == 1f) 2f else 1f) }
                s.bins.forEachIndexed { i, b ->
                    var top = size.height
                    val dim = picked != null && picked != i
                    series.forEach { (t, color) ->
                        val v = b.counts[t] ?: 0
                        if (v > 0) {
                            val h = size.height * v / max * grow.value
                            top -= h
                            drawRoundRect(color.copy(alpha = if (dim) 0.35f else 1f), Offset(i * slot + (slot - barW) / 2, top), Size(barW, h), CornerRadius(barW / 3))
                        }
                    }
                }
            }
        }
        // Day names under a week, otherwise the first date and today.
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp)) {
            if (s.bins.size <= 7) s.bins.forEachIndexed { i, b ->
                Text(if (i == s.bins.lastIndex) "Today" else day.format(Date(b.start)), style = MaterialTheme.typography.labelSmall,
                    color = if (i == s.bins.lastIndex) MaterialTheme.colorScheme.primary else Extra.ink3, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else {
                Text(s.bins.firstOrNull()?.let { date.format(Date(it.start)) } ?: "", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                Spacer(Modifier.weight(1f))
                Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 10.dp))
    }
}

/** A ring split into parts, with something in the middle. */
@Composable
private fun Donut(parts: List<Pair<Float, Color>>, modifier: Modifier, center: @Composable () -> Unit) {
    val sweep = rememberRegrow(parts.size, parts.sumOf { it.first.toDouble() })
    val track = Extra.sand
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            var start = -90f
            parts.forEach { (v, c) ->
                val a = 360f * v / total * sweep.value
                if (a > 0.5f) drawArc(c, start + 1f, (a - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += a
            }
        }
        center()
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelMedium, color = Extra.ink2)
    }
}

/** How sent messages got there: phone to phone, carried by other phones, or over the internet. */
@Composable
private fun DeliveryDonut(i: com.bluemob.app.activity.Insights) {
    val parts = listOf(
        Triple("nearby", "Phone to phone", ChartColors.received),
        Triple("phones", "Carried by other phones", ChartColors.dialled),
        Triple("internet", "Over the internet", ChartColors.sent),
    ).map { (k, l, c) -> Triple(l, i.delivery[k] ?: 0, c) }.filter { it.second > 0 } + listOfNotNull(
        if (i.waiting > 0) Triple("Not delivered yet", i.waiting, Extra.ink3) else null)
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Donut(parts.map { it.second.toFloat() to it.third }, Modifier.size(132.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("sent", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
            }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text("How your messages travelled", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            parts.forEach { (l, n, c) -> Legend(c, l, "${n * 100 / total}%") }
        }
    }
}

/** A 7 × 24 grid: darker where you send and receive more, by weekday and hour. */
@Composable
private fun Heatmap(events: List<ActivityEvent>, range: ActivityRange, now: Long) {
    val grid = remember(events, range) {
        val from = range.days?.let { Activity.dayStart(now) - (it - 1) * 86_400_000L } ?: Long.MIN_VALUE
        val g = Array(7) { IntArray(8) } // 8 slots of 3 hours
        val cal = Calendar.getInstance()
        events.filter { it.at in from..now }.forEach { e ->
            cal.timeInMillis = e.at
            val d = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
            g[d][cal.get(Calendar.HOUR_OF_DAY) / 3]++
        }
        g
    }
    val max = grid.maxOf { it.max() }.coerceAtLeast(1)
    val base = ChartColors.sent
    val empty = Extra.sand
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        if (max <= 1 && grid.sumOf { it.sum() } == 0) {
            Text("Use BlueMob for a few days and patterns show up here.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
            return@Column
        }
        days.forEachIndexed { d, name ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.width(34.dp))
                grid[d].forEach { v ->
                    Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(18.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (v == 0) empty else base.copy(alpha = 0.25f + 0.75f * v / max)))
                }
            }
        }
        Row(Modifier.padding(start = 34.dp, top = 4.dp)) {
            listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Extra.ink3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Time per part of the app over the range, as a ring with a legend. */
@Composable
private fun UsageDonut(usage: Map<String, Map<AppArea, Long>>, range: ActivityRange, now: Long) {
    val days = range.days ?: usage.size.coerceAtLeast(1)
    val keys = (0 until days).map { com.bluemob.app.guide.Streak.dayKey(now - it * 86_400_000L) }
    val inRange = if (range.days == null) usage.values.toList() else keys.mapNotNull { usage[it] }
    val byArea = inRange.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
    val total = byArea.sumOf { it.value }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        if (total == 0L) {
            Text("Nothing counted yet. Time is counted only while BlueMob is on screen, from this version on.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(byArea.mapIndexed { i, e -> e.value.toFloat() to ChartColors.areas[i % ChartColors.areas.size] }, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(com.bluemob.app.activity.Usage.words(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("~" + com.bluemob.app.activity.Usage.words(total / days) + "/day", style = MaterialTheme.typography.labelSmall, color = Extra.ink3)
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                byArea.take(6).forEachIndexed { i, (a, sec) -> Legend(ChartColors.areas[i % ChartColors.areas.size], "${a.emoji} ${a.label}", "${sec * 100 / total}%") }
            }
        }
    }
}

private val weekdays = listOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** Patterns as colourful cards you can swipe through. */
@Composable
private fun InsightCards(i: com.bluemob.app.activity.Insights, names: Map<String, String>) {
    val cards = buildList {
        if (i.topPeople.isNotEmpty()) add(Triple("👥", "Your people", i.topPeople.joinToString { (id, n) -> "${names[id] ?: "someone"} ($n)" }))
        i.busiestHour?.let { h -> add(Triple("⏰", "Busiest time", "around ${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}")) }
        i.busiestWeekday?.let { add(Triple("📅", "Busiest day", weekdays.getOrElse(it) { "" })) }
        add(Triple("💬", "Per day", "${"%.1f".format(i.messagesPerDay)} messages"))
        i.avgCallSeconds?.let { add(Triple("⏱", "Average call", talk(it))) }
    }
    val tints = listOf(Color(0xFF1C7FD6), Color(0xFF7B4DDB), Color(0xFF0E9468), Color(0xFFF59E0B), Color(0xFFE5484D))
    if (cards.size <= 1) Text("Use BlueMob for a few days and patterns show up here.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEachIndexed { n, (e, title, value) ->
            val c = tints[n % tints.size]
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.14f)).padding(14.dp)) {
                Text(e, fontSize = 24.sp)
                Text(title, style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 3)
            }
        }
    }
}

@Composable
private fun MiniStat(emoji: String, label: String, n: Int, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(emoji, fontSize = 20.sp)
        Text("$n", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Extra.ink2, maxLines = 2)
    }
}

/** 1 at first (charts are drawn fully straight away), then grows again from 0 whenever [keys] change. */
@Composable
private fun rememberRegrow(vararg keys: Any): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val a = remember { Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(*keys) {
        if (first.value) { first.value = false; return@LaunchedEffect }
        a.snapTo(0f); a.animateTo(1f, tween(600))
    }
    return a
}
