package com.bluemob.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bluemob.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bluemob.app.ui.theme.Extra

/** A pushed screen: back button and title on top, grouped sand background, scrolling content. */
/** The app's background: soft brand-coloured glows (blue at the top, green and violet lower down) behind the content. */
@Composable
fun Modifier.appGlow(): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val k = if (dark) 1f else 0.55f
    return this.drawBehind {
        fun glow(c: Color, x: Float, y: Float, r: Float, a: Float) {
            val center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = a * k), c.copy(alpha = 0f)), center = center, radius = size.width * r),
                radius = size.width * r, center = center)
        }
        glow(Color(0xFF1E7BD8), 0.05f, 0.05f, 0.95f, 0.20f)
        glow(Color(0xFF22B07D), 1.0f, 0.45f, 0.8f, 0.12f)
        glow(Color(0xFF8B5CF6), 0.0f, 0.9f, 0.85f, 0.12f)
    }
}

@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    /** Drawn over the page background, behind the content: glows, a gradient. */
    backdrop: Modifier = Modifier.appGlow(),
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Extra.sand).then(backdrop)) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                actions()
            }
            HorizontalDivider(color = Extra.line)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp), content = content)
    }
}
