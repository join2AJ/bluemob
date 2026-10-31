package com.bluemob.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bluemob.app.i18n.Text
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.util.Tiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor

/**
 * A satellite picture around a position, with a pin and its accuracy circle. The pictures come over the internet
 * (and are kept a week on the phone); with no internet it says so, and the position text above still works.
 */
@Composable
fun SatelliteMap(lat: Double, lon: Double, accuracyM: Double?, modifier: Modifier = Modifier) {
    val zoom = Tiles.zoomFor(accuracyM)
    val (px, py) = remember(lat, lon, zoom) { Tiles.pixel(lat, lon, zoom) }
    val cx = floor(px / Tiles.SIZE).toInt(); val cy = floor(py / Tiles.SIZE).toInt()
    val tiles = remember(lat, lon, zoom) { mutableStateMapOf<Pair<Int, Int>, Bitmap>() }
    var failed by remember(lat, lon, zoom) { mutableStateOf(false) }
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    if (!preview) LaunchedEffect(lat, lon, zoom) {
        val wanted = (-1..1).flatMap { dx -> (-1..1).map { dy -> (cx + dx) to (cy + dy) } }
        var got = 0
        for (t in wanted) {
            val bmp = withContext(Dispatchers.IO) { load(context.cacheDir, zoom, t.first, t.second) }
            if (bmp != null) { tiles[t] = bmp; got++ }
        }
        failed = got == 0
    }
    val pinColor = Color(0xFFE5484D)
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C2A26))) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = density
            // Faint grid while pictures load or with no internet.
            if (tiles.isEmpty()) {
                val step = 32.dp.toPx()
                var x = 0f; while (x < size.width) { drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height)); x += step }
                var y = 0f; while (y < size.height) { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y)); y += step }
            }
            tiles.forEach { (t, bmp) ->
                val left = ((t.first * Tiles.SIZE - px) * scale + size.width / 2).toFloat()
                val top = ((t.second * Tiles.SIZE - py) * scale + size.height / 2).toFloat()
                val s = (Tiles.SIZE * scale).toInt()
                drawImage(bmp.asImageBitmap(), dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(s, s))
            }
            val c = Offset(size.width / 2, size.height / 2)
            accuracyM?.let { a ->
                val r = (a / Tiles.metresPerPixel(lat, zoom) * scale).toFloat().coerceIn(10.dp.toPx(), size.minDimension)
                drawCircle(pinColor.copy(alpha = 0.18f), r, c)
                drawCircle(pinColor.copy(alpha = 0.7f), r, c, style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(Color.White, 9.dp.toPx(), c)
            drawCircle(pinColor, 6.5.dp.toPx(), c)
        }
        if (tiles.isNotEmpty()) Text(Tiles.CREDIT, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp))
        else Text(if (failed || preview) "Satellite view needs internet. The position still works offline." else "Loading satellite view…",
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
    }
}

/** Where someone is: a label, the description, the satellite picture, and buttons for Google Maps. */
@Composable
fun PlaceCard(label: String, body: String, lat: Double, lon: Double, accuracyM: Double?, modifier: Modifier = Modifier, mapHeight: androidx.compose.ui.unit.Dp = 190.dp) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Extra.skyTint).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Extra.sky)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        SatelliteMap(lat, lon, accuracyM, Modifier.fillMaxWidth().height(mapHeight))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(Tiles.googleMaps(lat, lon)) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Map, null, Modifier.size(18.dp)); Text("Google Maps", Modifier.padding(start = 6.dp), maxLines = 1)
            }
            OutlinedButton(onClick = { open(Tiles.googleSatellite(lat, lon)) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Satellite, null, Modifier.size(18.dp)); Text("Satellite", Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}

private const val KEEP_MS = 7 * 24 * 3_600_000L

private fun load(cache: File, z: Int, x: Int, y: Int): Bitmap? = runCatching {
    val dir = File(cache, "satellite").apply { mkdirs() }
    val f = File(dir, "$z-$x-$y.jpg")
    if (f.exists() && System.currentTimeMillis() - f.lastModified() < KEEP_MS) BitmapFactory.decodeFile(f.path)?.let { return@runCatching it }
    val c = java.net.URL(Tiles.url(z, x, y)).openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 8_000; c.readTimeout = 12_000
    c.setRequestProperty("User-Agent", "BlueMob (Android)")
    try {
        if (c.responseCode != 200) return@runCatching null
        val bytes = c.inputStream.use { it.readBytes() }
        if (bytes.size > 512 * 1024) return@runCatching null
        f.writeBytes(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } finally { c.disconnect() }
}.getOrNull()
