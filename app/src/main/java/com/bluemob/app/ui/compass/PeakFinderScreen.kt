package com.bluemob.app.ui.compass

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemob.app.contacts.GeoPoint
import com.bluemob.app.i18n.RawText
import com.bluemob.app.i18n.Text
import com.bluemob.app.peaks.Peaks
import com.bluemob.app.peaks.SeenPeak
import com.bluemob.app.ui.components.Chip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much of the scene the back camera shows across the screen (portrait), and up and down. */
private const val HFOV = 52f
private const val VFOV = 80f

/**
 * Peak finder: hold the phone up and the names of the mountains in front of you appear over the camera, with their
 * height and distance. Works offline from the built-in list or peaks downloaded for the area beforehand.
 */
@Composable
fun PeakFinderScreen(
    here: GeoPoint?,
    altitudeM: Double?,
    saved: Peaks.Saved?,
    pose: Flow<Pair<Float, Float>>,
    cameraAllowed: Boolean,
    onAskCamera: () -> Unit,
    onAskLocation: () -> Unit,
    /** Downloads peaks around here; returns an error to show, or null. */
    onDownload: suspend () -> String?,
    onBack: () -> Unit,
    /** Previews only: a fixed direction to look. */
    fixedPose: Pair<Float, Float>? = null,
) {
    val preview = LocalInspectionMode.current
    var az by remember { mutableStateOf(fixedPose?.first ?: 0f) }
    var pitch by remember { mutableStateOf(fixedPose?.second ?: 0f) }
    if (fixedPose == null) LaunchedEffect(pose) { pose.collect { (a, p) -> az = a; pitch = p } }
    var rangeKm by rememberSaveable { mutableStateOf(100) }
    var useCamera by rememberSaveable { mutableStateOf(true) }
    var picked by remember { mutableStateOf<SeenPeak?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val all = remember(saved) { (saved?.peaks.orEmpty() + Peaks.BUILT_IN).distinctBy { it.name.lowercase() to (it.lat * 20).roundToInt() } }
    val myEle = altitudeM ?: 0.0
    val seen = remember(here?.lat, here?.lon, myEle.roundToInt() / 20, rangeKm, all) {
        if (here == null) emptyList() else Peaks.around(all, here.lat, here.lon, myEle, rangeKm * 1000.0)
    }
    // The compass gives magnetic north; peaks are placed by true north.
    val declination = remember(here?.lat, here?.lon) {
        here?.let { runCatching { android.hardware.GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), myEle.toFloat(), System.currentTimeMillis()).declination }.getOrNull() } ?: 0f
    }
    val heading = (az + declination + 360) % 360

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraAllowed && useCamera && !preview) CameraBackground(Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16324F), Color(0xFF4F7CA8), Color(0xFF9DB7C9), Color(0xFF3E4A3F)))))

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current
            fun x(bearing: Double) = wPx / 2 + (Peaks.angleDiff(bearing, heading.toDouble()) / HFOV * wPx).toFloat()
            fun y(elev: Double) = hPx / 2 - ((elev - pitch) / VFOV * hPx).toFloat()
            // The level horizon, and a soft line across it.
            Canvas(Modifier.fillMaxSize()) {
                val hy = y(0.0)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, hy), Offset(size.width, hy), 2f)
            }
            // Labels: nearest first, stacked in rows so they don't cover each other.
            val visible = seen.filter { abs(Peaks.angleDiff(it.bearingDeg, heading.toDouble())) < HFOV / 2 + 3 }.take(25)
            val rows = ArrayList<MutableList<ClosedFloatingPointRange<Float>>>()
            val labelW = with(density) { 132.dp.toPx() }
            val rowH = with(density) { 46.dp.toPx() }
            val top = with(density) { 96.dp.toPx() }
            visible.sortedByDescending { it.peak.eleM }.forEach { sp ->
                val px = x(sp.bearingDeg); val py = y(sp.elevDeg)
                val lx = (px - labelW / 2).coerceIn(4f, (wPx - labelW - 4f).coerceAtLeast(4f))
                val span = lx..(lx + labelW)
                var row = rows.indexOfFirst { r -> r.none { it.start < span.endInclusive && span.start < it.endInclusive } }
                if (row == -1) { rows.add(mutableListOf()); row = rows.size - 1 }
                if (row > 5) return@forEach
                rows[row] += span
                val ly = top + row * rowH
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(px, ly + rowH * 0.85f), Offset(px, py), 2f)
                    drawCircle(Color.White, 7f, Offset(px, py)); drawCircle(Color(0xFFE8892B), 4.5f, Offset(px, py))
                }
                Column(Modifier.offset { IntOffset(lx.roundToInt(), ly.roundToInt()) }.widthIn(max = 132.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = if (picked?.peak == sp.peak) 0.8f else 0.55f))
                    .clickable { picked = sp }.padding(horizontal = 8.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RawText(sp.peak.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
                    Text("%,d m · %s".format(sp.peak.eleM, km(sp.distM)), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            }
        }

        // Top: back, title, and how many peaks are around.
        Row(Modifier.fillMaxWidth().statusBarsPadding().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Peak finder", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(if (here == null) "Waiting for your position…" else "${seen.size} peaks within $rangeKm km · facing ${heading.roundToInt()}° ${cardinal(heading.toDouble())}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
            if (cameraAllowed) IconButton(onClick = { useCamera = !useCamera }) { Icon(Icons.Rounded.CameraAlt, if (useCamera) "Hide camera" else "Show camera", tint = Color.White) }
        }

        // Bottom: what's off to the sides, the chosen peak, the data, and the range.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .navigationBarsPadding().padding(14.dp)) {
            val left = seen.count { Peaks.angleDiff(it.bearingDeg, heading.toDouble()) < -HFOV / 2 }
            val right = seen.count { Peaks.angleDiff(it.bearingDeg, heading.toDouble()) > HFOV / 2 }
            Row(Modifier.fillMaxWidth()) {
                if (left > 0) Text("← $left more", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Box(Modifier.weight(1f))
                if (right > 0) Text("$right more →", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            picked?.let { p ->
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.95f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Landscape, null, tint = Color(0xFF2F6FB0), modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        RawText(p.peak.name, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                        Text("%,d m high · %s away · %d° %s".format(p.peak.eleM, km(p.distM), p.bearingDeg.roundToInt(), cardinal(p.bearingDeg)) +
                            (if (altitudeM != null) " · %,d m above you".format((p.peak.eleM - altitudeM).roundToInt()) else ""),
                            style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    }
                    IconButton(onClick = { picked = null }) { Icon(Icons.Rounded.Close, "Close", tint = Color.DarkGray) }
                }
            }
            if (here == null) {
                Text("Turn on location so BlueMob knows where you are. GPS works without signal.", style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                Chip("Turn on location", true) { onAskLocation() }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(30, 100, 250).forEach { r -> Chip("$r km", rangeKm == r) { rangeKm = r } }
                if (!cameraAllowed) Chip("Use camera", false) { onAskCamera() }
            }
            Text(when {
                saved != null -> "${saved.peaks.size} peaks downloaded for this area " + java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(saved.at)) + ", plus famous peaks"
                else -> "Only famous peaks so far. Download the peaks around you while you have internet; they stay for offline."
            }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 10.dp))
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD27A), modifier = Modifier.padding(top = 4.dp)) }
            if (here != null) Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                .clickable(enabled = !busy) {
                    busy = true; note = null
                    scope.launch { note = onDownload() ?: "Done: peaks around you are saved on this phone."; busy = false }
                }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudDownload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(if (busy) "Downloading…" else if (saved != null) "Update peaks around me" else "Download peaks around me", style = MaterialTheme.typography.labelLarge,
                    color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            Text("Names: © OpenStreetMap contributors. Hold the phone upright and away from metal. A nearer ridge can hide a peak.",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun km(m: Double) = if (m < 10_000) "%.1f km".format(m / 1000) else "${(m / 1000).roundToInt()} km"

private fun cardinal(b: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((b + 22.5) % 360) / 45).toInt()]

@Composable
private fun CameraBackground(modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { Preview.Builder().build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ runCatching { val p = future.get(); provider = p; p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview) } }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbind(preview) } }
    }
    AndroidView({ c -> PreviewView(c).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; preview.setSurfaceProvider(it.surfaceProvider) } }, modifier)
}
