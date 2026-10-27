package com.bluemob.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bluemob.app.chat.ChatGroup
import com.bluemob.app.chat.Reach
import com.bluemob.app.chat.RollCall
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.HeroCard
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.profile.QrScanner
import com.bluemob.app.ui.profile.qrBitmap
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.safety.FieldTest

/** Start a trip group: a name, then everyone scans its code before you set off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTripScreen(onBack: () -> Unit, onCreate: (String) -> Unit, onJoin: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    SubScreen("Trip group", onBack) {
        item {
            HeroCard(Gradients.brand(), Modifier.padding(top = 12.dp)) {
                Text("🧭 Going somewhere together?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Make a trip group before you set off. Everyone scans one code, so all your phones can reach each other, even where there's no signal.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name of the trip") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        item {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Trek team", "Family at the mela", "Yatra group", "College trip").forEach { Chip(it, name == it) { name = it } }
            }
        }
        item {
            Button(onClick = { onCreate(name.ifBlank { "Trip" }) }, Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Create and show the code") }
        }
        item {
            Group(Modifier.padding(top = 24.dp)) {
                SettingRow(Icons.Outlined.QrCodeScanner, Palette.BrandBlue, "Someone already made one?", "Scan their code to join", onClick = onJoin) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3)
                }
            }
        }
        item { Steps() }
    }
}

@Composable
private fun Steps() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp).clip(MaterialTheme.shapes.large).background(Extra.sand).padding(16.dp)) {
        Text("Before you set off", style = MaterialTheme.typography.titleSmall)
        listOf(
            "1. Everyone in one place, BlueMob open, Bluetooth on",
            "2. Each person scans the trip code",
            "3. Do a roll call: every phone should get a ✓",
            "4. Keep \"Reachable when closed\" on, and phones out of battery saver",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** Scan a trip group's code. [onScanned] returns null when it joined, or why not. */
@Composable
fun JoinTripScreen(onBack: () -> Unit, onScanned: (String) -> String?, askCamera: (() -> Unit) -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    SubScreen("Join a trip group", onBack) {
        item {
            Text("Ask someone in the group to open it and show the code. Point your camera at it.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (scanning) QrScanner(Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(MaterialTheme.shapes.large)) { note = onScanned(it) }
                else Button(onClick = { askCamera { scanning = true } }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan the code")
                }
            }
        }
        note?.let { item { Text(it, color = Extra.rose, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) } }
    }
}

/**
 * A trip group: its code to join, and every member with how their phone was reached in the last roll call.
 */
@Composable
fun TripGroupScreen(
    g: ChatGroup,
    myId: String,
    roll: RollCall?,
    people: List<Person>,
    invite: String?,
    onBack: () -> Unit,
    onRollCall: () -> Unit,
    onOpenChat: () -> Unit,
    onPerson: (String) -> Unit,
    onFieldTest: () -> Unit,
    onLeave: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    var showCode by rememberSaveable { mutableStateOf(g.members.isEmpty()) }
    var leaving by remember { mutableStateOf(false) }
    if (leaving) androidx.compose.material3.AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Leave the trip group?") },
        text = { Text("The others are told, and the group's chat goes from this phone.") },
        confirmButton = { TextButton(onClick = { leaving = false; onLeave() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
    )
    SubScreen("Trip group", onBack) {
        item {
            HeroCard(Gradients.brand(), Modifier.padding(top = 12.dp)) {
                RawText("🧭 ${g.name}", style = MaterialTheme.typography.titleLarge, color = Color.White)
                val total = g.members.size
                Text(when {
                    total == 0 -> "Only you so far. Show the code below to the others"
                    roll == null -> "${total + 1} people. Do a roll call to check every phone can be reached"
                    else -> "${roll.reached} of ${roll.members.size} reached · roll call ${ago(now - roll.at)}"
                }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(onClick = onRollCall, enabled = total > 0, shape = RoundedCornerShape(50), color = Color.White) {
                        Text("📋 Roll call", style = MaterialTheme.typography.labelLarge, color = Palette.BrandBlue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                    Surface(onClick = onOpenChat, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                        Text("💬 Group chat", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
        }
        if (invite != null) {
            item { GroupLabel("Join code") }
            item {
                Group {
                    SettingRow(Icons.Outlined.QrCodeScanner, Palette.BrandBlue, if (showCode) "Hide the code" else "Show the code",
                        "Anyone who scans it joins this trip group", onClick = { showCode = !showCode })
                    if (showCode) Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val qr = remember(invite) { qrBitmap(invite) }
                        Box(Modifier.clip(MaterialTheme.shapes.large).background(Color.White).padding(12.dp)) {
                            Image(qr.asImageBitmap(), "Trip group join code", Modifier.size(210.dp), filterQuality = FilterQuality.None)
                        }
                        Text("Others: Nearby → Trip group → Scan their code", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        item { GroupLabel("People · ${g.members.size + 1}") }
        item {
            Group {
                MemberRow("You", null, myId, null, Reach.Direct(0), self = true, divider = false) {}
                g.members.entries.sortedBy { (id, _) -> rank(roll?.members?.get(id)) }.forEach { (id, name) ->
                    val p = people.firstOrNull { it.nodeId == id }
                    MemberRow(p?.name ?: name.ifBlank { "Someone" }, p?.avatar, id, p, roll?.members?.get(id), divider = true) { onPerson(id) }
                }
            }
        }
        item {
            Text(if (roll != null && roll.reached < roll.members.size)
                "Not reached yet? They may be out of range, or their phone stopped BlueMob. Walk closer, or ask them to open it."
                else "A roll call goes to everyone. Each ✓ shows the phone was reached, and how: straight to it, through other phones, or over the internet.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        }
        item { GroupLabel("Test before you go") }
        item {
            Group {
                SettingRow(Icons.Outlined.Groups, Palette.Violet, "Field test with this group",
                    "Roll calls every few minutes for a few hours: how many get through, how fast, and battery used", onClick = onFieldTest) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3)
                }
            }
        }
        item { Steps() }
        item {
            OutlinedButton(onClick = { leaving = true }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Leave the trip group", color = Extra.rose) }
        }
    }
}

private fun rank(r: Reach?) = when (r) { null -> 1; Reach.Waiting -> 0; else -> 2 }

private fun ago(ms: Long): String = when {
    ms < 60_000 -> "just now"
    ms < 3_600_000 -> "${ms / 60_000} min ago"
    else -> "${ms / 3_600_000} h ago"
}

@Composable
private fun MemberRow(name: String, avatar: String?, id: String, p: Person?, reach: Reach?, self: Boolean = false, divider: Boolean, onClick: () -> Unit) {
    Column {
        if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 66.dp), color = Extra.line)
        Row(Modifier.fillMaxWidth().then(if (self) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p != null) Avatar(p.avatar, p.name, id, 40.dp, p.presence) else Avatar(avatar, name, id, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                RawText(name, style = MaterialTheme.typography.titleMedium)
                Text(if (self) "This phone" else reachText(reach), style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
            if (!self) ReachDot(reach)
        }
    }
}

@Composable
private fun ReachDot(r: Reach?) {
    val (label, color) = when (r) {
        null -> "–" to Extra.ink3
        Reach.Waiting -> "…" to Palette.Warning
        else -> "✓" to Palette.Success
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(label, color = color, style = MaterialTheme.typography.titleSmall)
    }
}

fun reachText(r: Reach?): String = when (r) {
    null -> "Not in a roll call yet"
    Reach.Waiting -> "Not reached yet"
    is Reach.Direct -> "Reached directly · ${FieldTest.Report.secs(r.ms)}"
    is Reach.Mesh -> "Reached through ${r.carriers} phone${if (r.carriers == 1) "" else "s"} · ${FieldTest.Report.secs(r.ms)}"
    is Reach.Internet -> "Reached over the internet · ${FieldTest.Report.secs(r.ms)}"
}
