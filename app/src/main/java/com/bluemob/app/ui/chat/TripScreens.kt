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
                    "Tracking ends with the trip; the chat stays").forEach {
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
            Text(if (days == 0) "The trip goes on until you end it." else "Tracking stops a day after the end, unless you keep it going. The group and chat stay.",
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
fun JoinTripScreen(onBack: () -> Unit, onScanned: (String) -> String?, askCamera: (() -> Unit) -> Unit,
    nearby: List<com.bluemob.app.nearby.TripAd> = emptyList(), notes: Map<String, String> = emptyMap(), onJoinNearby: (com.bluemob.app.nearby.TripAd, String) -> Unit = { _, _ -> }) {
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
        item { TripPrivacyNote(Modifier.padding(top = 16.dp)) }
        item { GroupLabel("Or paste a shared code") }
        item {
            OutlinedTextField(pasted, { pasted = it.take(4000) }, placeholder = { Text("bluemob:t:…") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedButton(onClick = { note = onScanned(pasted) }, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Join") }
        }
        note?.let { item { Text(it, color = Extra.rose, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) } }
        item { NearbyTripGroups(nearby, notes, onJoinNearby) }
    }
}

/** What the trip group screen can do. */
class TripActions(
    val onBack: () -> Unit = {},
    val onRollCall: () -> Unit = {}, val onOpenChat: () -> Unit = {}, val onPerson: (String) -> Unit = {}, val onFieldTest: () -> Unit = {},
    val onLeave: () -> Unit = {}, val onShare: (String) -> Unit = {}, val onAutoMin: (Int) -> Unit = {}, val onEnd: (Long) -> Unit = {},
    val onKeepOpen: () -> Unit = {}, val onClose: () -> Unit = {}, val onNewTeam: () -> Unit = {}, val onOpenGroup: (String) -> Unit = {},
    val onAdd: (Map<String, String>) -> Unit = {},
    val onItinerary: () -> Unit = {}, val onShowNearby: (Boolean) -> Unit = {}, val onNewPasscode: () -> Unit = {},
    val onAnnounce: (String) -> Unit = {}, val onAck: (String) -> Unit = {}, val onBuddies: () -> Unit = {},
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
    /** When we last heard from each member (a message, a check-in, a roll call answer), and how. */
    lastHeard: Map<String, Pair<Long, String>> = emptyMap(),
) {
    val closedAt = g.closedTime(now)
    val owner = g.isOwner(myId)
    val manage = g.canManage(myId) && closedAt == 0L
    val nameOf = { id: String -> if (id == myId) "You" else people.firstOrNull { it.nodeId == id }?.name ?: g.members[id].orEmpty().ifBlank { main?.members?.get(id).orEmpty().ifBlank { "Someone" } } }
    var showCode by rememberSaveable { mutableStateOf(g.members.isEmpty()) }
    var leaving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var announcing by remember { mutableStateOf(false) }
    // People, the plan, and settings (for the admin or captain) or more (for everyone else).
    var tab by rememberSaveable { mutableStateOf(0) }
    if (announcing) {
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { announcing = false },
            title = { Text("📣 Announce to everyone") },
            text = {
                Column {
                    Text("Everyone gets it with a \"Got it\" button, and you see who has.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    OutlinedTextField(text, { text = it.take(1000) }, placeholder = { Text("e.g. Bus leaves at 6:00. Bring rain jackets") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { a.onAnnounce(text); announcing = false }, enabled = text.isNotBlank()) { Text("Send") } },
            dismissButton = { TextButton(onClick = { announcing = false }) { Text("Cancel") } },
        )
    }
    if (leaving) androidx.compose.material3.AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Leave the trip group?") },
        text = { Text("The others are told, and the group's chat goes from this phone.") },
        confirmButton = { TextButton(onClick = { leaving = false; a.onLeave() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
    )
    if (closing) androidx.compose.material3.AlertDialog(
        onDismissRequest = { closing = false },
        title = { Text("End the trip?") },
        text = { Text("Roll calls, automatic checks and check-ins stop, and no one new can join. Teams end too. The group and its chat stay, so everyone can keep in touch.") },
        confirmButton = { TextButton(onClick = { closing = false; a.onClose() }) { Text("End the trip", color = Extra.rose) } },
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
                    closedAt > 0 -> "The trip ended ${endFormat.format(java.util.Date(closedAt))}. The group stays, so you can keep in touch"
                    total == 0 -> "Only you so far. Show the code below to the others"
                    roll == null -> "${total + 1} people. Do a roll call to check every phone can be reached"
                    else -> "${roll.reached} of ${roll.members.size} reached · roll call ${ago(now - roll.at)}"
                }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                if (g.autoMin > 0 && closedAt == 0L) Text("⏱ Automatic roll call every ${g.autoMin} min", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
                FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (manage) Surface(onClick = a.onRollCall, enabled = total > 0, shape = RoundedCornerShape(50), color = Color.White) {
                        Text("📋 Roll call", style = MaterialTheme.typography.labelLarge, color = Palette.BrandBlue, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                    if (manage) Surface(onClick = { announcing = true }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                        Text("📣 Announce", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                    Surface(onClick = a.onOpenChat, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                        Text(if (g.isTeam) "💬 Team chat" else "💬 Group chat", style = MaterialTheme.typography.labelLarge, color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                }
            }
        }
        // The trip has ended: the admin decides (or tracking stops by itself a day later; the chat stays).
        if (owner && closedAt == 0L && g.expiresAt in 1..now) item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(Palette.Warning.copy(alpha = 0.16f)).padding(16.dp)) {
                Text("🏁 The trip has ended", style = MaterialTheme.typography.titleMedium)
                Text("End it now, or keep the trip going another day. If you do nothing, it ends by itself ${endFormat.format(java.util.Date(g.expiresAt + TripMeta.GRACE_MS))}. The group and its chat stay either way.",
                    style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { closing = true }, Modifier.weight(1f)) { Text("End now") }
                    OutlinedButton(onClick = a.onKeepOpen, Modifier.weight(1f)) { Text("Keep open 1 day") }
                }
            }
        }
        g.announcements.lastOrNull()?.let { ann -> item { AnnouncementCard(g, ann, myId, nameOf, now, a.onAck) } }
        item {
            com.bluemob.app.ui.components.Segmented(listOf("People", "Plan", if (manage || owner) "Settings" else "More"), tab, Modifier.padding(top = 14.dp)) { tab = it }
        }
        if (tab == 1) {
        if (g.itinerary.isNotEmpty() || g.canPlan(myId)) item { ItineraryCard(g, myId, now, a.onItinerary) }
        if (g.buddies.isNotEmpty() || (g.canPlan(myId) && closedAt == 0L)) item { BuddyCard(g, myId, nameOf, a.onBuddies) }
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
                    if (owner && !g.isTeam) {
                        SettingRow(Icons.Outlined.Groups, Palette.Violet, "Show to people nearby", "They see it in Nearby and join by typing a passcode you tell them",
                            divider = true) { androidx.compose.material3.Switch(g.advertise, a.onShowNearby) }
                        if (g.advertise) Row(Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Passcode", style = MaterialTheme.typography.labelMedium, color = Extra.ink3)
                                RawText(g.passcode.chunked(1).joinToString(" "), style = MaterialTheme.typography.headlineMedium, color = Palette.Violet)
                            }
                            TextButton(onClick = a.onNewPasscode) { Text("New passcode") }
                        }
                    }
                }
            }
        }
        if (!g.isTeam && closedAt == 0L) item { Steps() }
        }
        if (tab == 0) {
        item { GroupLabel("People · ${g.members.size + 1}") }
        item {
            Group {
                MemberRow("You", null, myId, null, null, roleOf(g, myId, teams), self = true, divider = false, buddy = g.buddies[myId]?.let(nameOf)) {}
                g.members.entries.sortedBy { (id, _) -> rank(roll?.members?.get(id)) }.forEach { (id, name) ->
                    val p = people.firstOrNull { it.nodeId == id }
                    MemberRow(p?.name ?: name.ifBlank { "Someone" }, p?.avatar, id, p, roll?.members?.get(id), roleOf(g, id, teams), divider = true,
                        heard = lastHeard[id]?.let { (at, how) -> "Last heard ${ago(now - at)} · $how" }, buddy = g.buddies[id]?.let(nameOf)) { a.onPerson(id) }
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
        }
        if (tab == 2) {
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
                Text(if (g.expiresAt == 0L) "No end date: the trip goes on until you end it."
                    else "Ends ${endFormat.format(java.util.Date(g.expiresAt))}. Tracking stops a day later unless you keep it going; the chat stays.",
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
        }
        if (!owner) item { TripPrivacyNote(Modifier.padding(top = 12.dp)) }
        if (owner && !g.isTeam && closedAt == 0L) item {
            OutlinedButton(onClick = { closing = true }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("End the trip") }
        }
        item {
            OutlinedButton(onClick = { leaving = true }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (g.isTeam) "Leave the team" else "Leave the trip group", color = Extra.rose) }
        }
        }
    }
}

/** The latest announcement: "Got it" for everyone; for whoever sent it, who has and who hasn't. */
@Composable
private fun AnnouncementCard(g: ChatGroup, ann: com.bluemob.app.chat.Announcement, myId: String, nameOf: (String) -> String, now: Long, onAck: (String) -> Unit) {
    val acked = g.acks[ann.id].orEmpty()
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(Palette.BrandBlue.copy(alpha = 0.14f)).padding(14.dp)) {
        Text("📣 ${if (ann.by == myId) "You" else nameOf(ann.by)} · ${ago(now - ann.at)}", style = MaterialTheme.typography.labelMedium, color = Palette.BrandBlue)
        RawText(ann.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        if (ann.by == myId) {
            val everyone = g.members.keys
            val got = everyone.count { it in acked }
            Text("👍 $got of ${everyone.size} got it" + if (got < everyone.size) " · tap to see who hasn't" else "", style = MaterialTheme.typography.bodySmall,
                color = if (got == everyone.size) Palette.Success else Extra.ink2, modifier = Modifier.padding(top = 6.dp).clickable { open = !open })
            if (open) RawText(everyone.filter { it !in acked }.joinToString { nameOf(it) }, style = MaterialTheme.typography.bodySmall, color = Extra.rose)
        } else if (myId in acked) Text("✓ You said got it", style = MaterialTheme.typography.bodySmall, color = Palette.Success, modifier = Modifier.padding(top = 6.dp))
        else Chip("👍 Got it", true) { onAck(ann.id) }
    }
}

/** Buddy pairs on the Plan tab: your buddy, how many pairs, and setting them up. */
@Composable
private fun BuddyCard(g: ChatGroup, myId: String, nameOf: (String) -> String, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤝", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Buddy pairs", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    g.buddies[myId] != null -> "Your buddy: ${nameOf(g.buddies.getValue(myId))} · ${g.buddies.size / 2} pairs"
                    g.buddies.isEmpty() -> "Pair people up: each phone tells its owner if their buddy drifts away"
                    else -> "${g.buddies.size / 2} pairs"
                }, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Set buddy pairs: tap two people to pair them, or pair everyone in order. Whoever plans the group. */
@Composable
fun BuddiesScreen(g: ChatGroup, myId: String, myName: String, nameOf: (String) -> String, onSave: (List<Pair<String, String>>) -> Unit, onBack: () -> Unit) {
    val everyone = remember(g.members) { listOf(myId) + g.members.keys }
    val canPlan = g.canPlan(myId) && !g.isClosed(System.currentTimeMillis())
    var pairs by remember(g.buddiesAt) { mutableStateOf(g.buddies.entries.filter { it.key < it.value }.map { it.key to it.value }) }
    var picking by remember { mutableStateOf<String?>(null) }
    val paired = pairs.flatMap { listOf(it.first, it.second) }.toSet()
    val name = { id: String -> if (id == myId) "$myName (you)" else nameOf(id) }
    SubScreen("Buddy pairs", onBack) {
        item {
            Text(if (canPlan) "Tap one person, then their buddy. Each phone tells its owner if their buddy's phone hasn't been near for 20 minutes, and tells the group after 40."
                else "Set by the group's admin. Your phone tells you if your buddy's phone hasn't been near for 20 minutes.",
                style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 12.dp))
        }
        if (pairs.isNotEmpty()) {
            item { GroupLabel("Pairs · ${pairs.size}") }
            item {
                Group {
                    pairs.forEachIndexed { i, (x, y) ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = Extra.line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RawText("${name(x)}  🤝  ${name(y)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (canPlan) TextButton(onClick = { pairs = pairs - (x to y) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
        if (canPlan) {
            val left = everyone.filter { it !in paired }
            if (left.isNotEmpty()) {
                item { GroupLabel(if (picking == null) "Not paired · ${left.size}" else "Pick ${name(picking!!)}'s buddy") }
                item {
                    Group {
                        left.forEachIndexed { i, id ->
                            if (i > 0) androidx.compose.material3.HorizontalDivider(color = Extra.line)
                            Row(Modifier.fillMaxWidth().background(if (picking == id) Palette.BrandBlue.copy(alpha = 0.14f) else Color.Transparent).clickable {
                                val p = picking
                                picking = when { p == null -> id; p == id -> null; else -> { pairs = pairs + (p to id); null } }
                            }.padding(horizontal = 14.dp, vertical = 12.dp)) { RawText(name(id), style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { pairs = pairs + left.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }; picking = null },
                        enabled = left.size >= 2, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Pair the rest in order") }
                }
                if (left.size % 2 == 1 && left.size > 1) item {
                    Text("One person will be left without a buddy: add them to a pair's team, or pair them with you.", style = MaterialTheme.typography.bodySmall,
                        color = Extra.ink3, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
            }
            item { Button(onClick = { onSave(pairs) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Save and send to everyone") } }
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
private fun MemberRow(name: String, avatar: String?, id: String, p: Person?, reach: Reach?, role: String? = null, self: Boolean = false, divider: Boolean,
    heard: String? = null, buddy: String? = null, onClick: () -> Unit) {
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
                // Not reached now: when we last heard from them tells the admin where to look (a team on its own path).
                if (!self && heard != null && (reach == null || reach == Reach.Waiting)) Text(heard, style = MaterialTheme.typography.bodySmall, color = Extra.ink3)
                buddy?.let { RawText("🤝 $it", style = MaterialTheme.typography.bodySmall, color = Extra.ink3) }
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

/** Trip groups that phones nearby are showing: tap, type the passcode the admin says, and their phone lets you in. */
@Composable
fun NearbyTripGroups(ads: List<com.bluemob.app.nearby.TripAd>, notes: Map<String, String>, onJoin: (com.bluemob.app.nearby.TripAd, String) -> Unit, modifier: Modifier = Modifier) {
    if (ads.isEmpty()) return
    var joining by remember { mutableStateOf<com.bluemob.app.nearby.TripAd?>(null) }
    var asked by remember { mutableStateOf(setOf<String>()) }
    joining?.let { ad ->
        var code by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { joining = null },
            title = { RawText("🧭 ${ad.name}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text("Ask ${ad.ownerName.ifBlank { "the admin" }} for the passcode and type it here.", style = MaterialTheme.typography.bodyMedium)
                    Text("The group will see your name, your roll call answers, the checkpoints you reach and when you were last heard from. Never your position.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(code, { code = it.filter(Char::isLetterOrDigit).take(12) }, label = { Text("Passcode") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = { TextButton(onClick = { onJoin(ad, code); asked = asked + ad.groupId; joining = null }, enabled = code.length >= 4) { Text("Join") } },
            dismissButton = { TextButton(onClick = { joining = null }) { Text("Cancel") } },
        )
    }
    Column(modifier) {
        GroupLabel("Trip groups nearby")
        Group {
            ads.forEachIndexed { i, ad ->
                val note = notes[ad.groupId] ?: if (ad.groupId in asked) "Asked to join. You're in as soon as the admin's phone answers" else null
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = Extra.line)
                Row(Modifier.fillMaxWidth().clickable { joining = ad }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🧭", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        RawText(ad.name, style = MaterialTheme.typography.titleMedium)
                        Text(note ?: "${ad.people} people · run by ${ad.ownerName.ifBlank { "someone nearby" }}", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    Text("Join", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * What a trip group shares about its members, shown before joining (DPDP notice, GDPR transparency) and in the group.
 */
@Composable
fun TripPrivacyNote(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Extra.sand).padding(14.dp)) {
        Text("🔒 What the group sees", style = MaterialTheme.typography.titleSmall)
        Text("Your name, whether your phone answers roll calls (and how), the checkpoints you reach and when, and when you were last heard from. " +
            "Your position is never sent: a checkpoint check-in only says you reached it. The admin and captains see this; messages are end-to-end encrypted. " +
            "You can leave any time. When the trip ends, tracking stops and check-ins are erased 30 days later; the group and its chat stay.",
            style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
    }
}
