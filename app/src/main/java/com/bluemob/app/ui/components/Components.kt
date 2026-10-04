package com.bluemob.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.theme.Palette
import kotlin.math.abs

fun avatarTint(seed: String): Color = Palette.AvatarTints[abs(seed.hashCode()) % Palette.AvatarTints.size]

fun presenceColor(presence: Presence): Color = when (presence) {
    Presence.ONLINE -> Palette.Online
    Presence.IN_RANGE -> Palette.Away
    Presence.OFFLINE -> Palette.Offline
}

/** A round avatar showing an emoji (or initial) with an optional live status dot. */
@Composable
fun Avatar(
    emoji: String?,
    name: String,
    seed: String,
    size: Dp = 48.dp,
    presence: Presence? = null,
) {
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).background(avatarTint(seed).copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emoji ?: name.firstOrNull()?.uppercase() ?: "?",
                fontSize = (size.value * 0.48f).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (presence != null) {
            val dot = size * 0.28f
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(presenceColor(presence), CircleShape)
            )
        }
    }
}

/** A small rounded label, e.g. "Coming soon" or "Online". */
@Composable
fun Pill(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
    dot: Color? = null,
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
            if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = content)
            Text(text, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

/** A dot that gently breathes, used for "live" status. */
@Composable
fun PulsingDot(color: Color, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.7f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(size).scale(s).background(color, CircleShape))
}

/** Three bouncing dots: the other side is typing. */
@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).alpha(a).background(color, CircleShape))
            if (i < 2) Spacer(Modifier.width(4.dp))
        }
    }
}
