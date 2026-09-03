package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import com.clashfit.core.model.PoseEvent
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PoseMatchDetectorTest {

    private fun createUtkatasanaSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "utkatasana",
            family = "POSE_MATCH",
            name = "Utkatasana (Chair)",
            detector = buildJsonObject {
                putJsonArray("reference") {
                    addJsonObject {
                        putJsonArray("angle") { add("HIP"); add("KNEE"); add("ANKLE") }
                        put("value", 120f)
                        put("tolerance", 14f)
                        put("weight", 1.0f)
                    }
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("KNEE") }
                        put("value", 130f)
                        put("tolerance", 16f)
                        put("weight", 1.0f)
                    }
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("ELBOW"); add("WRIST") }
                        put("value", 170f)
                        put("tolerance", 18f)
                        put("weight", 0.6f)
                    }
                }
                put("enterAccuracy", 0.7f)
                put("enterHoldMs", 1500)
                put("breakToleranceMs", 800)
                put("targetDurationSec", 25f)
            },
        )
    }

    private fun createVirabhadrasanaIiSpec(): ExerciseSpec {
        return ExerciseSpec(
            id = "virabhadrasana_ii",
            family = "POSE_MATCH",
            name = "Virabhadrasana II (Warrior II)",
            detector = buildJsonObject {
                putJsonArray("reference") {
                    addJsonObject {
                        putJsonArray("angle") { add("HIP"); add("KNEE"); add("ANKLE") }
                        put("value", 140f)
                        put("tolerance", 14f)
                        put("weight", 1.0f)
                    }
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("KNEE") }
                        put("value", 165f)
                        put("tolerance", 16f)
                        put("weight", 1.0f)
                    }
                }
                put("enterAccuracy", 0.7f)
                put("enterHoldMs", 1500)
                put("breakToleranceMs", 800)
                put("targetDurationSec", 20f)
            },
        )
    }

    /** Frames holding one asana, built from the angles the test asks for. */
    private fun generatePoseFrames(
        angles: Map<String, Float>,
        seconds: Int,
        fps: Int = 30,
        t0: Long = 0,
    ): List<Pair<List<Landmark>, Long>> {
        val knee = angles["knee"] ?: 176f
        val hipTorso = angles["hipTorso"] ?: 178f
        val elbow = angles["elbow"] ?: 172f

        val frames = mutableListOf<Pair<List<Landmark>, Long>>()
        for (i in 0 until seconds * fps) {
            frames.add(stickFigure(knee, hipTorso, elbow) to t0 + i * 1000L / fps)
        }
        return frames
    }

    /**
     * A coherent side-on skeleton that really reads back the angles it was asked for, ported
     * from the JS reference generator (test/synth.js `stick`). Both sides get the same
     * coordinates, so mirror tolerance scores either one identically.
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
    fun `F3 POSE_MATCH · a correct asana is recognised and held`() {
        val d = PoseMatchDetector(createUtkatasanaSpec())
        var ev: MovementEvent? = null
        for ((lm, ms) in generatePoseFrames(mapOf("knee" to 120f, "hipTorso" to 130f, "elbow" to 170f), 28)) {
            ev = d.onFrame(null, lm, ms, Side.LEFT) ?: ev
        }
        assertNotNull(ev, "asana never recognised")
        val pose = ev as PoseEvent
        assertTrue(pose.accuracy > 0.9f, "accuracy ${pose.accuracy}")
        // PoseEvent carries no `completed` flag, so the full hold is asserted by its duration.
        assertTrue(pose.heldSec >= 24f, "held only ${pose.heldSec}s")
    }

    @Test
    fun `F3 POSE_MATCH · a wrong shape is not recognised`() {
        val d = PoseMatchDetector(createUtkatasanaSpec())
        var ev: MovementEvent? = null
        for ((lm, ms) in generatePoseFrames(mapOf("knee" to 176f, "hipTorso" to 178f, "elbow" to 172f), 20)) {
            ev = d.onFrame(null, lm, ms, Side.LEFT) ?: ev
        }
        assertTrue(ev == null, "standing upright was accepted as Chair pose")
    }

    @Test
    fun `F3 POSE_MATCH · names the worst joint instead of a percentage`() {
        val d = PoseMatchDetector(createVirabhadrasanaIiSpec())
        for ((lm, ms) in generatePoseFrames(mapOf("knee" to 140f, "hipTorso" to 165f, "elbow" to 176f), 3)) {
            d.onFrame(null, lm, ms, Side.LEFT)
        }
        assertNotNull(d.last?.cue, "no cue produced")
        assertTrue(
            d.last!!.cue!!.contains("left") || d.last!!.cue!!.contains("right"),
            "cue does not name a side: ${d.last?.cue}",
        )
        assertTrue(
            !d.last!!.cue!!.contains("%") && !d.last!!.cue!!.contains("percent"),
            "cue is a percentage: ${d.last?.cue}",
        )
    }

    /**
     * 28s covers the 1.5s entry plus the 25s target duration, and leaves too little room after
     * the event for a second entry — so a single unbroken hold is a single rep.
     */
    @Test
    fun `F3 POSE_MATCH · one deterministic rep produces one event`() {
        val d = PoseMatchDetector(createUtkatasanaSpec())
        var count = 0
        for ((lm, ms) in generatePoseFrames(mapOf("knee" to 120f, "hipTorso" to 130f, "elbow" to 170f), 28)) {
            val ev = d.onFrame(null, lm, ms, Side.LEFT)
            if (ev != null) count++
        }
        assertTrue(count >= 1, "synthetic pose produced no events")
        assertEquals(1, count, "one hold produced $count events")
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
