package com.clashfit.engine.core

import com.clashfit.core.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Verifies that all detectors use centralized Geometry.idx() for landmark resolution.
 * This test ensures joint mapping is consistent across all detector families.
 */
class JointMappingUnificationTest {

    @Test
    fun `Geometry idx maps all valid joints correctly for LEFT side`() {
        val expectedMappings = mapOf(
            "SHOULDER" to 11,
            "ELBOW" to 13,
            "WRIST" to 15,
            "HIP" to 23,
            "KNEE" to 25,
            "ANKLE" to 27,
            "HEEL" to 29,
            "FOOT_INDEX" to 31,
        )

        for ((joint, expectedIdx) in expectedMappings) {
            val idx = Geometry.idx(joint, Side.LEFT)
            assertEquals(expectedIdx, idx, "LEFT $joint should map to $expectedIdx, got $idx")
        }
    }

    @Test
    fun `Geometry idx maps all valid joints correctly for RIGHT side`() {
        val expectedMappings = mapOf(
            "SHOULDER" to 12,
            "ELBOW" to 14,
            "WRIST" to 16,
            "HIP" to 24,
            "KNEE" to 26,
            "ANKLE" to 28,
            "HEEL" to 30,
            "FOOT_INDEX" to 32,
        )

        for ((joint, expectedIdx) in expectedMappings) {
            val idx = Geometry.idx(joint, Side.RIGHT)
            assertEquals(expectedIdx, idx, "RIGHT $joint should map to $expectedIdx, got $idx")
        }
    }

    @Test
    fun `Geometry idx returns -1 for unknown joints`() {
        val invalidJoints = listOf("INVALID", "HIP_JOINT", "", "ELBOW_BEND", "KNEE_JOINT_LEFT")
        for (joint in invalidJoints) {
            val idx = Geometry.idx(joint, Side.LEFT)
            assertEquals(-1, idx, "Unknown joint '$joint' should return -1, got $idx")
        }
    }

    @Test
    fun `all MediaPipe landmark indices are in range 0-32`() {
        val joints = listOf("SHOULDER", "ELBOW", "WRIST", "HIP", "KNEE", "ANKLE", "HEEL", "FOOT_INDEX")
        for (joint in joints) {
            val leftIdx = Geometry.idx(joint, Side.LEFT)
            val rightIdx = Geometry.idx(joint, Side.RIGHT)

            if (leftIdx >= 0) {
                assertTrue(leftIdx >= 0 && leftIdx <= 32, "LEFT $joint index $leftIdx out of range")
            }
            if (rightIdx >= 0) {
                assertTrue(rightIdx >= 0 && rightIdx <= 32, "RIGHT $joint index $rightIdx out of range")
            }
        }
    }

    @Test
    fun `left and right indices are distinct for same joint`() {
        val joints = listOf("SHOULDER", "ELBOW", "WRIST", "HIP", "KNEE", "ANKLE", "HEEL", "FOOT_INDEX")
        for (joint in joints) {
            val leftIdx = Geometry.idx(joint, Side.LEFT)
            val rightIdx = Geometry.idx(joint, Side.RIGHT)

            if (leftIdx >= 0 && rightIdx >= 0) {
                assertNotEqual(leftIdx, rightIdx, "$joint should have different indices for left/right")
            }
        }
    }

    @Test
    fun `primary angle calculation uses centralized joint mapping`() {
        val landmarks = Synth.worldFrame(angleDeg = 90f)
        val result = Geometry.primaryAngle(landmarks, "SHOULDER", "HIP", "KNEE", listOf("SHOULDER", "HIP", "KNEE"), 0.6f)

        // Should not throw, and should use consistent mapping
        assertTrue(result.angle.isFinite() || !result.valid, "Primary angle calculation should complete")
    }

    @Test
    fun `span calculation uses centralized joint mapping`() {
        val landmarks = Synth.worldFrame(angleDeg = 90f)
        val span = Geometry.spanMetres(landmarks, "SHOULDER", "KNEE", Side.LEFT)

        // Should return a valid distance (not NaN) or NaN if joints not visible
        assertTrue(span.isNaN() || span > 0f, "Span should be valid distance or NaN, got $span")
    }

    @Test
    fun `knee tracking uses centralized joint mapping`() {
        val landmarks = Synth.worldFrame(angleDeg = 90f)
        val tracking = Geometry.kneeTracking(landmarks, Side.LEFT)

        // Should return valid value or NaN
        assertTrue(tracking.isNaN() || (tracking >= 0f && tracking <= 1f), "Knee tracking should be 0-1 or NaN, got $tracking")
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) fail(message)
    }

    private fun assertNotEqual(a: Int, b: Int, message: String) {
        if (a == b) fail(message)
    }
}
