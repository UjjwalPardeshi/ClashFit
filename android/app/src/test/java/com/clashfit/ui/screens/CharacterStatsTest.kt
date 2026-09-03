package com.clashfit.ui.screens

import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import com.clashfit.ui.screens.character.CharacterStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterStatsTest {

    @Test
    fun `compute returns zeros for empty sessions`() {
        val stats = CharacterStats.compute(emptyList(), null)

        assertEquals(0, stats.power)
        assertEquals(0, stats.stamina)
        assertEquals(0, stats.totalReps)
        assertEquals(0, stats.sessionCount)
        assertEquals(0f, stats.formAvg)
    }

    @Test
    fun `compute calculates stats from sessions`() {
        val sessions = listOf(
            SessionEntity(
                id = 1,
                startedAtMs = 0,
                endedAtMs = 60000,
                mode = "BOSS_FIGHT",
                exerciseId = "squat",
                bossId = "pacemaker",
                outcome = "WON",
                totalDamage = 500,
                totalReps = 10,
                formMean = 0.8f,
                peakFatigue = 0.5f,
                peakBand = "WORKING"
            ),
            SessionEntity(
                id = 2,
                startedAtMs = 60000,
                endedAtMs = 120000,
                mode = "BOSS_FIGHT",
                exerciseId = "squat",
                bossId = "pacemaker",
                outcome = "WON",
                totalDamage = 600,
                totalReps = 12,
                formMean = 0.75f,
                peakFatigue = 0.6f,
                peakBand = "FADING"
            ),
        )

        val stats = CharacterStats.compute(sessions, null)

        assertEquals(2, stats.sessionCount)
        assertEquals(22, stats.totalReps)
        assertTrue(stats.formAvg > 0.7f)
        assertTrue(stats.power > 0)
        assertTrue(stats.stamina > 0)
    }

    @Test
    fun `compute includes streak resilience`() {
        val sessions = listOf(
            SessionEntity(
                id = 1,
                startedAtMs = 0,
                endedAtMs = 60000,
                mode = "BOSS_FIGHT",
                exerciseId = "squat",
                bossId = "pacemaker",
                outcome = "WON",
                totalDamage = 400,
                totalReps = 8,
                formMean = 0.7f,
                peakFatigue = 0.4f,
                peakBand = "WORKING"
            ),
        )
        val streak = StreakEntity(current = 5, best = 10, freezes = 1)

        val stats = CharacterStats.compute(sessions, streak)

        assertEquals(5, stats.resilience)
    }

    @Test
    fun `form avg is bounded to 0-1`() {
        val sessions = listOf(
            SessionEntity(
                id = 1,
                startedAtMs = 0,
                endedAtMs = 60000,
                mode = "BOSS_FIGHT",
                exerciseId = "squat",
                bossId = "pacemaker",
                outcome = "WON",
                totalDamage = 100,
                totalReps = 5,
                formMean = 0.95f,
                peakFatigue = 0.3f,
                peakBand = "WORKING"
            ),
        )

        val stats = CharacterStats.compute(sessions, null)

        assertTrue(stats.formAvg in 0f..1f)
        assertEquals(95, stats.focus) // 0.95f * 100
    }
}
