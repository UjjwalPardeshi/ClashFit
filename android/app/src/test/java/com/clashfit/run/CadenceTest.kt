package com.clashfit.run

import com.clashfit.core.util.FakeClock
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for cadence estimation from step counter and accelerometer.
 *
 * These drive [CadenceEstimator], the class the tracking service delegates to.
 */
class CadenceTest {

    private val clock = FakeClock()

    /** One accelerometer sample of a 2.5 Hz gait (150 spm) riding on gravity. */
    private fun gaitMagnitude(tMs: Long): Float =
        (9.8 + 3.0 * sin(2 * PI * 2.5 * (tMs / 1000.0))).toFloat()

    private fun feedGait(estimator: CadenceEstimator, samples: Int) {
        repeat(samples) {
            clock.advance(50) // 20 Hz
            estimator.onAccelerometer(gaitMagnitude(clock.nowMs()), 0f, 0f, clock.nowMs())
        }
    }

    @Test
    fun cadenceAccelerometer_noFallback_ifStepCounterUnavailable() {
        // No step counter, and barely any accelerometer history: the estimator must say nothing
        // rather than invent a plausible-looking 150.
        val estimator = CadenceEstimator()

        feedGait(estimator, samples = 20) // one second of gait, ≈2 footfalls

        assertTrue(estimator.peakCount < CadenceEstimator.MIN_PEAKS_FOR_ESTIMATE, "Precondition: too few peaks")
        assertEquals(0, estimator.cadenceSpm, "Cadence should be 0 when step counter unavailable, not fake estimate")
    }

    @Test
    fun stepCounterBaseline_setAtRegistration_notAtFirstEvent() {
        // The sensor counts steps since boot and fires late: registration at t=0, first event at
        // t=3s reading 1000. Baseline and clock come from that same event, so the three seconds of
        // silence before it cannot stretch the window the steps are divided by. Measuring from
        // registration instead would report 100 spm for a runner holding 150.
        val estimator = CadenceEstimator()

        clock.advance(3_000)
        assertEquals(0, estimator.onStepCount(totalSteps = 1_000L, nowMs = clock.nowMs()), "First reading only seeds")
        assertTrue(estimator.hasStepBaseline, "The first reading takes the baseline")

        // 5 steps every 2 seconds = 150 spm.
        var cadenceSpm = 0
        for (step in 1..3) {
            clock.advance(2_000)
            cadenceSpm = estimator.onStepCount(totalSteps = 1_000L + step * 5, nowMs = clock.nowMs())
        }

        assertTrue(cadenceSpm > 0, "Cadence should be calculated from baseline")
        assertTrue(cadenceSpm in 80..180, "Cadence should be reasonable (~150 spm for normal running)")
        assertEquals(150, cadenceSpm, "15 steps over 6 seconds is 150 spm")
    }

    @Test
    fun cadenceEstimate_fromStepCounter_reasonable() {
        // 100 steps over 40 seconds. Expected cadence: 150 spm.
        val estimator = CadenceEstimator()

        estimator.onStepCount(totalSteps = 100L, nowMs = clock.nowMs())
        clock.advance(40_000)
        val cadenceSpm = estimator.onStepCount(totalSteps = 200L, nowMs = clock.nowMs())

        assertEquals(150, cadenceSpm, "Cadence should be 150 spm (100 steps / 40 sec)")
    }

    @Test
    fun accelerometerFallback_requiresSufficientPeaks_beforeEstimate() {
        // The fallback stays silent below 10 footfalls and speaks up at 10.
        val estimator = CadenceEstimator()

        while (estimator.peakCount < CadenceEstimator.MIN_PEAKS_FOR_ESTIMATE - 1) {
            feedGait(estimator, samples = 1)
            assertTrue(clock.nowMs() < 60_000, "Gait simulation should produce peaks")
        }
        assertEquals(0, estimator.cadenceSpm, "Cadence should not estimate with < 10 peaks")

        while (estimator.peakCount < CadenceEstimator.MIN_PEAKS_FOR_ESTIMATE) {
            feedGait(estimator, samples = 1)
            assertTrue(clock.nowMs() < 60_000, "Gait simulation should produce peaks")
        }

        // Now should have estimate
        assertTrue(estimator.cadenceSpm > 0, "Cadence should estimate after 10+ peaks")
        assertTrue(estimator.cadenceSpm in 130..170, "A 2.5 Hz gait is ~150 spm, got ${estimator.cadenceSpm}")
    }
}
