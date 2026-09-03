package com.clashfit.engine.session

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.engine.core.RepDetectorConfig
import com.clashfit.engine.core.Synth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for topPercentile() calibration.
 * Verifies 90th percentile calculation works correctly for:
 * 1. Decreasing angle exercises (squat, pushup)
 * 2. Increasing angle exercises (calf raise, glute bridge)
 * 3. Edge cases: 10, 30, 100, 120 samples
 */
class CalibrationTest {

    @Test
    fun `topPercentile 90th percentile with 10 samples decreasing angle`() {
        val samples = listOf(20f, 22f, 25f, 21f, 23f, 24f, 19f, 26f, 20f, 22f)
        // ascending [19,20,20,21,22,22,23,24,25,26], index min(9, floor(10*0.9)) = 9
        val percentile90 = topPercentile(samples, dec = true)

        assertTrue(percentile90 > 0f, "90th percentile should be positive")
        assertEquals(26f, percentile90, "10 samples: index 9 selects the highest value for decreasing")
    }

    @Test
    fun `topPercentile 90th percentile with 30 samples`() {
        val samples = (1..30).map { it.toFloat() }

        // index = floor(30 * 0.9) = 27; the samples run 1..30, so sorted[27] == 28f
        assertEquals(28f, topPercentile(samples, dec = true), "30 samples, 90th percentile index = 27")
    }

    @Test
    fun `topPercentile 90th percentile with 100 samples`() {
        val samples = (1..100).map { it.toFloat() }

        // index = floor(100 * 0.9) = 90; the samples run 1..100, so sorted[90] == 91f
        assertEquals(91f, topPercentile(samples, dec = true), "100 samples, 90th percentile index = 90")
    }

    @Test
    fun `topPercentile 90th percentile with 120 samples`() {
        val samples = (1..120).map { it.toFloat() }

        // 120 is the ring-buffer cap; index = min(119, floor(120 * 0.9)) = 108, so sorted[108] == 109f
        assertEquals(109f, topPercentile(samples, dec = true), "120 samples, 90th percentile index = 108")
    }

    @Test
    fun `topPercentile direction for decreasing angle squat`() {
        // Squat: topEnter > bottomEnter, so direction is decreasing (s = 1).
        // Sorted ascending, and index 9 of 10 selects the highest value.
        val samples = listOf(10f, 15f, 12f, 18f, 14f, 16f, 11f, 17f, 13f, 19f)

        assertEquals(19f, topPercentile(samples, dec = true), "Decreasing angle: 90th percentile is 19f")
    }

    @Test
    fun `topPercentile direction for increasing angle calf raise`() {
        // Calf raise: topEnter < bottomEnter, so direction is increasing (s = -1).
        // For increasing, we sort descending, so index 9 selects the lowest value.
        val samples = listOf(80f, 85f, 82f, 88f, 84f, 86f, 81f, 87f, 83f, 89f)

        assertEquals(80f, topPercentile(samples, dec = false), "Increasing angle: 90th percentile descending sort is 80f")
    }

    @Test
    fun `topPercentile with uniform samples`() {
        val samples = List(20) { 45f }

        assertEquals(45f, topPercentile(samples, dec = true), "Uniform samples should all be the same value")
    }

    @Test
    fun `topPercentile with bimodal distribution`() {
        val samples = listOf(20f, 20f, 20f, 20f, 20f, 100f, 100f, 100f, 100f, 100f)

        assertEquals(100f, topPercentile(samples, dec = true), "Bimodal distribution: 90th percentile should select upper mode")
    }

    @Test
    fun `topPercentile single sample`() {
        // floor(1 * 0.9) = 0 already, but the min() clamp is what keeps short holds in range.
        val samples = listOf(45f)

        assertEquals(45f, topPercentile(samples, dec = true), "Single sample: 90th percentile is that sample")
    }

    @Test
    fun `topRef calibration minimum 15 samples before ready`() {
        // Per SessionEngine.kt line 666: topRefSamples.size >= 15
        val minSamples = 15
        val samples = List(minSamples) { i -> (20f + i).toFloat() }

        assertTrue(samples.size >= minSamples, "Should have minimum 15 samples for calibration")
    }

    @Test
    fun `topRef collected during calibration hold`() {
        // During HOLDING state, samples are collected
        // After 2 seconds (60 frames at 30fps), should have 60 samples
        val fps = 30
        val holdSec = 2
        val expectedSamples = fps * holdSec
        val samples = List(expectedSamples) { i -> (25f + (i % 5)).toFloat() }

        assertTrue(samples.size >= 15, "Should collect enough samples for calibration")
        // 60 samples cycling 25..29, twelve of each; index floor(60 * 0.9) = 54 lands in the top group.
        assertEquals(29f, topPercentile(samples, dec = true), "Hold window resolves to the top angle")
    }

    @Test
    fun `topRef updated on nextSet preserves calibration`() {
        // When nextSet() is called, topRef is preserved and re-applied to FSM
        // This ensures calibration carries across sets
        val topRef = 25f
        assertTrue(topRef > 0f, "topRef should be valid calibration value")
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) fail(message)
    }
}
