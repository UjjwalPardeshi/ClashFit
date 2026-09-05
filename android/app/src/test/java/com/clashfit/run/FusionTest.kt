package com.clashfit.run

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tracking that survives a building.
 *
 * The failure this covers is not hypothetical: a run recorded in a lab produced no route at all,
 * because indoors every GPS fix is wider than the accuracy gate and is thrown away. These tests
 * drive the two pieces that fix it with scripted sensor input, so the indoor path can be verified
 * without finding a basement.
 */
class FusionTest {

    // ── dead reckoning ──────────────────────────────────────────────────────

    @Test
    fun `the first step reading only seeds the counter`() {
        val dr = DeadReckoning()
        dr.setHeading(0f)
        assertFalse(dr.onSteps(1000).moved, "the first reading invented a walk from nothing")
    }

    @Test
    fun `steps without a heading move nothing`() {
        // Better to record no movement than movement in an invented direction.
        val dr = DeadReckoning()
        dr.onSteps(100)
        assertFalse(dr.onSteps(120).moved)
    }

    @Test
    fun `walking north moves north`() {
        val dr = DeadReckoning(initialStrideM = 0.8f)
        dr.setHeading(0f)
        dr.onSteps(0)
        val d = dr.onSteps(10)
        assertEquals(8f, d.northM, 0.01f)
        assertEquals(0f, d.eastM, 0.01f)
        assertEquals(8f, d.metres, 0.01f)
        assertEquals(10, d.steps)
    }

    @Test
    fun `walking east moves east, not north`() {
        // Heading is clockwise from north. Getting this backwards mirrors every indoor route.
        val dr = DeadReckoning(initialStrideM = 0.5f)
        dr.setHeading(90f)
        dr.onSteps(0)
        val d = dr.onSteps(10)
        assertEquals(5f, d.eastM, 0.01f)
        assertEquals(0f, d.northM, 0.01f)
    }

    @Test
    fun `heading wraps rather than going out of range`() {
        val dr = DeadReckoning()
        dr.setHeading(-90f)
        assertEquals(270f, dr.headingDeg!!, 0.01f)
        dr.setHeading(450f)
        assertEquals(90f, dr.headingDeg!!, 0.01f)
    }

    @Test
    fun `a counter that resets re-seeds instead of walking backwards`() {
        val dr = DeadReckoning()
        dr.setHeading(0f)
        dr.onSteps(5000)
        // A reboot zeroes the since-boot counter.
        assertFalse(dr.onSteps(3).moved, "a counter reset was read as a large negative walk")
        assertTrue(dr.onSteps(13).moved)
    }

    @Test
    fun `stride is learned from a stretch GPS measured`() {
        val dr = DeadReckoning()
        assertFalse(dr.calibrated)
        dr.learnStride(gpsDistanceM = 80f, steps = 100) // 0.80 m per step
        assertTrue(dr.calibrated)
        assertEquals(0.80f, dr.strideM, 0.001f)
    }

    @Test
    fun `too few steps teach nothing`() {
        val dr = DeadReckoning()
        dr.learnStride(gpsDistanceM = 8f, steps = 4)
        assertFalse(dr.calibrated, "four steps of GPS noise were treated as a measurement")
        assertEquals(DeadReckoning.DEFAULT_STRIDE_M, dr.strideM)
    }

    @Test
    fun `an implausible stride is refused rather than averaged in`() {
        val dr = DeadReckoning()
        dr.learnStride(gpsDistanceM = 80f, steps = 100)
        val good = dr.strideM
        dr.learnStride(gpsDistanceM = 500f, steps = 100) // 5 m per step: a car, not a person
        assertEquals(good, dr.strideM, 0.001f, "one bad fix pair poisoned the stride")
    }

    @Test
    fun `later observations move the stride gradually`() {
        val dr = DeadReckoning()
        dr.learnStride(80f, 100)   // 0.80
        dr.learnStride(60f, 100)   // 0.60 observed
        // Quarter of the way from 0.80 to 0.60.
        assertEquals(0.75f, dr.strideM, 0.001f)
    }

    @Test
    fun `a learned stride changes how far the same steps carry you`() {
        val dr = DeadReckoning()
        dr.setHeading(0f)
        dr.onSteps(0)
        val before = dr.onSteps(100).metres
        dr.learnStride(gpsDistanceM = 100f, steps = 100) // 1.0 m per step
        val after = dr.onSteps(200).metres
        assertTrue(after > before, "learning the stride did not change the distance")
        assertEquals(100f, after, 0.01f)
    }

    // ── fusing ──────────────────────────────────────────────────────────────

    @Test
    fun `with no fix yet the fuser knows nothing`() {
        val f = PositionFuser()
        assertEquals(PositionFuser.Source.NONE, f.source)
        assertFalse(f.hasEverFixed)
    }

    @Test
    fun `the first fix places you exactly, without blending toward nowhere`() {
        val f = PositionFuser()
        f.onGpsFix(eastM = 30f, northM = 40f, gpsDistanceM = 0f, tMs = 1000L)
        assertEquals(30f, f.eastM, 0.001f)
        assertEquals(40f, f.northM, 0.001f)
        assertEquals(PositionFuser.Source.GPS, f.source)
        assertTrue(f.hasEverFixed)
    }

    @Test
    fun `steps are ignored while GPS is healthy`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 1000L)
        val moved = f.onSteps(dr.onSteps(20), tMs = 2000L)
        assertFalse(moved, "dead reckoning moved the position while GPS was fine")
        assertEquals(0f, f.northM, 0.001f)
    }

    @Test
    fun `when GPS goes stale the steps take over`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 1000L)
        // Walking through a door: no fix for ten seconds.
        val moved = f.onSteps(dr.onSteps(20), tMs = 12_000L)
        assertTrue(moved, "GPS went stale and nothing took over — this is the lab bug")
        assertEquals(20f, f.northM, 0.001f)
        assertEquals(PositionFuser.Source.STEPS, f.source)
        assertTrue(f.indoors)
    }

    @Test
    fun `indoor distance accumulates from the steps`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 0.8f)
        dr.setHeading(0f); dr.onSteps(0)
        f.seedOrigin()
        f.onSteps(dr.onSteps(50), tMs = 5000L)
        f.onSteps(dr.onSteps(100), tMs = 6000L)
        assertEquals(80f, f.distanceM, 0.01f)
    }

    @Test
    fun `a returning fix is converged toward, not teleported to`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        f.onSteps(dr.onSteps(20), tMs = 20_000L)     // drifted to 20 m north
        f.onGpsFix(0f, 30f, 5f, tMs = 21_000L)       // GPS says 30 m north
        // 45% of the way from 20 to 30.
        assertEquals(24.5f, f.northM, 0.01f)
        assertTrue(f.northM < 30f, "the position snapped instead of converging")
        assertEquals(PositionFuser.Source.GPS, f.source)
    }

    @Test
    fun `converging repeatedly arrives at the truth`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        f.onSteps(dr.onSteps(20), tMs = 20_000L)
        repeat(12) { f.onGpsFix(0f, 30f, 0f, tMs = 21_000L + it * 1000L) }
        assertEquals(30f, f.northM, 0.2f)
    }

    @Test
    fun `a wildly wrong estimate snaps rather than drawing a false corridor`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        f.onSteps(dr.onSteps(200), tMs = 20_000L)    // 200 m of drift
        f.onGpsFix(0f, 10f, 0f, tMs = 21_000L)
        assertEquals(10f, f.northM, 0.001f, "a 190 m disagreement was crawled toward instead of snapped")
    }

    @Test
    fun `GPS distance is credited by the satellite, not re-estimated`() {
        val f = PositionFuser()
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        f.onGpsFix(0f, 12f, 12f, tMs = 1000L)
        f.onGpsFix(0f, 24f, 12f, tMs = 2000L)
        assertEquals(24f, f.distanceM, 0.001f)
    }

    @Test
    fun `walking outdoors teaches the stride used indoors`() {
        val f = PositionFuser()
        val dr = DeadReckoning()
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        // 100 steps while GPS is healthy, then a fix saying that was 95 m.
        f.onSteps(dr.onSteps(100), tMs = 1000L)
        f.onGpsFix(0f, 95f, 95f, tMs = 2000L, deadReckoning = dr)
        assertTrue(dr.calibrated, "a measured stretch taught the stride nothing")
        assertEquals(0.95f, dr.strideM, 0.001f)
    }

    @Test
    fun `a pause does not draw a line across the gap`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.setHeading(0f); dr.onSteps(0)
        f.onGpsFix(0f, 0f, 0f, tMs = 0L)
        f.clearAnchor()
        // Resuming far away: the next fix places us, and credits only what it measured.
        f.onGpsFix(0f, 500f, 0f, tMs = 60_000L)
        assertEquals(0f, f.distanceM, 0.001f, "the distance skipped while paused was credited")
    }

    @Test
    fun `an activity that starts indoors still has an origin to draw from`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 0.75f)
        dr.setHeading(90f); dr.onSteps(0)
        f.seedOrigin()
        assertEquals(PositionFuser.Source.STEPS, f.source)
        f.onSteps(dr.onSteps(40), tMs = 4000L)
        assertEquals(30f, f.eastM, 0.01f)
        assertFalse(f.hasEverFixed, "no satellite was ever seen, so this must not claim one was")
    }

    @Test
    fun `a lap of a room comes back to roughly where it started`() {
        // Four sides of a square, 20 steps each, turning right at every corner.
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 0.75f)
        dr.onSteps(0)
        f.seedOrigin()
        var steps = 0
        var t = 0L
        for (heading in listOf(0f, 90f, 180f, 270f)) {
            dr.setHeading(heading)
            steps += 20
            t += 20_000L
            f.onSteps(dr.onSteps(steps), t)
        }
        val offset = hypot(f.eastM, f.northM)
        assertTrue(offset < 0.5f, "a closed square did not close: ${offset}m out")
        assertEquals(60f, f.distanceM, 0.01f)
    }

    @Test
    fun `reset clears the position and the distance together`() {
        val f = PositionFuser()
        f.onGpsFix(10f, 10f, 14f, tMs = 0L)
        f.reset()
        assertEquals(PositionFuser.Source.NONE, f.source)
        assertEquals(0f, f.distanceM)
        assertEquals(0f, f.eastM)
        assertFalse(f.hasEverFixed)
    }

    @Test
    fun `heading changes steer the same steps in different directions`() {
        val f = PositionFuser()
        val dr = DeadReckoning(initialStrideM = 1f)
        dr.onSteps(0)
        f.seedOrigin()
        dr.setHeading(0f)
        f.onSteps(dr.onSteps(10), 1000L)
        dr.setHeading(90f)
        f.onSteps(dr.onSteps(20), 2000L)
        assertEquals(10f, f.northM, 0.01f)
        assertEquals(10f, f.eastM, 0.01f)
        assertTrue(abs(f.distanceM - 20f) < 0.01f)
    }
}
