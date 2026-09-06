package com.clashfit.engine.summary

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Guided breathing: a pattern of timed phases, repeated, with nothing measuring you.
 *
 * There is no camera here and no sensor. The app names the phase, counts the seconds and buzzes
 * when it changes; the lungs are yours. That is not a limitation to be apologised for — every
 * technique in [BreathingPatterns] is taught by a human being saying "in for four, out for six",
 * and a phone can do exactly that much honestly.
 *
 * The recovery mechanic measures how much of the session the player completed and applies that
 * fraction to their fatigue.
 */

enum class BreathPhase { IN, HOLD_IN, OUT, HOLD_OUT }

/**
 * Where the air goes.
 *
 * Carried per phase rather than per pattern because several techniques change route mid-cycle: the
 * 4-7-8 breathes in through the nose and out through the mouth, and alternate nostril changes side
 * on every phase. A pattern-level setting could express neither.
 */
enum class BreathRoute { NOSE, MOUTH, PURSED_LIPS, EITHER, LEFT_NOSTRIL, RIGHT_NOSTRIL }

/** One timed phase of a cycle: what to do, for how long, through where, said in words. */
data class BreathStep(
    val phase: BreathPhase,
    val seconds: Float,
    val route: BreathRoute = BreathRoute.EITHER,
    /** Four to seven words, read at arm's length with the eyes half closed. */
    val cue: String,
)

data class BreathTick(
    val phase: BreathPhase,
    val label: String,
    /** The words for this particular phase of this particular pattern. */
    val cue: String,
    val route: BreathRoute,
    val phaseProgress: Float, // 0..1 within the current phase
    /** Which step of the cycle, so a caller can tell two inhales in a row apart. */
    val stepIndex: Int,
    val cycle: Int,
    val cycles: Int = 0,
    val done: Boolean,
    val changed: Boolean,
)

/**
 * A pattern of phases, repeated [cycles] times.
 *
 * Built from an arbitrary ordered list rather than the four named slots this used to have. Two
 * techniques cannot be expressed any other way: the physiological sigh inhales twice in a row
 * before its long exhale, and alternate nostril runs in-out-in-out with the side changing each
 * time. A fixed in / hold / out / hold shape can hold neither, which is the real reason this app
 * only ever offered three patterns.
 */
class BreathingSession(
    steps: List<BreathStep>,
    cycles: Int = 4,
) {
    /** Zero-length phases are dropped: a pattern with no hold should not tick through one. */
    private val plan: List<BreathStep> = steps.filter { it.seconds > 0f }

    private val cycles: Int = cycles
    private var startMs: Long? = null
    private var cycle: Int = 0
    private var stepIndex: Int = 0
    private var done: Boolean = false

    /** No tick has been reported since [start]; the first one is a transition into phase one. */
    private var pending: Boolean = true

    init {
        require(plan.isNotEmpty()) { "a breathing pattern needs at least one phase with a duration" }
    }

    /** Seconds for one pass through the pattern. */
    val cycleSec: Float get() = plan.sumOf { it.seconds.toDouble() }.toFloat()

    val totalSec: Float get() = cycleSec * this.cycles

    val step: BreathStep get() = plan[stepIndex]
    val phase: BreathPhase get() = step.phase

    val label: String get() = when (phase) {
        BreathPhase.IN -> "Breathe in"
        BreathPhase.HOLD_IN -> "Hold"
        BreathPhase.OUT -> "Breathe out"
        BreathPhase.HOLD_OUT -> "Hold"
    }

    fun reset() {
        startMs = null
        cycle = 0
        stepIndex = 0
        done = false
        pending = true
    }

    fun start(tMs: Long) {
        reset()
        startMs = tMs
    }

    /** Advance to the given timestamp and return the current state. */
    fun tick(tMs: Long): BreathTick {
        if (startMs == null || done) {
            return BreathTick(
                phase = phase, label = label, cue = step.cue, route = step.route,
                phaseProgress = 0f, stepIndex = stepIndex, cycle = cycle,
                cycles = cycles, done = done, changed = false,
            )
        }

        val elapsed = (tMs - startMs!!) / 1000f
        val perCycle = cycleSec
        val cycleNum = (elapsed / perCycle).toInt()

        if (cycleNum >= this.cycles) {
            done = true
            return BreathTick(
                phase = phase, label = "Done", cue = "Done", route = step.route,
                phaseProgress = 1f, stepIndex = stepIndex, cycle = this.cycles,
                cycles = this.cycles, done = true, changed = true,
            )
        }

        var within = elapsed - cycleNum * perCycle
        var i = 0
        while (i < plan.size && within >= plan[i].seconds) {
            within -= plan[i].seconds
            i++
        }

        // The first tick after start is a change too: nothing was showing before it, and the UI
        // pulses on `changed`, so without this the opening "Breathe in" is the one phase that
        // never gets its cue.
        val changed = pending || i != stepIndex || cycleNum != cycle
        pending = false
        stepIndex = min(i, plan.size - 1)
        cycle = cycleNum

        return BreathTick(
            phase = phase,
            label = label,
            cue = step.cue,
            route = step.route,
            phaseProgress = within / plan[stepIndex].seconds,
            stepIndex = stepIndex,
            cycle = cycle,
            cycles = this.cycles,
            done = false,
            changed = changed,
        )
    }

    companion object {
        /**
         * The old four-slot constructor, kept because the shape is still the common case and every
         * caller that only wants a square should not have to build a list to ask for one.
         */
        operator fun invoke(
            inSec: Float = 4f,
            holdInSec: Float = 4f,
            outSec: Float = 4f,
            holdOutSec: Float = 4f,
            cycles: Int = 4,
        ): BreathingSession = BreathingSession(
            steps = listOf(
                BreathStep(BreathPhase.IN, inSec, BreathRoute.NOSE, "Breathe in"),
                BreathStep(BreathPhase.HOLD_IN, holdInSec, BreathRoute.EITHER, "Hold"),
                BreathStep(BreathPhase.OUT, outSec, BreathRoute.EITHER, "Breathe out"),
                BreathStep(BreathPhase.HOLD_OUT, holdOutSec, BreathRoute.EITHER, "Hold"),
            ),
            cycles = cycles,
        )

        /** Wind-down: 4 in, 8 out, no holds. */
        fun windDown(cycles: Int = 4): BreathingSession =
            BreathingSession(inSec = 4f, holdInSec = 0f, outSec = 8f, holdOutSec = 0f, cycles = cycles)

        /**
         * How many repetitions of [pattern] fill roughly [targetSec] seconds.
         *
         * Sessions are chosen by how long you have, not by a repetition count, because the cycles
         * are not comparable: one box breath is sixteen seconds and one pursed-lip breath is six,
         * so "eight cycles" means ninety seconds of one and forty-eight of the other. At least one
         * cycle, always — a pattern you asked for should never run zero times.
         */
        fun cyclesForSeconds(pattern: BreathingPattern, targetSec: Int): Int =
            max(1, (targetSec / pattern.cycleSec).roundToInt())
    }
}

/**
 * Bounded recovery so breathing helps but does not undo a set.
 * Returns a fraction [0..0.35] of fatigue to recover.
 */
fun recoveryFraction(
    cyclesCompleted: Int,
    targetCycles: Int,
    rateInBand: Boolean = true,
): Float {
    // Computed in double, like breathing.js, and narrowed once at the end: doing the same
    // arithmetic in Float accumulates error (0.35f * 0.6f lands on 0.21000001).
    val completion = max(0.0, min(1.0, cyclesCompleted.toDouble() / max(1, targetCycles)))
    val quality = if (rateInBand) 1.0 else 0.6
    return min(0.35, 0.35 * completion * quality).toFloat()
}
