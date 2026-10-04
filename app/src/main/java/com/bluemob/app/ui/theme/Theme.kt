package com.bluemob.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Palette.Leaf,
    onPrimary = Palette.Meadow,
    primaryContainer = Palette.LeafContainer,
    onPrimaryContainer = Palette.Bark,
    secondary = Palette.Sky,
    onSecondary = Palette.Meadow,
    secondaryContainer = Palette.SkyContainer,
    onSecondaryContainer = Palette.Bark,
    tertiary = Palette.Sun,
    onTertiary = Palette.Bark,
    tertiaryContainer = Palette.SunContainer,
    onTertiaryContainer = Palette.Bark,
    background = Palette.Meadow,
    onBackground = Palette.Bark,
    surface = Palette.Meadow,
    onSurface = Palette.Bark,
    surfaceVariant = Palette.Moss,
    onSurfaceVariant = Palette.Stone,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCF8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Palette.Moss,
    outline = Color(0xFFB9C6BD),
    outlineVariant = Color(0xFFDCE4DD),
    error = Palette.Coral,
)

private val DarkColors = darkColorScheme(
    primary = Palette.LeafLight,
    onPrimary = Palette.Night,
    primaryContainer = Palette.LeafContainerDark,
    onPrimaryContainer = Palette.Mist,
    secondary = Palette.SkyLight,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.SkyContainerDark,
    onSecondaryContainer = Palette.Mist,
    tertiary = Palette.SunLight,
    onTertiary = Palette.Night,
    tertiaryContainer = Palette.SunContainerDark,
    onTertiaryContainer = Palette.Mist,
    background = Palette.Night,
    onBackground = Palette.Mist,
    surface = Palette.Night,
    onSurface = Palette.Mist,
    surfaceVariant = Palette.NightMoss,
    onSurfaceVariant = Palette.MistDim,
    surfaceContainerLowest = Palette.Night,
    surfaceContainerLow = Palette.NightSurface,
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightMoss,
    outline = Color(0xFF41564C),
    outlineVariant = Color(0xFF2A3D35),
    error = Color(0xFFF4A6A0),
)

private val BlueMobShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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

/** Brand gradients: a horizon from sky to meadow. */
object Gradients {
    /** Deep enough in both themes for white text on top. */
    @Composable
    @ReadOnlyComposable
    fun horizon(): Brush = Brush.linearGradient(
        if (isSystemInDarkTheme()) listOf(Color(0xFF1F5A86), Color(0xFF1F6047))
        else listOf(Palette.Sky, Palette.Leaf)
    )

    @Composable
    @ReadOnlyComposable
    fun dawn(): Brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer,
        )
    )
}

@Composable
fun BlueMobTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BlueMobTypography,
        shapes = BlueMobShapes,
        content = content,
    )
}
