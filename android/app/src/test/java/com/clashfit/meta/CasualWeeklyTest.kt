package com.clashfit.meta

import org.junit.Test
import kotlin.test.assertEquals

/**
 * The weekly challenge screen tells the player, in these words, that casual sessions count toward
 * reps and sessions but not toward damage. That sentence is a promise, and this pins it.
 *
 * It matters because casual mode softens the form floor and weakens the boss. If casual damage
 * counted, casual would be the cheapest route to every damage target, and the mode that exists to
 * let someone train while tired would quietly become the optimal way to play.
 */
class CasualWeeklyTest {

    private fun facts(casual: Boolean) = SessionFacts(
        sessionId = 1, atMs = 0, mode = "BOSS_FIGHT", exerciseId = "squat",
        reps = 20, cleanReps = 12, formMean = 0.8f, damage = 4_000, won = true,
        casual = casual, durationSec = 300, streakAfter = 3, newPersonalBest = false,
    )

    @Test
    fun `a casual session contributes no damage to the weekly target`() {
        assertEquals(0, XpRules.factsValue(Metric.DAMAGE, facts(casual = true)))
    }

    @Test
    fun `a normal session contributes all of its damage`() {
        assertEquals(4_000, XpRules.factsValue(Metric.DAMAGE, facts(casual = false)))
    }

    @Test
    fun `reps and sessions count either way, because that work was real`() {
        for (casual in listOf(true, false)) {
            assertEquals(12, XpRules.factsValue(Metric.CLEAN_REPS, facts(casual)), "clean reps, casual=$casual")
            assertEquals(1, XpRules.factsValue(Metric.SESSIONS, facts(casual)), "sessions, casual=$casual")
        }
    }
}
