package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.FamilyGameState
import com.clashfit.core.model.SigilState
import com.clashfit.core.model.SigilSegment
import kotlin.math.max

/**
 * SIGIL — no boss, no damage, no ranking, deliberately.
 * Each asana lights a constellation segment; accuracy determines brightness.
 * This is the one non-combat mode, making the product look like a product.
 */
class SigilGame(config: SigilConfig = SigilConfig()) : FamilyGame {
    data class SigilConfig(
        val segments: Int = 8,
    )

    private val segments: MutableList<SigilSegment> = mutableListOf()
    private var outcome: GameOutcome = GameOutcome.RUNNING

    private val cfg: SigilConfig

    init {
        cfg = config
    }

    /** Process a pose-match event. */
    fun onEvent(event: SigilEvent): SigilState {
        if (outcome != GameOutcome.RUNNING) return state()
        val brightness = max(0.15f, event.accuracy)
        segments.add(
            SigilSegment(
                index = segments.size,
                brightness = brightness,
                heldSec = event.heldSec,
            )
        )
        if (segments.size >= cfg.segments) outcome = GameOutcome.WON
        return state()
    }

    /** Reset to initial state. */
    override fun reset() {
        segments.clear()
        outcome = GameOutcome.RUNNING
    }

    /** Mean brightness of all lit segments. */
    private val meanBrightness: Float
        get() = if (segments.isNotEmpty()) {
            segments.sumOf { it.brightness.toDouble() }.toFloat() / segments.size
        } else {
            0f
        }

    /** Current game state. */
    override fun state(): SigilState {
        val progress = if (cfg.segments > 0) segments.size.toFloat() / cfg.segments else 0f
        return SigilState(
            lit = segments.size,
            segments = cfg.segments,
            brightness = meanBrightness,
            segmentsDetail = segments.toList(),
            progress = progress,
            outcome = outcome,
        )
    }
}

/** Event from a pose-match detector. */
data class SigilEvent(
    val accuracy: Float,
    val heldSec: Float,
)
