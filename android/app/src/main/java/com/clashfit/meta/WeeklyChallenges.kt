package com.clashfit.meta

import com.clashfit.meta.Metric as ChallengeMetric
import com.clashfit.meta.WeeklyChallenge
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.abs

/**
 * One rotating weekly challenge per ISO week, deterministic per week key.
 * Same week → same challenge for all players.
 */
object WeeklyChallenges {

    private val CHALLENGES = listOf(
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.DAMAGE, target = 3000,
            title = "Damage Dealer", description = "3000 damage total"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.DAMAGE, target = 5000,
            title = "Heavy Hitter", description = "5000 damage total"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.DAMAGE, target = 8000,
            title = "Demolition", description = "8000 damage total"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.CLEAN_REPS, target = 150,
            title = "Clean Form", description = "150 clean reps"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.CLEAN_REPS, target = 250,
            title = "Pristine", description = "250 clean reps"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.CLEAN_REPS, target = 400,
            title = "Flawless", description = "400 clean reps"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.SESSIONS, target = 3,
            title = "Consistency", description = "3 sessions"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.SESSIONS, target = 5,
            title = "Dedication", description = "5 sessions"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.STREAK_DAYS, target = 3,
            title = "Building Momentum", description = "3-day streak"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.STREAK_DAYS, target = 5,
            title = "Unstoppable", description = "5-day streak"
        ),
        WeeklyChallenge(
            weekKey = "", metric = ChallengeMetric.STREAK_DAYS, target = 7,
            title = "Perfect Week", description = "7-day streak"
        ),
    )

    /**
     * The challenge for a given ISO week key. Deterministic rotation.
     */
    fun forWeek(weekKey: String): WeeklyChallenge {
        val hash = abs(weekKey.hashCode() % CHALLENGES.size)
        return CHALLENGES[hash].copy(weekKey = weekKey)
    }

    /**
     * ISO week key (e.g. "2026-W36") from a timestamp and zone.
     */
    fun weekKey(atMs: Long, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(atMs)
        val ldt = LocalDateTime.ofInstant(instant, zone)
        val year = ldt.get(IsoFields.WEEK_BASED_YEAR)
        val week = ldt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%d-W%02d".format(year, week)
    }
}
