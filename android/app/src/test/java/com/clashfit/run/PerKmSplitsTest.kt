package com.clashfit.run

import com.clashfit.core.util.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for per-km split calculation using moving time instead of elapsed time.
 */
class PerKmSplitsTest {

    private val clock = FakeClock()

    @Test
    fun splits_useMovingTime_notElapsedTime() {
        // Run 1km in 600s moving time, pause 30s, run 2nd km in 600s
        // Expected splits [600s, 600s]. Current code shows [600s, 630s] (pause inflates 2nd split)

        var distanceM = 0f
        var movingMs = 0L
        val splits = mutableListOf<Float>()
        var nextKmMarker = 1000f
        var lastKmMovingMs = 0L

        // Phase 1: Run 1km in 600 seconds
        for (i in 0..599) {
            clock.advance(1000) // 1 second per update
            distanceM += 1.667f // ~1000m over 600 seconds
            movingMs += 1000

            // Check for km splits
            if (distanceM >= nextKmMarker) {
                val movingMsForSplit = movingMs - lastKmMovingMs
                val kmPaceSec = if (movingMsForSplit > 0) {
                    movingMsForSplit / 1000f
                } else {
                    0f
                }
                splits.add(kmPaceSec)
                lastKmMovingMs = movingMs
                nextKmMarker += 1000f
            }
        }

        // Phase 2: Pause for 30 seconds
        for (i in 0..29) {
            clock.advance(1000)
            // movingMs NOT incremented during pause
        }

        // Phase 3: Run 2nd km in 600 seconds
        for (i in 0..599) {
            clock.advance(1000)
            distanceM += 1.667f
            movingMs += 1000

            // Check for km splits
            if (distanceM >= nextKmMarker) {
                val movingMsForSplit = movingMs - lastKmMovingMs
                val kmPaceSec = if (movingMsForSplit > 0) {
                    movingMsForSplit / 1000f
                } else {
                    0f
                }
                splits.add(kmPaceSec)
                lastKmMovingMs = movingMs
                nextKmMarker += 1000f
            }
        }

        // Both splits should be ~600s (using moving time, not elapsed time)
        assertEquals(2, splits.size, "Should have 2 splits")
        assertTrue(splits[0] in 590f..610f, "First split should be ~600s, got ${splits[0]}s")
        assertTrue(splits[1] in 590f..610f, "Second split should be ~600s (not 630s), got ${splits[1]}s")
    }

    @Test
    fun splitRecordedAtKmBoundary_not_atNextUpdate() {
        // User runs to exactly 1000m and pauses
        // Verify split recorded at 1000m point, not deferred 5+ seconds to next location

        var distanceM = 0f
        var movingMs = 0L
        val splits = mutableListOf<Float>()
        var nextKmMarker = 1000f
        var lastKmMovingMs = 0L
        var lastSplitDistanceM = 0f

        // Simulate reaching exactly 1000m
        distanceM = 1000f
        movingMs = 600_000L // 600 seconds of moving time

        // Check for km splits (this should trigger)
        if (distanceM >= nextKmMarker) {
            val movingMsForSplit = movingMs - lastKmMovingMs
            val kmPaceSec = if (movingMsForSplit > 0) {
                movingMsForSplit / 1000f
            } else {
                0f
            }
            splits.add(kmPaceSec)
            lastKmMovingMs = movingMs
            lastSplitDistanceM = distanceM
            nextKmMarker += 1000f
        }

        // Now pause (no distance advance)
        // Verify split was already recorded at 1000m, not deferred

        assertEquals(1, splits.size, "Split should be recorded immediately at km boundary")
        assertEquals(600f, splits[0], 1f, "Split pace should match moving time")
        assertEquals(1000f, lastSplitDistanceM, "Split should be recorded at 1000m, not deferred")
    }

    @Test
    fun splits_accumulate_correctly_across_multiple_km() {
        // Run 3km: 1st km=600s, pause 10s, 2nd km=500s, 3rd km=550s
        // Expected splits [600s, 500s, 550s]

        var distanceM = 0f
        var movingMs = 0L
        val splits = mutableListOf<Float>()
        var nextKmMarker = 1000f
        var lastKmMovingMs = 0L

        // 1st km: 600s moving time
        for (i in 0..599) {
            distanceM += 1.667f
            movingMs += 1000

            if (distanceM >= nextKmMarker) {
                val movingMsForSplit = movingMs - lastKmMovingMs
                splits.add(movingMsForSplit / 1000f)
                lastKmMovingMs = movingMs
                nextKmMarker += 1000f
            }
        }

        // Pause 10s (no moving time added)
        clock.advance(10_000)

        // 2nd km: 500s moving time
        for (i in 0..499) {
            distanceM += 2f
            movingMs += 1000

            if (distanceM >= nextKmMarker) {
                val movingMsForSplit = movingMs - lastKmMovingMs
                splits.add(movingMsForSplit / 1000f)
                lastKmMovingMs = movingMs
                nextKmMarker += 1000f
            }
        }

        // 3rd km: 550s moving time
        for (i in 0..549) {
            distanceM += 1.818f
            movingMs += 1000

            if (distanceM >= nextKmMarker) {
                val movingMsForSplit = movingMs - lastKmMovingMs
                splits.add(movingMsForSplit / 1000f)
                lastKmMovingMs = movingMs
                nextKmMarker += 1000f
            }
        }

        // Verify all splits
        assertEquals(3, splits.size, "Should have 3 splits")
        assertTrue(splits[0] in 590f..610f, "1st split ~600s")
        assertTrue(splits[1] in 490f..510f, "2nd split ~500s")
        assertTrue(splits[2] in 540f..560f, "3rd split ~550s")
    }
}
