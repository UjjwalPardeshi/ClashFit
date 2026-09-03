package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.FamilyGameState
import com.clashfit.core.model.PursuitState
import kotlin.math.max
import kotlin.math.min

/**
 * PURSUIT — something chases you. Distance is cadence and amplitude; a drop lets it close.
 * Escape by reaching the threshold; lose if caught.
 */
class PursuitGame(config: PursuitConfig = PursuitConfig()) : FamilyGame {
    data class PursuitConfig(
        val startGapM: Float = 12f,
        val escapeAtM: Float = 200f,
        val pursuerMps: Float = 2.6f,
        val metresPerCycle: Float = 1.6f,
    )

    private var distance: Float = 0f
    private var pursuer: Float
    private var cycles: Int = 0
    private var lastTMs: Long? = null
    private var outcome: GameOutcome = GameOutcome.RUNNING

    private val cfg: PursuitConfig

    init {
        cfg = config
        pursuer = -cfg.startGapM
    }

    /** The pursuer advances on the clock whether you move or not. */
    fun tick(tMs: Long): PursuitState {
        if (outcome != GameOutcome.RUNNING) return state()
        if (lastTMs != null) {
            val deltaMs = tMs - lastTMs!!
            pursuer += (cfg.pursuerMps * deltaMs) / 1000f
            if (pursuer >= distance) outcome = GameOutcome.LOST
        }
        lastTMs = tMs
        return state()
    }

    /** Process a cadence/amplitude event. */
    fun onEvent(event: PursuitEvent): PursuitState {
        if (outcome != GameOutcome.RUNNING) return state()
        cycles += 1
        val amplitude = min(1.4f, event.amplitude)
        distance += cfg.metresPerCycle * amplitude * (0.5f + 0.5f * event.formScore)
        if (distance >= cfg.escapeAtM) outcome = GameOutcome.WON
        return state()
    }

    /** Reset to initial state. */
    override fun reset() {
        distance = 0f
        pursuer = -cfg.startGapM
        cycles = 0
        lastTMs = null
        outcome = GameOutcome.RUNNING
    }

    /** Gap between player and pursuer. */
    val gapM: Float get() = max(0f, distance - pursuer)

    /** Current game state. */
    override fun state(): PursuitState {
        val progress = min(1f, distance / cfg.escapeAtM)
        return PursuitState(
            distanceM = distance,
            pursuerM = pursuer,
            gapM = gapM,
            cycles = cycles,
            progress = progress,
            outcome = outcome,
        )
    }
}

/** Event from a cadence detector. */
data class PursuitEvent(
    val amplitude: Float,
    val formScore: Float,
)
