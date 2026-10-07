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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
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


/** Stars filled to the nearest quarter (empty, ¼, ½, ¾, full), e.g. 4.25 shows four full stars and a quarter. */
@Composable
fun QuarterStars(stars: Double, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val q = com.bluemob.app.trust.Trust.quarter(stars)
    Row(modifier.semantics { contentDescription = "%.2f out of 5 stars".format(q) }) {
        for (i in 0 until 5) {
            val fill = (q - i).coerceIn(0.0, 1.0).toFloat()
            androidx.compose.foundation.layout.Box(Modifier.size(size)) {
                Icon(Icons.Filled.StarBorder, null, tint = StarGold, modifier = Modifier.size(size))
                if (fill > 0f) Icon(Icons.Filled.Star, null, tint = StarGold,
                    modifier = Modifier.size(size).drawWithContent { clipRect(right = this.size.width * fill) { this@drawWithContent.drawContent() } })
            }
        }
    }
}

/** 1–5 stars to tap, for giving a rating. */
@Composable
fun StarPicker(value: Int, size: Dp = 30.dp, onPick: (Int) -> Unit) {
    Row {
        for (i in 1..5) Icon(if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder, "$i star${if (i > 1) "s" else ""}", tint = StarGold,
            modifier = Modifier.size(size + 8.dp).clip(RoundedCornerShape(50)).clickable { onPick(i) }.padding(4.dp))
    }
}
