package com.bluemob.app.ui.call

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemob.app.call.Call
import com.bluemob.app.call.CallManager
import com.bluemob.app.call.CallPhase
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private val Ink = Color(0xFF0B1A15)
private val Pine = Color(0xFF2E9E6A)
private val Rose = Color(0xFFE0545A)
private val Amber = Color(0xFFFFD27A)

/**
 * Full-screen call: ringing with pulsing rings, answering, the call itself with the other person's video, and a
 * walkie-talkie mode (hold to talk) that stays clear on weak links and never echoes on speaker.
 */
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
    onPtt: () -> Unit = {},
    onTalk: (Boolean) -> Unit = {},
    avatar: String? = null,
) {
    var front by rememberSaveable { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    val showVideo = call.phase == CallPhase.ACTIVE && remote != null
    // In a video call, controls hide after a few seconds; tap the picture to bring them back.
    LaunchedEffect(controls, showVideo) { if (showVideo && controls) { delay(5_000); controls = false } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF123A2E), Ink)))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls }) {
        if (showVideo) Image(remote!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (call.phase == CallPhase.ACTIVE && call.video && call.cameraOn && canUseCamera) {
            CameraFrames(front, wantsFrame, onFrame)
            local?.let {
                Image(it.asImageBitmap(), "You", Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopEnd).width(104.dp).height(140.dp)
                    .clip(RoundedCornerShape(16.dp)).border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            }
        }
        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(call.phase, call.startedAt) {
            while (call.phase == CallPhase.ACTIVE) { elapsed = (System.currentTimeMillis() - call.startedAt) / 1000; delay(1_000) }
        }

        AnimatedVisibility(!showVideo || controls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().then(if (showVideo) Modifier.background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.6f), Color.Transparent, Ink.copy(alpha = 0.7f)))) else Modifier)) {
                // Header
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinkChip(call)
                    if (!showVideo) {
                        Spacer(Modifier.height(36.dp))
                        Ringed(call.phase == CallPhase.OUTGOING || call.phase == CallPhase.INCOMING, call.theyTalking) {
                            Box(Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(avatar ?: call.name.take(1).uppercase(), fontSize = if (avatar != null) 56.sp else 48.sp, color = Color.White)
                            }
                        }
                    }
                    Text(call.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
                    Text(
                        when (call.phase) {
                            CallPhase.OUTGOING -> "Ringing…"
                            CallPhase.INCOMING -> if (call.video) "Incoming video call" else "Incoming voice call"
                            CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60) + (if (call.video && remote == null) " · waiting for video…" else "")
                            CallPhase.ENDED -> call.ended ?: "Call ended"
                        },
                        color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                    )
                    problem(call)?.let {
                        Text(it, color = Amber, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    if (call.phase == CallPhase.ACTIVE && call.theyTalking) Text("🎙 ${call.name} is talking", color = Pine, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                // Walkie-talkie button
                if (call.phase == CallPhase.ACTIVE && call.ptt) TalkButton(call.talking, call.theyTalking, onTalk, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
                // Controls
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (call.phase) {
                        CallPhase.INCOMING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundButton("✕", "Decline", Rose, onDecline, big = true)
                            RoundButton(if (call.video) "🎥" else "📞", "Answer", Pine, onAccept, big = true)
                        }
                        CallPhase.OUTGOING -> RoundButton("✕", "Cancel", Rose, onDecline, big = true)
                        CallPhase.ACTIVE -> {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Toggle(if (call.muted) "🔇" else "🎙", if (call.muted) "Unmute" else "Mute", call.muted, onMute)
                                Toggle("🔊", "Speaker", call.speaker, onSpeaker)
                                Toggle("📻", "Walkie-talkie", call.ptt, onPtt)
                                if (call.video) {
                                    Toggle(if (call.cameraOn) "📷" else "🚫", "Camera", call.cameraOn, onCamera)
                                    if (call.cameraOn) Toggle("🔄", "Flip", false) { front = !front }
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            RoundButton("✕", "End call", Rose, onDecline, big = true)
                        }
                        CallPhase.ENDED -> Unit
                    }
                }
            }
        }
    }
}

private fun problem(call: Call): String? = when {
    call.phase != CallPhase.ACTIVE -> null
    call.warning != null -> call.warning
    call.theyMuted -> "${call.name} muted their microphone"
    call.noAudio -> "Can't hear ${call.name} right now: the link is weak. Move closer, or try walkie-talkie."
    call.theyPtt && !call.theyTalking && !call.ptt -> "${call.name} is using walkie-talkie: they talk while holding their button"
    else -> null
}

@Composable
private fun LinkChip(call: Call) {
    if (call.phase == CallPhase.ENDED) return
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔒", fontSize = 12.sp)
        Text("  Phone to phone · ${call.link.ifBlank { "nearby" }} · no internet", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

/** Pulsing rings while ringing, or while the other person is talking in walkie-talkie mode. */
@Composable
private fun Ringed(active: Boolean, talking: Boolean, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(contentAlignment = Alignment.Center) {
        if (active || talking) for (k in 0 until 3) {
            val f = (p + k / 3f) % 1f
            Box(Modifier.size(120.dp).scale(1f + f * 0.9f).graphicsLayer { alpha = (1f - f) * 0.5f }
                .border(2.dp, if (talking) Pine else Color.White, CircleShape))
        }
        content()
    }
}

@Composable
private fun RoundButton(icon: String, label: String, color: Color, onClick: () -> Unit, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (big) 72.dp else 60.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = if (big) 28.sp else 24.sp, color = Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Toggle(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp, color = if (on) Ink else Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Hold to talk, let go to listen. */
@Composable
private fun TalkButton(talking: Boolean, theyTalking: Boolean, onTalk: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val talk by rememberUpdatedState(onTalk)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(132.dp).scale(if (talking) 1.08f else 1f).clip(CircleShape)
                .background(if (talking) Rose else if (theyTalking) Color.White.copy(alpha = 0.2f) else Pine)
                .border(4.dp, Color.White.copy(alpha = if (talking) 0.9f else 0.4f), CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        talk(true)
                        waitForUpOrCancellation()
                        talk(false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎙", fontSize = 34.sp)
                Text(if (talking) "Talking…" else "Hold to talk", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(if (talking) "Let go to listen" else "Walkie-talkie: one person talks at a time",
            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
