package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PursuitGameTest {
    @Test
    fun `the pursuer closes on the clock even if you stop`() {
        val g = PursuitGame(
            PursuitGame.PursuitConfig(
                startGapM = 5f,
                pursuerMps = 3f,
                escapeAtM = 500f,
            )
        )
        g.tick(0)
        val before = g.gapM
        g.tick(1000)
        assertTrue(g.gapM < before, "gap did not close while idle")
        g.tick(3000)
        assertEquals(GameOutcome.LOST, g.state().outcome, "never caught despite standing still")
    }

    @Test
    fun `sustained cadence outruns it`() {
        val g = PursuitGame(
            PursuitGame.PursuitConfig(
                startGapM = 12f,
                pursuerMps = 2.6f,
                escapeAtM = 40f,
                metresPerCycle = 2f,
            )
        )
        var t = 0L
        for (i in 0 until 40) {
            if (g.state().outcome != GameOutcome.RUNNING) break
            t += 400
            g.tick(t)
            g.onEvent(PursuitEvent(amplitude = 1.1f, formScore = 0.9f))
        }
        assertEquals(
            GameOutcome.WON,
            g.state().outcome,
            "distance ${g.state().distanceM}"
        )
    }
}
