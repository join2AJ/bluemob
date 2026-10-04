package com.bluemob.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colours Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val sand: Color, val sand2: Color, val line: Color, val ink2: Color, val ink3: Color,
    val pineTint: Color, val sky: Color, val skyTint: Color, val ember: Color, val emberTint: Color,
    val rose: Color, val bubbleThem: Color,
)

private val LightExtra = ExtraColors(
    Palette.Sand, Palette.Sand2, Palette.Line, Palette.Ink2, Palette.Ink3, Palette.PineTint, Palette.Sky, Palette.SkyTint,
    Palette.Ember, Palette.EmberTint, Palette.Rose, Palette.BubbleThem,
)
private val DarkExtra = ExtraColors(
    Palette.SandDark, Palette.Sand2Dark, Palette.LineDark, Palette.Ink2Dark, Palette.Ink3Dark, Palette.PineTintDark,
    Palette.SkyDark, Palette.SkyTintDark, Palette.EmberDark, Palette.EmberTintDark, Palette.RoseDark, Palette.BubbleThemDark,
)
val LocalExtra = staticCompositionLocalOf { LightExtra }

private val LightColors = lightColorScheme(
    primary = Palette.Pine, onPrimary = Color.White, primaryContainer = Palette.PineTint, onPrimaryContainer = Palette.Pine,
    secondary = Palette.Sky, onSecondary = Color.White, secondaryContainer = Palette.SkyTint, onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Ember, onTertiary = Color.White, tertiaryContainer = Palette.EmberTint, onTertiaryContainer = Palette.Ink,
    background = Palette.Canvas, onBackground = Palette.Ink, surface = Palette.Canvas, onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand, onSurfaceVariant = Palette.Ink2,
    surfaceContainerLowest = Palette.Canvas, surfaceContainerLow = Palette.Sand, surfaceContainer = Palette.Canvas,
    surfaceContainerHigh = Palette.Sand2, surfaceContainerHighest = Palette.Sand2,
    outline = Palette.Ink3, outlineVariant = Palette.Line, error = Palette.Rose,
)
private val DarkColors = darkColorScheme(
    primary = Palette.PineDark, onPrimary = Palette.OnPineDark, primaryContainer = Palette.PineTintDark, onPrimaryContainer = Palette.PineDark,
    secondary = Palette.SkyDark, onSecondary = Palette.CanvasDark, secondaryContainer = Palette.SkyTintDark, onSecondaryContainer = Palette.InkDark,
    tertiary = Palette.EmberDark, onTertiary = Palette.CanvasDark, tertiaryContainer = Palette.EmberTintDark, onTertiaryContainer = Palette.InkDark,
    background = Palette.CanvasDark, onBackground = Palette.InkDark, surface = Palette.CanvasDark, onSurface = Palette.InkDark,
    surfaceVariant = Palette.SandDark, onSurfaceVariant = Palette.Ink2Dark,
    surfaceContainerLowest = Palette.SandDark, surfaceContainerLow = Palette.SandDark, surfaceContainer = Palette.CanvasDark,
    surfaceContainerHigh = Palette.Sand2Dark, surfaceContainerHighest = Palette.Sand2Dark,
    outline = Palette.Ink3Dark, outlineVariant = Palette.LineDark, error = Palette.RoseDark,
)

private val BlueMobShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing scale, so screens share the same rhythm. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Gradients {
    /** Sky to pine. Deep enough in both themes for white text. */
    @Composable
    @ReadOnlyComposable
    fun horizon(): Brush = Brush.linearGradient(
        if (isSystemInDarkTheme()) listOf(Color(0xFF1F5A86), Color(0xFF1F6047)) else listOf(Palette.Sky, Palette.Pine)
    )

    /** Soft morning wash behind the intro. */
    @Composable
    @ReadOnlyComposable
    fun dawn(): Brush = Brush.verticalGradient(
        listOf(LocalExtra.current.skyTint, MaterialTheme.colorScheme.background, LocalExtra.current.pineTint)
    )

    /** The radar always sits on a night sky. */
    val night: Brush = Brush.radialGradient(listOf(Palette.Night2, Palette.Night1))
}

/** Shortcut: `Extra.sand`, `Extra.ember` … from any composable. */
val Extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtra.current

@Composable
fun BlueMobTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = BlueMobTypography,
            shapes = BlueMobShapes,
            content = content,
        )
    }
}
