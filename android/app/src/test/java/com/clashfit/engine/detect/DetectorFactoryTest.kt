package com.clashfit.engine.detect

import com.clashfit.core.config.ExerciseSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectorFactoryTest {

    @Test
    fun `factory creates isometric hold detector`() {
        val spec = ExerciseSpec(
            id = "wall_sit",
            family = "ISOMETRIC_HOLD",
            name = "Wall Sit",
            detector = buildJsonObject { put("breakToleranceMs", 500) },
        )
        val detector = DetectorFactory.create(spec)
        assertNotNull(detector)
        assertTrue(detector is IsometricHoldDetector)
        assertEquals("ISOMETRIC_HOLD", detector.family)
    }

    @Test
    fun `factory creates cadence detector`() {
        val spec = ExerciseSpec(
            id = "jumping_jacks",
            family = "CADENCE",
            name = "Jumping Jacks",
            detector = buildJsonObject { put("signalJoint", "WRIST") },
        )
        val detector = DetectorFactory.create(spec)
        assertNotNull(detector)
        assertTrue(detector is CadenceDetector)
        assertEquals("CADENCE", detector.family)
    }

    @Test
    fun `factory creates ballistic detector`() {
        val spec = ExerciseSpec(
            id = "jump_squat",
            family = "BALLISTIC",
            name = "Jump Squat",
            detector = buildJsonObject { put("takeoffRise", 0.035f) },
        )
        val detector = DetectorFactory.create(spec)
        assertNotNull(detector)
        assertTrue(detector is BallisticDetector)
        assertEquals("BALLISTIC", detector.family)
    }

    @Test
    fun `factory creates pose match detector`() {
        val spec = ExerciseSpec(
            id = "utkatasana",
            family = "POSE_MATCH",
            name = "Utkatasana",
            detector = buildJsonObject { put("enterAccuracy", 0.7f) },
        )
        val detector = DetectorFactory.create(spec)
        assertNotNull(detector)
        assertTrue(detector is PoseMatchDetector)
        assertEquals("POSE_MATCH", detector.family)
    }

    @Test
    fun `factory returns null for rep cycle`() {
        val spec = ExerciseSpec(
            id = "squat",
            family = "REP_CYCLE",
            name = "Squat",
            detector = buildJsonObject { put("topEnter", 45f) },
        )
        val detector = DetectorFactory.create(spec)
        assertNull(detector, "REP_CYCLE should return null (handled by SessionEngine)")
    }

    @Test
    fun `factory returns null for unknown family`() {
        val spec = ExerciseSpec(
            id = "unknown",
            family = "UNKNOWN_FAMILY",
            name = "Unknown",
            detector = buildJsonObject { },
        )
        val detector = DetectorFactory.create(spec)
        assertNull(detector)
    }
}
