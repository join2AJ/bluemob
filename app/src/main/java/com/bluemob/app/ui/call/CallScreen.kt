package com.bluemob.app.ui.call

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemob.app.call.Call
import com.bluemob.app.call.CallManager
import com.bluemob.app.call.CallPhase
import java.util.concurrent.Executors

/** Full-screen call: ringing, answering, and the call itself, with the other person's video when they send it. */
@Composable
fun CallScreen(
    call: Call,
    remote: Bitmap?,
    local: Bitmap?,
    canUseCamera: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onCamera: () -> Unit,
    wantsFrame: () -> Boolean,
    onFrame: (Bitmap) -> Unit,
) {
    var front by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E2A22), Color(0xFF07130F)))).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
        if (call.phase == CallPhase.ACTIVE && remote != null) {
            Image(remote.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (call.phase == CallPhase.ACTIVE && call.video && call.cameraOn && canUseCamera) {
            CameraFrames(front, wantsFrame, onFrame)
            local?.let {
                Image(it.asImageBitmap(), "You", Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopEnd).width(110.dp)
                    .clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            }
        }
        val elapsed by produceState(0L, call.phase, call.startedAt) {
            while (call.phase == CallPhase.ACTIVE) { value = (System.currentTimeMillis() - call.startedAt) / 1000; kotlinx.coroutines.delay(1_000) }
        }
        val showFace = !(call.phase == CallPhase.ACTIVE && remote != null)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = if (showFace) 72.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showFace) Box(Modifier.size(112.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(call.name.take(1).uppercase(), fontSize = 48.sp, color = Color.White)
            }
            Text(call.name, color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(top = 16.dp))
            Text(
                when (call.phase) {
                    CallPhase.OUTGOING -> "Ringing…"
                    CallPhase.INCOMING -> if (call.video) "Video call · nearby" else "Voice call · nearby"
                    CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60) + (if (call.video && remote == null) " · waiting for video…" else "")
                    CallPhase.ENDED -> call.ended ?: "Call ended"
                },
                color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
            if (call.phase != CallPhase.ENDED) Text("Phone to phone over Bluetooth / Wi-Fi · no internet",
                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            when (call.phase) {
                CallPhase.INCOMING -> {
                    RoundButton("✕", "Decline", Color(0xFFD94F55), onDecline)
                    RoundButton(if (call.video) "🎥" else "📞", "Answer", Color(0xFF2E9E6A), onAccept)
                }
                CallPhase.OUTGOING -> RoundButton("✕", "Cancel", Color(0xFFD94F55), onDecline)
                CallPhase.ACTIVE -> {
                    RoundButton(if (call.muted) "🔇" else "🎙", if (call.muted) "Unmute" else "Mute", Color.White.copy(alpha = if (call.muted) 0.35f else 0.15f), onMute)
                    RoundButton(if (call.speaker) "🔊" else "🔈", "Speaker", Color.White.copy(alpha = if (call.speaker) 0.35f else 0.15f), onSpeaker)
                    if (call.video) {
                        RoundButton(if (call.cameraOn) "📷" else "🚫", if (call.cameraOn) "Camera" else "Camera off", Color.White.copy(alpha = if (call.cameraOn) 0.35f else 0.15f), onCamera)
                        if (call.cameraOn) RoundButton("🔄", "Flip", Color.White.copy(alpha = 0.15f)) { front = !front }
                    }
                    RoundButton("✕", "End", Color(0xFFD94F55), onDecline)
                }
                CallPhase.ENDED -> Unit
            }
        }
    }
}

@Composable
private fun RoundButton(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 26.sp, color = Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Runs the camera while visible and hands upright frames to [onFrame], only when [wantsFrame] says one is due. */
@Composable
private fun CameraFrames(front: Boolean, wantsFrame: () -> Boolean, onFrame: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val want by rememberUpdatedState(wantsFrame)
    val frame by rememberUpdatedState(onFrame)
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner, front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { img ->
                try {
                    if (want()) frame(CallManager.rotate(img.toBitmap(), img.imageInfo.rotationDegrees, mirror = front))
                } catch (_: Exception) {
                } finally { img.close() }
            }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching { p.unbindAll(); p.bindToLifecycle(owner, selector, analysis) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
}
