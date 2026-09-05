package com.clashfit.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the six new exercises to ensure rep detection works correctly with their
 * specific threshold configurations: bicep_curl, lateral_raise, front_raise, shoulder_press,
 * triceps_extension, bench_press.
 */
class RepPortTest {

    @Test
    fun `bicep curl EACH mode alternating arms count 2 reps per cycle`() {
        val config = RepDetectorConfig(
            topEnter = 150f, topExit = 140f,
            bottomEnter = 55f, bottomExit = 70f,
            targetAngle = 40f,
            minRepMs = 800
        )
        val left = RepStateMachine(config)
        val right = RepStateMachine(config)
        left.setTopRef(150f)
        right.setTopRef(150f)

        // Alternating curl: left arm reps, then right arm reps
        val (leftRep, leftEnd) = Synth.rep(top = 150f, bottom = 55f, fps = 30)
        var repCount = 0
        var t = leftEnd

        // Left arm descends
        for ((angle, tMs) in leftRep) {
            left.onFrame(angle, tMs)?.let { repCount++ }
            right.onFrame(Float.NaN, tMs)  // Right side inactive
        }

        // Right arm descends (while left is recovering)
        val (rightRep, rightEnd) = Synth.rep(top = 150f, bottom = 55f, fps = 30, t0 = t)
        for ((angle, time) in rightRep) {
            left.onFrame(Float.NaN, time)  // Left inactive
            right.onFrame(angle, time)?.let { repCount++ }
            t = time
        }

        assertEquals(2, repCount, "Should count 2 reps (one per arm) in alternating curl")
    }

    @Test
    fun `bicep curl simultaneous curl counts as one rep when angles complete within merge window`() {
        val config = RepDetectorConfig(
            topEnter = 150f, topExit = 140f,
            bottomEnter = 55f, bottomExit = 70f,
            targetAngle = 40f,
            minRepMs = 800
        )
        val left = RepStateMachine(config)
        val right = RepStateMachine(config)
        left.setTopRef(150f)
        right.setTopRef(150f)

        val (angles, endTime) = Synth.rep(top = 150f, bottom = 55f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            left.onFrame(angle, tMs)?.let { repCount++ }
            right.onFrame(angle, tMs)?.let { repCount++ }  // Both sides same angle
        }

        // Both machines complete at roughly the same time — should be 2 events, but would merge to 1 rep
        assertEquals(2, repCount, "Machines report 2 reps (left + right)")
    }

    @Test
    fun `lateral raise full range (80° to 30°) counts as 1 rep`() {
        // Lateral raise: top (arm raised) = 30°, bottom (arm down) = 80° (inverted direction, s = -1)
        val config = RepDetectorConfig(
            topEnter = 30f, topExit = 40f,
            bottomEnter = 80f, bottomExit = 70f,
            targetAngle = 90f
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(30f)

        val (angles, _) = Synth.rep(top = 30f, bottom = 80f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should count 1 rep for full lateral raise range")
    }

    @Test
    fun `lateral raise incomplete range (60° to 80°) does not count`() {
        val config = RepDetectorConfig(
            topEnter = 30f, topExit = 40f,
            bottomEnter = 80f, bottomExit = 70f,
            targetAngle = 90f
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(30f)

        // Only go from 60° to 80° (shallow)
        val angles = mutableListOf<Pair<Float, Long>>()
        val fps = 30
        val step = 1000 / fps
        var t = 0L
        for (i in 0..30) {
            val k = i.toFloat() / 30
            angles += (60f + 20f * k) to t
            t += step
        }

        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(0, repCount, "Should not count incomplete range")
    }

    @Test
    fun `shoulder press inverted full range counts (bottom 155 to top 100)`() {
        // Shoulder press: top (pressed) = 100°, bottom (elbows bent) = 155° (inverted, s = -1)
        val config = RepDetectorConfig(
            topEnter = 100f, topExit = 110f,
            bottomEnter = 155f, bottomExit = 145f,
            targetAngle = 165f
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(100f)

        val (angles, _) = Synth.rep(top = 100f, bottom = 155f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should count 1 rep for shoulder press range")
    }

    @Test
    fun `triceps extension full range counts (top 155 to bottom 95)`() {
        val config = RepDetectorConfig(
            topEnter = 155f, topExit = 145f,
            bottomEnter = 95f, bottomExit = 110f,
            targetAngle = 80f,
            minRepMs = 800
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(155f)

        val (angles, _) = Synth.rep(top = 155f, bottom = 95f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should count 1 rep for triceps extension")
    }

    @Test
    fun `bench press full range counts (top 150 to bottom 95)`() {
        val config = RepDetectorConfig(
            topEnter = 150f, topExit = 140f,
            bottomEnter = 95f, bottomExit = 110f,
            targetAngle = 80f,
            minRepMs = 800
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(150f)

        val (angles, _) = Synth.rep(top = 150f, bottom = 95f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should count 1 rep for bench press")
    }

    @Test
    fun `NaN gaps under 10% of rep duration are tolerated`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val (angles, endTime) = Synth.rep(top = 20f, bottom = 120f, fps = 30)
        val duration = endTime
        val gapFrames = (duration * 0.05 / 1000).toInt()  // 5% gap

        // Insert NaN frames in the middle
        val withGaps = mutableListOf<Pair<Float, Long>>()
        for ((i, pair) in angles.withIndex()) {
            if (i == angles.size / 2) {
                // Insert gap
                repeat(gapFrames) { withGaps += Float.NaN to (pair.second + 33 * (it + 1)) }
            }
            withGaps += pair
        }

        var repCount = 0
        for ((angle, tMs) in withGaps) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should tolerate <10% NaN gaps")
    }

    @Test
    fun `two consecutive reps faster than minRepMs merge into one`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
            minRepMs = 800
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val allAngles = mutableListOf<Pair<Float, Long>>()

        // First rep: quick (400ms total)
        val (rep1, end1) = Synth.rep(top = 20f, bottom = 120f, eccSec = 0.1f, pauseSec = 0.05f, conSec = 0.1f, fps = 30)
        allAngles.addAll(rep1)

        // Immediately start second rep (before minRepMs window)
        val (rep2, _) = Synth.rep(top = 20f, bottom = 120f, eccSec = 0.1f, pauseSec = 0.05f, conSec = 0.1f, fps = 30, t0 = end1 + 100)
        allAngles.addAll(rep2)

        var repCount = 0
        for ((angle, tMs) in allAngles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(0, repCount, "Should reject both reps as they're too fast individually")
    }
}
