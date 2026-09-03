package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.HoldEvent
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IsometricHoldDetectorTest {

    private fun createWallSitSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "wall_sit",
            family = "ISOMETRIC_HOLD",
            name = "Wall Sit",
            detector = buildJsonObject {
                putJsonArray("targets") {
                    addJsonObject {
                        putJsonArray("angle") { add("HIP"); add("KNEE"); add("ANKLE") }
                        put("value", 90f)
                        put("tolerance", 12f)
                        put("weight", 1.0f)
                    }
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("KNEE") }
                        put("value", 90f)
                        put("tolerance", 15f)
                        put("weight", 0.6f)
                    }
                }
                put("breakToleranceMs", 500)
                put("targetDurationSec", 45f)
            },
        )
    }

    private fun createPlankSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "plank",
            family = "ISOMETRIC_HOLD",
            name = "Plank",
            detector = buildJsonObject {
                putJsonArray("targets") {
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("ELBOW"); add("WRIST") }
                        put("value", 88f)
                        put("tolerance", 12f)
                        put("weight", 1.0f)
                    }
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("KNEE") }
                        put("value", 176f)
                        put("tolerance", 10f)
                        put("weight", 1.0f)
                    }
                }
                put("breakToleranceMs", 500)
                put("targetDurationSec", 45f)
            },
        )
    }

    /**
     * Frames holding one posture, with optional positional noise for the tremor test.
     * The noise is a deterministic LCG rather than kotlin.random so a tremor reading is
     * reproducible run to run.
     */
    private fun generateHoldFrames(
        angles: Map<String, Float>,
        seconds: Int,
        noise: Float = 0f,
        fps: Int = 30,
        t0: Long = 0,
    ): List<Pair<List<Landmark>, Long>> {
        val knee = angles["knee"] ?: 176f
        val hipTorso = angles["hipTorso"] ?: 178f
        val elbow = angles["elbow"] ?: 172f

        var seed = 12345
        fun jitter(): Float {
            seed = seed * 1103515245 + 12345
            return (seed and 0x7fffffff).toFloat() / 0x7fffffff - 0.5f
        }

        val frames = mutableListOf<Pair<List<Landmark>, Long>>()
        for (i in 0 until seconds * fps) {
            val body = stickFigure(knee, hipTorso, elbow)
            val lms = if (noise > 0f) {
                body.map { it.copy(x = it.x + jitter() * noise, y = it.y + jitter() * noise) }
            } else {
                body
            }
            frames.add(lms to t0 + i * 1000L / fps)
        }
        return frames
    }

    /**
     * A coherent side-on skeleton that really reads back the angles it was asked for, ported
     * from the JS reference generator (test/synth.js `stick`). Both sides get the same
     * coordinates, so a mirror-tolerant detector scores either one identically.
     */
    private fun stickFigure(
        knee: Float,
        hipTorso: Float,
        elbow: Float,
        femurDir: Float = 90f,
    ): List<Landmark> {
        val hip = Landmark(0f, 0f)
        val kneeP = step(hip, femurDir, FEMUR)
        val ankle = step(kneeP, femurDir + 180f - knee, SHIN)
        val shoulder = step(hip, femurDir + hipTorso, TORSO)
        val elbowDir = femurDir + hipTorso + 175f
        val elbowP = step(shoulder, elbowDir, UPPER_ARM)
        val wrist = step(elbowP, elbowDir + 180f - elbow, FOREARM)

        val joints = listOf(
            11 to shoulder, 13 to elbowP, 15 to wrist,
            23 to hip, 25 to kneeP, 27 to ankle, 29 to ankle, 31 to ankle,
        )
        val lms = MutableList(33) { Landmark(0f, 0f) }
        for (side in 0..1) {
            for ((index, p) in joints) lms[index + side] = p
        }
        return lms
    }

    /** One bone: `len` from `from`, in direction `deg` (0 = +x, 90 = +y). */
    private fun step(from: Landmark, deg: Float, len: Float): Landmark {
        val r = deg * DEG_TO_RAD
        return Landmark(from.x + cos(r) * len, from.y + sin(r) * len)
    }

    @Test
    fun `F2 ISOMETRIC · holding target duration and form`() {
        val d = IsometricHoldDetector(createWallSitSpec())
        var ev: MovementEvent? = null
        for ((lm, ms) in generateHoldFrames(mapOf("knee" to 90f, "hipTorso" to 90f), 47)) {
            ev = d.onFrame(null, lm, ms, Side.LEFT) ?: ev
        }
        assertNotNull(ev, "no hold event")
        val hold = ev as HoldEvent
        assertTrue(hold.completed, "hold not marked complete")
        assertTrue(hold.quality > 0.9f, "quality ${hold.quality}")
        assertTrue(hold.holdSec >= 44f, "held only ${hold.holdSec}s")
        assertTrue(hold.formScore > 0.9f, "formScore ${hold.formScore}")
    }

    @Test
    fun `F2 ISOMETRIC · breaking form ends the hold early`() {
        val d = IsometricHoldDetector(createWallSitSpec())
        var ev: MovementEvent? = null
        for ((lm, ms) in generateHoldFrames(mapOf("knee" to 90f, "hipTorso" to 90f), 8)) {
            ev = d.onFrame(null, lm, ms, Side.LEFT) ?: ev
        }
        for ((lm, ms) in generateHoldFrames(
            mapOf("knee" to 150f, "hipTorso" to 160f),
            3,
            t0 = 8000,
        )) {
            ev = d.onFrame(null, lm, ms, Side.LEFT) ?: ev
        }
        assertNotNull(ev, "no event on break")
        val hold = ev as HoldEvent
        assertTrue(!hold.completed, "break counted as completion")
        assertTrue(hold.holdSec > 6f && hold.holdSec < 9f, "holdSec ${hold.holdSec}")
    }

    @Test
    fun `F2 ISOMETRIC · tremor baseline and increase detectable`() {
        val quiet = generateHoldFrames(mapOf("knee" to 176f, "hipTorso" to 176f, "elbow" to 88f), 6, noise = 0.0f)
        val noisy = generateHoldFrames(mapOf("knee" to 176f, "hipTorso" to 176f, "elbow" to 88f), 6, noise = 0.01f)

        val d1 = IsometricHoldDetector(createPlankSpec())
        for ((lm, ms) in quiet) {
            d1.onFrame(null, lm, ms, Side.LEFT)
        }
        val quietTremor = d1.last?.tremor ?: 0f

        val d2 = IsometricHoldDetector(createPlankSpec())
        for ((lm, ms) in noisy) {
            d2.onFrame(null, lm, ms, Side.LEFT)
        }
        val noisyTremor = d2.last?.tremor ?: 0f

        assertTrue(noisyTremor > quietTremor, "tremor did not respond to wobble: $quietTremor vs $noisyTremor")
    }

    private companion object {
        const val FEMUR = 0.42f
        const val SHIN = 0.42f
        const val TORSO = 0.45f
        const val UPPER_ARM = 0.28f
        const val FOREARM = 0.26f
        const val DEG_TO_RAD = 0.017453292f
    }
}
