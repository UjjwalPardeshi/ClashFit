package com.clashfit.meta

import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind

/**
 * Which badges a finished session unlocks.
 *
 * Two rules govern this file:
 *
 * 1. Thresholds are **crossings**, never equalities. A session that takes you from 95 to 105 clean
 *    reps has passed 100 and earns the badge. Testing `== 100` silently loses badges for anyone
 *    who trains in big sets, which is exactly the player most likely to care.
 * 2. The counter arithmetic lives in [advance] and nowhere else. The repository writes the same
 *    numbers it tests against, so the two can never drift apart.
 */
object AchievementRules {

    /** Lifetime totals. [level] is the level those totals put the player at. */
    data class MetaCounters(
        val sessions: Int = 0,
        val wins: Int = 0,
        val totalReps: Int = 0,
        val cleanReps: Int = 0,
        val totalDamage: Int = 0,
        /** One bit per [Family] ordinal. */
        val familiesPlayedMask: Int = 0,
        val pbCount: Int = 0,
        val weeklyChallengesDone: Int = 0,
        val bestStreak: Int = 0,
        val level: Int = 1,
    )

    /**
     * The counters after one session. The single place this arithmetic exists.
     *
     * @param levelAfter the level the new XP total puts the player at
     * @param weeklyCompleted true when this session crossed the weekly challenge's target
     */
    fun advance(
        before: MetaCounters,
        facts: SessionFacts,
        levelAfter: Int = before.level,
        weeklyCompleted: Boolean = false,
    ): MetaCounters = before.copy(
        sessions = before.sessions + 1,
        wins = before.wins + if (facts.won) 1 else 0,
        totalReps = before.totalReps + facts.reps,
        cleanReps = before.cleanReps + facts.cleanReps,
        totalDamage = before.totalDamage + facts.damage,
        familiesPlayedMask = before.familiesPlayedMask or familyBit(facts),
        pbCount = before.pbCount + if (facts.newPersonalBest) 1 else 0,
        weeklyChallengesDone = before.weeklyChallengesDone + if (weeklyCompleted) 1 else 0,
        bestStreak = maxOf(before.bestStreak, facts.streakAfter),
        level = levelAfter,
    )

    /**
     * The badges this session earned. Pass the counters from either side of [advance]; anything
     * already in [alreadyUnlocked] is never returned twice.
     */
    fun newlyUnlocked(
        before: MetaCounters,
        after: MetaCounters,
        facts: SessionFacts,
        alreadyUnlocked: Set<String>,
    ): List<Achievement> {
        val ids = mutableListOf<String>()

        fun crossed(id: String, threshold: Int, from: Int, to: Int) {
            if (from < threshold && to >= threshold) ids += id
        }

        crossed("first_fight", 1, before.sessions, after.sessions)
        crossed("ten_fights", 10, before.sessions, after.sessions)
        crossed("fifty_fights", 50, before.sessions, after.sessions)

        crossed("first_win", 1, before.wins, after.wins)

        crossed("streak_7", 7, before.bestStreak, after.bestStreak)
        crossed("streak_30", 30, before.bestStreak, after.bestStreak)

        crossed("clean_100", 100, before.cleanReps, after.cleanReps)
        crossed("clean_1000", 1000, before.cleanReps, after.cleanReps)

        crossed("damage_10k", 10_000, before.totalDamage, after.totalDamage)
        crossed("damage_100k", 100_000, before.totalDamage, after.totalDamage)

        crossed("pb_5", 5, before.pbCount, after.pbCount)
        crossed("weekly_first", 1, before.weeklyChallengesDone, after.weeklyChallengesDone)

        crossed("level_10", 10, before.level, after.level)
        crossed("level_25", 25, before.level, after.level)

        // Every movement family played at least once.
        crossed(
            "five_families", Family.entries.size,
            Integer.bitCount(before.familiesPlayedMask), Integer.bitCount(after.familiesPlayedMask),
        )

        // The first session of a kind that needs other people, or a clinic protocol.
        when (modeOrNull(facts.mode)?.kind) {
            ModeKind.VERSUS -> ids += "versus_first"
            ModeKind.GROUP -> ids += "raid_first"
            ModeKind.CLINIC -> ids += "clinic_first"
            ModeKind.SOLO, ModeKind.FAMILY, null -> Unit
        }

        return ids.asSequence()
            .distinct()
            .filterNot { it in alreadyUnlocked }
            .mapNotNull { AchievementCatalog.byId(it) }
            .toList()
    }

    /** The family bit this session earned: the exercise's own family, else the mode's. */
    private fun familyBit(facts: SessionFacts): Int {
        val family = facts.family?.let { name -> Family.entries.firstOrNull { it.name == name } }
            ?: modeOrNull(facts.mode)?.family
            ?: return 0
        return 1 shl family.ordinal
    }

    /** A mode name from an older build, or one this build does not know, is not an error. */
    private fun modeOrNull(mode: String): GameMode? = runCatching { GameMode.valueOf(mode) }.getOrNull()
}
