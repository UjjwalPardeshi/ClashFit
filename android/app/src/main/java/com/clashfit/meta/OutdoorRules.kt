package com.clashfit.meta

import kotlin.math.min

/**
 * What a run or a walk is worth.
 *
 * The rule the rest of this package lives by applies here too: **XP comes from quality, not
 * quantity, and never from opening the app.** Indoors that means a clean rep beats a sloppy one.
 * Outdoors there is no camera and no form score, so the honest analogue is *steadiness* — how much
 * of the activity was actually spent moving. An hour that was forty minutes of standing around is
 * not an hour of training, and paying it as one would make the leaderboard a test of patience.
 *
 * A walk earns at a lower rate than a run for the same distance. That is not a judgement about
 * walking; it is the same principle as casual mode earning half. The alternative — paying both the
 * same — would make walking the cheapest route to a level, and the first person to notice would be
 * the last person still doing boss fights.
 */
object OutdoorRules {

    /** XP per kilometre of running, before steadiness is applied. */
    const val XP_PER_KM_RUN = 12f

    /** XP per kilometre of walking. Lower on purpose; see the class note. */
    const val XP_PER_KM_WALK = 7f

    /** A metre of ascent is worth this much. Hills are honest work and GPS can see them. */
    const val XP_PER_CLIMB_M = 0.4f

    /** Awarded once per personal best set by an activity. */
    const val XP_PER_RECORD = 30

    /**
     * The floor of the steadiness multiplier.
     *
     * Never zero: a stop-start activity is still an activity, and an athlete who had to wait at
     * four sets of traffic lights has not cheated anybody. It scales the reward; it does not
     * confiscate it.
     */
    const val STEADINESS_FLOOR = 0.6f

    /** A single activity cannot bank more than this, whatever its distance. */
    const val XP_CAP = 400

    /** What one finished activity contributed. Flat, so the rules never touch a database. */
    data class ActivityFacts(
        val activityId: Long,
        val atMs: Long,
        val isWalk: Boolean,
        val distanceM: Float,
        val movingMs: Long,
        val elapsedMs: Long,
        val climbM: Float,
        val steps: Int,
        val recordsSet: Int,
    ) {
        /**
         * Fraction of the activity actually spent moving, 0..1.
         *
         * Falls back to 1 when the elapsed time is unknown or nonsensical rather than to 0: a
         * missing denominator is a gap in our bookkeeping, and the athlete should not pay for it.
         */
        val steadiness: Float
            get() = if (elapsedMs <= 0L || movingMs <= 0L) 1f
            else min(1f, movingMs.toFloat() / elapsedMs)
    }

    /** The named lines that make up an activity's reward. Their sum is the total. */
    fun reward(facts: ActivityFacts): List<RewardLine> {
        val lines = mutableListOf<RewardLine>()
        val km = facts.distanceM / 1000f
        if (km <= 0f) return lines

        val rate = if (facts.isWalk) XP_PER_KM_WALK else XP_PER_KM_RUN
        val multiplier = STEADINESS_FLOOR + (1f - STEADINESS_FLOOR) * facts.steadiness
        val distanceXp = (km * rate * multiplier).toInt()
        if (distanceXp > 0) {
            lines += RewardLine(
                label = "%.2f km %s".format(km, if (facts.isWalk) "walked" else "run"),
                xp = distanceXp,
            )
        }

        val climbXp = (facts.climbM * XP_PER_CLIMB_M).toInt()
        if (climbXp > 0) lines += RewardLine("%.0f m of climb".format(facts.climbM), climbXp)

        if (facts.recordsSet > 0) {
            lines += RewardLine(
                label = if (facts.recordsSet == 1) "Personal best" else "${facts.recordsSet} personal bests",
                xp = XP_PER_RECORD * facts.recordsSet,
            )
        }

        // Capped by trimming the last lines rather than scaling them, so every line a player is
        // shown is a number they actually earned.
        var running = 0
        val capped = mutableListOf<RewardLine>()
        for (line in lines) {
            if (running >= XP_CAP) break
            val allowed = min(line.xp, XP_CAP - running)
            capped += if (allowed == line.xp) line else line.copy(xp = allowed)
            running += allowed
        }
        return capped
    }

    /** Lifetime outdoor totals, for the badges below. */
    data class OutdoorCounters(
        val activities: Int = 0,
        val distanceM: Float = 0f,
        val bestSteps: Int = 0,
    )

    fun advance(before: OutdoorCounters, facts: ActivityFacts) = OutdoorCounters(
        activities = before.activities + 1,
        distanceM = before.distanceM + facts.distanceM,
        bestSteps = maxOf(before.bestSteps, facts.steps),
    )

    /**
     * Badges an activity unlocked. Thresholds are crossings, never equalities — the same rule
     * [AchievementRules] states, for the same reason.
     */
    fun newlyUnlocked(
        before: OutdoorCounters,
        after: OutdoorCounters,
        alreadyUnlocked: Set<String>,
    ): List<Achievement> {
        val ids = mutableListOf<String>()
        fun crossed(id: String, threshold: Float, from: Float, to: Float) {
            if (from < threshold && to >= threshold) ids += id
        }
        crossed("outdoor_first", 1f, before.activities.toFloat(), after.activities.toFloat())
        crossed("distance_25k", 25_000f, before.distanceM, after.distanceM)
        crossed("distance_100k", 100_000f, before.distanceM, after.distanceM)
        crossed("steps_10k", 10_000f, before.bestSteps.toFloat(), after.bestSteps.toFloat())
        return ids.asSequence()
            .distinct()
            .filterNot { it in alreadyUnlocked }
            .mapNotNull { AchievementCatalog.byId(it) }
            .toList()
    }
}

/** Everything the activity summary says about what one run or walk earned. */
data class ActivityReward(
    val xp: Int,
    val lines: List<RewardLine>,
    val before: LevelProgress,
    val after: LevelProgress,
    val newAchievements: List<Achievement>,
) {
    val leveledUp: Boolean get() = after.level > before.level
}
