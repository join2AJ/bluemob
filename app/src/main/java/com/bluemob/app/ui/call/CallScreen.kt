package com.bluemob.app.ui.call

import androidx.compose.animation.core.animateFloatAsState
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

private val Ink = Color(0xFF050B1A)
private val Glow = Color(0xFF3D8BFF)
private val GlowSoft = Color(0xFF6FB3FF)
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
    /** Declines with a short message, like "Can't talk now, I'll call you back". */
    onQuickReply: (String) -> Unit = {},
    /** (their voice, my voice) loudness 0..1: the waves move with whoever is speaking. */
    levels: () -> Pair<Float, Float> = { 0f to 0f },
) {
    var replies by remember { mutableStateOf(false) }
    if (replies) androidx.compose.material3.AlertDialog(
        onDismissRequest = { replies = false },
        title = { Text("Reply with a message") },
        text = {
            Column {
                QUICK_REPLIES.forEach { r ->
                    Text(r, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { replies = false; onQuickReply(r) }.padding(vertical = 12.dp, horizontal = 8.dp))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { replies = false }) { Text("Cancel") } },
    )
    var front by rememberSaveable { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    val showVideo = call.phase == CallPhase.ACTIVE && remote != null
    // In a video call, controls hide after a few seconds; tap the picture to bring them back.
    LaunchedEffect(controls, showVideo) { if (showVideo && controls) { delay(5_000); controls = false } }
    var level by remember { mutableStateOf(0f to 0f) }
    LaunchedEffect(call.phase) { while (call.phase == CallPhase.ACTIVE) { level = levels(); delay(80) } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1630), Ink, Color(0xFF02050C))))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls }) {
        if (showVideo) Image(remote!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else AmbientWaves(level.first.coerceAtLeast(level.second), Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp))
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
                        // Glowing waves round their picture: they breathe while ringing and swell with the voices.
                        GlowWave(ringing = call.phase == CallPhase.OUTGOING || call.phase == CallPhase.INCOMING, level = maxOf(level.first, level.second * 0.6f),
                            modifier = Modifier.size(260.dp)) {
                            Box(Modifier.size(112.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, GlowSoft.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(avatar ?: call.name.take(1).uppercase(), fontSize = if (avatar != null) 54.sp else 46.sp, color = Color.White)
                            }
                        }
                    }
                    Text(call.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = if (showVideo) 22.dp else 4.dp))
                    Text(
                        when (call.phase) {
                            CallPhase.OUTGOING -> when {
                                call.waking -> "Ringing ${call.name}'s phone (BlueMob was closed)…"
                                call.waiting -> "Waiting for ${call.name} to come online…"
                                else -> "Ringing…"
                            }
                            CallPhase.INCOMING -> if (call.video) "Incoming video call" else "Incoming voice call"
                            CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60) + (if (call.video && remote == null) " · waiting for video…" else "")
                            CallPhase.ENDED -> call.ended ?: "Call ended"
                        },
                        color = if (call.phase == CallPhase.ACTIVE) GlowSoft else Color.White.copy(alpha = 0.75f), fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
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
                        CallPhase.INCOMING -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RoundButton("✕", "Decline", Rose, onDecline, big = true)
                                RoundButton(if (call.video) "🎥" else "📞", "Answer", Color(0xFF2E9E6A), onAccept, big = true)
                            }
                            Text("💬  Reply with a message", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                                modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { replies = true }.padding(horizontal = 16.dp, vertical = 8.dp))
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
    call.videoOnly -> "${call.name}'s picture is coming but not their voice. Restarting their microphone…\n${call.voiceStats}"
    call.noAudio -> "Can't hear ${call.name} right now: the link is weak. Move closer, or try walkie-talkie.\n${call.voiceStats}"
    call.theyPtt && !call.theyTalking && !call.ptt -> "${call.name} is using walkie-talkie: they talk while holding their button"
    else -> null
}

@Composable
private fun LinkChip(call: Call) {
    if (call.phase == CallPhase.ENDED) return
    val link = call.link.ifBlank { "nearby" }
    val nearby = link == "Wi-Fi" || link == "Bluetooth" || link == "Wi-Fi or Bluetooth" || link == "nearby"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when { link.startsWith("Internet") -> "🌐"; link.startsWith("Through") -> "🔗"; link == "Wi-Fi" -> "📶"; else -> "ᛒ" }, fontSize = 12.sp, color = Color.White)
            Text("  " + if (nearby) "Phone to phone · $link · no internet" else link, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        // Nearby links are encrypted by the radio link itself; anything that goes through others needs end-to-end.
        if (call.e2e || nearby) Text("🔒 " + if (call.e2e) "End-to-end encrypted" else "Encrypted link", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private val QUICK_REPLIES = listOf("Can't talk now, I'll call you back.", "I'm on my way.", "Send me a message instead.", "Is everything OK?")

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
        Box(Modifier.size(if (big) 76.dp else 60.dp).graphicsLayer { shadowElevation = 24f; shape = CircleShape; ambientShadowColor = color; spotShadowColor = color }
            .clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.75f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = if (big) 28.sp else 24.sp, color = Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A glass circle; lit blue while on. */
@Composable
private fun Toggle(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape)
            .background(if (on) Brush.radialGradient(listOf(Glow, Glow.copy(alpha = 0.55f))) else Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, if (on) GlowSoft else Color.White.copy(alpha = 0.18f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp, color = Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * Glowing light-blue waves round the caller: several thin closed curves, each gently deformed, slowly turning.
 * While ringing they breathe; in a call they swell with the voice ([level] 0..1).
 */
@Composable
private fun GlowWave(ringing: Boolean, level: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(7_000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400), RepeatMode.Reverse), label = "breathe")
    val loud by animateFloatAsState(level, tween(120), label = "loud")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension * 0.30f
            val amp = size.minDimension * (0.025f + (if (ringing) 0.03f * breathe else 0f) + 0.09f * loud)
            // A soft halo behind.
            drawCircle(Brush.radialGradient(listOf(Glow.copy(alpha = 0.28f + 0.25f * loud), Color.Transparent), c, base * 1.7f), base * 1.7f, c)
            for (k in 0 until 9) {
                val path = androidx.compose.ui.graphics.Path()
                val off = k * 0.35f
                for (i in 0..120) {
                    val a = i / 120f * 2 * Math.PI.toFloat()
                    val r = base + k * 2.2f + amp * (kotlin.math.sin(3 * a + phase + off) * 0.6f + kotlin.math.sin(5 * a - phase * 1.3f + off) * 0.4f)
                    val x = c.x + r * kotlin.math.cos(a); val y = c.y + r * kotlin.math.sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, (if (k % 3 == 0) GlowSoft else Glow).copy(alpha = 0.55f - k * 0.045f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f))
            }
        }
        content()
    }
}

/** Slow light-blue waves along the bottom of a voice call, rising with the voices. */
@Composable
private fun AmbientWaves(level: Float, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ambient")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "p")
    val loud by animateFloatAsState(level, tween(150), label = "l")
    androidx.compose.foundation.Canvas(modifier) {
        for (k in 0 until 7) {
            val path = androidx.compose.ui.graphics.Path()
            val amp = size.height * (0.10f + 0.05f * k / 7f + 0.25f * loud)
            val mid = size.height * (0.55f + k * 0.03f)
            for (i in 0..80) {
                val x = size.width * i / 80f
                val y = mid + amp * kotlin.math.sin(x / size.width * 2.5f * Math.PI.toFloat() + phase + k * 0.5f) * (0.6f + 0.4f * kotlin.math.sin(phase * 0.7f + k).toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Glow.copy(alpha = 0.32f - k * 0.035f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
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
