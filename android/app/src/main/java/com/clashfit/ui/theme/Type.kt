package com.clashfit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.clashfit.R

/** Condensed heavy display face. The variable font carries every weight we use. */
val Display: FontFamily = FontFamily(
    Font(R.font.bigshoulders_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.bigshoulders_variable, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

val Body: FontFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val Mono: FontFamily = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
)

/**
 * The player is two metres away, on the floor, sweating. Nothing they must read mid-set goes
 * below 28sp; numerals that matter are 80–140sp. docs/03-UI-UX-SPEC.md §1
 */
val ClashTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 128.sp, lineHeight = 112.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 88.sp, lineHeight = 80.sp, letterSpacing = (-0.01).em),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.14.em),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.18.em),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.14.em),
)

/** Telemetry readouts: mono, tabular. Not part of Material's scale on purpose. */
val MonoReadout = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
