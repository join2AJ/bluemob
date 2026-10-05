package com.bluemob.app.ui.compass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.settings.Spot
import com.bluemob.app.ui.Person
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.Group
import com.bluemob.app.ui.components.GroupLabel
import com.bluemob.app.ui.components.LargeTitle
import com.bluemob.app.ui.components.SettingRow
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Space
import com.bluemob.app.util.Geo
import com.bluemob.app.util.cardinal
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

private data class Target(val id: String, val name: String, val emoji: String, val point: GeoPoint)

@Composable
fun CompassScreen(
    people: List<Person>,
    spots: List<Spot>,
    myLocation: GeoPoint?,
    headings: Flow<Float>,
    compassAvailable: Boolean,
    hasLocationPermission: Boolean,
    initialTarget: String?,
    contentPadding: PaddingValues,
    onHoldLocation: () -> Unit,
    onReleaseLocation: () -> Unit,
    onRequestLocation: () -> Unit,
    onSaveSpot: () -> Unit,
    onRemoveSpot: (String) -> Unit,
    trail: TrailUi = TrailUi(),
    trailActions: TrailActions = TrailActions(),
) {
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) onHoldLocation()
        onDispose { if (hasLocationPermission) onReleaseLocation() }
    }
    val heading by remember(headings) { headings }.collectAsStateWithLifecycle(initialValue = 0f)
    val targets = spots.map { Target(it.id, it.name, if (it.isBaseCamp) "⛺" else "📍", GeoPoint(it.lat, it.lon, 0f, it.time)) } +
        people.mapNotNull { p -> p.location?.let { Target(p.nodeId, if (p.lost != null) "${p.name} (lost)" else p.name, p.avatar ?: "🙂", it) } }
    var selected by rememberSaveable { mutableStateOf(initialTarget) }
    val target = targets.firstOrNull { it.id == selected } ?: targets.firstOrNull()
    val bearing = if (myLocation != null && target != null) Geo.bearingDeg(myLocation, target.point).toFloat() else null
    val distance = if (myLocation != null && target != null) Geo.distanceM(myLocation, target.point) else null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = contentPadding.calculateTopPadding(), bottom = 120.dp)) {
        item { LargeTitle("Compass", over = "Works with no signal · GPS + compass") }
        if (!hasLocationPermission) item {
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text("Allow location to navigate", style = MaterialTheme.typography.titleMedium)
                    Text("GPS works without internet. BlueMob only uses it on this screen, and to share your position if you turn that on.",
                        style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = onRequestLocation) { Text("Allow location") }
                }
            }
        }
        if (targets.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(targets, key = { it.id }) { t -> Chip("${t.emoji} ${t.name}", t.id == target?.id) { selected = t.id } }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Dial(heading, bearing, Modifier.fillMaxWidth(0.82f).aspectRatio(1f))
                Column(
                    Modifier.size(104.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(target?.emoji ?: "🧭", fontSize = 22.sp)
                    Text(distance?.let { Geo.formatDistance(it) } ?: "${heading.roundToInt()}°", style = MaterialTheme.typography.titleLarge)
                    Text(bearing?.let { "${cardinal(it.toDouble())} · ${it.roundToInt()}°" } ?: cardinal(heading.toDouble()),
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                }
            }
        }
        item {
            val rel = bearing?.let { ((it - heading) % 360 + 360) % 360 }
            Text(
                when {
                    !compassAvailable -> "This phone has no compass sensor. Use the sun or the guide's tips to find north."
                    target == null -> "Save a spot, or ask friends to share their location, to get directions."
                    myLocation == null -> "Waiting for a GPS fix… Step outside for a clear view of the sky."
                    rel!! < 15 || rel > 345 -> "Straight ahead · about ${((distance ?: 0.0) / 75).roundToInt().coerceAtLeast(1)} min walk"
                    rel < 180 -> "Turn right ${rel.roundToInt()}°"
                    else -> "Turn left ${(360 - rel).roundToInt()}°"
                },
                style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onSaveSpot, enabled = hasLocationPermission) { Text("+ Save this spot") }
                TextButton(onClick = trailActions.onBaseCamp, enabled = hasLocationPermission) { Text("⛺ Set base camp here") }
            }
        }
        item { GroupLabel("Trail & lost mode") }
        item { if (trail.on) TrailCard(trail, spots, myLocation, trailActions) else TrailOptIn(trailActions.onTrail) }
        item { Box(Modifier.padding(top = 12.dp)) { LostCard(trail, trailActions.onLost) } }
        if (spots.any { !it.isBaseCamp }) {
            item { GroupLabel("Saved spots") }
            item {
                Group {
                    spots.filterNot { it.isBaseCamp }.forEachIndexed { i, s ->
                        SettingRow(Icons.Outlined.Place, MaterialTheme.colorScheme.primary, s.name,
                            myLocation?.let { Geo.formatDistance(Geo.distanceM(it, GeoPoint(s.lat, s.lon, 0f, 0))) + " away" } ?: "Saved place", divider = i > 0) {
                            TextButton(onClick = { onRemoveSpot(s.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
        item { GroupLabel("How it works offline") }
        item {
            Group {
                SettingRow(Icons.Outlined.Place, MaterialTheme.colorScheme.primary, "GPS needs no internet", "Your phone hears satellites directly. Works in airplane mode.")
                SettingRow(Icons.Outlined.Explore, Extra.sky, "Compass from the phone's sensor", "The magnetometer gives your heading. No data needed.", divider = true)
                SettingRow(Icons.Outlined.Hub, Extra.ember, "Friends' positions over the mesh", "People who share their location appear here as targets.", divider = true)
                SettingRow(Icons.Outlined.Map, Color(0xFF7C6BD6), "Maps: coming next", "Download an area's map at home, then use it offline.", divider = true)
            }
        }
    }
}

@Composable
private fun Dial(heading: Float, bearing: Float?, modifier: Modifier) {
    val dial by animateFloatAsState(-heading, label = "dial")
    val needle by animateFloatAsState(((bearing ?: 0f) - heading), label = "needle")
    val measurer = rememberTextMeasurer()
    val line = Extra.line
    val ink3 = Extra.ink3
    val ink2 = Extra.ink2
    val rose = Extra.rose
    val surface = MaterialTheme.colorScheme.surface
    val pine = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(surface, r, c)
        drawCircle(line, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        rotate(dial, c) {
            for (i in 0 until 72) {
                val long = i % 6 == 0
                rotate(i * 5f, c) {
                    drawLine(if (i == 0) rose else ink3, Offset(c.x, c.y - r + (if (long) 10 else 14).dp.toPx()), Offset(c.x, c.y - r + 22.dp.toPx()),
                        (if (long) 2 else 1).dp.toPx())
                }
            }
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (l, a) ->
                rotate(a, c) {
                    val layout = measurer.measure(l, TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (l == "N") rose else ink2))
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - r + 28.dp.toPx()))
                }
            }
        }
        if (bearing != null) {
            rotate(needle, c) {
                val p = Path().apply {
                    moveTo(c.x, c.y - r + 40.dp.toPx())
                    lineTo(c.x + 18.dp.toPx(), c.y - r + 100.dp.toPx())
                    lineTo(c.x, c.y - r + 90.dp.toPx())
                    lineTo(c.x - 18.dp.toPx(), c.y - r + 100.dp.toPx())
                    close()
                }
                drawPath(p, pine)
            }
        }
    }
}

