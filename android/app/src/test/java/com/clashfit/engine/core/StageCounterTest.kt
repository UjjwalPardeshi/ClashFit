package com.clashfit.engine.core

import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Side
import com.clashfit.core.pose.SyntheticBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** fitmon's counter on synthetic bodies: per-arm curls, wrists over shoulders, the debounce, and the overhead checks. */
class StageCounterTest {
    private val curlCfg = StageCounter.Config(
        angle = Triple("SHOULDER", "ELBOW", "WRIST"), sides = StageCounter.Sides.EACH,
        rest = StageCounter.Condition(">", 150f), count = StageCounter.Condition("<", 40f), debounceMs = 800L,
    )

    private fun curl(l: Float, r: Float) = SyntheticBody.world(170f, leftElbowDeg = l, rightElbowDeg = r)

    /** Feeds `poses` at 30 fps from `t0`, returning every rep counted. */
    private fun run(c: StageCounter, t0: Long, poses: List<Landmarks>): List<StageCounter.Rep> {
        val out = ArrayList<StageCounter.Rep>()
        var t = t0
        for (p in poses) { out += c.onFrame(p, t, Side.LEFT); t += 33 }
        return out
    }

    private fun ramp(from: Float, to: Float, frames: Int, pose: (Float) -> Landmarks) = (0 until frames).map { i -> pose(from + (to - from) * i / (frames - 1f)) }

    @Test
    fun `a curl on one arm counts once, on the way in, and the other arm is untouched`() {
        val c = StageCounter(curlCfg)
        val reps = run(c, 0, List(10) { curl(170f, 170f) } + ramp(170f, 20f, 20) { curl(it, 170f) } + List(5) { curl(20f, 170f) } + ramp(20f, 170f, 20) { curl(it, 170f) })
        assertEquals(1, reps.size)
        assertEquals(Side.LEFT, reps[0].side)
        assertTrue(reps[0].thetaMin < 40f && reps[0].thetaMax > 150f, "the rep spans rest to curled: ${reps[0]}")
        assertEquals(StageCounter.Stage.REST, c.stage(Side.RIGHT))
    }

    @Test
    fun `the very first rep counts, with no earlier rep to debounce against`() {
        val c = StageCounter(curlCfg)
        val reps = run(c, 0, List(5) { curl(170f, 170f) } + ramp(170f, 20f, 15) { curl(it, 170f) })
        assertEquals(1, reps.size, "nothing has been counted yet, so nothing can debounce this")
    }

    @Test
    fun `both arms curling together are two reps, as in fitmon`() {
        val c = StageCounter(curlCfg)
        val reps = run(c, 0, List(10) { curl(170f, 170f) } + ramp(170f, 20f, 20) { curl(it, it) } + ramp(20f, 170f, 20) { curl(it, it) })
        assertEquals(setOf(Side.LEFT, Side.RIGHT), reps.map { it.side }.toSet())
        assertEquals(2, reps.size)
    }

    @Test
    fun `a rep must return to rest before it can count again, and never inside the debounce`() {
        val c = StageCounter(curlCfg)
        val bounce = List(10) { curl(170f, 170f) } + ramp(170f, 20f, 10) { curl(it, 170f) } + ramp(20f, 60f, 5) { curl(it, 170f) } + ramp(60f, 20f, 5) { curl(it, 170f) }
        assertEquals(1, run(c, 0, bounce).size, "bouncing at the bottom does not count twice")
        // Straight back to rest and down again within 800 ms of the count: the debounce holds it.
        val quick = ramp(20f, 170f, 3) { curl(it, 170f) } + ramp(170f, 20f, 3) { curl(it, 170f) }
        assertEquals(0, run(c, 30 * 33L, quick).size)
    }

    @Test
    fun `a lateral raise counts when both wrists pass the shoulders and not at sixty degrees`() {
        val cfg = StageCounter.Config(
            angle = Triple("HIP", "SHOULDER", "ELBOW"), signal = StageCounter.Signal.WRIST_HEIGHT, sides = StageCounter.Sides.BOTH,
            rest = StageCounter.Condition("<", 0f), count = StageCounter.Condition(">", 0f), debounceMs = 1000L,
        )
        val c = StageCounter(cfg)
        fun raise(a: Float) = SyntheticBody.world(170f, shoulderAbductionDeg = a)
        assertEquals(1, run(c, 0, List(10) { raise(5f) } + ramp(5f, 100f, 25) { raise(it) } + ramp(100f, 5f, 25) { raise(it) }).size)
        assertEquals(0, run(c, 3000, ramp(5f, 60f, 25) { raise(it) } + ramp(60f, 5f, 25) { raise(it) }).size, "sixty degrees leaves the wrists under the shoulders")
        assertEquals(1, run(c, 6000, ramp(5f, 100f, 25) { raise(it) } + ramp(100f, 5f, 25) { raise(it) }).size)
    }

    @Test
    fun `a press counts overhead and is refused out in front, with a miss recorded for the cue`() {
        val cfg = StageCounter.Config(
            angle = Triple("SHOULDER", "ELBOW", "WRIST"), sides = StageCounter.Sides.BEST,
            rest = StageCounter.Condition("<", 100f), count = StageCounter.Condition(">", 160f),
            wristAboveShoulderAt = StageCounter.Check.COUNT, debounceMs = 1000L,
        )
        val c = StageCounter(cfg)
        fun press(e: Float, abd: Float) = SyntheticBody.world(170f, elbowDeg = e, shoulderAbductionDeg = abd)
        val overhead = List(10) { press(80f, 90f) } + (0 until 25).map { i -> press(80f + 98f * i / 24f, 90f + 88f * i / 24f) } + (0 until 25).map { i -> press(178f - 98f * i / 24f, 178f - 88f * i / 24f) }
        assertEquals(1, run(c, 0, overhead).size)
        val inFront = List(10) { press(80f, 90f) } + ramp(80f, 178f, 25) { press(it, 90f) } + ramp(178f, 80f, 25) { press(it, 90f) }
        assertEquals(0, run(c, 5000, inFront).size, "a press out in front is not overhead")
        assertTrue(c.lastMissMs != null, "the refusal is recorded for the cue")
    }

    @Test
    fun `a triceps extension needs the arm overhead to be at rest, so a curl at the sides never counts`() {
        val cfg = StageCounter.Config(
            angle = Triple("SHOULDER", "ELBOW", "WRIST"), sides = StageCounter.Sides.BEST,
            rest = StageCounter.Condition(">", 160f), count = StageCounter.Condition("<", 90f),
            wristAboveShoulderAt = StageCounter.Check.REST, debounceMs = 1000L,
        )
        val c = StageCounter(cfg)
        fun overhead(e: Float) = SyntheticBody.world(170f, elbowDeg = e, shoulderAbductionDeg = 175f)
        assertEquals(1, run(c, 0, List(10) { overhead(170f) } + ramp(170f, 80f, 20) { overhead(it) } + ramp(80f, 170f, 20) { overhead(it) }).size)
        // Arms down, same elbow motion: the start position was left, so these are curls and count nothing.
        assertEquals(0, run(c, 5000, List(10) { curl(170f, 170f) } + ramp(170f, 80f, 20) { curl(it, it) } + ramp(80f, 170f, 20) { curl(it, it) }).size,
            "a curl after an overhead set must not count as a triceps extension")
        assertEquals(StageCounter.Stage.NONE, c.stage(Side.LEFT), "the arm is down, so there is no start position to count from")
    }
}
