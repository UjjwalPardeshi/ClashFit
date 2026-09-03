package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiegeGameTest {
    @Test
    fun `a held plank damages the boss, a broken one costs you`() {
        val g = SiegeGame(
            SiegeGame.SiegeConfig(
                playerHp = 100,
                bossHp = 600,
                dpsPerQuality = 20f,
                hitOnBreak = 25,
            )
        )
        val held = g.onEvent(SiegeEvent(holdSec = 20f, quality = 0.9f, completed = true))
        assertTrue(held.bossHp < 600, "holding dealt no damage")
        assertEquals(100, held.playerHp, "a clean hold cost the player health")
        val broke = g.onEvent(SiegeEvent(holdSec = 4f, quality = 0.5f, completed = false))
        assertEquals(75, broke.playerHp, "breaking form did not let a hit through")
    }

    @Test
    fun `outlasting the boss wins, running out of health loses`() {
        val win = SiegeGame(
            SiegeGame.SiegeConfig(
                bossHp = 300,
                dpsPerQuality = 20f,
            )
        )
        win.onEvent(SiegeEvent(holdSec = 20f, quality = 1f, completed = true))
        assertEquals(GameOutcome.WON, win.state().outcome)

        val lose = SiegeGame(
            SiegeGame.SiegeConfig(
                playerHp = 40,
                bossHp = 99999,
                hitOnBreak = 20,
            )
        )
        lose.onEvent(SiegeEvent(holdSec = 2f, quality = 0.3f, completed = false))
        lose.onEvent(SiegeEvent(holdSec = 2f, quality = 0.3f, completed = false))
        assertEquals(GameOutcome.LOST, lose.state().outcome)
    }

    @Test
    fun `damage calculation uses rounding not truncation`() {
        val g = SiegeGame(
            SiegeGame.SiegeConfig(
                bossHp = 10000,
                dpsPerQuality = 20f,
            )
        )
        // holdSec * dpsPerQuality * quality = 0.75 * 20 * 1.0 = 15.0
        val held1 = g.onEvent(SiegeEvent(holdSec = 0.75f, quality = 1.0f, completed = true))
        assertEquals(9985, held1.bossHp, "0.75 * 20 * 1.0 should deal 15 damage")

        // holdSec * dpsPerQuality * quality = 1.25 * 20 * 1.0 = 25.0 (exact)
        val held2 = g.onEvent(SiegeEvent(holdSec = 1.25f, quality = 1.0f, completed = true))
        assertEquals(9960, held2.bossHp, "1.25 * 20 * 1.0 should deal 25 damage")

        // holdSec * dpsPerQuality * quality = 1.3 * 20 * 0.5 = 13.0
        val held3 = g.onEvent(SiegeEvent(holdSec = 1.3f, quality = 0.5f, completed = true))
        assertEquals(9947, held3.bossHp, "1.3 * 20 * 0.5 should deal 13 damage")
    }
}
