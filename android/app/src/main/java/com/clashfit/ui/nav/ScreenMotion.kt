package com.clashfit.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * How one screen becomes another.
 *
 * Every route used to cross-fade, which is the one transition that carries no information: going
 * deeper and coming back looked identical, so back never felt like back. Three transitions replace
 * it, and which one you get says where you just went.
 *
 * **Going deeper** is a parallax push, the movement almost every Android app uses: the arriving
 * screen slides in from the right while the one you left drifts a shorter distance to the left.
 * The unequal distance is the whole trick — matched speeds read as one sheet sliding across, and
 * the parallax is what makes the screen you left read as sitting *behind* the new one.
 *
 * **Coming back** is that, reversed, which is also what the system's predictive-back gesture drags
 * through: the manifest opts into `enableOnBackInvokedCallback`, so holding a back swipe scrubs
 * this same animation under your thumb instead of playing it after you let go.
 *
 * **Switching tabs** is not a push at all. The four tabs are siblings, not a stack — nothing is
 * behind anything — so they fade through each other with a small scale, which is Material's motion
 * for a lateral move. Sliding tabs sideways would imply an order they do not have.
 *
 * Reduce motion collapses all three back to a fade. Somebody who turned that on asked for less
 * movement, and a parallax push is movement.
 */
object ScreenMotion {

    private const val DURATION = 300
    private const val FADE = 200
    private const val REDUCED = 150

    /** Material's emphasized-decelerate: leaves fast, settles slowly, never rubber-bands. */
    private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized-accelerate, for the half that is leaving. */
    private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** The arriving screen crosses this much of the width; the leaving one only covers [TRAIL]. */
    private const val LEAD = 0.40f
    private const val TRAIL = 0.18f

    /**
     * The five tab roots. Named, rather than read off [Tab], so the typing matches [Tab.owns] —
     * and asserted against [Tab] by a test, which is exactly how the fifth one got here. The
     * warning this comment used to carry came true within the week: Workout was added to the tab
     * bar and forgotten here, and the test named the file nobody would have thought to look at.
     */
    internal val TAB_ROOTS: Set<KClass<out Route>> =
        setOf(Home::class, WorkoutHome::class, Compete::class, Progress::class, You::class)

    private fun NavDestination.isTabRoot(): Boolean = TAB_ROOTS.any { hasRoute(it) }

    /** True when both ends are tab roots — a sideways move between siblings, not a push. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.lateral(): Boolean =
        initialState.destination.isTabRoot() && targetState.destination.isTabRoot()

    // ── the three moves ───────────────────────────────────────────────────────────────────────

    private fun fadeThroughIn(): EnterTransition =
        fadeIn(tween(210, delayMillis = 90)) +
            scaleIn(tween(210, delayMillis = 90), initialScale = 0.94f)

    private fun fadeThroughOut(): ExitTransition =
        fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 1.03f)

    private fun pushIn(fromRight: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = Decelerate)) { full ->
            (full * LEAD).roundToInt().let { if (fromRight) it else -it }
        } + fadeIn(tween(FADE, delayMillis = 40))

    private fun pushOut(toRight: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = Accelerate)) { full ->
            if (toRight) (full * LEAD).roundToInt() else -(full * TRAIL).roundToInt()
        } + fadeOut(tween(FADE))

    // ── what NavHost asks for ─────────────────────────────────────────────────────────────────

    fun enter(reduceMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduceMotion -> fadeIn(tween(REDUCED))
            lateral() -> fadeThroughIn()
            else -> pushIn(fromRight = true)
        }
    }

    fun exit(reduceMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduceMotion -> fadeOut(tween(REDUCED))
            lateral() -> fadeThroughOut()
            else -> pushOut(toRight = false)
        }
    }

    fun popEnter(reduceMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduceMotion -> fadeIn(tween(REDUCED))
            lateral() -> fadeThroughIn()
            else -> pushIn(fromRight = false)
        }
    }

    fun popExit(reduceMotion: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduceMotion -> fadeOut(tween(REDUCED))
            lateral() -> fadeThroughOut()
            else -> pushOut(toRight = true)
        }
    }
}
