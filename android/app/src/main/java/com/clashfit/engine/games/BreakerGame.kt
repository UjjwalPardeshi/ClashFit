package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.FamilyGameState
import com.clashfit.core.model.BreakerState
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * BREAKER — jumps break floors; height is force; landing softness gates damage.
 * Soft landings break more floors; stiff landings protect knees but score less.
 */
class BreakerGame(config: BreakerConfig = BreakerConfig()) : FamilyGame {
    data class BreakerConfig(
        val floors: Int = 12,
        val cmPerFloor: Float = 14f,
    )

    private var broken: Int = 0
    private var jumps: Int = 0
    private var bestCm: Float = 0f
    private var outcome: GameOutcome = GameOutcome.RUNNING
    private var lastFloors: Int = 0

    private val cfg: BreakerConfig

    init {
        cfg = config
    }

    /** Process a ballistic jump event. */
    fun onEvent(event: BreakerEvent): BreakerState {
        if (outcome != GameOutcome.RUNNING) return state()
        jumps += 1
        if (event.heightCm > bestCm) bestCm = event.heightCm
        // Stiff landing scores the jump but does not break through — force went into you.
        val force = (event.heightCm / cfg.cmPerFloor) * event.softness
        lastFloors = max(0, floor(force).toInt())
        broken = min(cfg.floors, broken + lastFloors)
        if (broken >= cfg.floors) outcome = GameOutcome.WON
        return state()
    }

    /** Reset to initial state. */
    override fun reset() {
        broken = 0
        jumps = 0
        bestCm = 0f
        outcome = GameOutcome.RUNNING
        lastFloors = 0
    }

    /** Current game state. */
    override fun state(): BreakerState {
        val progress = if (cfg.floors > 0) broken.toFloat() / cfg.floors else 0f
        return BreakerState(
            broken = broken,
            floors = cfg.floors,
            jumps = jumps,
            bestCm = bestCm,
            progress = progress,
            outcome = outcome,
            lastFloors = lastFloors,
        )
    }
}

/** Event from a ballistic jump detector. */
data class BreakerEvent(
    val heightCm: Float,
    val softness: Float,
)
