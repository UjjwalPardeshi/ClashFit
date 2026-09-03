package com.clashfit.meta

import kotlin.test.Test
import kotlin.test.assertEquals

/** Cumulative XP to reach level L is 100 × (L−1)^1.5; titles apply from the level they name. */
class LevelsTest {

    @Test
    fun `thresholds follow the formula`() {
        assertEquals(0, Levels.xpForLevel(1))
        assertEquals(100, Levels.xpForLevel(2))
        assertEquals(283, Levels.xpForLevel(3))
        assertEquals(800, Levels.xpForLevel(5))
        assertEquals(1852, Levels.xpForLevel(8))
        assertEquals(2700, Levels.xpForLevel(10))
        assertEquals(8282, Levels.xpForLevel(20))
    }

    @Test
    fun `level is the highest threshold reached`() {
        assertEquals(1, Levels.forXp(0).level)
        assertEquals(1, Levels.forXp(99).level)
        assertEquals(2, Levels.forXp(100).level)
        assertEquals(3, Levels.forXp(283).level)
        assertEquals(5, Levels.forXp(800).level)
        assertEquals(10, Levels.forXp(2700).level)
        assertEquals(20, Levels.forXp(8290).level)
        assertEquals(30, Levels.forXp(Levels.xpForLevel(30)).level)
        assertEquals(40, Levels.forXp(Levels.xpForLevel(40)).level)
    }

    @Test
    fun `titles change at 1 3 5 8 12 16 20 30 40`() {
        assertEquals("Recruit", Levels.forXp(0).title)
        assertEquals("Recruit", Levels.forXp(100).title)          // level 2 keeps the first title
        assertEquals("Contender", Levels.forXp(283).title)        // level 3
        assertEquals("Brawler", Levels.forXp(800).title)          // level 5
        assertEquals("Brawler", Levels.forXp(1500).title)         // level 7
        assertEquals("Fighter", Levels.forXp(1852).title)         // level 8
        assertEquals("Fighter", Levels.forXp(2700).title)         // level 10
        assertEquals("Warrior", Levels.forXp(Levels.xpForLevel(12)).title)
        assertEquals("Gladiator", Levels.forXp(Levels.xpForLevel(16)).title)
        assertEquals("Champion", Levels.forXp(8290).title)        // level 20
        assertEquals("Titan", Levels.forXp(Levels.xpForLevel(30)).title)
        assertEquals("Legend", Levels.forXp(Levels.xpForLevel(45)).title)
    }

    @Test
    fun `progress into the next level`() {
        val p = Levels.forXp(150)
        assertEquals(2, p.level)
        assertEquals(50, p.xpIntoLevel)
        assertEquals(183, p.xpForLevel)
        assertEquals(50f / 183f, p.fraction, 0.0001f)
    }
}
