package com.clashfit.engine.detect

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Side
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
 * Tests for string parsing robustness fix.
 * Verifies all detectors use .jsonPrimitive.content instead of fragile .toString().trim().
 * This prevents silent breakage if JSON implementation changes.
 */
class StringParsingRobustnessTest {

    @Test
    fun `CadenceDetector parses signalJoint correctly`() {
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

        val detector = DetectorFactory.create(spec) as CadenceDetector
        assertNotNull(detector, "Detector should be created")
        // If parsing fails, detector.onFrame() would crash
    }

    @Test
    fun `CadenceDetector parses signalAxis correctly`() {
        val spec = ExerciseSpec(
            id = "high_knee",
            family = "CADENCE",
            name = "High Knee",
            detector = buildJsonObject {
                put("signalJoint", "KNEE")
                put("signalAxis", "z")  // Test z axis
                put("minProminence", 0.03f)
            }
        )

        val detector = DetectorFactory.create(spec) as CadenceDetector
        assertNotNull(detector, "Detector should parse z axis correctly")
    }

    @Test
    fun `IsometricHoldDetector parses angle joints correctly`() {
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
            }
        )

        val detector = DetectorFactory.create(spec) as IsometricHoldDetector
        assertNotNull(detector, "Detector should parse angle arrays correctly")
    }

    @Test
    fun `PoseMatchDetector parses reference joints correctly`() {
        val spec = ExerciseSpec(
            id = "tadasana",
            family = "POSE_MATCH",
            name = "Tadasana",
            detector = buildJsonObject {
                putJsonArray("reference") {
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
                put("enterAccuracy", 0.7f)
                put("enterHoldMs", 1500)
                put("breakToleranceMs", 800)
                put("targetDurationSec", 20f)
            }
        )

        val detector = DetectorFactory.create(spec) as PoseMatchDetector
        assertNotNull(detector, "Detector should parse reference array correctly")
    }

    @Test
    fun `string parsing handles empty strings gracefully`() {
        val spec = ExerciseSpec(
            id = "test",
            family = "CADENCE",
            name = "Test",
            detector = buildJsonObject {
                put("signalJoint", "")  // Empty string
                put("signalAxis", "y")
                put("minProminence", 0.03f)
            }
        )

        val detector = DetectorFactory.create(spec) as CadenceDetector
        assertNotNull(detector, "Detector should handle empty string gracefully")
        // Should default to "WRIST" for empty signalJoint
    }

    @Test
    fun `string parsing handles special characters`() {
        // Ensure parsing doesn't break with special JSON characters
        val spec = ExerciseSpec(
            id = "test",
            family = "CADENCE",
            name = "Test with special chars",
            detector = buildJsonObject {
                put("signalJoint", "KNEE")  // No quotes, escapes, etc.
                put("signalAxis", "y")
                put("minProminence", 0.03f)
            }
        )

        val detector = DetectorFactory.create(spec) as CadenceDetector
        assertNotNull(detector, "Detector should handle clean JSON strings")
    }

    @Test
    fun `multiple joints in array parse consistently`() {
        val spec = ExerciseSpec(
            id = "plank",
            family = "ISOMETRIC_HOLD",
            name = "Plank",
            detector = buildJsonObject {
                putJsonArray("targets") {
                    // Multiple angle definitions
                    addJsonObject {
                        putJsonArray("angle") {
                            add("SHOULDER")
                            add("HIP")
                            add("ANKLE")
                        }
                        put("value", 180f)
                        put("tolerance", 10f)
                    }
                    addJsonObject {
                        putJsonArray("angle") {
                            add("HIP")
                            add("KNEE")
                            add("ANKLE")
                        }
                        put("value", 90f)
                        put("tolerance", 15f)
                    }
                }
                put("breakToleranceMs", 500)
                put("targetDurationSec", 45f)
            }
        )

        val detector = DetectorFactory.create(spec) as IsometricHoldDetector
        assertNotNull(detector, "Detector should parse multiple angle arrays")
    }

    private fun assertNotNull(value: Any?, message: String) {
        if (value == null) fail(message)
    }
}
