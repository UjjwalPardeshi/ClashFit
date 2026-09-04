package com.clashfit.run

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored splits column is named splitsJson and does not hold JSON.
 *
 * A plain split-on-comma over a bracketed list drops the first and last entry and silently
 * renumbers the rest, so a five-kilometre run reports three splits and calls km 2 "km 1". That is
 * a wrong number presented with total confidence, which is the worst kind.
 */
class RunSplitsTest {

    @Test
    fun `the bare comma-joined form every run actually writes`() {
        assertEquals(listOf(298f, 306f, 311f), parseSplits("298.0,306.0,311.0"))
    }

    @Test
    fun `a bracketed list keeps its first and last split`() {
        assertEquals(listOf(298f, 306f, 311f, 320f, 331f), parseSplits("[298,306,311,320,331]"))
    }

    @Test
    fun `spaces around the separators do not lose a kilometre`() {
        assertEquals(listOf(298f, 306f), parseSplits("[ 298 , 306 ]"))
    }

    @Test
    fun `nothing stored means no splits rather than a phantom one`() {
        assertEquals(emptyList<Float>(), parseSplits(""))
        assertEquals(emptyList<Float>(), parseSplits("[]"))
    }
}
