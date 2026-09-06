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
    private const val FADE = 240
    private const val REDUCED = 150

    /** Material's emphasized-decelerate: leaves fast, settles slowly, never rubber-bands. */
    private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /**
     * For the half that is leaving. Deliberately not Material's emphasized-accelerate, which holds
     * almost still for the first third and then whips out — correct on paper, and on a phone it
     * reads as the old screen not moving at all while the new one slides in over it. This one
     * starts moving immediately, so a back gesture looks like something being pushed off rather
     * than something being covered up.
     */
    private val Accelerate = CubicBezierEasing(0.4f, 0f, 0.7f, 0.9f)

    /**
     * The arriving screen crosses this much of the width; the leaving one only covers [TRAIL].
     *
     * Both were smaller and the movement did not register on a real phone at 300ms — a slide you
     * have to be told about is decoration. The gap between them is what sells it: matched
     * distances read as one sheet, and the parallax is what makes the old screen sit behind.
     */
    private const val LEAD = 0.55f
    private const val TRAIL = 0.22f

    /**
     * The tab roots, read off [Tab] rather than listed here.
     *
     * They were listed by hand once, and within hours a Workout tab and a Profile tab arrived and
     * the list did not: two of the five tabs pushed sideways like a stacked screen instead of
     * fading like the siblings they are. A test caught it, which is the only reason it is not
     * still in the app — but a list that has to be kept in step with another list is a bug waiting
     * for the next tab, so now there is only one list.
     */
    internal val TAB_ROOTS: Set<KClass<out Route>> = Tab.entries.map { it.root::class }.toSet()

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
