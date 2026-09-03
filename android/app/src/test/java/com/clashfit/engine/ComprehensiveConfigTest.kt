package com.clashfit.engine

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Side
import com.clashfit.engine.detect.DetectorFactory
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Comprehensive configuration test: Loads every shipped exercise config file and verifies:
 * 1. ExerciseSpec parses correctly
 * 2. Family enum is valid
 * 3. Detector can be instantiated via DetectorFactory
 * 4. onFrame() can be called without crashing with synthetic landmarks
 *
 * The configs are read off disk exactly as SessionEngineTest reads them, so this test tracks the
 * real roster instead of a hand-maintained list of file names.
 */
class ComprehensiveConfigTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val exercisesDir = File("src/main/assets/config/exercises")

    /** Every exercise record on disk. `index.json` is the roster, not an exercise. */
    private fun configFiles(): List<File> {
        val files = exercisesDir.listFiles()
            ?: fail("No exercise config directory at ${exercisesDir.absolutePath}")
        return files.filter { it.name.endsWith(".json") && it.name != "index.json" }.sortedBy { it.name }
    }

    private fun specs(): List<Pair<File, ExerciseSpec>> = configFiles().map { file ->
        val spec = try {
            json.decodeFromString(ExerciseSpec.serializer(), file.readText())
        } catch (e: Exception) {
            fail("Failed to parse ${file.name}: ${e.message}")
        }
        file to spec
    }

    @Test
    fun `load all exercise configs and verify no parsing errors`() {
        val files = configFiles()
        assertTrue(files.isNotEmpty(), "No exercise configs found in ${exercisesDir.absolutePath}")

        val loaded = specs()
        assertEquals(files.size, loaded.size, "every config file must decode into an ExerciseSpec")

        val ids = loaded.map { it.second.id }
        assertEquals(ids.size, ids.toSet().size, "exercise ids must be unique: $ids")

        for ((file, spec) in loaded) {
            assertTrue(spec.id.isNotBlank(), "${file.name}: id must not be blank")
            assertTrue(spec.name.isNotBlank(), "${file.name}: name must not be blank")
            assertTrue(spec.detector.isNotEmpty(), "${file.name}: detector block must not be empty")
        }
    }

    @Test
    fun `verify detector families are valid enums`() {
        for ((file, spec) in specs()) {
            val family = assertNotNull(
                runCatching { Family.valueOf(spec.family) }.getOrNull(),
                "Invalid family enum: ${spec.family} in ${file.name}",
            )
            // familyEnum falls back to REP_CYCLE on a bad string, so it must agree with the raw value.
            assertEquals(family, spec.familyEnum, "${file.name}: familyEnum disagrees with family")
        }
    }

    @Test
    fun `verify detectors can be instantiated and onFrame accepts null safely`() {
        val landmarks = createSyntheticLandmarks()
        var checked = 0

        for ((file, spec) in specs()) {
            val detector = DetectorFactory.create(spec)

            if (spec.familyEnum == Family.REP_CYCLE) {
                // REP_CYCLE is driven by SessionEngine's rep machine, not by a detector.
                assertNull(detector, "${file.name}: REP_CYCLE must not get a detector")
                continue
            }

            val det = assertNotNull(detector, "${file.name}: no detector for family ${spec.family}")
            assertEquals(spec.family, det.family, "${file.name}: detector family mismatch")

            try {
                // A frame with nothing detected, then world-only, then world plus image.
                det.onFrame(null, null, 0L, Side.LEFT)
                det.onFrame(landmarks, null, 33L, Side.LEFT)
                det.onFrame(landmarks, landmarks, 66L, Side.LEFT)
            } catch (e: Exception) {
                fail("Detector for ${file.name} (${spec.family}) crashed: ${e.javaClass.simpleName}: ${e.message}")
            }
            checked++
        }

        assertTrue(checked > 0, "no non-REP_CYCLE exercises were exercised")
    }

    private fun createSyntheticLandmarks(): List<Landmark> {
        return List(33) { i ->
            Landmark(x = i.toFloat() * 0.1f, y = i.toFloat() * 0.2f, z = 1.0f, visibility = 0.9f)
        }
    }
}
