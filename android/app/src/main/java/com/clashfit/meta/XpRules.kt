package com.clashfit.meta

import com.clashfit.meta.RewardLine
import com.clashfit.meta.SessionFacts
import com.clashfit.meta.Metric
import com.clashfit.meta.WeeklyProgress
import com.clashfit.meta.LevelProgress

/**
 * Pure rules for XP earning. A clean rep is worth more than a sloppy one, and nothing
 * here ever makes the game easier for someone who played more.
 */
object XpRules {

    /**
     * What one finished session earned. Each line is a named bonus; the sum is the total.
     * Never negative.
     */
    fun reward(facts: SessionFacts, weeklyBefore: WeeklyProgress, before: LevelProgress): List<RewardLine> {
        val lines = mutableListOf<RewardLine>()

        // Base XP from reps: quality, not quantity
        val perRepXp = (4 + 8 * facts.formMean).toInt()
        val repsXp = facts.reps * perRepXp
        val baseXp = if (facts.casual) repsXp / 2 else repsXp
        if (baseXp > 0) {
            lines += RewardLine(label = "${facts.reps} reps", xp = baseXp)
        }

        // Win bonus (not casual)
        if (facts.won && !facts.casual) {
            lines += RewardLine(label = "Boss defeated", xp = 25)
        }

        // Personal best bonus
        if (facts.newPersonalBest) {
            lines += RewardLine(label = "New personal best", xp = 30)
        }

        // Streak bonus
        if (facts.streakAfter >= 2) {
            val streakXp = 5 * minOf(facts.streakAfter, 7)
            lines += RewardLine(label = "Streak day ${facts.streakAfter}", xp = streakXp)
        }

        // Weekly challenge completion bonus (first time this week)
        val newWeeklyValue = weeklyBefore.value + factsValue(weeklyBefore.challenge.metric, facts)
        if (!weeklyBefore.done && newWeeklyValue >= weeklyBefore.challenge.target) {
            lines += RewardLine(label = "Weekly challenge complete", xp = 100)
        }

        return lines
    }

    /**
     * How much a finished session moves the weekly challenge along.
     *
     * Casual damage does not count. The weekly screen promises exactly that, and it is the rule
     * that keeps casual mode safe to offer: a mode with a softer form floor and a weaker boss
     * must not be the cheapest route to a damage target. Its reps and its sessions still count,
     * because those are honest work either way.
     */
    fun factsValue(metric: Metric, facts: SessionFacts): Int = when (metric) {
        Metric.DAMAGE -> if (facts.casual) 0 else facts.damage
        Metric.CLEAN_REPS -> {
            // Count of reps with verdict "CLEAN" — parsed from RepEntity verdicts during session
            // This will be provided in SessionFacts as cleanReps
            facts.cleanReps
        }
        Metric.SESSIONS -> 1
        Metric.STREAK_DAYS -> facts.streakAfter
    }
}
