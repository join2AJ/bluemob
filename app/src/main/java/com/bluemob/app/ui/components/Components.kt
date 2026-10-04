package com.bluemob.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.data.MessageStatus
import com.bluemob.app.ui.Presence
import com.bluemob.app.ui.theme.Extra
import com.bluemob.app.ui.theme.Palette
import com.bluemob.app.ui.theme.Space
import kotlin.math.abs

fun avatarTint(seed: String): Color = Palette.AvatarTints[abs(seed.hashCode()) % Palette.AvatarTints.size]

fun presenceColor(presence: Presence): Color = when (presence) {
    Presence.ONLINE -> Palette.Online
    Presence.IN_RANGE -> Palette.Away
    Presence.OFFLINE -> Palette.Offline
}

private val RingBrush = Brush.sweepGradient(listOf(Palette.Mint, Palette.Pine, Palette.Sky, Palette.Mint))

/** A round emoji avatar. Online people wear a pine ring; others get a small status dot. */
@Composable
fun Avatar(emoji: String?, name: String, seed: String, size: Dp = 48.dp, presence: Presence? = null, sos: Boolean = false) {
    val ring = presence == Presence.ONLINE
    Box(Modifier.size(size)) {
        val face = Modifier
            .size(size)
            .then(if (ring) Modifier.background(RingBrush, CircleShape).padding(3.dp) else Modifier)
            .then(if (ring) Modifier.border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(avatarTint(seed))
        Box(face, contentAlignment = Alignment.Center) {
            Text(emoji ?: name.firstOrNull()?.uppercase() ?: "?", fontSize = (size.value * if (ring) 0.42f else 0.48f).sp, textAlign = TextAlign.Center, color = Palette.Ink)
        }
        if (sos || (presence != null && !ring)) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.28f)
                    .border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape).padding(2.dp)
                    .background(if (sos) Extra.rose else presenceColor(presence!!), CircleShape)
            )
        }
    }
}

/** A small uppercase label, e.g. "VIA BRIDGE". */
@Composable
fun Tag(text: String, container: Color = Extra.sand2, content: Color = Extra.ink2, dot: Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** Selectable rounded chip: dark when on, outlined when off. */
@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, Extra.line, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Uppercase section label above a group. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Extra.ink3,
        modifier = modifier.padding(start = 12.dp, top = Space.xl, bottom = Space.sm))
}

/** A white rounded group of rows, like Signal's settings. Put [SettingRow]s inside. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Extra.line, MaterialTheme.shapes.large),
        content = content,
    )
}

/** One row in a [Group]: coloured icon tile, title, subtitle, and whatever goes on the right. */
@Composable
fun SettingRow(
    icon: ImageVector?,
    tile: Color,
    title: String,
    subtitle: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column {
        if (divider) HorizontalDivider(Modifier.padding(start = 62.dp), color = Extra.line)
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tile), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Extra.ink2)
            }
            trailing()
        }
    }
}

/** The big screen title, with an optional line above it. */
@Composable
fun LargeTitle(title: String, over: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = Space.lg)) {
        if (over != null) Text(over, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}

/** The red SOS button that sits at the top of every main tab. */
@Composable
fun SosPill(onClick: () -> Unit) {
    Text(
        "SOS",
        color = Color.White,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Extra.rose).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = Space.xl, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

/** A card used for feature entry points: icon tile, title, one line, chevron. */
@Composable
fun FeatureCard(icon: ImageVector, tile: Color, title: String, body: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Extra.line), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(tile), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2)
            }
        }
    }
}

/**
 * Message status, Signal style: dotted ring sending, clock waiting, ring + check sent,
 * two rings delivered, two filled rings read.
 */
@Composable
fun StatusTick(status: MessageStatus, color: Color, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier.size(width = if (status == MessageStatus.DELIVERED || status == MessageStatus.READ) 20.dp else 14.dp, height = 14.dp)) {
        val r = size.height / 2 - 1.dp.toPx()
        val stroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
        fun check(cx: Float, c: Color) {
            val s = r * 0.55f
            drawLine(c, Offset(cx - s, size.height / 2), Offset(cx - s * 0.2f, size.height / 2 + s * 0.7f), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(cx - s * 0.2f, size.height / 2 + s * 0.7f), Offset(cx + s, size.height / 2 - s * 0.6f), 1.4.dp.toPx(), StrokeCap.Round)
        }
        val cy = size.height / 2
        when (status) {
            MessageStatus.PENDING -> {
                drawCircle(color, r, Offset(size.height / 2, cy), style = stroke)
                drawLine(color, Offset(size.height / 2, cy), Offset(size.height / 2, cy - r * 0.6f), 1.4.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.height / 2, cy), Offset(size.height / 2 + r * 0.45f, cy + r * 0.3f), 1.4.dp.toPx(), StrokeCap.Round)
            }
            MessageStatus.SENT -> { drawCircle(color, r, Offset(size.height / 2, cy), style = stroke); check(size.height / 2, color) }
            MessageStatus.DELIVERED, MessageStatus.READ -> {
                val read = status == MessageStatus.READ
                val first = Offset(size.height / 2, cy)
                val second = Offset(size.width - size.height / 2, cy)
                if (read) drawCircle(color, r, first) else drawCircle(color, r, first, style = stroke)
                drawCircle(bg, r + 1.5.dp.toPx(), second)
                drawCircle(color, r, second)
                check(second.x, bg)
            }
            else -> drawCircle(color, r, Offset(size.height / 2, cy), style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
        }
    }
}

/** A dot that gently breathes, used for "live". */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.7f, 1.2f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(size).scale(s).background(color, CircleShape))
}

/** Three bouncing dots: the other side is typing. */
@Composable
fun TypingDots(color: Color = Extra.ink3) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(7.dp).alpha(a).background(color, CircleShape))
            if (i < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

/** A list-row divider that starts after the avatar, like Signal. */
@Composable
fun InsetDivider(start: Dp = 82.dp) = HorizontalDivider(Modifier.padding(start = start), color = Extra.line)

/** Bottom padding so content clears the floating tab bar. */
val TabBarClearance = PaddingValues(bottom = 108.dp)

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
