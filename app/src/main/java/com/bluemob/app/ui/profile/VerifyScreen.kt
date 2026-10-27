package com.bluemob.app.ui.profile

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bluemob.app.i18n.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemob.app.ui.AppViewModel.ScanResult
import com.bluemob.app.ui.components.Chip
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.Executors

/**
 * Checks that a chat really is with the person in front of you: scan each other's QR code, or compare the 60-digit
 * safety number aloud (in person or on a video call).
 */
@Composable
fun VerifyScreen(
    name: String,
    verified: Boolean,
    myCode: String,
    safetyNumber: String?,
    onBack: () -> Unit,
    onScanned: (String) -> ScanResult,
    onSetVerified: (Boolean) -> Unit,
    askCamera: (() -> Unit) -> Unit,
) {
    val first = name.substringBefore(" ")
    var scanning by rememberSaveable { mutableStateOf(false) }
    var note by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    SubScreen("Verify $first", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (verified) "✓ $first is verified" else "Is this really $first?", style = MaterialTheme.typography.headlineSmall,
                    color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(if (verified) "You checked in person that this chat is with them. Their messages and calls are end-to-end encrypted with their own key."
                    else "Meet $first (or video call) and scan each other's code. Then nobody can pretend to be them.",
                    style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Chip("My code", !scanning) { scanning = false }
                Chip("Scan their code", scanning) { askCamera { scanning = true } }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (scanning) {
                    QrScanner(Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(MaterialTheme.shapes.large)) { text ->
                        note = when (onScanned(text)) {
                            ScanResult.VERIFIED -> { scanning = false; "✓ Verified! This is really $first." to true }
                            ScanResult.WRONG_PERSON -> "That's someone else's code, not $first's." to false
                            ScanResult.NOT_BLUEMOB -> "That isn't a BlueMob code." to false
                        }
                    }
                } else {
                    val qr = remember(myCode) { qrBitmap(myCode) }
                    Box(Modifier.clip(MaterialTheme.shapes.large).background(Color.White).padding(14.dp)) {
                        Image(qr.asImageBitmap(), "Your verification QR code", Modifier.size(230.dp), filterQuality = FilterQuality.None)
                    }
                }
            }
        }
        note?.let { (text, good) ->
            item { Text(text, style = MaterialTheme.typography.titleMedium, color = if (good) MaterialTheme.colorScheme.primary else Extra.rose,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
        }
        item {
            Text(if (scanning) "Point the camera at $first's code (You → their name → Verify)." else "Ask $first to open your chat → your name → Verify → Scan their code.",
                style = MaterialTheme.typography.bodySmall, color = Extra.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp).clip(MaterialTheme.shapes.large).background(Extra.sand).padding(16.dp)) {
                Text("Or compare the safety number", style = MaterialTheme.typography.titleSmall)
                if (safetyNumber == null) {
                    Text("It appears once you've exchanged a message with $first, or after scanning their code.", style = MaterialTheme.typography.bodyMedium, color = Extra.ink2,
                        modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text("$first's phone shows the same 60 digits for this chat. Read them out to each other.", style = MaterialTheme.typography.bodySmall, color = Extra.ink2,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                    safetyNumber.split(" ").chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(vertical = 3.dp)) }
                        }
                    }
                    if (verified) OutlinedButton(onClick = { onSetVerified(false); note = null }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Remove verification") }
                    else Button(onClick = { onSetVerified(true); note = "✓ Verified." to true }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("The numbers match: mark verified") }
                }
            }
        }
    }
}

/** A QR code as a bitmap, one pixel per module (drawn sharp, scaled up). */
fun qrBitmap(text: String): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

/** The back camera, reading QR codes; [onCode] gets each code read (on the main thread). */
@Composable
internal fun QrScanner(modifier: Modifier, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) } }
    var last by remember { mutableStateOf("") }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember { ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        analysis.setAnalyzer(executor) { img ->
            runCatching {
                val y = img.planes[0]
                val w = img.width; val h = img.height
                val data = ByteArray(w * h)
                val buf = y.buffer
                for (row in 0 until h) { buf.position(row * y.rowStride); buf.get(data, row * w, w) }
                val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)))).text
                ContextCompat.getMainExecutor(context).execute { if (text != last) { last = text; onCode(text) } }
            }
            reader.reset()
            img.close()
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get(); provider = p
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbind(preview, analysis) }; analysis.clearAnalyzer(); executor.shutdown() }
    }
    AndroidView({ c -> PreviewView(c).also { preview.setSurfaceProvider(it.surfaceProvider) } }, modifier)
}
