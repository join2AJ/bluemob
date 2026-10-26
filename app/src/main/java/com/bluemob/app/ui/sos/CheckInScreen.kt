package com.bluemob.app.ui.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.i18n.Text
import com.bluemob.app.safety.CheckInTimer
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.HeroCard
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The check-in timer: "I'll be back by…". If the user doesn't tap I'm back in time, BlueMob asks, waits 10 minutes,
 * then sends an SOS with their note and last position.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    due: Long, note: String, sosContacts: Int, background: Boolean,
    onBack: () -> Unit, onStart: (Long, String) -> Unit, onExtend: (Long) -> Unit, onDone: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(due) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    var draft by rememberSaveable { mutableStateOf(note) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }
        val state = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), 0, false)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Back by") },
            text = { TimePicker(state) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                val at = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, state.hour); set(Calendar.MINUTE, state.minute); set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                onStart(at, draft); picking = false
            }) { Text("Start") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
    SubScreen("Check-in timer", onBack) {
        if (due > 0L) {
            item {
                val left = due - now
                HeroCard(if (left > 0) Gradients.brand() else Gradients.danger, Modifier.padding(top = 12.dp)) {
                    Text(if (left > 0) "⏰ You'll be back by" else "⏰ It's past your time", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Text(CheckInTimer.time(due), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(if (left > 0) "in " + span(left) else "Tap I'm back, or an SOS goes out soon", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (note.isNotBlank()) Text("“$note”", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                Button(onClick = onDone, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("✓ I'm back safely") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExtend(30 * 60_000L) }, Modifier.weight(1f)) { Text("+30 min") }
                    OutlinedButton(onClick = { onExtend(3_600_000L) }, Modifier.weight(1f)) { Text("+1 hour") }
                }
            }
        } else {
            item {
                Text("Going somewhere alone? Tell BlueMob when you'll be back. If you don't check in, it sends an SOS for you, with where you were going and your last position.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
            }
            item {
                OutlinedTextField(draft, { draft = it.take(120) }, label = { Text("Where are you going? (optional)") },
                    placeholder = { Text("e.g. Summit and back by the forest trail") }, modifier = Modifier.fillMaxWidth())
            }
            item { GroupLabel("I'll be back") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 8).forEach { h -> Chip("In $h h") { onStart(System.currentTimeMillis() + h * 3_600_000L, draft) } }
                    Chip("Pick a time…") { picking = true }
                }
            }
        }
        item { GroupLabel("What happens") }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step("🔔", "15 minutes before, a reminder.")
                Step("⏰", "At the time, BlueMob asks if you're back.")
                Step("🆘", "10 minutes later, a 30-second countdown, then an SOS to people nearby and your SOS contacts.")
            }
        }
        if (sosContacts == 0) item { Warn("Add an SOS contact (You → SOS) so someone far away hears about it too.") }
        if (!background) item { Warn("Background is off: turn it on (You → Connections & power) so the timer works with BlueMob closed.") }
    }
}

@Composable
private fun Step(emoji: String, text: String) = Row {
    Text(emoji, modifier = Modifier.padding(end = 10.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Warn(text: String) = Text("⚠️ $text", style = MaterialTheme.typography.bodyMedium, color = Extra.ember, modifier = Modifier.padding(top = 12.dp))

private fun span(ms: Long): String {
    val min = (ms / 60_000L).coerceAtLeast(1)
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
