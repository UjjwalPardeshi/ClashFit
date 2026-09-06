package com.clashfit.engine.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one piece of the gym log that can silently lie.
 *
 * Reps are counted by the camera and cannot be fabricated. The weight is typed, converted, stored
 * and read back, and every one of those steps is a chance to turn a number into a different one.
 */
class WeightUnitTest {

    @Test
    fun `kilograms are the identity, because that is what is on disk`() {
        assertEquals(20f, WeightUnit.KG.fromKg(20f), 0.001f)
        assertEquals(20f, WeightUnit.KG.toKg(20f), 0.001f)
    }

    @Test
    fun `a round trip through pounds returns the same weight`() {
        // The failure this guards: type 45 lbs, store it, come back tomorrow, and be told you
        // lifted 44.9 — which reads as having got weaker, from arithmetic alone.
        listOf(0f, 2.5f, 20f, 42.5f, 100f, 227.5f).forEach { kg ->
            val shown = WeightUnit.LBS.fromKg(kg)
            assertEquals(kg, WeightUnit.LBS.toKg(shown), 0.001f, "$kg kg did not survive the trip")
        }
    }

    @Test
    fun `the conversion is the real one`() {
        assertEquals(220.462f, WeightUnit.LBS.fromKg(100f), 0.01f)
        assertEquals(45.359f, WeightUnit.LBS.toKg(100f), 0.01f)
    }

    @Test
    fun `a whole number loses its decimal and everything else keeps exactly one`() {
        assertEquals("20", WeightUnit.KG.show(20f))
        assertEquals("2.5", WeightUnit.KG.show(2.5f))
        // 20 kg is 44.09 lbs. One decimal, because the second is arithmetic rather than a weight
        // anybody actually put on a bar.
        assertEquals("44.1", WeightUnit.LBS.show(20f))
        assertEquals("0", WeightUnit.KG.show(0f))
    }

    @Test
    fun `bodyweight shows as zero rather than as nothing`() {
        // Zero is a real answer in this log: it means the movement was done with no added weight.
        assertEquals("0", WeightUnit.KG.show(0f))
        assertEquals("0", WeightUnit.LBS.show(0f))
    }

    @Test
    fun `the flag maps to the unit the way the preference reads`() {
        assertEquals(WeightUnit.LBS, WeightUnit.of(lbs = true))
        assertEquals(WeightUnit.KG, WeightUnit.of(lbs = false))
    }

    @Test
    fun `pounds are always the larger number, which is the point of the toggle`() {
        listOf(1f, 20f, 100f).forEach { kg ->
            assertTrue(WeightUnit.LBS.fromKg(kg) > WeightUnit.KG.fromKg(kg))
        }
    }
}
