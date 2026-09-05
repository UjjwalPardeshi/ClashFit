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

    private val eitherCfg = StageCounter.Config(
        angle = Triple("SHOULDER", "ELBOW", "WRIST"), sides = StageCounter.Sides.EITHER,
        rest = StageCounter.Condition(">", 135f), count = StageCounter.Condition("<", 80f), debounceMs = 800L,
    )

    @Test
    fun `either arm counts, and both arms together are still one rep`() {
        val together = StageCounter(eitherCfg)
        assertEquals(
            1,
            run(together, 0, List(10) { curl(170f, 170f) } + ramp(170f, 20f, 20) { curl(it, it) } + ramp(20f, 170f, 20) { curl(it, it) }).size,
            "both arms curling at once is one curl",
        )
        val alone = StageCounter(eitherCfg)
        assertEquals(
            1,
            run(alone, 0, List(10) { curl(170f, 170f) } + ramp(170f, 20f, 20) { curl(it, 170f) } + ramp(20f, 170f, 20) { curl(it, 170f) }).size,
            "one arm curling is also one curl",
        )
    }

    @Test
    fun `a curl held with the other arm hanging does not count again every debounce`() {
        val c = StageCounter(eitherCfg)
        // The hanging arm satisfies the rest condition for the whole set. Without the guard against
        // re-arming while the count still holds, this re-armed under that arm and fired again every
        // 800 ms for as long as the other arm stayed curled.
        val held = List(10) { curl(170f, 170f) } + ramp(170f, 20f, 15) { curl(it, 170f) } + List(90) { curl(20f, 170f) }
        assertEquals(1, run(c, 0, held).size, "three seconds of holding one curl is still one rep")
    }

    @Test
    fun `a one-armed rep records the working arm's depth, not its average with the arm at rest`() {
        val c = StageCounter(eitherCfg)
        val reps = run(c, 0, List(10) { curl(170f, 170f) } + ramp(170f, 25f, 20) { curl(it, 170f) } + ramp(25f, 170f, 20) { curl(it, 170f) })
        assertEquals(1, reps.size)
        assertTrue(reps[0].thetaMin < 90f, "the working arm passed 80; averaged with the arm at rest it would read about 125: ${reps[0].thetaMin}")
    }

    /** The shipped lateral raise: the 15-20 rest band, the 95-105 counting band, and out to the side. */
    private val raiseCfg = StageCounter.Config(
        angle = Triple("HIP", "SHOULDER", "ELBOW"), sides = StageCounter.Sides.BOTH,
        rest = StageCounter.Condition("<", 20f), count = StageCounter.Condition(">", 95f),
        ceiling = 105f, plane = StageCounter.Condition(">", 0.5f),
        countAt = StageCounter.CountAt.RETURN, debounceMs = 1000L,
    )

    @Test
    fun `the plane is what separates a lateral raise from a front raise`() {
        // Out to the side and straight out in front reach the same hip-shoulder-elbow angle -- that
        // is the whole problem. On this body 93 degrees of abduction and 100 of forward elevation
        // both read about 100 at the joint, so only the direction of the upper arm tells them apart.
        fun side(a: Float) = SyntheticBody.world(170f, shoulderAbductionDeg = a)
        fun forward(a: Float) = SyntheticBody.world(170f, shoulderElevationDeg = a)
        fun cycle(pose: (Float) -> Landmarks, top: Float) =
            List(10) { pose(5f) } + ramp(5f, top, 25, pose) + List(8) { pose(top) } + ramp(top, 5f, 25, pose) + List(10) { pose(5f) }

        assertEquals(1, run(StageCounter(raiseCfg), 0, cycle(::side, 93f)).size, "out to the side is the exercise")
        assertEquals(0, run(StageCounter(raiseCfg), 0, cycle(::forward, 100f)).size, "the same angle out in front is not")
    }

    @Test
    fun `a raise past the ceiling counts nothing, and does not poison the next rep`() {
        fun side(a: Float) = SyntheticBody.world(170f, shoulderAbductionDeg = a)
        val c = StageCounter(raiseCfg)
        val over = List(10) { side(5f) } + ramp(5f, 130f, 25) { side(it) } + ramp(130f, 5f, 25) { side(it) } + List(10) { side(5f) }
        assertEquals(0, run(c, 0, over).size, "a raise carried up into a press is not a raise")
        val good = ramp(5f, 93f, 25) { side(it) } + List(8) { side(93f) } + ramp(93f, 5f, 25) { side(it) } + List(10) { side(5f) }
        val reps = run(c, 5000, good)
        assertEquals(1, reps.size, "the counter re-arms after a refusal")
        assertTrue(reps[0].thetaMax < 105f, "the refused cycle's turning point is not reported as this rep's depth: ${reps[0].thetaMax}")
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
