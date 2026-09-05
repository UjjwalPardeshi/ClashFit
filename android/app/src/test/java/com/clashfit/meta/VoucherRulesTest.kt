package com.clashfit.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A voucher is earned by crossing a milestone, not by landing on it.
 *
 * Testing `cleanReps == 100` loses the reward for anybody whose set takes them from 98 to 103,
 * which is nearly everybody. And a voucher is a thing you tell people about, so minting a second
 * copy of one — or handing one out twice because a session was re-run — is worse than not awarding
 * it at all.
 */
class VoucherRulesTest {

    private fun counters(
        bestStreak: Int = 0,
        cleanReps: Int = 0,
        level: Int = 1,
        weeklyChallengesDone: Int = 0,
    ) = AchievementRules.MetaCounters(
        bestStreak = bestStreak,
        cleanReps = cleanReps,
        level = level,
        weeklyChallengesDone = weeklyChallengesDone,
    )

    private fun earned(before: AchievementRules.MetaCounters, after: AchievementRules.MetaCounters, already: Set<String> = emptySet()) =
        VoucherRules.newlyEarned(before, after, already).map { it.id }

    // ── crossing, not landing ─────────────────────────────────────────────────────────────────

    @Test
    fun `a set that steps straight over the threshold still earns it`() {
        assertEquals(listOf("clean_100"), earned(counters(cleanReps = 98), counters(cleanReps = 103)))
    }

    @Test
    fun `landing exactly on the threshold earns it too`() {
        assertEquals(listOf("clean_100"), earned(counters(cleanReps = 92), counters(cleanReps = 100)))
    }

    @Test
    fun `staying below earns nothing`() {
        assertEquals(emptyList<String>(), earned(counters(cleanReps = 40), counters(cleanReps = 99)))
    }

    @Test
    fun `a huge jump earns every threshold it passed`() {
        val ids = earned(counters(cleanReps = 0), counters(cleanReps = 1200))
        assertEquals(listOf("clean_100", "clean_500", "clean_1000"), ids)
    }

    // ── never twice ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a voucher already earned is never handed out again`() {
        val ids = earned(counters(cleanReps = 98), counters(cleanReps = 103), already = setOf("clean_100"))
        assertEquals(emptyList<String>(), ids)
    }

    @Test
    fun `re-running the same session earns nothing the second time`() {
        val before = counters(bestStreak = 6)
        val after = counters(bestStreak = 7)
        val first = earned(before, after)
        assertEquals(listOf("streak_7"), first)
        assertEquals("second run is silent", emptyList<String>(), earned(before, after, already = first.toSet()))
    }

    // ── each milestone family ─────────────────────────────────────────────────────────────────

    @Test
    fun `streaks pay out at seven and thirty days`() {
        assertEquals(listOf("streak_7"), earned(counters(bestStreak = 6), counters(bestStreak = 7)))
        assertEquals(listOf("streak_30"), earned(counters(bestStreak = 29), counters(bestStreak = 31)))
    }

    @Test
    fun `levels pay out at five, ten and twenty`() {
        assertEquals(listOf("level_5"), earned(counters(level = 4), counters(level = 5)))
        assertEquals(listOf("level_10"), earned(counters(level = 9), counters(level = 10)))
        assertEquals(listOf("level_20"), earned(counters(level = 19), counters(level = 20)))
    }

    @Test
    fun `weekly challenges pay out on the first and the fifth`() {
        assertEquals(listOf("weekly_1"), earned(counters(weeklyChallengesDone = 0), counters(weeklyChallengesDone = 1)))
        assertEquals(listOf("weekly_5"), earned(counters(weeklyChallengesDone = 4), counters(weeklyChallengesDone = 5)))
    }

    @Test
    fun `one session can earn from more than one family at once`() {
        val ids = earned(
            counters(bestStreak = 6, cleanReps = 95, level = 4),
            counters(bestStreak = 7, cleanReps = 110, level = 5),
        )
        assertTrue(ids.containsAll(listOf("streak_7", "clean_100", "level_5")))
    }

    @Test
    fun `nothing changing earns nothing`() {
        val same = counters(bestStreak = 12, cleanReps = 700, level = 11, weeklyChallengesDone = 3)
        assertEquals(emptyList<String>(), earned(same, same))
    }

    // ── the catalogue itself ──────────────────────────────────────────────────────────────────

    @Test
    fun `every id a rule can produce exists in the catalogue`() {
        // A rule naming an id the catalogue does not have would award a voucher that cannot be
        // drawn — the player is told they earned something and then shown nothing.
        val everything = earned(
            counters(),
            counters(bestStreak = 99, cleanReps = 99_999, level = 99, weeklyChallengesDone = 99),
        )
        assertEquals("every rule fired", 10, everything.size)
        everything.forEach { assertNotNull("no catalogue entry for $it", VoucherCatalog.byId(it)) }
    }

    @Test
    fun `every voucher in the catalogue is reachable by some rule`() {
        val reachable = earned(
            counters(),
            counters(bestStreak = 99, cleanReps = 99_999, level = 99, weeklyChallengesDone = 99),
        ).toSet()
        VoucherCatalog.all.forEach {
            assertTrue("${it.id} can never be earned", it.id in reachable)
        }
    }

    @Test
    fun `every voucher is fully specified and its code is readable off a screen`() {
        // A code is read aloud or typed by hand, so it has to survive that: upper case, no
        // ambiguous punctuation, and short enough to fit one line on a 384dp phone.
        VoucherCatalog.all.forEach {
            assertTrue("${it.id} has no brand", it.brand.isNotBlank())
            assertTrue("${it.id} has no offer", it.offer.isNotBlank())
            assertTrue("${it.id} has no milestone text", it.milestone.isNotBlank())
            assertTrue("${it.id} code is not shoutable: ${it.code}", it.code.matches(Regex("[A-Z0-9-]{6,24}")))
        }
    }

    @Test
    fun `no two vouchers share an id or a code`() {
        assertEquals(VoucherCatalog.all.size, VoucherCatalog.all.map { it.id }.toSet().size)
        assertEquals(VoucherCatalog.all.size, VoucherCatalog.all.map { it.code }.toSet().size)
    }

    @Test
    fun `vouchers are rarer than badges`() {
        // Twenty-two badges to ten vouchers. A badge is a pat on the back; a voucher is meant to
        // feel like the thing you tell somebody about, and it cannot if it arrives every session.
        assertTrue(
            "vouchers ${VoucherCatalog.all.size} vs badges ${AchievementCatalog.all.size}",
            VoucherCatalog.all.size < AchievementCatalog.all.size,
        )
    }
}
