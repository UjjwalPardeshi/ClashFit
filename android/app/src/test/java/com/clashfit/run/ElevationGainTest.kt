package com.clashfit.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for elevation gain calculation with smoothing and hysteresis.
 *
 * These drive [ElevationTracker], the class the tracking service delegates to.
 *
 * Every stream below holds each altitude for a few fixes, the way a 1 Hz receiver does. A level
 * that appears in a single sample is exactly what the smoothing exists to discard, so a test that
 * expects one to count would be asking the tracker to fail at its job.
 */
class ElevationGainTest {

    @Test
    fun elevationSmoothed_with3mHysteresis_noOscillation() {
        // A metre of wander around 100m, then a real climb to 105m.
        // The wander must not accumulate; the climb must land at ≈5m.
        val tracker = ElevationTracker()

        val wander = listOf(100.0, 101.0, 100.5, 101.5, 100.0, 100.5, 101.0, 100.0)
        wander.forEach { tracker.onAltitude(it) }
        assertTrue(tracker.gainM < 2f, "Metre-scale wander should not accumulate, got ${tracker.gainM}m")

        val climb = listOf(101.0, 102.0, 103.0, 104.0, 105.0, 105.0, 105.0)
        climb.forEach { tracker.onAltitude(it) }

        val elevationGainM = tracker.gainM
        assertTrue(elevationGainM in 4.5f..6.5f, "Elevation gain should be ~5-6m with hysteresis, got ${elevationGainM}m")
    }

    @Test
    fun elevationGain_fine100mClimb_notFiltered() {
        // 100m→110m at 0.5 m/s: a fine-grained climb, no single step of which clears the 3m band.
        // The hysteresis must delay credit, not swallow it.
        val tracker = ElevationTracker()

        for (i in 0..20) {
            tracker.onAltitude(100.0 + i * 0.5)
        }
        // The runner reaches the top and holds it for two more fixes.
        tracker.onAltitude(110.0)
        tracker.onAltitude(110.0)

        val elevationGainM = tracker.gainM
        assertTrue(elevationGainM >= 9f, "Fine-grained climb should not be filtered, expected ≥9m gain, got ${elevationGainM}m")
        assertTrue(elevationGainM <= 11f, "…and should not over-count the 10m climb either, got ${elevationGainM}m")
    }

    @Test
    fun elevationGain_downwardHysteresis_onlyPositiveCounts() {
        // 100m → 95m → 100m → 105m, each level held.
        // The 5m descent must not subtract. Climbing back out of it is 5m of ascent, and the last
        // 5m is another: total ascent is 10m. (The audit's original expectation of 5m assumed a
        // re-climb of ground already descended is free, which is not how ascent is counted.)
        val tracker = ElevationTracker()

        val gainSamples = mutableListOf<Float>()
        for (altM in listOf(100.0, 100.0, 100.0, 95.0, 95.0, 95.0, 100.0, 100.0, 100.0, 105.0, 105.0, 105.0)) {
            gainSamples += tracker.onAltitude(altM)
        }

        assertEquals(10f, tracker.gainM, 0.5f, "Elevation gain should only count upward changes")
        assertTrue(
            gainSamples.zipWithNext().all { (previous, next) -> next >= previous },
            "Gain must never tick backwards on a descent: $gainSamples",
        )
    }

    @Test
    fun elevationGain_rapidOscillation_smoothed() {
        // Altitude oscillates rapidly: 100, 102, 98, 103, 97, 104 (noise pattern)
        // With smoothing, should result in lower gain than raw data
        val tracker = ElevationTracker()

        listOf(100.0, 102.0, 98.0, 103.0, 97.0, 104.0).forEach { tracker.onAltitude(it) }

        val elevationGainM = tracker.gainM
        assertTrue(elevationGainM < 4f, "Rapid oscillation should be mostly filtered by hysteresis, got ${elevationGainM}m")
    }
}
