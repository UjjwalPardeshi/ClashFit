package com.clashfit.meta

/**
 * Which vouchers a finished session just earned.
 *
 * Pure, and shaped exactly like [AchievementRules.newlyUnlocked], for the same reason: a milestone
 * is crossed, not landed on. Testing `cleanReps == 100` loses the voucher for anybody whose set
 * took them from 98 to 103, which is most people — so every rule asks whether the threshold sits
 * between the total before and the total after.
 *
 * Already-earned ids are filtered out at the end rather than checked per rule, so re-running a
 * session cannot mint a second copy of the same voucher.
 */
object VoucherRules {

    fun newlyEarned(
        before: AchievementRules.MetaCounters,
        after: AchievementRules.MetaCounters,
        alreadyEarned: Set<String>,
    ): List<Voucher> {
        val ids = mutableListOf<String>()

        fun crossed(id: String, threshold: Int, from: Int, to: Int) {
            if (from < threshold && to >= threshold) ids += id
        }

        crossed("streak_7", 7, before.bestStreak, after.bestStreak)
        crossed("streak_30", 30, before.bestStreak, after.bestStreak)

        crossed("clean_100", 100, before.cleanReps, after.cleanReps)
        crossed("clean_500", 500, before.cleanReps, after.cleanReps)
        crossed("clean_1000", 1000, before.cleanReps, after.cleanReps)

        crossed("level_5", 5, before.level, after.level)
        crossed("level_10", 10, before.level, after.level)
        crossed("level_20", 20, before.level, after.level)

        crossed("weekly_1", 1, before.weeklyChallengesDone, after.weeklyChallengesDone)
        crossed("weekly_5", 5, before.weeklyChallengesDone, after.weeklyChallengesDone)

        return ids.asSequence()
            .filterNot { it in alreadyEarned }
            .distinct()
            .mapNotNull { VoucherCatalog.byId(it) }
            .toList()
    }
}
