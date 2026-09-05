package com.clashfit.perception.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ring hands back the frame from the moment asked for, and nothing it has already let go of.
 *
 * The frame the referee wants is the one at the bottom of a rep, and by the time the rep completes
 * that frame is one or two seconds old. Losing it means the model sees somebody standing up
 * straight and says the squat looked fine.
 */
class FrameRingTest {

    @Test
    fun `the nearest frame to a moment is the one returned`() {
        val ring = FrameRing<String>(8)
        for (t in 0L..700L step 100) ring.push(t, "f$t")
        assertEquals("f300", ring.nearest(320))
        assertEquals("f700", ring.nearest(690))
        assertEquals("f0", ring.nearest(-50))
    }

    @Test
    fun `a moment nobody has a frame near returns nothing`() {
        val ring = FrameRing<String>(4)
        ring.push(1000, "a"); ring.push(1100, "b")
        assertNull(ring.nearest(5000, toleranceMs = 200))
        assertEquals("b", ring.nearest(5000))   // no tolerance: the newest is still the nearest
    }

    @Test
    fun `the oldest frame is evicted first and handed back for recycling`() {
        val ring = FrameRing<Int>(3)
        assertNull(ring.push(1, 10))
        assertNull(ring.push(2, 20))
        assertNull(ring.push(3, 30))
        assertEquals(10, ring.push(4, 40))   // full: 10 goes
        assertEquals(20, ring.push(5, 50))
        assertEquals(3, ring.size)
        assertNull(ring.nearest(1, toleranceMs = 0))   // gone
        assertEquals(30, ring.nearest(3))
    }

    @Test
    fun `drain returns everything oldest first and empties the ring`() {
        val ring = FrameRing<Int>(3)
        for (i in 1..5) ring.push(i.toLong(), i)
        assertEquals(listOf(3, 4, 5), ring.drain())
        assertEquals(0, ring.size)
        assertNull(ring.nearest(4))
    }

    @Test
    fun `an empty ring answers nothing rather than throwing`() {
        val ring = FrameRing<String>(2)
        assertNull(ring.nearest(0))
        assertEquals(emptyList<String>(), ring.drain())
    }
}
