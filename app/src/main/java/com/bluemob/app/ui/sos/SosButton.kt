package com.bluemob.app.ui.sos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.theme.Extra
import kotlin.math.ceil

/** How long the button stays armed after the first tap. */
const val SOS_ARM_MS = 4_000

/**
 * The big SOS button. Ripples spread out from it gently while idle. The first tap arms it: the ripples speed up
 * and a white ring counts down; a second tap within [SOS_ARM_MS] sends. Two taps so it can't go off in a pocket.
 */
@Composable
fun SosButton(armed: Boolean, onTap: () -> Unit, onDisarm: () -> Unit, modifier: Modifier = Modifier, still: Boolean = false) {
    val rose = Extra.rose
    val t = rememberInfiniteTransition(label = "sos")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (armed) 1100 else 2600, easing = LinearEasing)), label = "ripple")
    val breath by t.animateFloat(1f, if (armed) 1.05f else 1.025f, infiniteRepeatable(tween(if (armed) 450 else 1600), RepeatMode.Reverse), label = "breath")
    val countdown = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        if (armed) {
            countdown.snapTo(1f)
            countdown.animateTo(0f, tween(SOS_ARM_MS, easing = LinearEasing))
            onDisarm()
        } else countdown.snapTo(1f)
    }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val phase = if (still) 0.35f else ripple

    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        // Ripples and halo.
        Canvas(Modifier.fillMaxSize()) {
            val r0 = 92.dp.toPx()
            val reach = 44.dp.toPx()
            for (k in 0 until 3) {
                val p = (phase + k / 3f) % 1f
                drawCircle(rose.copy(alpha = (1f - p) * (if (armed) 0.30f else 0.18f)), radius = r0 + reach * p)
            }
            drawCircle(rose.copy(alpha = 0.10f), radius = r0 + 12.dp.toPx())
        }
        Box(
            Modifier.size(184.dp).scale((if (still) 1f else breath) * (if (pressed) 0.95f else 1f))
                .shadow(20.dp, CircleShape, spotColor = rose, ambientColor = rose)
                .clip(CircleShape)
                .clickable(interactionSource = press, indication = null, onClick = onTap)
                .semantics { contentDescription = if (armed) "Tap again to send SOS" else "Send SOS" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * 0.36f, size.height * 0.30f)
                val base = if (armed) listOf(Color(0xFFF48A90), Color(0xFFE0454C), Color(0xFF8E2229)) else listOf(Color(0xFFF0727A), rose, Color(0xFFA8323A))
                drawCircle(Brush.radialGradient(base, center = c, radius = size.width * 0.85f))
                // Soft glass highlight and an inner rim.
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), center = c, radius = size.width * 0.38f))
                val inset = 9.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.28f), radius = size.width / 2 - inset, style = Stroke(1.5.dp.toPx()))
                if (armed) {
                    val w = 5.dp.toPx()
                    val pad = inset
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(pad, pad), Size(size.width - pad * 2, size.height - pad * 2), style = Stroke(w))
                    drawArc(Color.White, -90f, 360f * countdown.value, false, Offset(pad, pad), Size(size.width - pad * 2, size.height - pad * 2), style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOS", color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displaySmall)
                Text(
                    if (armed) "Tap again to send · ${ceil(countdown.value * SOS_ARM_MS / 1000f).toInt()}" else "Tap to send",
                    color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
