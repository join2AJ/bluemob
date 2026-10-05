package com.bluemob.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val StarGold = Color(0xFFE0A526)

/** Five stars, filled to the nearest half. */
@Composable
fun StarRow(stars: Double, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val halves = (stars * 2).roundToInt().coerceIn(0, 10)
    Row(modifier.semantics { contentDescription = "%.1f out of 5 stars".format(stars) }) {
        for (i in 1..5) {
            val icon = when {
                halves >= i * 2 -> Icons.Filled.Star
                halves == i * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, null, tint = StarGold, modifier = Modifier.size(size))
        }
    }
}

/** A compact "★ 4.5" chip. Shows "New" for someone nobody has rated yet. */
@Composable
fun StarChip(stars: Double, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(StarGold.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = StarGold, modifier = Modifier.size(13.dp))
        Text(if (count == 0) " New" else " " + ((stars * 10).roundToInt() / 10.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
