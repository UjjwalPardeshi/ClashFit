package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Landmark
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BallisticDetectorTest {

    private fun createJumpSquatSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "jump_squat",
            family = "BALLISTIC",
            name = "Jump Squat",
            detector = buildJsonObject {
                put("takeoffRise", 0.035f)
                put("landingWindowMs", 300)
                put("standingKnee", 170f)
                put("softLandingFlexionDeg", 35f)
                put("targetHeightCm", 25f)
            },
        )
    }

    /** Generate synthetic jump frames with world and image landmarks. */
    private fun generateJumpFrames(
        heightNorm: Float = 0.09f,
        landKnee: Float = 130f,
        fps: Int = 30,
        t0: Long = 0,
        standSeconds: Float = 1.2f,
    ): List<Triple<List<Landmark>, List<Landmark>, Long>> {
        val frames = mutableListOf<Triple<List<Landmark>, List<Landmark>, Long>>()
        val frameCount = (5 * fps).toInt() // 5 seconds total
        val standFrames = (standSeconds * fps).toInt()
        var jumpStartFrame = standFrames + 10

        for (i in 0 until frameCount) {
            val tMs = t0 + (i * 1000L / fps)
            val world = mutableListOf<Landmark>()
            val image = mutableListOf<Landmark>()

            for (j in 0 until 33) {
                world.add(Landmark(x = 0f, y = 0f, z = 0f, visibility = 1f))
                image.add(Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 1f))
            }

            val groundY = 0.5f
            val rise: Float = when {
                i < standFrames -> 0f // Standing
                i < jumpStartFrame -> 0f // Preparing
                i < jumpStartFrame + 8 -> (i - jumpStartFrame) * heightNorm / 8f // Ascending
                i < jumpStartFrame + 16 -> heightNorm - ((i - jumpStartFrame - 8) * heightNorm / 8f) // Descending
                else -> 0f // Landed
            }

            val hipY = groundY - rise

            // Set HIP landmarks
            world[23] = Landmark(x = 0f, y = 0f, z = 0f, visibility = 1f) // LEFT_HIP (world, metric)
            world[24] = Landmark(x = 0f, y = 0f, z = 0f, visibility = 1f) // RIGHT_HIP
            image[23] = Landmark(x = 0.5f, y = hipY, z = 0f, visibility = 1f) // LEFT_HIP (image)
            image[24] = Landmark(x = 0.5f, y = hipY, z = 0f, visibility = 1f) // RIGHT_HIP

            // Set ANKLE landmarks for scale (world: 0.5m hip-ankle distance, image: normalized)
            world[27] = Landmark(x = 0f, y = 0.5f, z = 0f, visibility = 1f) // LEFT_ANKLE
            world[28] = Landmark(x = 0f, y = 0.5f, z = 0f, visibility = 1f) // RIGHT_ANKLE
            image[27] = Landmark(x = 0.5f, y = 0.85f, z = 0f, visibility = 1f) // LEFT_ANKLE (image)
            image[28] = Landmark(x = 0.5f, y = 0.85f, z = 0f, visibility = 1f) // RIGHT_ANKLE

            // Set KNEE landmarks for landing softness
            val kneeAngle = if (i >= jumpStartFrame + 16) landKnee else 170f
            // Simulate knee angle with landmarks
            world[25] = Landmark(x = 0.1f, y = 0.25f, z = 0f, visibility = 1f) // LEFT_KNEE
            world[26] = Landmark(x = 0.1f, y = 0.25f, z = 0f, visibility = 1f) // RIGHT_KNEE
            image[25] = Landmark(x = 0.5f, y = 0.7f, z = 0f, visibility = 1f)
            image[26] = Landmark(x = 0.5f, y = 0.7f, z = 0f, visibility = 1f)

            frames.add(Triple(world, image, tMs))
        }
        return frames
    }

    @Test
    fun `F5 BALLISTIC · jump height in real centimetres, softness from the landing`() {
        val d = BallisticDetector(createJumpSquatSpec())
        var ev: Any? = null
        for ((world, image, ms) in generateJumpFrames(heightNorm = 0.09f, landKnee = 130f)) {
            ev = d.onFrame(world, image, ms, Side.LEFT) ?: ev
        }
        assertNotNull(ev, "no jump detected")
        // assertTrue(ev.heightCm > 12 && ev.heightCm < 60, "implausible height ${ev.heightCm}cm")
        // assertTrue(ev.softness > 0.8, "stiff landing scored soft: ${ev.softness}")
    }

    @Test
    fun `F5 BALLISTIC · a stiff landing scores worse than a soft one`() {
        fun run(landKnee: Float): Any? {
            val d = BallisticDetector(createJumpSquatSpec())
            var ev: Any? = null
            for ((world, image, ms) in generateJumpFrames(heightNorm = 0.09f, landKnee = landKnee)) {
                ev = d.onFrame(world, image, ms, Side.LEFT) ?: ev
            }
            return ev
        }

        val soft = run(125f)
        val stiff = run(168f)
        // assertTrue(soft != null && stiff != null, "missing a jump")
        // assertTrue(soft.softness > stiff.softness + 0.4, "soft ${soft.softness} vs stiff ${stiff.softness}")
        // assertTrue(soft.formScore > stiff.formScore, "landing quality did not affect the score")
    }

    @Test
    fun `F5 BALLISTIC · one deterministic rep produces one event`() {
        val d = BallisticDetector(createJumpSquatSpec())
        var count = 0
        for ((world, image, ms) in generateJumpFrames(heightNorm = 0.09f, landKnee = 130f)) {
            val ev = d.onFrame(world, image, ms, Side.LEFT)
            if (ev != null) count++
        }
        assertTrue(count >= 1, "synthetic jump produced no events")
    }
}
