package com.clashfit.perception.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A hand passing the camera must not pause the fight; a hand held up must.
 *
 * The recogniser runs at about ten frames a second and sees an open palm for a fraction of a second
 * every time an arm comes up out of a squat. These tests pin the difference between that and a
 * deliberate shape: held long enough, confident enough, and not simply still there from last time.
 */
class GestureIntentTest {

    private fun palm(t: Long, score: Float = 0.9f) = GestureReading(HandGesture.OPEN_PALM, score, t)
    private fun fist(t: Long, score: Float = 0.9f) = GestureReading(HandGesture.CLOSED_FIST, score, t)
    private fun none(t: Long) = GestureReading(HandGesture.NONE, 0f, t)

    /** Ten readings a second, like the recogniser. */
    private fun GestureIntent.feed(from: Long, to: Long, make: (Long) -> GestureReading): List<HandGesture> =
        (from..to step 100).mapNotNull { offer(make(it)) }

    @Test
    fun `a palm that flashes past does nothing`() {
        val g = GestureIntent(holdMs = 600)
        assertEquals(emptyList<HandGesture>(), g.feed(0, 300) { palm(it) })
        assertEquals(emptyList<HandGesture>(), g.feed(400, 1000) { none(it) })
    }

    @Test
    fun `a palm held for the hold time fires exactly once`() {
        val g = GestureIntent(holdMs = 600)
        val fired = g.feed(0, 2000) { palm(it) }
        assertEquals(listOf(HandGesture.OPEN_PALM), fired)
    }

    @Test
    fun `progress fills as the hold continues`() {
        val g = GestureIntent(holdMs = 600)
        g.offer(palm(0)); assertEquals(0f, g.progress, 0.01f)
        g.offer(palm(300)); assertEquals(0.5f, g.progress, 0.01f)
        g.offer(palm(600)); assertEquals(1f, g.progress, 0.01f)
        assertEquals(HandGesture.OPEN_PALM, g.holding)
    }

    @Test
    fun `lowering the hand and raising it again fires again after cooldown`() {
        val g = GestureIntent(holdMs = 600, cooldownMs = 1500)
        assertEquals(listOf(HandGesture.OPEN_PALM), g.feed(0, 700) { palm(it) })
        g.feed(800, 1200) { none(it) }                       // hand down, but still inside cooldown
        assertEquals(emptyList<HandGesture>(), g.feed(1300, 1900) { palm(it) })   // held again — too soon
        g.feed(2000, 2200) { none(it) }                      // hand down, past cooldown
        assertEquals(listOf(HandGesture.OPEN_PALM), g.feed(2300, 3000) { palm(it) })
    }

    @Test
    fun `a different gesture is not blocked by the last one's cooldown`() {
        val g = GestureIntent(holdMs = 600, cooldownMs = 1500)
        assertEquals(listOf(HandGesture.OPEN_PALM), g.feed(0, 700) { palm(it) })
        // Straight into a fist, no gap, inside the palm's cooldown: the fist is a new decision.
        assertEquals(listOf(HandGesture.CLOSED_FIST), g.feed(800, 1500) { fist(it) })
    }

    @Test
    fun `a low-confidence reading breaks the hold`() {
        val g = GestureIntent(holdMs = 600, minScore = 0.6f)
        g.feed(0, 400) { palm(it) }
        g.offer(palm(500, score = 0.3f))                     // the recogniser wavered
        assertNull(g.holding)
        assertEquals(emptyList<HandGesture>(), g.feed(600, 700) { palm(it) })   // hold restarted at 600
        assertEquals(listOf(HandGesture.OPEN_PALM), g.feed(800, 1300) { palm(it) })
    }

    @Test
    fun `reset forgets a hold in progress`() {
        val g = GestureIntent(holdMs = 600)
        g.feed(0, 500) { palm(it) }
        g.reset()
        assertNull(g.holding)
        assertEquals(0f, g.progress, 0f)
        assertTrue(g.feed(600, 1100) { palm(it) }.isEmpty())   // 500 ms since reset: not yet
    }
}
