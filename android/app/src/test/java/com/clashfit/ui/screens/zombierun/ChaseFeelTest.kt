package com.clashfit.ui.screens.zombierun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The heartbeat curve.
 *
 * It is the only thing in the chase that a runner with the phone in a pocket can perceive, so it
 * has to be monotonic and bounded: a beat that got slower as the horde closed, or that ran away
 * to zero and became a buzz, would actively mislead somebody who cannot look at the screen.
 */
class ChaseFeelTest {

    @Test
    fun `closer is always faster`() {
        var previous = beatIntervalMs(200f)
        for (gap in listOf(150f, 120f, 90f, 60f, 40f, 25f, 15f, 5f)) {
            val interval = beatIntervalMs(gap)
            assertTrue(interval <= previous, "the beat slowed as the gap closed: ${gap}m gave ${interval}ms")
            previous = interval
        }
    }

    @Test
    fun `the curve is clamped at both ends`() {
        // Beyond the far end it must not keep slowing toward silence.
        assertEquals(beatIntervalMs(150f), beatIntervalMs(10_000f))
        // Inside the near end it must not keep quickening toward a continuous tone.
        assertEquals(beatIntervalMs(15f), beatIntervalMs(0f))
    }

    @Test
    fun `every interval is a usable tempo`() {
        // Slower than two seconds reads as nothing happening; faster than 250 ms is a buzz.
        for (gap in 0..400 step 5) {
            val interval = beatIntervalMs(gap.toFloat())
            assertTrue(interval in 250..2000, "gap ${gap}m produced ${interval}ms, outside a usable tempo")
        }
    }

    @Test
    fun `a negative gap is treated as touching, not as far away`() {
        // Floating point can put the nearest zombie a hair below zero. That must be the fastest
        // beat, not the slowest, which is what an unclamped subtraction would give.
        assertEquals(beatIntervalMs(0f), beatIntervalMs(-3f))
    }
}
