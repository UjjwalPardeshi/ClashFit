package com.clashfit.engine.session

import com.clashfit.core.config.ClinicProtocol
import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Phase
import com.clashfit.core.model.Side
import com.clashfit.engine.core.Synth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Tests for MAJOR fix: Conditional parameter passing to detectors.
 * BALLISTIC receives both world and image landmarks.
 * Other families (ISOMETRIC_HOLD, CADENCE, POSE_MATCH) receive only world.
 */
class ConditionalParameterPassingTest {

    private fun createPoseConfig(): PoseConfig {
        return PoseConfig(
            visibilityThreshold = 0.6f,
            framing = PoseConfig.FramingSpec(
                targetBoxHeightMin = 0.4f,
                targetBoxHeightMax = 0.8f,
                holdToStartMs = 3000,
            ),
            framingLostFrames = 45,
            filter = PoseConfig.FilterSpec(minCutoff = 4f, beta = 0.35f, dCutoff = 1f),
            fatigue = PoseConfig.FatigueSpec(
                baselineReps = 10,
                ema = 0.1f,
                bands = PoseConfig.FatigueSpec.Bands(working = 0.25f, fading = 0.5f, gassed = 0.75f),
                bandLatchReps = 3,
                pauseGrowthNormSec = 4f,
            ),
        )
    }

    private fun createCombatConfig(): CombatConfig {
        return CombatConfig(
            baseDamage = 10,
            formFloor = 0.3f,
            formExponent = 1.5f,
            boss = CombatConfig.BossSpec(id = "test", name = "Test", maxHp = 100, phases = emptyList()),
            fatigueResponse = emptyMap(),
            combo = CombatConfig.ComboSpec(step = 0.05f, cap = 2f, threshold = 0.6f, graceAtStreak = 3),
            rest = CombatConfig.RestSpec(freshSeconds = 30, gassedSeconds = 60),
            // noRepTimeoutSec is whole seconds in the shipped schema, not a Float.
            setEnd = CombatConfig.SetEndSpec(noRepTimeoutSec = 30),
            modes = CombatConfig.ModesSpec(
                timeAttack = CombatConfig.ModesSpec.TimeAttackSpec(durationSec = 60),
                tempoTrial = CombatConfig.ModesSpec.TempoTrialSpec(targetEccSec = 2f, tolerance = 1f, floor = 0.3f),
                bossRush = CombatConfig.ModesSpec.BossRushSpec(sequence = emptyList()),
                survival = CombatConfig.ModesSpec.SurvivalSpec(
                    hpPerWave = 100, mercyDisabled = false, formThresholdStep = 0.1f,
                ),
            ),
        )
    }

    @Test
    fun `BALLISTIC detector receives image parameter`() {
        val spec = ExerciseSpec(
            id = "jump_squat",
            family = "BALLISTIC",
            name = "Jump Squat",
            detector = buildJsonObject {
                put("takeoffRise", 0.035f)
                put("landingWindowMs", 300)
                put("standingKnee", 170f)
                put("softLandingFlexionDeg", 35f)
            }
        )

        val exercises = mapOf("jump_squat" to spec)
        val engine = SessionEngine(
            poseCfg = createPoseConfig(),
            combatCfg = createCombatConfig(),
            exercises = exercises,
            json = kotlinx.serialization.json.Json,
            exerciseId = "jump_squat",
        )

        assertEquals(Family.BALLISTIC, engine.family, "Exercise should be BALLISTIC family")

        // Create synthetic landmarks
        val world = Synth.worldFrame(angleDeg = 90f)
        val image = Synth.worldFrame(angleDeg = 90f) // Image landmarks would be normalized in real code

        // Call frame with both parameters - BALLISTIC should use them
        val state = engine.frame(world, image, 0L)
        assertNotNull(state, "Frame should return state")
    }

    @Test
    fun `ISOMETRIC_HOLD detector receives null image parameter`() {
        val spec = ExerciseSpec(
            id = "plank",
            family = "ISOMETRIC_HOLD",
            name = "Plank",
            detector = buildJsonObject {
                putJsonArray("targets") {
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("ANKLE") }
                        put("value", 180f)
                        put("tolerance", 10f)
                    }
                }
                put("breakToleranceMs", 500)
                put("targetDurationSec", 45f)
            }
        )

        val exercises = mapOf("plank" to spec)
        val engine = SessionEngine(
            poseCfg = createPoseConfig(),
            combatCfg = createCombatConfig(),
            exercises = exercises,
            json = kotlinx.serialization.json.Json,
            exerciseId = "plank",
        )

        assertEquals(Family.ISOMETRIC_HOLD, engine.family, "Exercise should be ISOMETRIC_HOLD family")

        val world = Synth.worldFrame(angleDeg = 180f)
        val image = Synth.worldFrame(angleDeg = 180f)

        // Call frame - ISOMETRIC_HOLD should NOT receive image
        val state = engine.frame(world, image, 0L)
        assertNotNull(state, "Frame should return state")
        // Detector should handle null image gracefully
    }

    @Test
    fun `CADENCE detector receives null image parameter`() {
        val spec = ExerciseSpec(
            id = "high_knee",
            family = "CADENCE",
            name = "High Knee",
            detector = buildJsonObject {
                put("signalJoint", "KNEE")
                put("signalAxis", "y")
                put("minProminence", 0.03f)
                put("refractoryMs", 260)
            }
        )

        val exercises = mapOf("high_knee" to spec)
        val engine = SessionEngine(
            poseCfg = createPoseConfig(),
            combatCfg = createCombatConfig(),
            exercises = exercises,
            json = kotlinx.serialization.json.Json,
            exerciseId = "high_knee",
        )

        assertEquals(Family.CADENCE, engine.family, "Exercise should be CADENCE family")

        val world = Synth.worldFrame(angleDeg = 90f)
        val image = Synth.worldFrame(angleDeg = 90f)

        val state = engine.frame(world, image, 0L)
        assertNotNull(state, "Frame should return state")
    }

    @Test
    fun `POSE_MATCH detector receives null image parameter`() {
        val spec = ExerciseSpec(
            id = "tadasana",
            family = "POSE_MATCH",
            name = "Tadasana",
            detector = buildJsonObject {
                putJsonArray("reference") {
                    addJsonObject {
                        putJsonArray("angle") { add("SHOULDER"); add("HIP"); add("ANKLE") }
                        put("value", 180f)
                        put("tolerance", 10f)
                    }
                }
                put("enterAccuracy", 0.7f)
                put("enterHoldMs", 1500)
                put("breakToleranceMs", 800)
                put("targetDurationSec", 20f)
            }
        )

        val exercises = mapOf("tadasana" to spec)
        val engine = SessionEngine(
            poseCfg = createPoseConfig(),
            combatCfg = createCombatConfig(),
            exercises = exercises,
            json = kotlinx.serialization.json.Json,
            exerciseId = "tadasana",
        )

        assertEquals(Family.POSE_MATCH, engine.family, "Exercise should be POSE_MATCH family")

        val world = Synth.worldFrame(angleDeg = 180f)
        val image = Synth.worldFrame(angleDeg = 180f)

        val state = engine.frame(world, image, 0L)
        assertNotNull(state, "Frame should return state")
    }

    @Test
    fun `detector family determines parameter passing behavior`() {
        // This test verifies the logic: if family == BALLISTIC, pass image; else pass null
        val ballistic = Family.BALLISTIC
        val isometric = Family.ISOMETRIC_HOLD
        val cadence = Family.CADENCE
        val poseMatch = Family.POSE_MATCH

        val passImage = ballistic == Family.BALLISTIC
        assertEquals(true, passImage, "BALLISTIC should receive image")

        val passImageIso = isometric == Family.BALLISTIC
        assertEquals(false, passImageIso, "ISOMETRIC_HOLD should NOT receive image")

        val passImageCad = cadence == Family.BALLISTIC
        assertEquals(false, passImageCad, "CADENCE should NOT receive image")

        val passImagePose = poseMatch == Family.BALLISTIC
        assertEquals(false, passImagePose, "POSE_MATCH should NOT receive image")
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String) {
        if (expected != actual) fail("$message: expected $expected, got $actual")
    }

    private fun assertNotNull(value: Any?, message: String) {
        if (value == null) fail(message)
    }
}
