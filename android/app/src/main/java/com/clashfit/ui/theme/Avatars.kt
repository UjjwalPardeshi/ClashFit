package com.clashfit.ui.theme

import androidx.compose.ui.graphics.Color

/** The colours a player can pick for their avatar. Index is what Prefs stores. */
object AvatarPalette {
    val colors: List<Color> = listOf(
        Ember,
        Color(0xFF5ED28A), // green
        Color(0xFF4FA3F7), // blue
        Color(0xFFB57BEE), // violet
        Color(0xFFF2C14E), // gold
        Color(0xFFE85D8A), // pink
    )

    fun at(index: Int): Color = colors[index.mod(colors.size)]
}
