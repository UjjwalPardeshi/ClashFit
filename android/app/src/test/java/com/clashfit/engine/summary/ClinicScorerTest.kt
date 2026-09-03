package com.clashfit.engine.summary

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClinicScorerTest {

    @Test
    fun `clinic scorer - never shows norms until they are cited`() {
        val scorer = ClinicScorer()
        val result = scorer.score(14)
        assertEquals(false, result.hasNormComparison)
        assertEquals(14, result.rawCount)
    }

    @Test
    fun `clinic scorer - ships no norms until they are cited`() {
        // This is enforced by the schema — norms.bands must be empty until sourced
        val scorer = ClinicScorer("sit_to_stand_30s")
        val result = scorer.score(12)
        assertFalse(result.hasNormComparison)
    }

    @Test
    fun `clinic scorer - protocol is recorded`() {
        val scorer = ClinicScorer("sit_to_stand_30s")
        val result = scorer.score(14)
        assertEquals("sit_to_stand_30s", result.protocol)
    }

    @Test
    fun `clinic scorer - trend extraction works with history`() {
        val scorer = ClinicScorer("sit_to_stand_30s")
        val history = listOf(
            ClinicTrendPoint(1000, 10),
            ClinicTrendPoint(2000, 12),
            ClinicTrendPoint(3000, 14),
        )
        val result = scorer.score(14, history)
        assertEquals(3, result.trend.size)
        assertEquals(10, result.trend[0].count)
        assertEquals(14, result.trend[2].count)
    }

    @Test
    fun `clinic scorer - raw count is always shown`() {
        val scorer = ClinicScorer()
        val result = scorer.score(20)
        assertEquals(20, result.rawCount)
    }

    @Test
    fun `clinic scorer - handles empty history`() {
        val scorer = ClinicScorer()
        val result = scorer.score(10, emptyList())
        assertEquals(10, result.rawCount)
        assertEquals(0, result.trend.size)
    }
}
