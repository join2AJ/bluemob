package com.bluemob.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Share
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
import com.bluemob.app.chat.TripMeta
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

/** What makes a trip group different from a normal group, in a few lines (Groups tab, New trip group). */
@Composable
fun TripVsGroup(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Extra.sand).padding(16.dp)) {
        Text("Trip group or normal group?", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("👥 Group", style = MaterialTheme.typography.labelLarge)
                listOf("You pick people you've met", "Everyone is equal", "Stays until you leave").forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("🧭 Trip group", style = MaterialTheme.typography.labelLarge)
                listOf("Join by one QR code or shared code", "An admin, teams and captains", "Roll calls, automatic checks and an alert when a phone can't be reached",
                    "Can end with the trip").forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** How long the trip lasts, as choices (0: no end). */
private val ENDS = listOf(0 to "No end", 1 to "1 day", 2 to "2 days", 3 to "3 days", 7 to "1 week")
private const val DAY = 24 * 3_600_000L
private val endFormat = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault())

/** Start a trip group: a name (or the next "Group N"), how long it lasts, then everyone joins with its code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTripScreen(onBack: () -> Unit, onCreate: (String, Long) -> Unit, onJoin: () -> Unit, nextNumber: Int = 1) {
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf(0) }
    SubScreen("New trip group", onBack) {
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
                (listOf("Group $nextNumber") + listOf("Trek team", "Family at the mela", "Yatra group", "College trip")).forEach { RawChip(it, name == it) { name = it } }
            }
        }
        item { GroupLabel("The trip ends") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ENDS.forEach { (d, label) -> Chip(label, days == d) { days = d } }
            }
        }
        item {
            Text(if (days == 0) "The group stays until you close it." else "It closes a day after the end, unless you keep it open.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        item {
            Button(onClick = { onCreate(name.ifBlank { "Group $nextNumber" }, if (days == 0) 0 else System.currentTimeMillis() + days * DAY) },
                Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Create and show the code") }
        }
        item {
            Group(Modifier.padding(top = 24.dp)) {
                SettingRow(Icons.Outlined.QrCodeScanner, Palette.BrandBlue, "Someone already made one?", "Scan or paste their code to join", onClick = onJoin) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3)
                }
            }
        }
        item { TripVsGroup(Modifier.padding(top = 24.dp)) }
        item { Steps() }
    }
}

/** A chip whose text is a name, not translated. */
@Composable
private fun RawChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    RawText(text, color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, Extra.line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp))
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

/** Scan a trip group's code, or paste one someone shared. [onScanned] returns null when it joined, or why not. */
@Composable
fun JoinTripScreen(onBack: () -> Unit, onScanned: (String) -> String?, askCamera: (() -> Unit) -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var pasted by rememberSaveable { mutableStateOf("") }
    SubScreen("Join a trip group", onBack) {
        item {
            Text("Ask someone in the group to show the code, and point your camera at it. Or paste a code they shared.",
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
        item { GroupLabel("Or paste a shared code") }
        item {
            OutlinedTextField(pasted, { pasted = it.take(4000) }, placeholder = { Text("bluemob:t:…") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedButton(onClick = { note = onScanned(pasted) }, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Join") }
        }
        note?.let { item { Text(it, color = Extra.rose, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) } }
    }
}

/** What the trip group screen can do. */
class TripActions(
    val onBack: () -> Unit = {},
    val onRollCall: () -> Unit = {}, val onOpenChat: () -> Unit = {}, val onPerson: (String) -> Unit = {}, val onFieldTest: () -> Unit = {},
    val onLeave: () -> Unit = {}, val onShare: (String) -> Unit = {}, val onAutoMin: (Int) -> Unit = {}, val onEnd: (Long) -> Unit = {},
    val onKeepOpen: () -> Unit = {}, val onClose: () -> Unit = {}, val onNewTeam: () -> Unit = {}, val onOpenGroup: (String) -> Unit = {},
    val onAdd: (Map<String, String>) -> Unit = {},
)

/**
 * A trip group or team: its code to join, its people (with the admin and captains), how each phone was reached in the
 * last roll call, its teams, and for the admin: automatic roll calls, when the trip ends, and closing it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripGroupScreen(
    g: ChatGroup,
    myId: String,
    roll: RollCall?,
    people: List<Person>,
    invite: String?,
    a: TripActions,
    teams: List<ChatGroup> = emptyList(),
    main: ChatGroup? = null,
    now: Long = System.currentTimeMillis(),
) {
    val closedAt = g.closedTime(now)
    val owner = g.isOwner(myId)
    val manage = g.canManage(myId) && closedAt == 0L
    val nameOf = { id: String -> people.firstOrNull { it.nodeId == id }?.name ?: g.members[id].orEmpty().ifBlank { main?.members?.get(id).orEmpty().ifBlank { "Someone" } } }
    var showCode by rememberSaveable { mutableStateOf(g.members.isEmpty()) }
    var leaving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    if (leaving) androidx.compose.material3.AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Leave the trip group?") },
        text = { Text("The others are told, and the group's chat goes from this phone.") },
        confirmButton = { TextButton(onClick = { leaving = false; a.onLeave() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
    )
    if (closing) androidx.compose.material3.AlertDialog(
        onDismissRequest = { closing = false },
        title = { Text("Close the trip group?") },
        text = { Text("Everyone can still read it, but no one can write, and roll calls stop. Teams close too.") },
        confirmButton = { TextButton(onClick = { closing = false; a.onClose() }) { Text("Close it", color = Extra.rose) } },
        dismissButton = { TextButton(onClick = { closing = false }) { Text("Cancel") } },
    )
    if (adding && main != null) {
        val candidates = (main.members + (main.owner to nameOf(main.owner))).filterKeys { it.isNotBlank() && it != myId && it !in g.members }
        var picked by remember { mutableStateOf(setOf<String>()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add people from ${main.name}") },
            text = {
                Column {
                    if (candidates.isEmpty()) Text("Everyone in ${main.name} is already in this team.")
                    candidates.forEach { (id, _) ->
                        Row(Modifier.fillMaxWidth().clickable { picked = if (id in picked) picked - id else picked + id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(id in picked, null)
                            RawText(nameOf(id), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { a.onAdd(picked.associateWith { nameOf(it) }); adding = false }, enabled = picked.isNotEmpty()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    SubScreen(if (g.isTeam) "Team" else "Trip group", a.onBack) {
        item {
            HeroCard(if (closedAt > 0) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF374151))) else Gradients.brand(), Modifier.padding(top = 12.dp)) {
                RawText((if (g.isTeam) "👥 " else "🧭 ") + g.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                val role = when {
                    owner && g.isTeam -> "You're the admin · captain: ${nameOf(g.captain)}"
                    owner -> "You're the admin"
                    g.captain == myId -> "You're the captain of this team"
                    g.isTeam -> "Captain: ${nameOf(g.captain)}"
                    g.owner.isNotBlank() -> "Admin: ${nameOf(g.owner)}"
                    else -> null
                }
                role?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.95f), modifier = Modifier.padding(top = 2.dp)) }
                val total = g.members.size
                Text(when {
                    closedAt > 0 -> "Closed ${endFormat.format(java.util.Date(closedAt))}. You can still read it"
                    total == 0 -> "Only you so far. Show the code below to the others"
                    roll == null -> "${total + 1} people. Do a roll call to check every phone can be reached"
                    else -> "${roll.reached} of ${roll.members.size} reached · roll call ${ago(now - roll.at)}"
                }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                if (g.autoMin > 0 && closedAt == 0L) Text("⏱ Automatic roll call every ${g.autoMin} min", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (manage) Surface(onClick = a.onRollCall, enabled = total > 0, shape = RoundedCornerShape(50), color = Color.White) {
                        Text("📋 Roll call", style = MaterialTheme.typography.labelLarge, color = Palette.BrandBlue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                    Surface(onClick = a.onOpenChat, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                        Text(if (g.isTeam) "💬 Team chat" else "💬 Group chat", style = MaterialTheme.typography.labelLarge, color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
        }
        // The trip has ended: the admin decides (or it closes by itself a day later).
        if (owner && closedAt == 0L && g.expiresAt in 1..now) item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(Palette.Warning.copy(alpha = 0.16f)).padding(16.dp)) {
                Text("🏁 The trip has ended", style = MaterialTheme.typography.titleMedium)
                Text("Close the group now, or keep it open another day. If you do nothing, it closes by itself ${endFormat.format(java.util.Date(g.expiresAt + TripMeta.GRACE_MS))}.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { closing = true }, Modifier.weight(1f)) { Text("Close now") }
                    OutlinedButton(onClick = a.onKeepOpen, Modifier.weight(1f)) { Text("Keep open 1 day") }
                }
            }
        }
        if (invite != null && closedAt == 0L) {
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
                        Text("Others: Groups → Trip group → Join", style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp))
                    }
                    SettingRow(Icons.Outlined.Share, Palette.BrandGreen, "Share the code", "Send it in any app; they paste it in BlueMob to join",
                        divider = true, onClick = { a.onShare(invite) })
                }
            }
        }
        item { GroupLabel("People · ${g.members.size + 1}") }
        item {
            Group {
                MemberRow("You", null, myId, null, null, roleOf(g, myId, teams), self = true, divider = false) {}
                g.members.entries.sortedBy { (id, _) -> rank(roll?.members?.get(id)) }.forEach { (id, name) ->
                    val p = people.firstOrNull { it.nodeId == id }
                    MemberRow(p?.name ?: name.ifBlank { "Someone" }, p?.avatar, id, p, roll?.members?.get(id), roleOf(g, id, teams), divider = true) { a.onPerson(id) }
                }
                if (manage && g.isTeam && main != null) SettingRow(null, Palette.BrandBlue, "+ Add people from ${main.name}", null, divider = true, onClick = { adding = true })
            }
        }
        item {
            Text(if (roll != null && roll.reached < roll.members.size)
                "Not reached yet? They may be out of range, or their phone stopped BlueMob. Walk closer, or ask them to open it."
                else "A roll call goes to everyone. Each ✓ shows the phone was reached, and how: straight to it, through other phones, or over the internet.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        }
        if (!g.isTeam && (teams.isNotEmpty() || (owner && closedAt == 0L))) {
            item { GroupLabel("Teams · ${teams.size}") }
            item {
                Group {
                    teams.forEachIndexed { i, t ->
                        SettingRow(Icons.Outlined.Groups, Palette.Violet, t.name, "Captain: ${nameOf(t.captain)} · ${t.members.size + 1} people",
                            divider = i > 0, onClick = { a.onOpenGroup(t.id) }) { Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3) }
                    }
                    if (owner && closedAt == 0L) SettingRow(null, Palette.Violet, "+ Add a team", "Pick a captain and their people. You see every team; each captain runs theirs",
                        divider = teams.isNotEmpty(), onClick = a.onNewTeam)
                }
            }
        }
        if (manage) {
            item { GroupLabel("Automatic roll call") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 15, 30, 60).forEach { m -> Chip(if (m == 0) "Off" else "$m min", g.autoMin == m) { a.onAutoMin(m) } }
                }
            }
            item {
                Text("Your phone sends a roll call this often. If a phone doesn't answer within 3 minutes, your phone sounds an alert.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
            }
        }
        if (owner && !g.isTeam && closedAt == 0L) {
            item { GroupLabel("The trip ends") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ENDS.forEach { (d, label) ->
                        val chosen = if (d == 0) g.expiresAt == 0L else false
                        Chip(label, chosen) { a.onEnd(if (d == 0) 0 else now + d * DAY) }
                    }
                }
            }
            item {
                Text(if (g.expiresAt == 0L) "No end date: the group stays until you close it."
                    else "Ends ${endFormat.format(java.util.Date(g.expiresAt))}. It closes a day later unless you keep it open.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
            }
        }
        if (closedAt == 0L) {
            item { GroupLabel("Test before you go") }
            item {
                Group {
                    SettingRow(Icons.Outlined.Groups, Palette.Violet, "Field test with this group",
                        "Roll calls every few minutes for a few hours: how many get through, how fast, and battery used", onClick = a.onFieldTest) {
                        Icon(Icons.Outlined.ChevronRight, null, tint = Extra.ink3)
                    }
                }
            }
            if (!g.isTeam) item { Steps() }
        }
        if (owner && !g.isTeam && closedAt == 0L) item {
            OutlinedButton(onClick = { closing = true }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Close the trip group") }
        }
        item {
            OutlinedButton(onClick = { leaving = true }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (g.isTeam) "Leave the team" else "Leave the trip group", color = Extra.rose) }
        }
    }
}

/** "Admin", "Captain · Team 1", or nothing. */
private fun roleOf(g: ChatGroup, id: String, teams: List<ChatGroup>): String? = when {
    g.owner == id -> "Admin"
    g.captain == id -> "Captain"
    else -> teams.firstOrNull { it.captain == id }?.let { "Captain · ${it.name}" }
}

/** Start a team in a trip group: a name, its captain, and its people. */
@Composable
fun NewTeamScreen(main: ChatGroup, myId: String, myName: String, people: List<Person>, teamCount: Int, onBack: () -> Unit, onCreate: (String, String, Map<String, String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Team ${teamCount + 1}") }
    var captain by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val nameOf = { id: String -> if (id == myId) "$myName (you)" else people.firstOrNull { it.nodeId == id }?.name ?: main.members[id].orEmpty().ifBlank { "Someone" } }
    val everyone = listOf(myId) + main.members.keys
    SubScreen("New team", onBack) {
        item {
            Text("A team has its own chat and roll calls, run by its captain. You see every team; the captain sees theirs and the main group.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        item { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Team name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { GroupLabel("Captain") }
        item {
            Group {
                everyone.forEachIndexed { i, id ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = Extra.line)
                    Row(Modifier.fillMaxWidth().clickable { captain = id; picked = picked - id }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(captain == id, null)
                        RawText(nameOf(id), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { GroupLabel("People in the team · ${picked.size}") }
        item {
            Group {
                main.members.keys.filter { it != captain }.forEachIndexed { i, id ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = Extra.line)
                    Row(Modifier.fillMaxWidth().clickable { picked = if (id in picked) picked - id else picked + id }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(id in picked, null)
                        RawText(nameOf(id), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            Button(onClick = { onCreate(name.ifBlank { "Team ${teamCount + 1}" }, captain, picked.associateWith { main.members[it].orEmpty() }) },
                enabled = captain.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Create the team") }
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
private fun MemberRow(name: String, avatar: String?, id: String, p: Person?, reach: Reach?, role: String? = null, self: Boolean = false, divider: Boolean, onClick: () -> Unit) {
    Column {
        if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 66.dp), color = Extra.line)
        Row(Modifier.fillMaxWidth().then(if (self) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p != null) Avatar(p.avatar, p.name, id, 40.dp, p.presence) else Avatar(avatar, name, id, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RawText(name, style = MaterialTheme.typography.titleMedium)
                    role?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.Violet, modifier = Modifier.padding(start = 8.dp)
                        .clip(RoundedCornerShape(50)).background(Palette.Violet.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)) }
                }
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
