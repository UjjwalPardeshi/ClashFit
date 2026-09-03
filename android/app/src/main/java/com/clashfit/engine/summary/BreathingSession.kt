package com.clashfit.engine.summary

import kotlin.math.max
import kotlin.math.min

/**
 * Box breathing (4-4-4-4 by default) and wind-down. The recovery mechanic measures how much
 * of the breathing session the player completed, then applies that fraction to recover fatigue.
 */

enum class BreathPhase { IN, HOLD_IN, OUT, HOLD_OUT }

data class BreathTick(
    val phase: BreathPhase,
    val label: String,
    val phaseProgress: Float, // 0..1 within the current phase
    val cycle: Int,
    val cycles: Int = 0,
    val done: Boolean,
    val changed: Boolean,
)

/**
 * Box breathing with configurable phases. Wind-down uses a longer exhale (4-8 instead of 4-4-4-4).
 */
class BreathingSession(
    inSec: Float = 4f,
    holdInSec: Float = 4f,
    outSec: Float = 4f,
    holdOutSec: Float = 4f,
    cycles: Int = 4,
) {
    private val plan: List<Pair<BreathPhase, Float>> = listOf(
        BreathPhase.IN to inSec,
        BreathPhase.HOLD_IN to holdInSec,
        BreathPhase.OUT to outSec,
        BreathPhase.HOLD_OUT to holdOutSec,
    ).filter { it.second > 0 }

    private val cycles: Int = cycles
    private var startMs: Long? = null
    private var cycle: Int = 0
    private var phaseIndex: Int = 0
    private var done: Boolean = false

    val totalSec: Float get() = plan.sumOf { it.second.toDouble() }.toFloat() * this.cycles

    val phase: BreathPhase get() = plan[phaseIndex].first
    val label: String get() = when (phase) {
        BreathPhase.IN -> "Breathe in"
        BreathPhase.HOLD_IN -> "Hold"
        BreathPhase.OUT -> "Breathe out"
        BreathPhase.HOLD_OUT -> "Hold"
    }

    fun reset() {
        startMs = null
        cycle = 0
        phaseIndex = 0
        done = false
    }

    fun start(tMs: Long) {
        reset()
        startMs = tMs
    }

    /** Advance to the given timestamp and return the current state. */
    fun tick(tMs: Long): BreathTick {
        if (startMs == null || done) {
            return BreathTick(phase, label, 0f, cycle, cycles, done, false)
        }

        var elapsed = (tMs - startMs!!) / 1000f
        val cycleSec = plan.sumOf { it.second.toDouble() }.toFloat()
        val cycleNum = (elapsed / cycleSec).toInt()

        if (cycleNum >= this.cycles) {
            done = true
            return BreathTick(phase, "Done", 1f, this.cycles, this.cycles, true, true)
        }

        var within = elapsed - cycleNum * cycleSec
        var i = 0
        while (i < plan.size && within >= plan[i].second) {
            within -= plan[i].second
            i++
        }

        val changed = i != phaseIndex || cycleNum != cycle
        phaseIndex = min(i, plan.size - 1)
        cycle = cycleNum

        return BreathTick(
            phase = phase,
            label = label,
            phaseProgress = within / plan[phaseIndex].second,
            cycle = cycle,
            cycles = this.cycles,
            done = false,
            changed = changed,
        )
    }

    companion object {
        /** Wind-down: 4 in, 8 out, no holds. */
        fun windDown(cycles: Int = 4): BreathingSession =
            BreathingSession(inSec = 4f, holdInSec = 0f, outSec = 8f, holdOutSec = 0f, cycles = cycles)
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
    val completion = max(0f, min(1f, cyclesCompleted.toFloat() / max(1, targetCycles)))
    val quality = if (rateInBand) 1f else 0.6f
    return min(0.35f, 0.35f * completion * quality)
}
