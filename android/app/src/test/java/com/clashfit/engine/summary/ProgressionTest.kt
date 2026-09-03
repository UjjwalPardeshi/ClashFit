package com.clashfit.engine.summary

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {
    private val DAY = 86_400_000L

    @Test
    fun `streak - a rest day earns the streak rather than breaking it`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        prog.updateStreak(base)
        assertEquals(1, prog.getStreak().current)

        prog.updateStreak(base + DAY)
        assertEquals(2, prog.getStreak().current)

        prog.updateStreak(base + 3 * DAY) // skipped one day
        assertEquals(3, prog.getStreak().current)
        assertEquals(1, prog.getStreak().restDaysUsedThisWeek)
    }

    @Test
    fun `streak - two sessions on one day do not double-count`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T08:00:00Z").toEpochMilli()

        prog.updateStreak(base)
        assertEquals(1, prog.getStreak().current)

        prog.updateStreak(base + 3600_000)
        assertEquals(1, prog.getStreak().current)
    }

    @Test
    fun `streak - a long gap resets and never reports a number lost`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        prog.updateStreak(base)
        prog.updateStreak(base + 30 * DAY)
        assertEquals(1, prog.getStreak().current)
        assertTrue(prog.getStreak().best >= 1)

        val label = Progression().streakLabel()
        assertEquals("Back at it", label)
        assertFalse(label.contains("0") && label.contains("lost") && label.contains("broke"))
    }

    @Test
    fun `streak - a broken streak never shows a number lost`() {
        val prog = Progression()
        val label = prog.streakLabel()
        assertEquals("Back at it", label)
    }

    @Test
    fun `streak - day 1 has its own label`() {
        val prog = Progression()
        prog.updateStreak(System.currentTimeMillis())
        assertEquals("Day 1", prog.streakLabel())
    }

    @Test
    fun `streak - multiple days show the count`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()
        prog.updateStreak(base)
        prog.updateStreak(base + DAY)
        prog.updateStreak(base + 2 * DAY)
        assertEquals("3 day streak", prog.streakLabel())
    }

    @Test
    fun `bests - track per exercise and do not leak across exercises`() {
        val prog = Progression()
        prog.updateBests("squat", 12, 0.9f)
        prog.updateBests("push_up", 5, 0.6f)

        assertEquals(12, prog.getBests("squat").reps)
        assertEquals(5, prog.getBests("push_up").reps)
        assertEquals(null, prog.getBests("plank").reps)
    }

    @Test
    fun `bests - form is tracked`() {
        val prog = Progression()
        prog.updateBests("squat", 10, 0.85f)
        assertEquals(0.85f, prog.getBests("squat").formScore)
    }

    @Test
    fun `bests - depth, height, and hold are optional per rep`() {
        val prog = Progression()
        val details = listOf(
            Progression.RepDetail(depthCm = 45.5f),
            Progression.RepDetail(heightCm = 60.2f),
            Progression.RepDetail(holdSec = 30.5f),
        )
        prog.updateBests("squat", 8, 0.8f, details)
        assertEquals(45.5f, prog.getBests("squat").depthCm)
        assertEquals(60.2f, prog.getBests("squat").heightCm)
        assertEquals(30.5f, prog.getBests("squat").holdSec)
    }

    @Test
    fun `ladder - promotes on a sustained average, not one good day`() {
        val prog = Progression()
        val trend = mutableListOf<Progression.TrendPoint>()

        // First session: one good squat
        trend.add(Progression.TrendPoint("squat", 0.95f))
        var result = prog.ladderCheck("SQUAT", Ladders.SQUAT, "squat", trend = trend)
        assertFalse(result.changed)

        // Second and third sessions: sustained high form
        trend.add(Progression.TrendPoint("squat", 0.95f))
        trend.add(Progression.TrendPoint("squat", 0.95f))
        result = prog.ladderCheck("SQUAT", Ladders.SQUAT, "squat", trend = trend)
        assertTrue(result.changed)
        assertTrue(result.promoted)
        assertEquals("lunge", result.rung)
    }

    @Test
    fun `ladder - quietly offers a lower rung when form collapses`() {
        val prog = Progression()
        val trend = listOf(
            Progression.TrendPoint("squat", 0.3f),
            Progression.TrendPoint("squat", 0.3f),
            Progression.TrendPoint("squat", 0.3f),
        )
        val result = prog.ladderCheck("SQUAT", Ladders.SQUAT, "squat", trend = trend)
        assertFalse(result.promoted)
        assertEquals("chair_squat", result.rung)
    }

    @Test
    fun `personal bests - nothing to say is a valid answer`() {
        val prog = Progression()
        prog.updateBests("squat", 8, 0.7f)
        val bests = prog.personalBestsIn("push_up", 5, 0.8f)
        assertTrue(bests.isEmpty())
    }

    @Test
    fun `personal bests - reports what actually improved`() {
        val prog = Progression()
        prog.updateBests("squat", 10, 0.85f)
        val newBests = prog.personalBestsIn("squat", 12, 0.87f)
        assertEquals(2, newBests.size)
        assertEquals("reps", newBests[0].key)
        assertEquals("form", newBests[1].key)
    }

    @Test
    fun `freezes - earned every 10 days up to 3`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        for (i in 0 until 10) {
            prog.updateStreak(base + i * DAY)
        }
        assertEquals(2, prog.getStreak().freezes) // 1 (initial) + 1 (earned at 10)

        for (i in 10 until 20) {
            prog.updateStreak(base + i * DAY)
        }
        assertEquals(3, prog.getStreak().freezes) // earned at 20, capped at 3
    }

    @Test
    fun `freezes - cover a missed day or two but not a month`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        prog.updateStreak(base)
        prog.updateStreak(base + DAY)
        // Miss 2 days, then return
        prog.updateStreak(base + 4 * DAY)
        assertEquals(3, prog.getStreak().current) // freeze covered the gap
    }

    @Test
    fun `day key formats correctly`() {
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()
        assertEquals("2026-08-03", dayKey(base))
    }
}
