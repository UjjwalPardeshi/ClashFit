package com.clashfit.run

import com.clashfit.core.util.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for pause detection with hysteresis.
 *
 * These drive [MovingTimeTracker], the class the tracking service delegates to.
 */
class PauseDetectionTest {

    private val clock = FakeClock()

    /** One second of running at [speedMps]. */
    private fun tick(tracker: MovingTimeTracker, speedMps: Float) {
        clock.advance(1_000)
        tracker.onFix(clock.nowMs(), speedMps, deltaMs = 1_000L)
    }

    @Test
    fun pauseWithHysteresis_speedJitterAround0p5mps_singleToggle() {
        // Speed oscillates: 0.4→0.6→0.4→0.6→0.1 m/s
        // Verify isMoving toggles at most once, not 4 times
        // movingMs accumulates only during moving periods
        val tracker = MovingTimeTracker()

        listOf(0.4f, 0.6f, 0.4f, 0.6f, 0.1f).forEach { tick(tracker, it) }

        assertTrue(tracker.toggleCount <= 2, "isMoving toggled ${tracker.toggleCount} times (expected ≤2)")
        assertTrue(tracker.movingMs > 0, "movingMs should accumulate for moving periods")
        assertTrue(tracker.isMoving, "A single slow second is not a pause")
    }

    @Test
    fun movingTimeAccumulation_pausedInterval_notCounted() {
        // 2s at 1.0 m/s → 5s standing at 0.2 m/s → 2s at 1.0 m/s.
        // The three seconds of hysteresis buy time to be *sure* it is a pause; they are not three
        // seconds of running, so they must not be added to moving time either.
        val tracker = MovingTimeTracker()

        repeat(2) { tick(tracker, 1.0f) }
        assertEquals(2_000L, tracker.movingMs, "Two seconds of running")

        repeat(5) { tick(tracker, 0.2f) }
        assertTrue(!tracker.isMoving, "Five seconds below the threshold is a pause")

        repeat(2) { tick(tracker, 1.0f) }

        assertTrue(tracker.movingMs <= 5000, "movingMs should exclude pause (expected ~4000ms, got ${tracker.movingMs}ms)")
        assertEquals(4_000L, tracker.movingMs, "Only the seconds actually covered count")
        assertTrue(tracker.isMoving, "Running again after the pause")
    }

    @Test
    fun hysteresisBuffer_multipleSpeedJitters_noToggle() {
        // Speed rapidly jitters around 0.3 m/s threshold without settling
        // Should not toggle isMoving until threshold is held for 3+ seconds
        val tracker = MovingTimeTracker()
        tracker.markMoving()

        listOf(0.35f, 0.25f, 0.32f, 0.28f, 0.31f, 0.29f, 0.27f).forEach { tick(tracker, it) }

        assertEquals(0, tracker.toggleCount, "Should not toggle with jitter around threshold")
        assertTrue(tracker.isMoving, "Should remain moving until threshold is held")
    }
}
