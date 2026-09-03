package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreakerGameTest {
    @Test
    fun `a stiff landing breaks fewer floors than a soft one`() {
        val soft = BreakerGame(
            BreakerGame.BreakerConfig(
                floors = 50,
                cmPerFloor = 10f,
            )
        )
        val stiff = BreakerGame(
            BreakerGame.BreakerConfig(
                floors = 50,
                cmPerFloor = 10f,
            )
        )
        soft.onEvent(BreakerEvent(heightCm = 40f, softness = 1.0f))
        stiff.onEvent(BreakerEvent(heightCm = 40f, softness = 0.3f))
        assertTrue(
            soft.state().broken > stiff.state().broken,
            "soft ${soft.state().broken} vs stiff ${stiff.state().broken}"
        )
    }

    @Test
    fun `clearing the tower wins`() {
        val g = BreakerGame(
            BreakerGame.BreakerConfig(
                floors = 6,
                cmPerFloor = 10f,
            )
        )
        for (i in 0 until 5) {
            if (g.state().outcome != GameOutcome.RUNNING) break
            g.onEvent(BreakerEvent(heightCm = 35f, softness = 0.9f))
        }
        assertEquals(GameOutcome.WON, g.state().outcome)
    }
}
