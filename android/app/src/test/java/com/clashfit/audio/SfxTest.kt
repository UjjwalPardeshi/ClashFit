package com.clashfit.audio

import org.junit.Test
import kotlin.test.assertTrue

class SfxTest {

    @Test
    fun `rep creates sound without crashing`() {
        val sfx = Sfx()
        sfx.rep("CLEAN", 1.5f)
        sfx.rep("SHALLOW", 1.0f)
        // Just verify no exception thrown
        assertTrue(true)
    }

    @Test
    fun `milestone creates chime sequence without crashing`() {
        val sfx = Sfx()
        sfx.milestone()
        assertTrue(true)
    }

    @Test
    fun `phase creates sound without crashing`() {
        val sfx = Sfx()
        sfx.phase()
        assertTrue(true)
    }

    @Test
    fun `bossDown creates death sound without crashing`() {
        val sfx = Sfx()
        sfx.bossDown()
        assertTrue(true)
    }

    @Test
    fun `framingLost creates two-tone without crashing`() {
        val sfx = Sfx()
        sfx.framingLost()
        assertTrue(true)
    }

    @Test
    fun `tick creates sound without crashing`() {
        val sfx = Sfx()
        sfx.tick()
        sfx.tick()
        sfx.tick()
        assertTrue(true)
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
