package com.clashfit.ui.screens.zombierun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.clashfit.AppGraph
import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.ZombieRunPhase
import com.clashfit.core.model.ZombieRunState
import kotlinx.coroutines.delay

/**
 * What the chase sounds and feels like.
 *
 * A map with numbers on it is a tracking app. The difference between that and a game is almost
 * entirely in the ear and the hand, and this app already synthesises everything it needs — there
 * are no audio assets to license and none to load.
 *
 * The heartbeat is the whole idea. It is the only channel that works when the phone is in a
 * pocket or an armband, which is where it will be, and it carries the one number that matters
 * without asking anybody to look at a screen while running: how close the nearest zombie is. It
 * quickens smoothly rather than in steps, so the player feels the gap closing before they could
 * have read it.
 *
 * Everything here is driven by [ZombieRunState] alone. Nothing in this file can change the
 * outcome of a chase, which is why it can be this liberal with the senses.
 */
@Composable
fun ChaseFeel(state: ZombieRunState, graph: AppGraph) {
    val sfx = graph.sfx
    val haptics = graph.haptics

    // ── the moments ─────────────────────────────────────────────────────────

    var lastPhase by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        if (state.phase != lastPhase) {
            when (state.phase) {
                // The head start is over. This is the beat the whole mode is built around.
                ZombieRunPhase.CHASE -> { sfx.phase(); haptics.phase() }
                ZombieRunPhase.CAUGHT -> { sfx.playerHit(); haptics.playerHit() }
                ZombieRunPhase.ESCAPED -> { sfx.bossDown(); haptics.milestone() }
                ZombieRunPhase.HEAD_START -> Unit
            }
            lastPhase = state.phase
        }
    }

    var lastCaches by remember { mutableStateOf(state.cachesCollected) }
    LaunchedEffect(state.cachesCollected) {
        if (state.cachesCollected > lastCaches) { sfx.milestone(); haptics.milestone() }
        lastCaches = state.cachesCollected
    }

    var lastDown by remember { mutableStateOf(state.neutralised) }
    LaunchedEffect(state.neutralised) {
        if (state.neutralised > lastDown) { sfx.bossDown(); haptics.playerHit() }
        lastDown = state.neutralised
    }

    // A pulse in the hand as the gap crosses each threshold, once per crossing. Latched on the
    // way in and released on the way out, so a zombie hovering at thirty-one metres does not
    // buzz the phone continuously.
    var crossed30 by remember { mutableStateOf(false) }
    var crossed15 by remember { mutableStateOf(false) }
    LaunchedEffect(state.nearestZombieM) {
        val gap = state.nearestZombieM
        if (gap == null) { crossed30 = false; crossed15 = false; return@LaunchedEffect }
        if (gap <= 30f && !crossed30) { crossed30 = true; haptics.tempoPulse() }
        if (gap > 40f && crossed30) crossed30 = false
        if (gap <= 15f && !crossed15) { crossed15 = true; haptics.framingLostWarning() }
        if (gap > 22f && crossed15) crossed15 = false
    }

    // ── the heartbeat ───────────────────────────────────────────────────────

    // The loop must see the *current* gap, not the one that existed when it launched. A
    // LaunchedEffect captures its parameters, so reading the plain `state` argument inside would
    // beat at whatever distance the chase happened to be at when the horde started moving, for
    // the rest of the chase — which sounds like tension and is a frozen number.
    val live by rememberUpdatedState(state)
    LaunchedEffect(state.phase == ZombieRunPhase.CHASE) {
        if (live.phase != ZombieRunPhase.CHASE) return@LaunchedEffect
        while (true) {
            val gap = live.nearestZombieM
            if (live.outcome != GameOutcome.RUNNING || gap == null) return@LaunchedEffect
            sfx.tick()
            // Below the panic threshold the hand joins in, so the last few metres are felt as
            // well as heard.
            if (gap <= PANIC_M) haptics.repTick()
            delay(beatIntervalMs(gap))
        }
    }
}

/**
 * How long to wait before the next beat, from the gap to the nearest zombie.
 *
 * Linear between the two ends and flat outside them. Linear rather than exponential on purpose:
 * an exponential curve spends most of its range doing nothing and then panics all at once, which
 * reads as a bug rather than as tension.
 */
internal fun beatIntervalMs(gapM: Float): Long {
    val clamped = gapM.coerceIn(NEAR_M, FAR_M)
    val t = (clamped - NEAR_M) / (FAR_M - NEAR_M)
    return (FAST_MS + (SLOW_MS - FAST_MS) * t).toLong()
}

/** Closer than this and the beat is as fast as it gets. */
private const val NEAR_M = 15f

/** Further than this and it is as slow as it gets. */
private const val FAR_M = 150f

/** Fastest beat, in milliseconds. Roughly 190 bpm. */
private const val FAST_MS = 320f

/** Slowest beat. Present but unhurried, so silence never means "nothing is chasing you". */
private const val SLOW_MS = 1500f

/** Inside this gap the haptics join the audio. */
private const val PANIC_M = 40f
