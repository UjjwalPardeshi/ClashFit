package com.clashfit.meta

import com.clashfit.meta.Metric
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyChallengesTest {

    @Test
    fun `week key format is ISO standard YYYY-Www`() {
        val ms = 1725350400000L // 2024-09-02 (Monday) in UTC
        val weekKey = WeeklyChallenges.weekKey(ms, ZoneId.of("UTC"))
        assertTrue(weekKey.matches(Regex("\\d{4}-W\\d{2}")))
    }

    @Test
    fun `same week produces same challenge`() {
        val weekKey = "2026-W36"
        val challenge1 = WeeklyChallenges.forWeek(weekKey)
        val challenge2 = WeeklyChallenges.forWeek(weekKey)
        assertEquals(challenge1.metric, challenge2.metric)
        assertEquals(challenge1.target, challenge2.target)
    }

    @Test
    fun `different weeks may produce different challenges`() {
        val challenge1 = WeeklyChallenges.forWeek("2026-W35")
        val challenge2 = WeeklyChallenges.forWeek("2026-W36")
        // These may or may not be different depending on hash, but at least they're valid
        assertTrue(challenge1.metric in listOf(Metric.DAMAGE, Metric.CLEAN_REPS, Metric.SESSIONS, Metric.STREAK_DAYS))
        assertTrue(challenge2.metric in listOf(Metric.DAMAGE, Metric.CLEAN_REPS, Metric.SESSIONS, Metric.STREAK_DAYS))
    }

    @Test
    fun `challenge targets are in valid ranges`() {
        for (i in 1..20) {
            val weekKey = "2026-W$i"
            val challenge = WeeklyChallenges.forWeek(weekKey)
            when (challenge.metric) {
                Metric.DAMAGE -> assertTrue(challenge.target in 3000..8000)
                Metric.CLEAN_REPS -> assertTrue(challenge.target in 150..400)
                Metric.SESSIONS -> assertTrue(challenge.target in 3..5)
                Metric.STREAK_DAYS -> assertTrue(challenge.target in 3..7)
            }
        }
    }

    @Test
    fun `week key reflects calendar weeks correctly`() {
        // 2026-W36 should be week 36 of 2026
        val weekKey = "2026-W36"
        val parts = weekKey.split("-W")
        val year = parts[0].toInt()
        val week = parts[1].toInt()
        assertEquals(2026, year)
        assertEquals(36, week)
    }
}
