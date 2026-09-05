package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.Family
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for defensive exercise configuration validation.
 * Ensures setExercise() validates that detector configuration exists
 * and provides clear error messages when config is missing.
 */
class ExerciseConfigValidationTest {

    private fun createMinimalPoseConfig(): PoseConfig {
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

    private fun createMinimalCombatConfig(): CombatConfig {
        return CombatConfig(
            baseDamage = 10,
            formFloor = 0.3f,
            formExponent = 1.5f,
            boss = CombatConfig.BossSpec(id = "test", name = "Test", maxHp = 100, phases = emptyList()),
            fatigueResponse = emptyMap(),
            combo = CombatConfig.ComboSpec(step = 0.05f, cap = 2f, threshold = 0.6f, graceAtStreak = 3),
            setEnd = CombatConfig.SetEndSpec(),
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

    private fun engineFor(exercises: Map<String, ExerciseSpec>, id: String) = SessionEngine(
        poseCfg = createMinimalPoseConfig(),
        combatCfg = createMinimalCombatConfig(),
        exercises = exercises,
        json = kotlinx.serialization.json.Json,
        exerciseId = id,
    )

    @Test
    fun `setExercise validates detector configuration exists`() {
        val validSpec = ExerciseSpec(
            id = "squat",
            family = "REP_CYCLE",
            name = "Squat",
            detector = buildJsonObject {
                put("topEnter", 20f)
                put("topExit", 30f)
                put("bottomEnter", 120f)
                put("bottomExit", 100f)
                put("targetAngle", 90f)
            },
        )

        val engine = engineFor(mapOf("squat" to validSpec), "squat")

        assertEquals("squat", engine.exercise.id)
        assertEquals(Family.REP_CYCLE, engine.family)
    }

    @Test
    fun `setExercise fails fast on an empty detector block`() {
        // ExerciseSpec.detector is a non-null JsonObject in the real schema, so "missing detector"
        // can only be expressed as an empty object. The engine still has to refuse it rather than
        // build a rep machine on absent thresholds.
        val invalidSpec = ExerciseSpec(
            id = "invalid",
            family = "REP_CYCLE",
            name = "Invalid",
            detector = JsonObject(emptyMap()),
        )

        val thrown = assertFailsWith<Exception> {
            engineFor(mapOf("invalid" to invalidSpec), "invalid")
        }

        // The thresholds are read straight out of the detector block, so the failure names the
        // first key it could not find instead of silently producing a detector that never fires.
        assertTrue(
            thrown.message.orEmpty().contains("topEnter"),
            "Error should name the missing detector key: ${thrown.message}",
        )
    }

    @Test
    fun `unknown exercise raises clear error`() {
        val thrown = assertFailsWith<IllegalStateException> {
            engineFor(emptyMap(), "nonexistent")
        }

        assertTrue(
            thrown.message.orEmpty().contains("nonexistent"),
            "Error should name the unknown exercise: ${thrown.message}",
        )
    }

    @Test
    fun `REP_CYCLE exercise requires detector configuration`() {
        val spec = ExerciseSpec(
            id = "squat",
            family = "REP_CYCLE",
            name = "Squat",
            detector = buildJsonObject {
                put("topEnter", 20f)
                put("topExit", 30f)
                put("bottomEnter", 120f)
                put("bottomExit", 100f)
                put("targetAngle", 90f)
                put("minRepMs", 800)
                put("maxRepMs", 8000)
            },
        )

        // Should succeed - REP_CYCLE requires detector for threshold config
        val engine = engineFor(mapOf("squat" to spec), "squat")

        assertEquals(Family.REP_CYCLE, engine.family)
    }

    @Test
    fun `non-REP_CYCLE exercise requires detector implementation`() {
        val spec = ExerciseSpec(
            id = "plank",
            family = "ISOMETRIC_HOLD",
            name = "Plank",
            detector = buildJsonObject {
                putJsonArray("targets") {
                    addJsonObject {
                        putJsonArray("angle") {
                            add("SHOULDER")
                            add("HIP")
                            add("ANKLE")
                        }
                        put("value", 180f)
                        put("tolerance", 10f)
                    }
                }
                put("breakToleranceMs", 500)
                put("targetDurationSec", 45f)
            },
        )

        val engine = engineFor(mapOf("plank" to spec), "plank")

        assertEquals(Family.ISOMETRIC_HOLD, engine.family)
    }
}
