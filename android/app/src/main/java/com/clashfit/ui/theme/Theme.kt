package com.clashfit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Every Material token mapped, so a component that reads the scheme never falls back to purple. */
private val Scheme = darkColorScheme(
    primary = Ember,
    onPrimary = Ground,
    primaryContainer = EmberTint,
    onPrimaryContainer = Brass,
    inversePrimary = EmberDeep,
    secondary = Brass,
    onSecondary = Ground,
    secondaryContainer = PanelLift,
    onSecondaryContainer = Ink,
    tertiary = Success,
    onTertiary = Ground,
    tertiaryContainer = Color(0xFF14301F),
    onTertiaryContainer = Success,
    background = Ground,
    onBackground = Ink,
    surface = Ground,
    onSurface = Ink,
    surfaceVariant = PanelLift,
    onSurfaceVariant = InkMuted,
    surfaceDim = Ground,
    surfaceBright = PanelTop,
    surfaceContainerLowest = Ground2,
    surfaceContainerLow = Ground2,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelLift,
    surfaceContainerHighest = PanelTop,
    inverseSurface = Ink,
    inverseOnSurface = Ground,
    outline = Rule,
    outlineVariant = RuleSoft,
    error = Gassed,
    onError = Ground,
    errorContainer = Color(0xFF3A1A12),
    onErrorContainer = Heavy,
    scrim = Color(0xCC000000),
)

/** Rounded, in the Material 3 scale. Cards are 16, sheets 28, buttons are pills. */
private val ClashShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Fast, physical, never decorative. Everything on the fight screen resolves inside 300ms.
 * docs/03-UI-UX-SPEC.md §7
 */
object Motion {
    /** Impact has no wind-up. */
    val hitFlash = tween<Float>(durationMillis = 120, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
    val damageNumeral = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh)
    val hpBar = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    val counterPop = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val screen = tween<Float>(durationMillis = 220)
    /** Progress rings and XP bars fill with an ease-out, long enough to be seen. */
    val fill = tween<Float>(durationMillis = 650, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
    val bossBreathMs = 3000
    val shakePx = 6f
    val shakeMs = 180
    val phaseShiftMs = 400
}

/** Reduced motion swaps shake and flash for a border pulse. Read it from here, never from a pref directly. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun ClashFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = ClashTypography,
        shapes = ClashShapes,
    ) {
        // MaterialTheme does not set LocalContentColor; Surface does. Without this wrapper every
        // Text that does not name a colour inherits black and disappears against the dark ground.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
