package com.clashfit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

private val Scheme = darkColorScheme(
    primary = Ember,
    onPrimary = Ground,
    primaryContainer = EmberDeep,
    onPrimaryContainer = Ink,
    secondary = Brass,
    onSecondary = Ground,
    tertiary = Fresh,
    onTertiary = Ground,
    background = Ground,
    onBackground = Ink,
    surface = Ground,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelLift,
    surfaceContainerLow = Ground2,
    outline = Rule,
    outlineVariant = RuleSoft,
    error = Gassed,
    onError = Ground,
)

/** Square corners are the brand. The only rounding is on chips and the phone bezels. */
private val ClashShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(999.dp),
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
    val screen = tween<Float>(durationMillis = 200)
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
        // MaterialTheme does not set LocalContentColor; Surface does. Without this
        // wrapper every Text that does not name a colour inherits the default black
        // and disappears against a black app. It is why the home headline, the screen
        // titles and most body copy were invisible on device.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
