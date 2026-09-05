package com.clashfit.meta

import kotlinx.coroutines.flow.Flow

/*
 * Meta-progression: XP, levels, ranks, achievements and the weekly challenge. The rules are pure
 * and tested; the repository is the only writer of the tables behind them. docs/23-META-PROGRESSION.md
 *
 * One rule governs XP: it is earned from quality, not quantity. A clean rep is always worth more
 * than a sloppy one, and nothing here ever makes the game easier for someone who played more.
 */

/** Where the player stands on the ladder, and how far to the next rung. */
data class LevelProgress(
    val level: Int,
    val title: String,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
) {
    val fraction: Float get() = if (xpForLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
}

enum class Tier { BRONZE, SILVER, GOLD }

data class Achievement(val id: String, val title: String, val description: String, val tier: Tier)

data class Unlocked(val id: String, val unlockedAtMs: Long)

enum class Metric { DAMAGE, CLEAN_REPS, SESSIONS, STREAK_DAYS }

/** One rotating target per ISO week, the same for everyone that week. */
data class WeeklyChallenge(
    val weekKey: String,
    val title: String,
    val description: String,
    val metric: Metric,
    val target: Int,
)

data class WeeklyProgress(val challenge: WeeklyChallenge, val value: Int, val completedAtMs: Long?) {
    val fraction: Float get() = (value.toFloat() / challenge.target).coerceIn(0f, 1f)
    val done: Boolean get() = completedAtMs != null || value >= challenge.target
}

data class MetaState(
    val xp: Long,
    val progress: LevelProgress,
    val unlocked: List<Unlocked>,
    val weekly: WeeklyProgress,
)

/** What a finished session contributed. A flat summary, so the rules never need the database. */
data class SessionFacts(
    val sessionId: Long,
    val atMs: Long,
    val mode: String,
    val exerciseId: String,
    val reps: Int,
    val cleanReps: Int,
    val formMean: Float,
    val damage: Int,
    val won: Boolean,
    val casual: Boolean,
    val durationSec: Int,
    val streakAfter: Int,
    val newPersonalBest: Boolean,
    /** The exercise's movement family (Family.name), when the mode does not fix one. */
    val family: String? = null,
)

data class RewardLine(val label: String, val xp: Int)

/** Everything the summary screen says about what one session earned. */
data class SessionReward(
    val xp: Int,
    val lines: List<RewardLine>,
    val before: LevelProgress,
    val after: LevelProgress,
    val newAchievements: List<Achievement>,
    val weekly: WeeklyProgress,
) {
    val leveledUp: Boolean get() = after.level > before.level
}

interface MetaRepository {
    val state: Flow<MetaState>
    suspend fun current(): MetaState
    suspend fun onSessionFinished(facts: SessionFacts): SessionReward

    /**
     * Banks what a finished run or walk earned.
     *
     * Deliberately not routed through [onSessionFinished]. An activity outdoors is measured by
     * GPS, not by the camera, so it has no form score, no damage and no movement family — and
     * feeding it in as a session would inflate the session count, the family mask and the weekly
     * damage target with something the camera never graded. Levels are shared; the counters that
     * describe fighting are not.
     *
     * Idempotent: banking the same activity twice returns what it earned the first time.
     */
    suspend fun onActivityFinished(facts: OutdoorRules.ActivityFacts): ActivityReward
}
