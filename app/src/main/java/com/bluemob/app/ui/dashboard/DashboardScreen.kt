package com.bluemob.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bluemob.app.mesh.LinkQuality
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.components.Avatar
import com.bluemob.app.ui.components.Pill
import com.bluemob.app.ui.components.PulsingDot
import com.bluemob.app.ui.components.presenceColor
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.Geo
import com.bluemob.app.util.TimeText
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.log10

data class DashboardState(
    val name: String,
    val avatar: String,
    val nodeId: String,
    val running: Boolean,
    val people: List<Person>,
    val permissionsGranted: Boolean,
    val locationServicesOff: Boolean,
    val sharingLocation: Boolean,
    val hasMyFix: Boolean,
    val online: Boolean,
)

@Composable
fun DashboardScreen(
    state: DashboardState,
    contentPadding: PaddingValues,
    onToggleMesh: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onShareLocation: () -> Unit,
    onOpenChat: (String) -> Unit,
    onTalkToSky: () -> Unit,
) {
    val onlineCount = state.people.count { it.presence == Presence.ONLINE }
    val inRange = state.people.count { it.presence == Presence.IN_RANGE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg, end = Space.lg,
            top = contentPadding.calculateTopPadding() + Space.lg,
            bottom = contentPadding.calculateBottomPadding() + Space.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { Greeting(state) }
        item { MeshCard(state.running, state.permissionsGranted, onlineCount, inRange, state.people.size, onToggleMesh) }

        if (!state.permissionsGranted) {
            item {
                NoticeCard(
                    "Allow nearby access",
                    "BlueMob needs Bluetooth and Wi-Fi permission to find people around you, without towers or internet.",
                    "Allow", onRequestPermissions,
                )
            }
        }
        if (state.locationServicesOff) {
            item {
                NoticeCard(
                    "Turn on Location",
                    "Android 12 and older need the Location switch on to find nearby phones. No internet is used.",
                    "Open settings", onOpenLocationSettings,
                )
            }
        }

        item { RadarCard(state, onShareLocation) }
        item { BridgeCard(state.online) }

        item {
            Text("People", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.sm))
        }
        if (state.people.isEmpty()) {
            item { EmptyPeople(state.running, onTalkToSky) }
        }
        items(state.people, key = { it.nodeId }) { person ->
            PersonRow(person, onClick = { onOpenChat(person.nodeId) })
        }
    }
}

@Composable
private fun Greeting(state: DashboardState) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(hello, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Avatar(state.avatar, state.name, state.nodeId, 52.dp)
    }
}

@Composable
private fun MeshCard(
    running: Boolean,
    canRun: Boolean,
    online: Int,
    inRange: Int,
    met: Int,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Gradients.horizon())
            .padding(Space.xl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running) {
                            PulsingDot(Color.White)
                            Spacer(Modifier.width(Space.sm))
                        }
                        Text(
                            if (running) "You're on the mesh" else "Mesh is off",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                    }
                    Text(
                        if (running) "Nearby phones can see and reach you" else "Switch on to find people around you",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = onToggle,
                    enabled = canRun,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.Leaf,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.25f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Stat("$online", "online", Modifier.weight(1f))
                Stat("$inRange", "in range", Modifier.weight(1f))
                Stat("$met", "met so far", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.16f)).padding(Space.md),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun RadarCard(state: DashboardState, onShareLocation: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    if (state.hasMyFix) "rings: 10 m · 300 m · 10 km" else "you're in the middle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadarSweep(
                people = state.people.map { it.toBlip() },
                active = state.running,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(Space.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Pill("Online", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, dot = Palette.Online)
                Pill("In range", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, dot = Palette.Away)
                Pill("Seen before", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, dot = Palette.Offline)
            }
            AnimatedVisibility(!state.sharingLocation) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onShareLocation),
                ) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            "Share your location to see real distances. GPS works without internet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onShareLocation) { Text("Share") }
                    }
                }
            }
        }
    }
}

/**
 * Where a person sits on the radar. With both GPS positions we use the real direction and a
 * log distance scale (10 m, 300 m, 10 km rings); otherwise a stable pseudo-direction and
 * a ring that reflects how reachable they are.
 */
private fun Person.toBlip(): RadarBlip {
    val d = distanceM
    val b = bearingDeg
    val radius = if (d != null && b != null) {
        // Rings sit at 1/3, 2/3 and the edge: 10 m, ~300 m and 10 km.
        ((log10(d.coerceAtLeast(1.0)) + 0.5) / 4.5).toFloat().coerceIn(0.12f, 0.95f)
    } else when (presence) {
        Presence.ONLINE -> 0.42f
        Presence.IN_RANGE -> 0.66f
        Presence.OFFLINE -> 0.9f
    }
    val bearing = b ?: (abs(nodeId.hashCode()) % 360).toDouble()
    return RadarBlip(nodeId, avatar, bearing, radius, presenceColor(presence), faded = presence == Presence.OFFLINE)
}

@Composable
private fun BridgeCard(online: Boolean) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (online) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (online) Icons.Filled.Public else Icons.Filled.CloudOff,
                null,
                Modifier.size(32.dp),
                tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "You have internet" else "Off-grid mode",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (online) "Soon your phone can be the bridge that carries nearby friends' messages to the world."
                    else "No internet here, and that's fine. BlueMob talks phone to phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonRow(person: Person, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.avatar, person.name, person.nodeId, 48.dp, person.presence)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusLine(person),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            FilledTonalIconButton(onClick = onClick) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat with ${person.name}")
            }
        }
    }
}

private fun statusLine(p: Person): String {
    val parts = mutableListOf<String>()
    when (p.presence) {
        Presence.ONLINE -> {
            parts += "Online"
            p.distanceM?.let { parts += "${Geo.formatDistance(it)} away" }
            parts += when (p.quality) {
                LinkQuality.HIGH -> "fast Wi-Fi link"
                LinkQuality.MEDIUM -> "good link"
                LinkQuality.LOW, null -> "Bluetooth link"
            }
        }
        Presence.IN_RANGE -> {
            parts += "In range, connecting…"
            p.distanceM?.let { parts += "${Geo.formatDistance(it)} away" }
        }
        Presence.OFFLINE -> {
            parts += "Last seen ${TimeText.ago(p.lastSeen)}"
            p.distanceM?.let { parts += "was ${Geo.formatDistance(it)} away" }
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyPeople(running: Boolean, onTalkToSky: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text("🏕️", style = MaterialTheme.typography.displaySmall)
            Text("Quiet out here", style = MaterialTheme.typography.titleMedium)
            Text(
                if (running) "When someone nearby opens BlueMob, they'll appear on your radar."
                else "Switch on the mesh to look for people nearby.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            Button(onClick = onTalkToSky, colors = ButtonDefaults.buttonColors()) {
                Text("Say hi to Sky 🌤️")
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(action) }
        }
    }
}
