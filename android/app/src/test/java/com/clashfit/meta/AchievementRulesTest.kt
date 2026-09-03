package com.clashfit.meta

import com.clashfit.core.model.Family
import com.clashfit.meta.AchievementRules.MetaCounters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules are crossings, not equalities. Every test that lands *past* a threshold rather than
 * exactly on it is guarding the bug this file was rewritten to fix.
 */
class AchievementRulesTest {

    private fun facts(
        mode: String = "BOSS_FIGHT",
        reps: Int = 10,
        cleanReps: Int = 5,
        damage: Int = 100,
        won: Boolean = false,
        streakAfter: Int = 0,
        newPersonalBest: Boolean = false,
        family: String? = null,
    ) = SessionFacts(
        sessionId = 1L, atMs = 1_000L, mode = mode, exerciseId = "squat",
        reps = reps, cleanReps = cleanReps, formMean = 0.8f, damage = damage,
        won = won, casual = false, durationSec = 300,
        streakAfter = streakAfter, newPersonalBest = newPersonalBest, family = family,
    )

    /** Run one session against the rules, deriving `after` the same way the repository does. */
    private fun run(
        before: MetaCounters = MetaCounters(),
        f: SessionFacts = facts(),
        levelAfter: Int = before.level,
        weeklyCompleted: Boolean = false,
        alreadyUnlocked: Set<String> = emptySet(),
    ): List<String> {
        val after = AchievementRules.advance(before, f, levelAfter, weeklyCompleted)
        return AchievementRules.newlyUnlocked(before, after, f, alreadyUnlocked).map { it.id }
    }

    @Test
    fun `the very first session unlocks first_fight`() {
        assertTrue("first_fight" in run())
    }

    @Test
    fun `first_fight is not handed out twice`() {
        assertFalse("first_fight" in run(before = MetaCounters(sessions = 1)))
        assertFalse("first_fight" in run(alreadyUnlocked = setOf("first_fight")))
    }

    @Test
    fun `a versus session unlocks versus_first`() {
        assertTrue("versus_first" in run(f = facts(mode = "DUEL")))
        assertTrue("versus_first" in run(f = facts(mode = "REP_RACE")))
    }

    @Test
    fun `a group session unlocks raid_first and a clinic session unlocks clinic_first`() {
        assertTrue("raid_first" in run(f = facts(mode = "RAID")))
        assertTrue("clinic_first" in run(f = facts(mode = "CLINIC_STS")))
    }

    @Test
    fun `a solo session unlocks neither versus nor raid`() {
        val ids = run(f = facts(mode = "BOSS_FIGHT"))
        assertFalse("versus_first" in ids)
        assertFalse("raid_first" in ids)
        assertFalse("clinic_first" in ids)
    }

    @Test
    fun `an unknown mode name is survivable`() {
        val ids = run(f = facts(mode = "MODE_FROM_A_LATER_BUILD"))
        assertTrue("first_fight" in ids)
    }

    @Test
    fun `clean reps unlock by crossing the threshold, not by landing on it`() {
        // The bug this replaced: 95 + 10 = 105 skipped straight past 100 and never unlocked.
        assertTrue("clean_100" in run(before = MetaCounters(cleanReps = 95), f = facts(cleanReps = 10)))
        assertTrue("clean_100" in run(before = MetaCounters(cleanReps = 99), f = facts(cleanReps = 1)))
        assertFalse("clean_100" in run(before = MetaCounters(cleanReps = 100), f = facts(cleanReps = 10)))
    }

    @Test
    fun `damage unlocks by crossing`() {
        assertTrue("damage_10k" in run(before = MetaCounters(totalDamage = 9_950), f = facts(damage = 100)))
        assertFalse("damage_10k" in run(before = MetaCounters(totalDamage = 9_950), f = facts(damage = 10)))
        assertTrue("damage_100k" in run(before = MetaCounters(totalDamage = 99_000), f = facts(damage = 2_000)))
    }

    @Test
    fun `sessions unlock at ten and fifty`() {
        assertTrue("ten_fights" in run(before = MetaCounters(sessions = 9)))
        assertFalse("ten_fights" in run(before = MetaCounters(sessions = 10)))
        assertTrue("fifty_fights" in run(before = MetaCounters(sessions = 49)))
    }

    @Test
    fun `wins and personal bests`() {
        assertTrue("first_win" in run(f = facts(won = true)))
        assertFalse("first_win" in run(f = facts(won = false)))
        assertTrue("pb_5" in run(before = MetaCounters(pbCount = 4), f = facts(newPersonalBest = true)))
    }

    @Test
    fun `streaks unlock from the best streak, and a longer one still counts`() {
        assertTrue("streak_7" in run(f = facts(streakAfter = 7)))
        assertTrue("streak_7" in run(f = facts(streakAfter = 9)))     // jumped past 7
        assertFalse("streak_7" in run(before = MetaCounters(bestStreak = 7), f = facts(streakAfter = 8)))
        assertTrue("streak_30" in run(before = MetaCounters(bestStreak = 29), f = facts(streakAfter = 30)))
    }

    @Test
    fun `five_families needs a session in every family`() {
        // Four of the five already played; this session is the fifth.
        val fourPlayed = (0..3).fold(0) { mask, i -> mask or (1 shl i) }
        val fifth = Family.entries[4].name
        assertTrue("five_families" in run(before = MetaCounters(familiesPlayedMask = fourPlayed), f = facts(family = fifth)))
        // Repeating a family already played does not complete the set.
        assertFalse("five_families" in run(before = MetaCounters(familiesPlayedMask = fourPlayed), f = facts(family = Family.entries[0].name)))
    }

    @Test
    fun `the family comes from the mode when the session does not name one`() {
        // SIEGE is the isometric-hold family game, so it should light that bit with no explicit family.
        val before = MetaCounters()
        val after = AchievementRules.advance(before, facts(mode = "SIEGE", family = null))
        assertEquals(1 shl Family.ISOMETRIC_HOLD.ordinal, after.familiesPlayedMask)
    }

    @Test
    fun `levels unlock on the level after the session`() {
        assertTrue("level_10" in run(before = MetaCounters(level = 9), levelAfter = 10))
        assertTrue("level_10" in run(before = MetaCounters(level = 8), levelAfter = 12))  // jumped past 10
        assertFalse("level_10" in run(before = MetaCounters(level = 10), levelAfter = 11))
        assertTrue("level_25" in run(before = MetaCounters(level = 24), levelAfter = 25))
    }

    @Test
    fun `weekly_first unlocks the first time a challenge is completed`() {
        assertTrue("weekly_first" in run(weeklyCompleted = true))
        assertFalse("weekly_first" in run(weeklyCompleted = false))
        assertFalse("weekly_first" in run(before = MetaCounters(weeklyChallengesDone = 1), weeklyCompleted = true))
    }

    @Test
    fun `nothing already unlocked is returned again`() {
        val ids = run(f = facts(mode = "DUEL"), alreadyUnlocked = setOf("first_fight", "versus_first"))
        assertTrue(ids.isEmpty(), "expected nothing new, got $ids")
    }

    @Test
    fun `every id the rules can emit exists in the catalog`() {
        // newlyUnlocked maps ids through the catalog, so a typo would silently drop a badge.
        // Drive every branch at once and assert the count matches what the ids imply.
        val before = MetaCounters(
            sessions = 49, wins = 0, cleanReps = 999, totalDamage = 99_999,
            familiesPlayedMask = (0..3).fold(0) { m, i -> m or (1 shl i) },
            pbCount = 4, weeklyChallengesDone = 0, bestStreak = 29, level = 24,
        )
        val f = facts(mode = "DUEL", cleanReps = 10, damage = 5_000, won = true, streakAfter = 30, newPersonalBest = true, family = Family.entries[4].name)
        val ids = run(before = before, f = f, levelAfter = 25, weeklyCompleted = true)
        val expected = setOf(
            "fifty_fights", "first_win", "streak_30", "clean_1000", "damage_100k",
            "pb_5", "weekly_first", "level_25", "five_families", "versus_first",
        )
        assertEquals(expected, ids.toSet())
    }

    @Test
    fun `advance is the only arithmetic and it accumulates`() {
        val a = AchievementRules.advance(MetaCounters(), facts(reps = 10, cleanReps = 6, damage = 200, won = true, streakAfter = 3))
        assertEquals(1, a.sessions)
        assertEquals(1, a.wins)
        assertEquals(10, a.totalReps)
        assertEquals(6, a.cleanReps)
        assertEquals(200, a.totalDamage)
        assertEquals(3, a.bestStreak)
        val b = AchievementRules.advance(a, facts(reps = 5, cleanReps = 2, damage = 50, won = false, streakAfter = 2))
        assertEquals(2, b.sessions)
        assertEquals(15, b.totalReps)
        assertEquals(3, b.bestStreak, "a shorter streak must not lower the best")
    }
}
