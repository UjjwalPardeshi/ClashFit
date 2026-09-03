package com.clashfit.meta

import com.clashfit.meta.LevelProgress
import com.clashfit.meta.Metric
import com.clashfit.meta.SessionFacts
import com.clashfit.meta.WeeklyChallenge
import com.clashfit.meta.WeeklyProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class XpRulesTest {

    @Test
    fun `20 reps at formMean 0_8 earns base XP of 200`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 20, cleanReps = 10, formMean = 0.8f, damage = 100, won = false, casual = false,
            durationSec = 300, streakAfter = 1, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val baseXp = lines.first { it.label == "20 reps" }.xp
        assertEquals(200, baseXp)
    }

    @Test
    fun `casual session earns half rep XP`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 20, cleanReps = 10, formMean = 0.8f, damage = 100, won = true, casual = true,
            durationSec = 300, streakAfter = 1, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val baseXp = lines.first { it.label == "20 reps" }.xp
        assertEquals(100, baseXp)
    }

    @Test
    fun `win bonus awarded only for non-casual wins`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 10, cleanReps = 5, formMean = 0.5f, damage = 100, won = true, casual = false,
            durationSec = 300, streakAfter = 1, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val hasWinBonus = lines.any { it.label == "Boss defeated" }
        assertEquals(true, hasWinBonus)
    }

    @Test
    fun `casual win has no win bonus`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 10, cleanReps = 5, formMean = 0.5f, damage = 100, won = true, casual = true,
            durationSec = 300, streakAfter = 1, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val hasWinBonus = lines.any { it.label == "Boss defeated" }
        assertEquals(false, hasWinBonus)
    }

    @Test
    fun `personal best bonus awarded`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 10, cleanReps = 5, formMean = 0.5f, damage = 100, won = false, casual = false,
            durationSec = 300, streakAfter = 1, newPersonalBest = true
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val pbBonus = lines.find { it.label == "New personal best" }
        assertEquals(30, pbBonus?.xp)
    }

    @Test
    fun `streak day 7 earns 5×7=35 XP`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 10, cleanReps = 5, formMean = 0.5f, damage = 100, won = false, casual = false,
            durationSec = 300, streakAfter = 7, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val streakLine = lines.find { it.label.startsWith("Streak day") }
        assertEquals(35, streakLine?.xp)
    }

    @Test
    fun `streak day 15 is capped at 5×7=35`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 10, cleanReps = 5, formMean = 0.5f, damage = 100, won = false, casual = false,
            durationSec = 300, streakAfter = 15, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val streakLine = lines.find { it.label.startsWith("Streak day") }
        assertEquals(35, streakLine?.xp)
    }

    @Test
    fun `weekly challenge completion fires exactly once`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 20, cleanReps = 10, formMean = 0.5f, damage = 5500, won = false, casual = false,
            durationSec = 300, streakAfter = 1, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 1000, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val weeklyLine = lines.find { it.label == "Weekly challenge complete" }
        assertEquals(100, weeklyLine?.xp)

        // Call again with same weekly already done - should not award
        val weekly2 = WeeklyProgress(
            challenge = weekly.challenge,
            value = 1500, completedAtMs = 2000
        )
        val lines2 = XpRules.reward(facts, weekly2, before)
        val weeklyLine2 = lines2.find { it.label == "Weekly challenge complete" }
        assertEquals(null, weeklyLine2)
    }

    @Test
    fun `no XP below formMean 0_125`() {
        val facts = SessionFacts(
            sessionId = 1L, atMs = 1000, mode = "BOSS_FIGHT", exerciseId = "squat",
            reps = 1, cleanReps = 0, formMean = 0.0f, damage = 1, won = false, casual = false,
            durationSec = 30, streakAfter = 0, newPersonalBest = false
        )
        val weekly = WeeklyProgress(
            challenge = WeeklyChallenge(weekKey = "2026-W36", title = "", description = "", metric = Metric.DAMAGE, target = 5000),
            value = 0, completedAtMs = null
        )
        val before = LevelProgress(level = 1, title = "Recruit", xpIntoLevel = 0, xpForLevel = 100)

        val lines = XpRules.reward(facts, weekly, before)
        val baseXp = lines.find { it.label == "1 reps" }?.xp ?: 0
        assertEquals(4, baseXp)
    }
}
