package com.clashfit.meta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a run or a walk is worth.
 *
 * The rules these pin are the ones a player would notice being broken: that standing around does
 * not pay, that walking does not out-earn running, and that no single activity can vault somebody
 * up the ladder.
 */
class OutdoorRulesTest {

    private fun activity(
        id: Long = 1L,
        isWalk: Boolean = false,
        distanceM: Float = 5000f,
        movingMs: Long = 1_500_000L,
        elapsedMs: Long = 1_500_000L,
        climbM: Float = 0f,
        steps: Int = 0,
        recordsSet: Int = 0,
    ) = OutdoorRules.ActivityFacts(id, 0L, isWalk, distanceM, movingMs, elapsedMs, climbM, steps, recordsSet)

    private fun total(f: OutdoorRules.ActivityFacts) = OutdoorRules.reward(f).sumOf { it.xp }

    @Test
    fun `an activity with no distance earns nothing`() {
        assertTrue(OutdoorRules.reward(activity(distanceM = 0f)).isEmpty())
    }

    @Test
    fun `a five kilometre run at full steadiness earns its rate`() {
        // 5 km × 12 XP × (0.6 + 0.4 × 1.0) = 60
        assertEquals(60, total(activity()))
    }

    @Test
    fun `standing around for half the activity pays less than moving for all of it`() {
        val steady = total(activity(movingMs = 1_500_000L, elapsedMs = 1_500_000L))
        val stopStart = total(activity(movingMs = 750_000L, elapsedMs = 1_500_000L))
        assertTrue(stopStart < steady, "a stop-start activity earned as much as a steady one")
        // …but it is never confiscated: the floor keeps it worth doing.
        assertTrue(stopStart >= (steady * OutdoorRules.STEADINESS_FLOOR).toInt() - 1)
    }

    @Test
    fun `a walk earns less than a run over the same ground`() {
        assertTrue(total(activity(isWalk = true)) < total(activity(isWalk = false)))
    }

    @Test
    fun `steadiness falls back to full rather than zero when the clock is missing`() {
        // A gap in our own bookkeeping must not cost the athlete anything.
        assertEquals(1f, activity(elapsedMs = 0L).steadiness)
        assertEquals(1f, activity(movingMs = 0L).steadiness)
    }

    @Test
    fun `steadiness never exceeds one`() {
        // Moving time longer than elapsed time is nonsense, and must not become a bonus.
        assertEquals(1f, activity(movingMs = 3_000_000L, elapsedMs = 1_000_000L).steadiness)
    }

    @Test
    fun `climb is paid for`() {
        assertTrue(total(activity(climbM = 100f)) > total(activity(climbM = 0f)))
    }

    @Test
    fun `each personal best is worth its stated bonus`() {
        val plain = total(activity())
        assertEquals(plain + OutdoorRules.XP_PER_RECORD, total(activity(recordsSet = 1)))
        assertEquals(plain + OutdoorRules.XP_PER_RECORD * 2, total(activity(recordsSet = 2)))
    }

    @Test
    fun `a single activity cannot exceed the cap`() {
        // A hundred kilometres up a mountain, with every record broken.
        val absurd = activity(distanceM = 100_000f, climbM = 5000f, recordsSet = 4)
        assertEquals(OutdoorRules.XP_CAP, total(absurd))
    }

    @Test
    fun `every line shown is a whole number the player earned`() {
        val lines = OutdoorRules.reward(activity(distanceM = 100_000f, climbM = 5000f, recordsSet = 4))
        assertTrue(lines.all { it.xp > 0 }, "a zero-XP line would be noise on the summary")
        assertEquals(OutdoorRules.XP_CAP, lines.sumOf { it.xp })
    }

    @Test
    fun `a run names itself run and a walk names itself walk`() {
        assertTrue(OutdoorRules.reward(activity()).first().label.contains("run"))
        assertTrue(OutdoorRules.reward(activity(isWalk = true)).first().label.contains("walked"))
    }

    // ── badges ──────────────────────────────────────────────────────────────

    @Test
    fun `the first activity unlocks the first badge`() {
        val before = OutdoorRules.OutdoorCounters()
        val after = OutdoorRules.advance(before, activity())
        val ids = OutdoorRules.newlyUnlocked(before, after, emptySet()).map { it.id }
        assertTrue("outdoor_first" in ids)
    }

    @Test
    fun `distance badges fire on the crossing, not on an exact total`() {
        val before = OutdoorRules.OutdoorCounters(activities = 4, distanceM = 24_000f)
        val after = OutdoorRules.advance(before, activity(distanceM = 3000f)) // 24 km -> 27 km
        val ids = OutdoorRules.newlyUnlocked(before, after, emptySet()).map { it.id }
        assertTrue("distance_25k" in ids, "crossing 25 km in one activity did not award the badge")
    }

    @Test
    fun `a badge already held is never awarded twice`() {
        val before = OutdoorRules.OutdoorCounters(activities = 4, distanceM = 24_000f)
        val after = OutdoorRules.advance(before, activity(distanceM = 3000f))
        val ids = OutdoorRules.newlyUnlocked(before, after, setOf("distance_25k")).map { it.id }
        assertTrue("distance_25k" !in ids)
    }

    @Test
    fun `ten thousand steps in one activity is a badge`() {
        val before = OutdoorRules.OutdoorCounters(activities = 3, distanceM = 5000f, bestSteps = 4000)
        val after = OutdoorRules.advance(before, activity(steps = 12_000))
        val ids = OutdoorRules.newlyUnlocked(before, after, emptySet()).map { it.id }
        assertTrue("steps_10k" in ids)
    }

    @Test
    fun `every outdoor badge id exists in the catalog`() {
        val before = OutdoorRules.OutdoorCounters()
        val after = OutdoorRules.OutdoorCounters(activities = 200, distanceM = 500_000f, bestSteps = 99_000)
        val earned = OutdoorRules.newlyUnlocked(before, after, emptySet())
        assertEquals(4, earned.size, "an outdoor badge id has no catalog entry, so it silently vanishes")
    }

    @Test
    fun `advance accumulates distance and keeps the best step count`() {
        var c = OutdoorRules.OutdoorCounters()
        c = OutdoorRules.advance(c, activity(distanceM = 1000f, steps = 900))
        c = OutdoorRules.advance(c, activity(distanceM = 2000f, steps = 300))
        assertEquals(2, c.activities)
        assertEquals(3000f, c.distanceM)
        assertEquals(900, c.bestSteps, "bestSteps is a high-water mark, not the latest value")
    }
}
