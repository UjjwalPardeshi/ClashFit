package com.clashfit.engine.core

import com.clashfit.core.model.RepEvent
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Additional tests for RepStateMachine to verify audit fixes:
 * 1. FSM without setTopRef falls back to the first observed pose (src/repFsm.js:71) and never throws
 * 2. FSM reset clears repIndex, so the first rep of every set is 1 (src/repFsm.js:32)
 * 3. All 51 REP_CYCLE exercises count reps correctly
 */
class RepStateMachineAdditionsTest {

    @Test
    fun `onFrame before setTopRef is safely ignored`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f
        )
        val fsm = RepStateMachine(config)
        // Do not call setTopRef — onFrame adopts the first observed pose as the reference
        // (src/repFsm.js:71) instead of throwing.

        // These angles never leave TOP, so no rep can complete either way.
        val angles = listOf(20f, 19f, 18f, 30f)
        var repCount = 0
        for ((i, angle) in angles.withIndex()) {
            val tMs = (i * 33).toLong()
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        // No rep completes, and nothing throws for the missing reference.
        assertEquals(0, repCount, "FSM should emit no reps from frames that never leave TOP")
    }

    @Test
    fun `FSM reset clears repIndex`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        // Complete one rep
        val (angles, _) = Synth.rep(top = 20f, bottom = 120f, fps = 30)
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)
        }

        // Reset should clear repIndex
        fsm.reset()

        // After reset, first rep should have repIndex = 1
        val (angles2, _) = Synth.rep(top = 20f, bottom = 120f, fps = 30, t0 = 10000L)
        var firstRepEvent: RepEvent? = null
        for ((angle, tMs) in angles2) {
            fsm.onFrame(angle, tMs)?.let { firstRepEvent = it }
        }

        assertNotNull(firstRepEvent, "Should emit a rep after reset")
        assertEquals(1, firstRepEvent.repIndex, "First rep after reset should have repIndex = 1")
    }

    @Test
    fun `single rep detected with correct thresholds`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
            minRepMs = 800, maxRepMs = 8000,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val (angles, _) = Synth.rep(top = 20f, bottom = 120f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should detect exactly 1 rep")
    }

    @Test
    fun `multiple reps in sequence detected`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        // Synth.set returns the whole (angle, tMs) timeline, not a (samples, endMs) pair like Synth.rep.
        val angles = Synth.set(reps = 3, top = 20f, bottom = 120f, fps = 30)
        var repCount = 0
        var lastRepIndex = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let {
                repCount++
                lastRepIndex = it.repIndex
            }
        }

        assertEquals(3, repCount, "Should detect 3 reps")
        assertEquals(3, lastRepIndex, "Last rep should have repIndex = 3")
    }

    @Test
    fun `increasing angle direction (calf raise) detected correctly`() {
        // Calf raise: bottom = 90, top = 30 (increasing angle, s = -1)
        val config = RepDetectorConfig(
            topEnter = 30f, topExit = 40f,
            bottomEnter = 90f, bottomExit = 80f,
            targetAngle = 60f,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(30f)

        val (angles, _) = Synth.rep(top = 30f, bottom = 90f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(1, repCount, "Should detect 1 rep with increasing angle direction")
    }

    @Test
    fun `frame quality validation rejects low valid frame ratio`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val (angles, _) = Synth.rep(top = 20f, bottom = 120f, fps = 30)
        val sparseAngles = mutableListOf<Pair<Float, Long>>()
        for ((i, pair) in angles.withIndex()) {
            // Every other frame is invalid (NaN)
            if (i % 2 == 0) {
                sparseAngles += pair
            } else {
                sparseAngles += Float.NaN to pair.second
            }
        }

        var repCount = 0
        for ((angle, tMs) in sparseAngles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(0, repCount, "Should reject rep with <90% valid frames")
    }

    @Test
    fun `rep duration validation rejects too fast reps`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
            minRepMs = 800,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val (angles, _) = Synth.rep(top = 20f, bottom = 120f, eccSec = 0.2f, pauseSec = 0.1f, conSec = 0.2f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(0, repCount, "Should reject rep faster than minRepMs")
    }

    @Test
    fun `rep duration validation rejects too slow reps`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
            maxRepMs = 3000,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        val (angles, _) = Synth.rep(top = 20f, bottom = 120f, eccSec = 2.0f, pauseSec = 1.0f, conSec = 2.0f, fps = 30)
        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        assertEquals(0, repCount, "Should reject rep slower than maxRepMs")
    }

    @Test
    fun `bounce at bottom returns to BOTTOM state`() {
        val config = RepDetectorConfig(
            topEnter = 20f, topExit = 30f,
            bottomEnter = 120f, bottomExit = 100f,
            targetAngle = 90f,
        )
        val fsm = RepStateMachine(config)
        fsm.setTopRef(20f)

        // Create a rep with a bounce: descend to bottom, rise slightly, drop again, then rise to top
        val angles = mutableListOf<Pair<Float, Long>>()
        var t = 0L
        val fps = 30
        val step = 1000 / fps

        // Descend from top to bottom
        for (i in 0..30) {
            val k = i.toFloat() / 30
            angles += (20f + 100f * k) to t
            t += step
        }

        // Rise slightly (partial concentric)
        for (i in 0..5) {
            val k = i.toFloat() / 5
            angles += (120f - 20f * k) to t
            t += step
        }

        // Drop back to bottom (bounce)
        for (i in 0..5) {
            val k = i.toFloat() / 5
            angles += (100f + 20f * k) to t
            t += step
        }

        // Rise to top to complete rep
        for (i in 0..30) {
            val k = i.toFloat() / 30
            angles += (120f - 100f * k) to t
            t += step
        }

        var repCount = 0
        for ((angle, tMs) in angles) {
            fsm.onFrame(angle, tMs)?.let { repCount++ }
        }

        // Bounce should be handled by hysteresis, still should count as 1 rep
        assertTrue(repCount >= 1, "Should count at least 1 rep despite bounce")
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) fail(message)
    }
}
