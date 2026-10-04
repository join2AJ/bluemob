package com.bluemob.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Pine & Sand": calm like Signal (one accent, flat lists), warm like Airbnb (sand neutrals,
 * soft depth). The same tokens as the web preview's design system.
 */
object Palette {
    // Light
    val Canvas = Color(0xFFFFFFFF)
    val Sand = Color(0xFFF6F4EF)
    val Sand2 = Color(0xFFEDEAE3)
    val Line = Color(0xFFE8E4DC)
    val Ink = Color(0xFF15201B)
    val Ink2 = Color(0xFF5D6A64)
    val Ink3 = Color(0xFF98A29D)
    val Pine = Color(0xFF11694E)
    val PineTint = Color(0xFFE2F1E9)
    val Sky = Color(0xFF2F7BC0)
    val SkyTint = Color(0xFFE3EEF8)
    val Ember = Color(0xFFE07F2E)
    val EmberTint = Color(0xFFFBEBDC)
    val Rose = Color(0xFFD94F55)
    val BubbleThem = Color(0xFFF1EEE8)

    // Dark
    val CanvasDark = Color(0xFF0E1412)
    val SandDark = Color(0xFF0A0F0D)
    val Sand2Dark = Color(0xFF18201D)
    val LineDark = Color(0xFF212B27)
    val InkDark = Color(0xFFEDF2EF)
    val Ink2Dark = Color(0xFFA3B0AA)
    val Ink3Dark = Color(0xFF6C7873)
    val PineDark = Color(0xFF3FC897)
    val OnPineDark = Color(0xFF05231A)
    val PineTintDark = Color(0xFF123129)
    val SkyDark = Color(0xFF7DB7EA)
    val SkyTintDark = Color(0xFF14283A)
    val EmberDark = Color(0xFFF0A564)
    val EmberTintDark = Color(0xFF3A2614)
    val RoseDark = Color(0xFFF0858A)
    val BubbleThemDark = Color(0xFF1B2421)

    // Shared
    val Mint = Color(0xFF5FD3A3)
    val Night1 = Color(0xFF0A1B16)
    val Night2 = Color(0xFF12382C)
    val Online = Color(0xFF1FB26F)
    val Away = Color(0xFFE9A23B)
    val Offline = Color(0xFFB5BDB9)
    val Remote = Color(0xFF2F7BC0)

    /** Avatar backgrounds, picked per person from their ID. */
    val AvatarTints = listOf(
        Color(0xFFCFEBDC), Color(0xFFD6E6F5), Color(0xFFF8E2C4),
        Color(0xFFF5D5D2), Color(0xFFE0D8F4), Color(0xFFCDECE8),
    )
}
