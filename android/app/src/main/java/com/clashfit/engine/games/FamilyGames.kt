package com.clashfit.engine.games

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.model.FamilyGameState

/** Base interface for family-specific games. */
interface FamilyGame {
    fun state(): FamilyGameState
    fun reset()
}

/** Factory for constructing family games. */
object FamilyGames {
    /**
     * Create a family game by mode name and config.
     * Returns null if the mode is not a family game.
     */
    fun create(mode: String, config: CombatConfig): FamilyGame? {
        val familyGames = config.familyGames
        return when (mode) {
            "SIEGE" -> SiegeGame(
                SiegeGame.SiegeConfig(
                    playerHp = familyGames.siege.playerHp,
                    bossHp = familyGames.siege.bossHp,
                    dpsPerQuality = familyGames.siege.dpsPerQuality,
                    hitOnBreak = familyGames.siege.hitOnBreak,
                )
            )
            "PURSUIT" -> PursuitGame(
                PursuitGame.PursuitConfig(
                    startGapM = familyGames.pursuit.startGapM,
                    escapeAtM = familyGames.pursuit.escapeAtM,
                    pursuerMps = familyGames.pursuit.pursuerMps,
                    metresPerCycle = familyGames.pursuit.metresPerCycle,
                )
            )
            "BREAKER" -> BreakerGame(
                BreakerGame.BreakerConfig(
                    floors = familyGames.breaker.floors,
                    cmPerFloor = familyGames.breaker.cmPerFloor,
                )
            )
            "SIGIL" -> SigilGame(
                SigilGame.SigilConfig(
                    segments = familyGames.sigil.segments,
                )
            )
            else -> null
        }
    }
}
