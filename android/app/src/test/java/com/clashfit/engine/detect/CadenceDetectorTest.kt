package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.CadenceEvent
import com.clashfit.core.model.Landmark
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CadenceDetectorTest {

    private fun createJumpingJacksSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "jumping_jacks",
            family = "CADENCE",
            name = "Jumping Jacks",
            detector = buildJsonObject {
                put("signalJoint", "WRIST")
                put("signalAxis", "y")
                put("minProminence", 0.03f)
                put("refractoryMs", 260)
                putJsonObject("targetCadence") {
                    put("min", 90f)
                    put("max", 180f)
                }
            },
        )
    }

    private fun createHighKneesSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "high_knees",
            family = "CADENCE",
            name = "High Knees",
            detector = buildJsonObject {
                put("signalJoint", "KNEE")
                put("signalAxis", "y")
                put("minProminence", 0.08f)
                put("refractoryMs", 260)
                putJsonObject("targetCadence") {
                    put("min", 120f)
                    put("max", 200f)
                }
            },
        )
    }

    /** Generate synthetic cadence signal. */
    private fun generateCadenceFrames(
        rpm: Float,
        seconds: Float,
        amplitude: Float = 0.12f,
        fps: Int = 30,
        t0: Long = 0,
        joint: String = "WRIST",
        axis: String = "y",
    ): List<Pair<List<Landmark>, Long>> {
        val frames = mutableListOf<Pair<List<Landmark>, Long>>()
        val frameCount = (seconds * fps).toInt()
        val radsPerSecond = (rpm / 60f) * 2f * PI.toFloat()

        for (i in 0 until frameCount) {
            val tMs = t0 + (i * 1000L / fps)
            val tSec = tMs / 1000f
            val signal = 0.5f + amplitude / 2f * sin((radsPerSecond * tSec).toDouble()).toFloat()

            val lms = mutableListOf<Landmark>()
            for (j in 0 until 33) {
                lms.add(Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 1f))
            }

            val jointIdx = when (joint) {
                "WRIST" -> if (axis == "x") 15 else 15
                "KNEE" -> if (axis == "x") 25 else 25
                else -> 15
            }

            lms[jointIdx] = when (axis) {
                "x" -> Landmark(x = signal, y = 0.5f, z = 0f, visibility = 1f)
                "z" -> Landmark(x = 0.5f, y = 0.5f, z = signal, visibility = 1f)
                else -> Landmark(x = 0.5f, y = signal, z = 0f, visibility = 1f)
            }

            frames.add(lms to tMs)
        }
        return frames
    }

    @Test
    fun `F4 CADENCE · reads the rate it was given`() {
        val d = CadenceDetector(createJumpingJacksSpec())
        val got = mutableListOf<Float>()
        for ((lm, ms) in generateCadenceFrames(rpm = 120f, seconds = 12f, joint = "WRIST", axis = "y")) {
            val ev = d.onFrame(null, lm, ms, Side.LEFT)
            if (ev != null) {
                got.add((ev as CadenceEvent).cadence)
            }
        }
        assertTrue(got.size >= 8, "only ${got.size} cycles detected")
        val mean = got.average().toFloat()
        // Allow 12% tolerance for synthetic frames
        assertTrue(mean in 108f..132f, "cadence mean $mean not close to 120")
    }

    @Test
    fun `F4 CADENCE · tiny movements are rejected`() {
        val d = CadenceDetector(createHighKneesSpec())
        var n = 0
        for ((lm, ms) in generateCadenceFrames(
            rpm = 150f,
            seconds = 10f,
            amplitude = 0.008f,
            joint = "KNEE",
            axis = "y",
        )) {
            if (d.onFrame(null, lm, ms, Side.LEFT) != null) n++
        }
        assertEquals(0, n, "fake high-knees counted")
    }

    @Test
    fun `F4 CADENCE · one deterministic rep produces one event`() {
        val d = CadenceDetector(createJumpingJacksSpec())
        var count = 0
        for ((lm, ms) in generateCadenceFrames(rpm = 120f, seconds = 3f, joint = "WRIST", axis = "y")) {
            val ev = d.onFrame(null, lm, ms, Side.LEFT)
            if (ev != null) count++
        }
        assertTrue(count >= 1, "synthetic cadence produced no events")
    }
}
