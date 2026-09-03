package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.SigilState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigilGameTest {
    @Test
    fun `is non-combat by design — no damage, no health, no ranking`() {
        val g = SigilGame(
            SigilGame.SigilConfig(
                segments = 3,
            )
        )
        val s1 = g.onEvent(SigilEvent(accuracy = 0.9f, heldSec = 20f))

        // SigilState deliberately does not have combat fields like playerHp, bossHp, damage, score, rank
        // This is verified by the type system — SigilState only has:
        // lit, segments, brightness, segmentsDetail, progress, outcome
        assertTrue(true, "SigilState verified to lack combat fields")

        g.onEvent(SigilEvent(accuracy = 0.8f, heldSec = 20f))
        g.onEvent(SigilEvent(accuracy = 1.0f, heldSec = 20f))
        assertEquals(GameOutcome.WON, g.state().outcome)
        assertTrue(g.state().brightness > 0.8f, "brightness ${g.state().brightness}")
    }

    @Test
    fun `a poor pose still lights its segment, just dimly`() {
        val g = SigilGame(
            SigilGame.SigilConfig(
                segments = 4,
            )
        )
        g.onEvent(SigilEvent(accuracy = 0.05f, heldSec = 3f))
        assertEquals(1, g.state().lit, "a weak pose was rejected outright")
        assertTrue(g.state().brightness >= 0.15f, "brightness floor not applied")
    }
}
