package com.clashfit.engine.summary

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathingSessionTest {

    @Test
    fun `breathing - box pattern walks its phases and completes`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 2)
        b.start(0)

        assertEquals(BreathPhase.IN, b.tick(1000).phase)
        assertEquals(BreathPhase.HOLD_IN, b.tick(5000).phase)
        assertEquals(BreathPhase.OUT, b.tick(9000).phase)
        assertEquals(BreathPhase.HOLD_OUT, b.tick(13000).phase)
        assertEquals(1, b.tick(17000).cycle)
        assertTrue(b.tick(33000).done)
        assertEquals(32f, b.totalSec)
    }

    @Test
    fun `breathing - wind-down uses a longer exhale than inhale`() {
        val w = BreathingSession.windDown(3)
        w.start(0)

        assertEquals(BreathPhase.IN, w.tick(1000).phase)
        assertEquals(BreathPhase.OUT, w.tick(6000).phase)
        assertEquals(36f, w.totalSec)
    }

    @Test
    fun `breathing - recovery is bounded so it cannot undo a set`() {
        val recovery = recoveryFraction(99, 4)
        assertTrue(recovery <= 0.35f)
        assertEquals(0f, recoveryFraction(0, 4))
        assertTrue(recoveryFraction(4, 4, false) < recoveryFraction(4, 4, true))
    }

    @Test
    fun `breathing - phase progress tracks within-phase position`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        b.start(0)

        val tick1 = b.tick(2000) // 2 seconds into 4-second phase
        assertEquals(BreathPhase.IN, tick1.phase)
        assertTrue(abs(tick1.phaseProgress - 0.5f) < 0.01f)

        val tick2 = b.tick(6000) // 2 seconds into hold-in phase (after 4 sec in)
        assertEquals(BreathPhase.HOLD_IN, tick2.phase)
        assertTrue(abs(tick2.phaseProgress - 0.5f) < 0.01f)
    }

    @Test
    fun `breathing - mark completion accurately`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        b.start(0)

        val tick1 = b.tick(15999) // Just before done
        assertEquals(false, tick1.done)

        val tick2 = b.tick(16001) // Just after done
        assertEquals(true, tick2.done)
    }

    @Test
    fun `breathing - changed flag tracks phase transitions`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        b.start(0)

        val tick1 = b.tick(1000) // IN phase
        assertEquals(BreathPhase.IN, tick1.phase)
        assertEquals(true, tick1.changed)

        val tick2 = b.tick(2000) // Still IN
        assertEquals(BreathPhase.IN, tick2.phase)
        assertEquals(false, tick2.changed)

        val tick3 = b.tick(5000) // Transition to HOLD_IN
        assertEquals(BreathPhase.HOLD_IN, tick3.phase)
        assertEquals(true, tick3.changed)
    }

    @Test
    fun `recovery - full completion recovers up to 35 percent`() {
        val recovery = recoveryFraction(4, 4, true)
        assertEquals(0.35f, recovery)
    }

    @Test
    fun `recovery - partial completion scales linearly`() {
        val recovery1 = recoveryFraction(2, 4, true)
        val recovery2 = recoveryFraction(1, 4, true)
        assertTrue(recovery1 > recovery2)
        assertTrue(abs(recovery1 - 0.175f) < 0.01f)
    }

    @Test
    fun `recovery - breathing off-pace recovers less`() {
        val inBand = recoveryFraction(4, 4, true)
        val offBand = recoveryFraction(4, 4, false)
        assertTrue(offBand < inBand)
        // 0.35 * 0.6, to within a float's worth of slack.
        assertTrue(abs(offBand - 0.21f) < 1e-4f)
    }

    @Test
    fun `breathing - reset clears state`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        b.start(0)
        b.tick(5000)

        b.reset()
        val tick = b.tick(0)
        assertEquals(0, tick.cycle)
        assertEquals(false, tick.done)
    }

    @Test
    fun `breathing - label reflects current phase`() {
        val b = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        b.start(0)

        assertEquals("Breathe in", b.tick(1000).label)
        assertEquals("Hold", b.tick(5000).label)
        assertEquals("Breathe out", b.tick(9000).label)
    }
}
