package com.bluemob.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** BlueMob's own icons, where the stock ones look dated. Drawn in black; [androidx.compose.material3.Icon] tints them. */
object BlueMobIcons {
    /** Games: a playing die in front of a tilted card, with a sparkle. */
    val Games: ImageVector by lazy {
        ImageVector.Builder("BlueMobGames", 24.dp, 24.dp, 24f, 24f).apply {
            val line = SolidColor(Color.Black)
            // The card behind, tilted.
            path(stroke = line, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9.2f, 5.6f); lineTo(14.6f, 3.2f)
                curveTo(15.6f, 2.8f, 16.6f, 3.2f, 17.0f, 4.2f)
                lineTo(20.6f, 12.4f)
                curveTo(21.0f, 13.4f, 20.6f, 14.4f, 19.6f, 14.8f)
                lineTo(17.6f, 15.7f)
            }
            // The die in front.
            path(stroke = line, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5.5f, 8.5f); lineTo(12.5f, 8.5f)
                curveTo(13.9f, 8.5f, 15.0f, 9.6f, 15.0f, 11.0f)
                lineTo(15.0f, 18.0f)
                curveTo(15.0f, 19.4f, 13.9f, 20.5f, 12.5f, 20.5f)
                lineTo(5.5f, 20.5f)
                curveTo(4.1f, 20.5f, 3.0f, 19.4f, 3.0f, 18.0f)
                lineTo(3.0f, 11.0f)
                curveTo(3.0f, 9.6f, 4.1f, 8.5f, 5.5f, 8.5f)
                close()
            }
            // Three pips.
            listOf(6.2f to 11.7f, 9.0f to 14.5f, 11.8f to 17.3f).forEach { (x, y) ->
                path(fill = line) { moveTo(x + 1.15f, y); arcToRelative(1.15f, 1.15f, 0f, true, true, -2.3f, 0f); arcToRelative(1.15f, 1.15f, 0f, true, true, 2.3f, 0f); close() }
            }
            // A sparkle.
            path(fill = line) {
                moveTo(20.0f, 17.2f); lineTo(20.6f, 18.9f); lineTo(22.3f, 19.5f); lineTo(20.6f, 20.1f)
                lineTo(20.0f, 21.8f); lineTo(19.4f, 20.1f); lineTo(17.7f, 19.5f); lineTo(19.4f, 18.9f); close()
            }
        }.build()
    }
}
