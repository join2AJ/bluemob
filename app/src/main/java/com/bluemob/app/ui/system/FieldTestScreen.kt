package com.bluemob.app.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.safety.FieldTest
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.HeroCard
import com.bluemob.app.ui.components.PulsingDot
import com.bluemob.app.ui.components.Segmented
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Palette

/**
 * Field test: run BlueMob for a few hours with a trip group, then see how many roll calls got through, how fast, the
 * battery used, and whether the phone stopped BlueMob. Each phone measures itself; share the results to compare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldTestScreen(
    session: FieldTest.Session?,
    report: FieldTest.Report,
    trips: List<ChatGroup>,
    preselect: String?,
    onStart: (tripId: String?, everyMin: Int) -> Unit,
    onStop: () -> Unit,
    onShare: (String) -> Unit,
    onShareRaw: () -> Unit,
    onNewTrip: () -> Unit,
    onBack: () -> Unit,
) {
    var trip by rememberSaveable { mutableStateOf(preselect ?: trips.firstOrNull()?.id) }
    var every by rememberSaveable { mutableStateOf(1) }
    SubScreen("Field test", onBack) {
        if (session == null) {
            item {
                HeroCard(Gradients.brand(), Modifier.padding(top = 12.dp)) {
                    Text("🧪 Will it work out there?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Run this with your group for a few hours, phones in pockets, like on the day. Each phone measures how many roll calls got through, how fast, the battery used, and whether the phone stopped BlueMob.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
                }
            }
            item { GroupLabel("Trip group to test with") }
            item {
                if (trips.isEmpty()) {
                    Column {
                        Text("No trip group yet. Without one, the test still measures battery and whether the phone stops BlueMob.",
                            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                        OutlinedButton(onClick = onNewTrip, Modifier.padding(top = 8.dp)) { Text("Make a trip group") }
                    }
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trips.forEach { g -> Chip("🧭 ${g.name} · ${g.members.size + 1}", trip == g.id) { trip = g.id } }
                    Chip("None", trip == null) { trip = null }
                }
            }
            item { GroupLabel("Roll call every") }
            item { Segmented(EVERY.map { "$it min" }, every) { every = it } }
            item {
                Button(onClick = { onStart(trip, EVERY[every]) }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Start the field test") }
            }
            item {
                Text("Start it on every phone in the group. Keep \"Reachable when closed\" on. Stop it at the end, then share the results.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
            }
            if (report.minutes > 0) {
                item { GroupLabel("Last test") }
                item { Results(report) }
                item { ShareRow(report, onShare, onShareRaw) }
            }
        } else {
            item {
                HeroCard(Gradients.brand(), Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(Color.White, 10.dp)
                        Text("Field test running", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(start = 10.dp))
                    }
                    Text("${report.minutes / 60} h ${report.minutes % 60} min so far · roll call every ${session.everyMin} min",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                }
            }
            item { GroupLabel("So far") }
            item { Results(report) }
            item { Button(onClick = onStop, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Stop the test") } }
            item { ShareRow(report, onShare, onShareRaw) }
        }
    }
}

private val EVERY = listOf(5, 10, 15)

@Composable
private fun ShareRow(r: FieldTest.Report, onShare: (String) -> Unit, onShareRaw: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { onShare(r.summary()) }, Modifier.weight(1f)) { Text("Share results") }
        OutlinedButton(onClick = onShareRaw, Modifier.weight(1f)) { Text("Share raw data") }
    }
}

@Composable
private fun Results(r: FieldTest.Report) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), "Delivered", r.deliveryPct?.let { "$it%" } ?: "–", if (r.sent == 0) "no roll calls yet" else "${r.delivered} of ${r.sent}",
                r.deliveryPct?.let { if (it >= 90) Palette.Success else if (it >= 60) Palette.Warning else Palette.Danger })
            Tile(Modifier.weight(1f), "Time to reach", r.medianMs?.let { FieldTest.Report.secs(it) } ?: "–", r.slowestMs?.let { "slowest ${FieldTest.Report.secs(it)}" } ?: "typical")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), "Battery", r.batteryPerHour?.let { "${"%.1f".format(it)}%/h" } ?: "–",
                if (r.batteryFrom != null && r.batteryTo != null) "${r.batteryFrom}% → ${r.batteryTo}%" else "measured every 5 min",
                r.batteryPerHour?.let { if (it <= 4) Palette.Success else if (it <= 8) Palette.Warning else Palette.Danger })
            Tile(Modifier.weight(1f), "Stopped by phone", "${r.stops}", if (r.stops == 0) "never: good" else "${r.stoppedMinutes} min in all",
                if (r.stops == 0) Palette.Success else Palette.Danger)
        }
        if (r.paths.isNotEmpty()) Paths(r.paths)
        RawText("📶 Phones connected: up to ${r.maxPeers}, ${"%.1f".format(r.avgPeers)} on average", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
            modifier = Modifier.padding(start = 4.dp))
        if (r.stops > 0) Text("The phone paused or closed BlueMob. Turn off battery saver for BlueMob (You → Connections & power) and test again.",
            style = MaterialTheme.typography.bodySmall, color = Extra.rose, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Tile(modifier: Modifier, label: String, value: String, note: String, accent: Color? = null) {
    Column(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
        RawText(value, style = MaterialTheme.typography.headlineSmall, color = accent ?: MaterialTheme.colorScheme.onSurface)
        Text(note, style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1)
    }
}

/** How answers travelled: one bar per path. */
@Composable
private fun Paths(paths: Map<String, Int>) {
    val order = listOf("Direct", "1 phone", "2 phones", "3+ phones", "Internet")
    val max = paths.values.max().coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text("How answers travelled", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
        order.filter { it in paths }.forEach { k ->
            val n = paths.getValue(k)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Extra.sand)) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                        .background(if (k == "Internet") Palette.BrandBlue else Palette.BrandGreen))
                }
                RawText("$n", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
