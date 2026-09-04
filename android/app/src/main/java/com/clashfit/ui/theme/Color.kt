package com.clashfit.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * One world, shared with the site: a layered dark ground, off-white ink, one ember accent.
 * Never pure black: it smears on OLED and leaves no room for elevation. Surfaces step up in
 * four tones so a card reads as a card without a border.
 */
val Ground = Color(0xFF0E0F12)      // background
val Ground2 = Color(0xFF131418)     // bars, lowest container
val Panel = Color(0xFF1B1C21)       // cards
val PanelLift = Color(0xFF24262C)   // a card on a card, inputs
val PanelTop = Color(0xFF2D3037)    // highest container, pressed

// Depth. A flat rectangle of one colour reads as a placeholder; a surface lit from above reads as
// an object. These are the two ends of a card's gradient and the highlight along its top edge —
// the whole difference between "functional" and "made".
val PanelHi = Color(0xFF23252B)     // top of a card, where the light falls
val PanelLo = Color(0xFF181A1E)     // bottom of a card, in its own shade
val Sheen = Color(0x1FFFFFFF)       // 12 %: the lit edge along the top
val SheenSoft = Color(0x0FFFFFFF)   //  6 %: the same edge on a nested surface
val Shade = Color(0x40000000)       // 25 %: what a card casts on the one behind it

val Ink = Color(0xFFF5F2EC)
val InkMuted = Color(0xB8F5F2EC)    // 72 %: secondary text, 9:1 on Ground
val InkFaint = Color(0x80F5F2EC)    // 50 %: hints and disabled, 4.6:1 on Ground
val Rule = Color(0x24F5F2EC)        // 14 %: outlines
val RuleSoft = Color(0x14F5F2EC)    //  8 %: tracks and dividers inside cards

val Ember = Color(0xFFFF5A2C)       // primary, 6.2:1 on Ground
val EmberLift = Color(0xFFFF7A52)
val EmberDeep = Color(0xFFC93E14)
val EmberTint = Color(0xFF2E1810)   // the ember container: a warm surface for selected states
val Brass = Color(0xFFFFB59B)
val Success = Color(0xFF5ED28A)

// Fatigue bands are data colours, never decoration.
val Fresh = Color(0xFF8FD18A)
val Working = Color(0xFFF2C14E)
val Heavy = Color(0xFFFF8A3D)
val Gassed = Color(0xFFFF4F1F)

// Rep verdicts.
val Clean = Color(0xFF8FD18A)
val Ok = Color(0xFFF2C14E)
val Shallow = Color(0xFFFF8A3D)

// Achievement tiers.
val Bronze = Color(0xFFCD8B5A)
val Silver = Color(0xFFC9CDD4)
val Gold = Color(0xFFF2C14E)
