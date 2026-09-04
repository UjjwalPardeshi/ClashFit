package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The reward moment.
 *
 * A finished set already produced every number this screen needs: XP by line, the level bar, any
 * badges crossed. It printed them all at once, which is the difference between being handed a
 * receipt and being given something. Revealing them in order — total, then each line, then the
 * bar filling, then badges one at a time — is the whole reason a game feels like a reward and a
 * tracker feels like homework.
 *
 * Under reduced motion every step is revealed immediately. Someone who asked their phone to stop
 * moving things should not have to wait out a celebration to read their own numbers.
 */

/**
 * A step counter that advances on a timer.
 *
 * @param steps how many things there are to reveal
 * @param stepMs the gap between them
 * @return how many steps have been revealed so far, recomposing only when that count changes
 */
@Composable
fun rememberReveal(steps: Int, stepMs: Int = 240, reduceMotion: Boolean = false): State<Int> {
    val progress = remember(steps) { Animatable(if (reduceMotion) steps.toFloat() else 0f) }
    LaunchedEffect(steps, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(steps.toFloat())
        } else {
            progress.snapTo(0f)
            progress.animateTo(steps.toFloat(), tween(steps * stepMs, easing = LinearEasing))
        }
    }
    // derivedStateOf so a caller recomposes once per step rather than once per frame.
    return remember(progress) { derivedStateOf { progress.value.toInt().coerceIn(0, steps) } }
}

/**
 * A number that counts up to its value once revealed.
 *
 * Watching 168 climb from zero is the difference between a number and an achievement. It settles
 * fast enough that nobody waits for it.
 */
@Composable
fun rememberCountUp(target: Int, revealed: Boolean, durationMs: Int = 620): State<Int> {
    val value = remember(target) { Animatable(0f) }
    LaunchedEffect(target, revealed) {
        if (!revealed) return@LaunchedEffect
        value.snapTo(0f)
        value.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return remember(value) { derivedStateOf { value.value.roundToInt() } }
}

/**
 * Fades and slides content in from below once its step arrives.
 *
 * Uses a layout offset rather than a Modifier.offset so nothing outside the reveal shifts: the
 * item occupies its final space from the start, and only its own drawing moves.
 */
@Composable
fun RevealStep(
    visible: Boolean,
    modifier: Modifier = Modifier,
    rise: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) t.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) else t.snapTo(0f)
    }
    val f = t.value
    Box(
        modifier
            .alpha(f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, ((1f - f) * rise.toPx()).roundToLong().toInt())
                }
            },
    ) { content() }
}

/**
 * A badge arriving.
 *
 * Slams in oversized and settles. A badge that simply appears is a list item; a badge that lands
 * is something you earned.
 */
@Composable
fun BadgeSlam(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(visible, reduceMotion) {
        if (!visible) { t.snapTo(0f); return@LaunchedEffect }
        if (reduceMotion) { t.snapTo(1f); return@LaunchedEffect }
        t.snapTo(0f)
        t.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    val f = t.value
    // Overshoots past its size on the way in, which is what makes it read as landing.
    val scale = if (f < 0.7f) 0.6f + 0.62f * (f / 0.7f) else 1.22f - 0.22f * ((f - 0.7f) / 0.3f)
    Box(modifier.scale(if (reduceMotion) 1f else scale).alpha(f.coerceIn(0f, 1f))) { content() }
}
