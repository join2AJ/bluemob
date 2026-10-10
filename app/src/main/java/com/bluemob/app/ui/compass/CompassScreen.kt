package com.bluemob.app.ui.compass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloat
import com.bluemob.app.trail.TrailMath
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
import androidx.compose.material3.Surface
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

private data class Target(val id: String, val name: String, val emoji: String, val point: GeoPoint, val person: Person? = null)

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
    /** Saves where we are, under the name the user gives it. */
    onSaveSpot: (String) -> Unit,
    onRemoveSpot: (String) -> Unit,
    trail: TrailUi = TrailUi(),
    trailActions: TrailActions = TrailActions(),
    /** Asks a lost or SOS person's phone to whistle and flash. */
    onRing: (String) -> Unit = {},
    /** Height above sea level from GPS. */
    altitude: Double? = null,
    /** The compass sensor's accuracy (SensorManager.SENSOR_STATUS_*). */
    compassAccuracy: Int = 3,
    /** Share where I am (coordinates and a map link) with any app. */
    onShareLocation: (GeoPoint) -> Unit = {},
) {
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) onHoldLocation()
        onDispose { if (hasLocationPermission) onReleaseLocation() }
    }
    val heading by remember(headings) { headings }.collectAsStateWithLifecycle(initialValue = 0f)
    // "Save this spot" asks for a name; the date and time are kept with it.
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        val stamp = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date()) }
        var spotName by rememberSaveable { mutableStateOf("Spot ${spots.count { !it.isBaseCamp } + 1}") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save this spot") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(spotName, { spotName = it.take(40) }, label = { Text("Name") }, singleLine = true,
                        placeholder = { Text("e.g. Water source, Car park, Camp 2") })
                    Text("Saved with today's date and time: $stamp", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { Button(onClick = { onSaveSpot(spotName.trim().ifBlank { "Spot" }); naming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    val targets = spots.map { Target(it.id, it.name, if (it.isBaseCamp) "⛺" else "📍", GeoPoint(it.lat, it.lon, 0f, it.time)) } +
        people.mapNotNull { p -> p.location?.let { Target(p.nodeId, if (p.lost != null) "${p.name} (lost)" else p.name, p.avatar ?: "🙂", it, p) } }
    var selected by rememberSaveable { mutableStateOf(initialTarget) }
    // "Retrace my trail": the target is a point on our own trail a little way back, so we walk back the way we came.
    val retrace = if (selected == RETRACE && myLocation != null) TrailMath.retracePoint(trail.points.filter { !it.estimated }.map { GeoPoint(it.lat, it.lon, it.accuracyM, it.time) }, myLocation)
        ?.let { Target(RETRACE, "Back along my trail", "↩", it) } else null
    val target = retrace ?: targets.firstOrNull { it.id == selected } ?: targets.firstOrNull()
    val now = remember { System.currentTimeMillis() }
    val sun = myLocation?.let { com.bluemob.app.util.SunMoon.sun(System.currentTimeMillis(), it.lat, it.lon) }
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
                Dial(heading, bearing, Modifier.fillMaxWidth(0.82f).aspectRatio(1f), sunDeg = sun?.takeIf { it.altitudeDeg > -2 }?.azimuthDeg?.toFloat())
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
            // Walking time at an easy 4.5 km/h, and height above sea level.
            val extras = listOfNotNull(
                distance?.let { "🚶 about ${WalkTime.words(it)}" },
                altitude?.let { "⛰ ${it.roundToInt()} m above sea level" },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = Extra.ink3, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        // The compass needs a figure-8 wave when the phone says it's unsure.
        if (compassAvailable && compassAccuracy <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW) item { CalibrateCard() }
        target?.person?.let { p -> item { PersonFix(p, target.point, distance, onRing) } }
        // Back to base: straight to base camp, or back along the way you came.
        val base = spots.firstOrNull { it.isBaseCamp }
        if (base != null || trail.points.size > 2) item {
            Group(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Back to base", style = MaterialTheme.typography.titleMedium)
                    base?.let { b ->
                        val d = myLocation?.let { Geo.distanceM(it, GeoPoint(b.lat, b.lon, 0f, 0)) }
                        Text("⛺ Base camp" + (d?.let { " · ${Geo.formatDistance(it)} · ${WalkTime.words(it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (base != null) Chip("⛺ Straight to camp", selected == base.id) { selected = base.id }
                        if (trail.points.size > 2) Chip("↩ Retrace my trail", selected == RETRACE) { selected = RETRACE }
                    }
                    if (selected == RETRACE) Text("The arrow points a little way back along your own path. Follow it, and it moves on as you go: you walk back exactly the way you came.",
                        style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        // Daylight: the most important number outdoors.
        if (myLocation != null) item { SunCard(myLocation, now) }
        if (myLocation != null) item {
            Surface(onClick = { onShareLocation(myLocation) }, shape = MaterialTheme.shapes.large, color = Extra.skyTint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📤", fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Share my location", style = MaterialTheme.typography.titleMedium)
                        Text(Geo.formatLatLon(myLocation.lat, myLocation.lon) + " · ±${myLocation.accuracyM.roundToInt()} m", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // A map of where everything is, even without the trail on.
        if (!trail.on && myLocation != null && (spots.isNotEmpty() || people.any { it.location != null })) {
            item { GroupLabel("Map") }
            item {
                Group {
                    TrailMap(emptyList(), spots, myLocation, null, Modifier.padding(12.dp).fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium),
                        others = people.mapNotNull { p -> p.location?.let { (p.avatar ?: "🙂") to it } })
                    Text("You, your saved spots and friends sharing their location. No map download needed.", style = MaterialTheme.typography.bodySmall, color = Extra.ink3,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { naming = true }) { Text("+ Save this spot") }
                TextButton(onClick = trailActions.onBaseCamp) { Text("⛺ Set base camp here") }
            }
        }
        item { GroupLabel("Trail & lost mode") }
        item { if (trail.on) TrailCard(trail, spots, myLocation, trailActions) else TrailOptIn(trailActions.onTrail, trailActions.onTrips) }
        item { Box(Modifier.padding(top = 12.dp)) { LostCard(trail, trailActions.onLost) } }
        if (spots.any { !it.isBaseCamp }) {
            item { GroupLabel("Saved spots") }
            item {
                Group {
                    spots.filterNot { it.isBaseCamp }.forEachIndexed { i, s ->
                        SettingRow(Icons.Outlined.Place, MaterialTheme.colorScheme.primary, s.name,
                            listOfNotNull(myLocation?.let { Geo.formatDistance(Geo.distanceM(it, GeoPoint(s.lat, s.lon, 0f, 0))) + " away" },
                                "saved " + java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(s.time))).joinToString(" · "), divider = i > 0) {
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
                SettingRow(Icons.Outlined.Map, Color(0xFF7C6BD6), "Map without downloads", "You, spots, your trail and friends drawn to scale: no map tiles needed.", divider = true)
            }
        }
    }
}

@Composable
private fun Dial(heading: Float, bearing: Float?, modifier: Modifier, sunDeg: Float? = null) {
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
        // The sun on the dial's edge, so you can check the compass against it (and walk by it).
        if (sunDeg != null) rotate(dial + sunDeg, c) {
            val layout = measurer.measure("☀", TextStyle(fontSize = 18.sp, color = Color(0xFFE8A33A)))
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - r - layout.size.height / 2 + 4.dp.toPx()))
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


/**
 * How good the position we're steering to is, and what else helps find them: how old it is, whether it's GPS
 * or estimated from their steps, whether they're within radio range right now, and a button to ring their phone.
 */
@Composable
private fun PersonFix(p: Person, point: GeoPoint, distance: Double?, onRing: (String) -> Unit) {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(5_000); value = System.currentTimeMillis() }
    }
    val lost = p.lost
    val age = now - (lost?.at ?: point.time)
    val acc = lost?.uncertaintyM?.roundToInt() ?: point.accuracyM.roundToInt()
    val source = when {
        lost == null -> "their GPS"
        lost.gps -> "their GPS"
        else -> "estimated from their steps since GPS dropped ${agoShort(lost.fixAt, now)}"
    }
    val stale = age > 2 * 60_000
    Group {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "Position from ${agoShort(lost?.at ?: point.time, now)} · ±$acc m · $source",
                style = MaterialTheme.typography.bodyMedium, color = if (stale) MaterialTheme.colorScheme.error else Extra.ink2,
            )
            if (stale) Text(
                "This is old. They may have moved; it updates every 15 s while they're in lost mode and in range of someone.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 4.dp),
            )
            if (p.presence == com.bluemob.app.ui.Presence.ONLINE) Text(
                "📶 Connected to them directly" + when (p.quality) {
                    com.bluemob.app.mesh.LinkQuality.HIGH -> " over Wi-Fi: they're within about 100–200 m."
                    else -> " over Bluetooth: they're within about 10–100 m. Call out and listen."
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            ) else if (distance != null && distance < 30) Text(
                "You're within GPS accuracy of them. Stop, look around and call their name.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )
            if (p.lost != null || p.sos) Button(onClick = { onRing(p.nodeId) }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                Text("🔔 Ring their phone (whistle + flash)")
            }
        }
    }
}

private fun agoShort(then: Long, now: Long): String {
    val sec = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        sec < 10 -> "a few seconds ago"
        sec < 60 -> "$sec s ago"
        else -> com.bluemob.app.util.TimeText.ago(then, now)
    }
}


private const val RETRACE = "retrace"

/** Walking time at an easy pace. */
object WalkTime {
    fun words(m: Double): String {
        val min = (m / 75.0).roundToInt().coerceAtLeast(1) // 4.5 km/h
        return if (min < 60) "$min min walk" else "${min / 60} h ${min % 60} min walk"
    }
}

/** Sunrise, sunset, daylight left, where the sun is, and tonight's moon. */
@Composable
private fun SunCard(me: GeoPoint, now: Long) {
    val t = com.bluemob.app.util.SunMoon.times(now, me.lat, me.lon)
    val pos = com.bluemob.app.util.SunMoon.sun(now, me.lat, me.lon)
    val (moon, moonWords) = com.bluemob.app.util.SunMoon.moonWords(com.bluemob.app.util.SunMoon.moonPhase(now))
    val fmt = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val up = pos.altitudeDeg > -0.833
    val left = t.set?.let { it - now }
    val warn = up && left != null && left in 0..90 * 60_000L
    Surface(shape = MaterialTheme.shapes.large, color = if (warn) Extra.emberTint else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (warn) Extra.ember else Extra.line), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(if (up) "☀️  Daylight" else "🌙  Night", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    t.set == null -> "The sun doesn't set or rise here today."
                    up && left != null && left > 0 -> "${com.bluemob.app.util.SunMoon.span(left)} of light left · sunset ${fmt.format(java.util.Date(t.set))}" +
                        if (warn) ". Head back or make camp now." else ""
                    else -> "Sunrise ${t.rise?.let { fmt.format(java.util.Date(if (it < now) it + 86_400_000L else it)) } ?: "—"}"
                },
                style = MaterialTheme.typography.bodyMedium, color = if (warn) Extra.ember else Extra.ink2, modifier = Modifier.padding(top = 4.dp),
            )
            if (up) Text("Sun at ${pos.azimuthDeg.roundToInt()}° ${cardinal(pos.azimuthDeg)}, ${pos.altitudeDeg.roundToInt()}° up. It's the ☀ on the dial: if your compass disagrees, trust the sun.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, modifier = Modifier.padding(top = 4.dp))
            Text("$moon  $moonWords", style = MaterialTheme.typography.bodySmall, color = Extra.ink2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** "Wave the phone in a figure 8", with the movement drawn. */
@Composable
private fun CalibrateCard() {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "fig8").animateFloat(0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val ember = Extra.ember
    val ink3 = Extra.ink3
    Surface(shape = MaterialTheme.shapes.large, color = Extra.emberTint, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(84.dp, 56.dp)) {
                val w = size.width / 2.4f
                val h = size.height / 2.4f
                val path = Path()
                for (i in 0..64) {
                    val a = i / 64.0 * 2 * Math.PI
                    val x = center.x + w * kotlin.math.sin(a).toFloat()
                    val y = center.y + h * kotlin.math.sin(2 * a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, ink3, style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                val a = t * 2 * Math.PI
                val p = Offset(center.x + w * kotlin.math.sin(a).toFloat(), center.y + h * kotlin.math.sin(2 * a).toFloat())
                drawRoundRect(ember, Offset(p.x - 7.dp.toPx(), p.y - 11.dp.toPx()), androidx.compose.ui.geometry.Size(14.dp.toPx(), 22.dp.toPx()),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Calibrate the compass", style = MaterialTheme.typography.titleMedium)
                Text("Wave your phone in a figure 8 a few times, away from metal, cars and power lines.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
        }
    }
}
