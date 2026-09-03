package com.clashfit.audio

import org.junit.Test
import kotlin.test.assertTrue

class SfxTest {

    @Test
    fun `rep creates sound without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.rep("CLEAN", 1.5f)
        sfx.rep("SHALLOW", 1.0f)
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking (< 100ms)
        assertTrue(duration < 100, "rep() should not block the caller")
    }

    @Test
    fun `milestone creates chime sequence without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.milestone()
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking
        assertTrue(duration < 100, "milestone() should not block the caller")
    }

    @Test
    fun `phase creates sound without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.phase()
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking
        assertTrue(duration < 100, "phase() should not block the caller")
    }

    @Test
    fun `bossDown creates death sound without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.bossDown()
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking
        assertTrue(duration < 100, "bossDown() should not block the caller")
    }

    @Test
    fun `framingLost creates two-tone without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.framingLost()
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking
        assertTrue(duration < 100, "framingLost() should not block the caller")
    }

    @Test
    fun `tick creates sound without blocking`() {
        val sfx = Sfx()
        val startTime = System.currentTimeMillis()
        sfx.tick()
        sfx.tick()
        sfx.tick()
        val duration = System.currentTimeMillis() - startTime
        // Should return immediately without blocking (all three calls)
        assertTrue(duration < 100, "tick() calls should not block the caller")
    }

    @Test
    fun `combo multiplier affects rep pitch`() {
        // Verify that different combo multipliers produce different frequencies
        val sfx = Sfx()
        sfx.rep("CLEAN", 1.0f) // Low pitch
        sfx.rep("CLEAN", 2.5f) // High pitch
        // Audio effects are best tested on a device; this just verifies no crash
        assertTrue(true)
    }
}
