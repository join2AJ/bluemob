package com.bluemob.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SmartToy
import com.bluemob.app.system.Radio
import com.bluemob.app.system.RadioState
import com.bluemob.app.ui.system.RadioBanner
import com.bluemob.app.ui.system.RadioStrip
import com.bluemob.app.ui.system.firstProblem
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.FeatureCard
import com.bluemob.app.ui.components.LargeTitle
import com.bluemob.app.ui.components.PulsingDot
import com.bluemob.app.ui.components.SectionHeader
import com.bluemob.app.ui.components.Tag
import com.bluemob.app.ui.components.avatarTint
import com.bluemob.app.ui.components.presenceColor
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.Geo
import com.bluemob.app.util.TimeText
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.log10

data class NearbyState(
    val name: String,
    val running: Boolean,
    val people: List<Person>,
    val permissionsGranted: Boolean,
    val locationServicesOff: Boolean,
    val sharingLocation: Boolean,
    val hasMyFix: Boolean,
    val online: Boolean,
    val radios: RadioState? = null,
    /** Names of BlueMob users nearby whose version can't link with this one. */
    val otherVersions: List<String> = emptyList(),
    /** Batteries nearby phones told us about. */
    val batteries: Map<String, com.bluemob.app.nearby.Battery> = emptyMap(),
    /** Our latest "Check on everyone". */
    val check: com.bluemob.app.nearby.CheckIn? = null,
)

/** Which people to list under the radar. */
private enum class NearbyFilter(val label: String) { ALL("All"), ONLINE("Online"), IN_RANGE("In range"), SOS("SOS"), LOW("Low battery") }

@Composable
fun NearbyScreen(
    state: NearbyState,
    contentPadding: PaddingValues,
    onToggleMesh: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onShareLocation: () -> Unit,
    onOpenChat: (String) -> Unit,
    onTalkToSky: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenBattery: () -> Unit,
    onConnections: () -> Unit = {},
    onFixRadio: (Radio) -> Unit = {},
    onGames: () -> Unit = {},
    onFindLost: (String) -> Unit = {},
    onCheckEveryone: () -> Unit = {},
    onClearCheck: () -> Unit = {},
) {
    val list = state.people
    var filter by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(NearbyFilter.ALL) }
    val shown = list.filter { p ->
        when (filter) {
            NearbyFilter.ALL -> true
            NearbyFilter.ONLINE -> p.presence == Presence.ONLINE
            NearbyFilter.IN_RANGE -> p.presence != Presence.OFFLINE
            NearbyFilter.SOS -> p.sos || p.lost != null
            NearbyFilter.LOW -> (state.batteries[p.nodeId]?.pct ?: 100) <= 20
        }
    }
    val online = list.count { it.presence == Presence.ONLINE }
    val inRange = list.count { it.presence == Presence.IN_RANGE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding(), bottom = 120.dp),
    ) {
        item { LargeTitle("Nearby", over = "${greeting()}, ${state.name}") }
        state.radios?.let { radios ->
            item { RadioStrip(radios, state.online, onConnections, Modifier.padding(bottom = 12.dp)) }
            radios.firstProblem()?.let { problem ->
                if (state.permissionsGranted) item { RadioBanner(problem, onFix = { onFixRadio(problem.first) }, onOpen = onConnections, modifier = Modifier.padding(bottom = 12.dp)) }
            }
        }
        if (state.otherVersions.isNotEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = Extra.sand2, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${state.otherVersions.distinct().joinToString()} ${if (state.otherVersions.distinct().size == 1) "is" else "are"} nearby on a different BlueMob version",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Your phones can't link until you're both on BlueMob 0.5 or newer. Ask them to update, then you'll see each other here.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        list.filter { it.lost != null }.forEach { p ->
            item(key = "lost-" + p.nodeId) {
                Surface(onClick = { onFindLost(p.nodeId) }, shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🧭  ${p.name} is lost", style = MaterialTheme.typography.titleMedium)
                        Text(p.lost!!.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text("Tap to walk to them with the compass", style = MaterialTheme.typography.labelMedium, color = Extra.sky, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        if (!state.permissionsGranted) item {
            Notice("Allow nearby access", "BlueMob needs Bluetooth and Wi-Fi permission to find people around you, without towers or internet.", "Allow", onRequestPermissions)
        }
        if (state.locationServicesOff && state.radios == null) item {
            Notice("Turn on Location", "Android 12 and older need the Location switch on to find nearby phones. No internet is used.", "Open settings", onOpenLocationSettings)
        }

        item {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Gradients.night),
            ) {
                RadarSweep(list.map { it.toBlip(state.sharingLocation && state.hasMyFix) }, Modifier.fillMaxSize(), active = state.running, onTap = onOpenChat)
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.running) PulsingDot(Palette.Mint, 7.dp) else Box(Modifier.width(7.dp).height(7.dp).background(Color(0xFF8C9A94), RoundedCornerShape(50)))
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.running) "MESH ON" else "MESH OFF", color = Color(0xFFEAF5F0), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.running, onCheckedChange = onToggleMesh, enabled = state.permissionsGranted,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Palette.Mint, checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                            uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Stat("$online", "online"); Spacer(Modifier.width(18.dp))
                    Stat("$inRange", "in range"); Spacer(Modifier.width(18.dp))
                    Stat("${list.size}", "met")
                    Spacer(Modifier.weight(1f))
                    Text(if (state.sharingLocation && state.hasMyFix) "10 m · 300 m · 10 km" else "inner ring = online",
                        color = Color(0xFFEAF5F0).copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // "Check on everyone": one tap asks every phone nearby "Are you OK?".
        item {
            val c = state.check
            Surface(onClick = if (c == null) onCheckEveryone else ({}), shape = MaterialTheme.shapes.large, color = if (c?.help?.isNotEmpty() == true) Extra.emberTint else Extra.pineTint,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (c == null) "🙋" else if (c.help.isNotEmpty()) "🆘" else "✅", fontSize = 24.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (c == null) {
                            Text("Check on everyone", style = MaterialTheme.typography.titleMedium)
                            Text("Asks every phone nearby \"Are you OK?\". Answers come back as they tap", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                        } else {
                            val names = { ids: Collection<String> -> ids.map { id -> list.firstOrNull { it.nodeId == id }?.name ?: "someone" } }
                            Text("${c.ok} OK" + (if (c.help.isNotEmpty()) " · ${c.help.size} need help" else "") + " · ${c.waiting.size} waiting", style = MaterialTheme.typography.titleMedium)
                            if (c.help.isNotEmpty()) Text("Need help: " + names(c.help).joinToString(), style = MaterialTheme.typography.bodySmall, color = Extra.rose)
                            if (c.waiting.isNotEmpty()) Text("Waiting for " + names(c.waiting).joinToString(), style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 2)
                        }
                    }
                    if (c != null) androidx.compose.material3.TextButton(onClick = onClearCheck) { Text("Done") }
                }
            }
        }
        item { SectionHeader("Around you") }
        if (list.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(NearbyFilter.entries) { f -> com.bluemob.app.ui.components.Chip(f.label, filter == f) { filter = f } }
            }
        }
        item {
            if (list.isNotEmpty() && shown.isEmpty()) Text("No one matches this filter.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(8.dp))
            else if (list.isEmpty()) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏕️", fontSize = 34.sp)
                        Text("Quiet out here", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                        Text(if (state.running) "People appear as they open BlueMob near you." else "Switch the mesh on to look for people nearby.",
                            style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(shown, key = { it.nodeId }) { PersonCard(it, state.batteries[it.nodeId], onClick = { onOpenChat(it.nodeId) }) }
                }
            }
        }
        if (!state.sharingLocation) item {
            Surface(onClick = onShareLocation, shape = MaterialTheme.shapes.medium, color = Extra.skyTint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("📍  Share your location to see real distances. GPS works without internet.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
        }

        item { SectionHeader("Ready for anything") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard(Icons.Outlined.SmartToy, Extra.sky, "Ask Sky", "Your built-in guide. Lives on your phone, works offline.", onTalkToSky)
                FeatureCard(Icons.Outlined.MenuBook, Palette.Pine, "Survival guide", "First aid, water, fire, shelter and more. Stored on your phone.", onOpenGuide)
                FeatureCard(com.bluemob.app.ui.components.BlueMobIcons.Games, Extra.ember, "Play a game", "With someone nearby, or against the computer when no one's around.", onGames)
                FeatureCard(Icons.Outlined.BatterySaver, Color(0xFF3A9A5B), "Make the battery last", "Battery Saver for the phone, while BlueMob keeps running.", onOpenBattery)
            }
        }
        item {
            Text(if (state.online) "This phone has internet right now." else "Off-grid: no internet here, and that's fine. BlueMob talks phone to phone.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, color = Color(0xFFEAF5F0), style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Color(0xFFEAF5F0).copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PersonCard(p: Person, battery: com.bluemob.app.nearby.Battery? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line), modifier = Modifier.width(148.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(96.dp).background(avatarTint(p.nodeId)), contentAlignment = Alignment.Center) {
                Text(p.avatar ?: p.name.take(1), fontSize = 44.sp)
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Tag(if (p.sos) "SOS" else when (p.presence) { Presence.ONLINE -> "Online"; Presence.IN_RANGE -> "In range"; Presence.OFFLINE -> "Away" },
                        container = Color.White.copy(alpha = 0.85f), content = Palette.Ink, dot = if (p.sos) Palette.Rose else presenceColor(p.presence))
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                com.bluemob.app.ui.components.StarChip(p.stars, p.ratingCount, Modifier.padding(vertical = 2.dp))
                Text("BM " + com.bluemob.app.util.formatId(p.nodeId).take(9), style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = Extra.ink3, maxLines = 1)
                Text(statusLine(p), style = MaterialTheme.typography.bodySmall, color = Extra.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Battery (as their phone last said) and when we last saw them.
                Text(listOfNotNull(
                    battery?.let { b -> (if (b.charging) "⚡" else if (b.pct <= 20) "🪫" else "🔋") + " ${b.pct}%" },
                    if (p.presence == Presence.OFFLINE && p.lastSeen > 0) "seen " + com.bluemob.app.util.TimeText.ago(p.lastSeen) else null,
                ).joinToString(" · ").ifBlank { " " }, style = MaterialTheme.typography.labelSmall,
                    color = if ((battery?.pct ?: 100) <= 20 && battery?.charging != true) Extra.rose else Extra.ink3, maxLines = 1)
            }
        }
    }
}

fun statusLine(p: Person): String = when (p.presence) {
    Presence.ONLINE -> listOfNotNull(p.distanceM?.let { Geo.formatDistance(it) }, linkWords(p.quality)).joinToString(" · ")
    Presence.IN_RANGE -> "Connecting…"
    Presence.OFFLINE -> if (p.lastSeen == 0L) "Added by ID · not met yet" else "Seen " + TimeText.ago(p.lastSeen)
}

fun linkWords(q: LinkQuality?): String = when (q) {
    LinkQuality.HIGH -> "fast Wi-Fi link"
    LinkQuality.MEDIUM -> "good link"
    LinkQuality.LOW, null -> "Bluetooth link"
}

/**
 * Where a person sits on the radar. With both GPS positions: real direction on a log scale
 * (rings at 10 m, ~300 m and 10 km). Otherwise a stable direction and a ring by reachability.
 */
private fun Person.toBlip(useDistance: Boolean): RadarBlip {
    val d = distanceM
    val b = bearingDeg
    val radius = if (useDistance && d != null && b != null) ((log10(d.coerceAtLeast(1.0)) + 0.5) / 4.5).toFloat().coerceIn(0.14f, 0.9f)
    else when (presence) { Presence.ONLINE -> 0.36f; Presence.IN_RANGE -> 0.6f; Presence.OFFLINE -> 0.84f }
    return RadarBlip(nodeId, avatar, b ?: (abs(nodeId.hashCode()) % 360).toDouble(), radius, presenceColor(presence),
        faded = presence == Presence.OFFLINE, sos = sos)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello, night owl"
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = Extra.emberTint, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(action) }
        }
    }
}
