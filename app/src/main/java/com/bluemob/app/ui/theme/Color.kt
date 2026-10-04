package com.bluemob.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Open sky & wild meadow": colors taken from the outdoors, where BlueMob is meant to work.
 * Leaf green is the brand, sky blue marks connection, sun amber is the warm accent.
 */
object Palette {
    val Leaf = Color(0xFF2E7D5B)
    val LeafLight = Color(0xFF7FD8A8)
    val LeafContainer = Color(0xFFCDEFD9)
    val LeafContainerDark = Color(0xFF1D4A37)

    val Sky = Color(0xFF2B7BB9)
    val SkyLight = Color(0xFF9CCBF0)
    val SkyContainer = Color(0xFFD5E9F8)
    val SkyContainerDark = Color(0xFF173E5C)

    val Sun = Color(0xFFE8A33D)
    val SunLight = Color(0xFFFFCF80)
    val SunContainer = Color(0xFFFCE7C4)
    val SunContainerDark = Color(0xFF5A3D0E)

    val Meadow = Color(0xFFF6F8F1)
    val Moss = Color(0xFFE6EDE1)
    val Bark = Color(0xFF1E2A24)
    val Stone = Color(0xFF5B6B62)

    val Night = Color(0xFF0F1A17)
    val NightSurface = Color(0xFF16241F)
    val NightMoss = Color(0xFF223630)
    val Mist = Color(0xFFE2EBE5)
    val MistDim = Color(0xFFA9BAB0)

    val Online = Color(0xFF3CC47C)
    val Away = Color(0xFFE8A33D)
    val Offline = Color(0xFF9AA59F)
    val Coral = Color(0xFFD9534F)

    /** Avatar background tints, picked per person from their device ID. */
    val AvatarTints = listOf(
        Color(0xFF7FD8A8), Color(0xFF9CCBF0), Color(0xFFFFCF80),
        Color(0xFFF4A6A0), Color(0xFFC3B1F0), Color(0xFF8FD3D0),
    )
}
