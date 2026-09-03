package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.FamilyGameState
import com.clashfit.core.model.SiegeState
import kotlin.math.max

/**
 * SIEGE — holds are shields; form quality is defence; breaking costs health.
 * Win by outlasting the boss.
 */
class SiegeGame(config: SiegeConfig = SiegeConfig()) : FamilyGame {
    data class SiegeConfig(
        val playerHp: Int = 100,
        val bossHp: Int = 1200,
        val dpsPerQuality: Float = 26f,
        val hitOnBreak: Int = 18,
    )

    private var playerHp: Int
    private var bossHp: Int
    private var holds: Int = 0
    private var totalHeldSec: Float = 0f
    private var outcome: GameOutcome = GameOutcome.RUNNING

    private val cfg: SiegeConfig

    init {
        cfg = config
        playerHp = cfg.playerHp
        bossHp = cfg.bossHp
    }

    /** Process an ISOMETRIC_HOLD event. */
    fun onEvent(event: SiegeEvent): SiegeState {
        if (outcome != GameOutcome.RUNNING) return state()
        holds += 1
        totalHeldSec += event.holdSec
        val dealt = (event.holdSec * cfg.dpsPerQuality * event.quality).toInt()
        bossHp = max(0, bossHp - dealt)
        if (!event.completed) {
            playerHp = max(0, playerHp - cfg.hitOnBreak)
        }
        if (bossHp <= 0) outcome = GameOutcome.WON
        else if (playerHp <= 0) outcome = GameOutcome.LOST
        return state()
    }

    /** Reset to initial state. */
    override fun reset() {
        playerHp = cfg.playerHp
        bossHp = cfg.bossHp
        holds = 0
        totalHeldSec = 0f
        outcome = GameOutcome.RUNNING
    }

    /** Current game state. */
    override fun state(): SiegeState {
        val playerHpPct = playerHp.toFloat() / cfg.playerHp
        val bossHpPct = bossHp.toFloat() / cfg.bossHp
        return SiegeState(
            playerHp = playerHp,
            playerHpPct = playerHpPct,
            bossHp = bossHp,
            bossHpPct = bossHpPct,
            holds = holds,
            totalHeldSec = totalHeldSec,
            outcome = outcome,
        )
    }
}

/** Event from an isometric hold detector. */
data class SiegeEvent(
    val holdSec: Float,
    val quality: Float,
    val completed: Boolean,
)
