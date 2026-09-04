package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.CombatState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * A whole set, replayed through the real engine against the real shipped configuration.
 *
 * Every other engine test builds a config by hand and feeds it a handful of synthesised frames.
 * That proves each piece behaves; it does not prove the pieces are wired to each other, or that
 * the JSON we actually ship parses into something the engine can run. A recorded set of 2,648
 * frames does both, on the JVM, with no phone and no camera.
 *
 * The trace is a squat set taken to failure, which is the useful case: reps get slower and
 * shallower toward the end, so it exercises the fatigue model rather than only the rep counter.
 * The assertions are about shape rather than exact numbers — an exact rep count would be a
 * change-detector that has to be edited every time a threshold moves, and would fail without
 * telling anybody anything. What must hold is that a set to failure counts a plausible number of
 * reps, that the scores stay inside their own range, and that the set gets harder rather than
 * easier as it goes on.
 */
class TraceReplayTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class Recorder : SessionEngine.Listener {
        val reps = mutableListOf<RepRecord>()
        val bands = mutableListOf<FatigueBand>()
        override fun onRep(rec: RepRecord, combat: CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) { bands += band }
    }

    @Test
    fun `a recorded set to failure runs end to end through the shipped configuration`() {
        val trace = traceFile()
        assumeTrue("no recorded trace in the assets to replay", trace != null)
        val lines = trace!!.readLines().filter { it.isNotBlank() }
        assertTrue("the trace has a header and frames", lines.size > 100)

        val header = json.parseToJsonElement(lines.first()).jsonObject
        val keep = header["keep"]!!.jsonArray.map { it.jsonPrimitive.int() }
        val exerciseId = header["meta"]!!.jsonObject["exercise"]!!.jsonPrimitive.content

        val recorder = Recorder()
        val engine = SessionEngine(
            poseCfg = poseConfig(),
            combatCfg = combatConfig(),
            exercises = exercises(),
            json = json,
            exerciseId = exerciseId,
            mode = GameMode.BOSS_FIGHT,
            listener = recorder,
        )

        var frames = 0
        var lastState: SessionState? = null
        for (i in 1 until lines.size) {
            val obj = json.parseToJsonElement(lines[i]).jsonObject
            val t = obj["t"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            val lms = landmarks(obj["lm"], keep) ?: continue
            // World landmarks only. The image set exists to check framing against the camera
            // preview, and a replay has no preview — passing the world coordinates in its place
            // would fail a box-height check that is meaningless here, and the engine would sit in
            // calibration for the whole trace. Null is the seam: no image, no framing check.
            lastState = engine.frame(world = lms, image = null, tMs = t)
            frames++
        }

        assertTrue("replayed almost every frame, got $frames of ${lines.size - 1}", frames > (lines.size - 1) * 0.9)
        assertTrue("the engine returned a state", lastState != null)

        // A set to failure. The exact count depends on thresholds that are allowed to move; what
        // cannot move is that a two-and-a-half-thousand-frame squat set produces a real set.
        val reps = recorder.reps
        assertTrue("counted a plausible number of reps, got ${reps.size}", reps.size in 5..60)

        // Nothing may escape its own range. A score outside [0,1] means a sub-score is being
        // combined wrongly, and it would reach the damage curve before anybody noticed.
        reps.forEachIndexed { i, r ->
            assertTrue("rep ${i + 1} form ${r.formScore} outside 0..1", r.formScore in 0f..1f)
            assertTrue("rep ${i + 1} depth ${r.depth} outside 0..1", r.depth in 0f..1f)
            assertTrue("rep ${i + 1} tempo ${r.tempo} outside 0..1", r.tempo in 0f..1f)
            assertTrue("rep ${i + 1} alignment ${r.alignment} outside 0..1", r.alignment in 0f..1f)
            assertTrue("rep ${i + 1} did no negative damage", r.damage >= 0)
            assertTrue("rep ${i + 1} ended after it started", r.tEndMs >= r.tStartMs)
        }

        // Reps come out in order and are numbered without gaps. An off-by-one here would put the
        // wrong index on every coach line and every summary row.
        assertEquals("rep indices run 1..n", (1..reps.size).toList(), reps.map { it.repIndex })

        // Fatigue only ever rises within a set — it is a measure of accumulated cost, and a dip
        // would mean the estimator is reading noise as recovery.
        val fatigue = reps.map { it.fatigue.value }
        fatigue.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("fatigue fell between rep ${i + 1} and ${i + 2}: $a then $b", b >= a - 1e-4f)
        }

        // A set taken to failure has to end harder than it began. Comparing thirds rather than
        // single reps, because one bad rep early is normal and proves nothing either way.
        if (reps.size >= 6) {
            val third = reps.size / 3
            val opening = reps.take(third).map { it.fatigue.value }.average()
            val closing = reps.takeLast(third).map { it.fatigue.value }.average()
            assertTrue(
                "a set to failure should end more fatigued than it started: $opening then $closing",
                closing > opening,
            )
        }
    }

    // ── the shipped configuration, read the same way the app reads it ──────────────────────────

    private fun assets(): File {
        val direct = File("src/main/assets")
        if (direct.isDirectory) return direct
        val fromRoot = File("app/src/main/assets")
        if (fromRoot.isDirectory) return fromRoot
        throw AssertionError("could not find the shipped assets from ${File(".").absolutePath}")
    }

    private fun poseConfig(): PoseConfig =
        json.decodeFromString(PoseConfig.serializer(), File(assets(), "config/pose.json").readText())

    private fun combatConfig(): CombatConfig =
        json.decodeFromString(CombatConfig.serializer(), File(assets(), "config/combat.json").readText())

    /** Every exercise record the app ships, keyed the way the engine expects. */
    private fun exercises(): Map<String, ExerciseSpec> =
        File(assets(), "config/exercises").listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name != "index.json" }
            .mapNotNull { f ->
                runCatching { json.decodeFromString(ExerciseSpec.serializer(), f.readText()) }.getOrNull()
            }
            .associateBy { it.id }

    /** The trace ships inside the APK so the phone can replay it too. */
    private fun traceFile(): File? =
        listOf(File(assets(), "traces/synthetic-f3-to-failure.jsonl"), File("../traces/synthetic-f3-to-failure.jsonl"))
            .firstOrNull { it.isFile }

    /**
     * A sparse recorded frame back into the 33-landmark array the engine expects.
     *
     * The trace stores only the joints that matter for the movement, listed in the header's `keep`
     * array. Everything else stays at zero visibility, which is exactly what the engine sees when
     * a joint is out of shot.
     */
    private fun landmarks(element: kotlinx.serialization.json.JsonElement?, keep: List<Int>): List<Landmark>? {
        val arr = (element as? JsonArray) ?: return null
        val full = MutableList(33) { Landmark(0f, 0f, 0f, 0f) }
        arr.forEachIndexed { i, e ->
            if (e is JsonNull || i >= keep.size) return@forEachIndexed
            val v = (e as? JsonArray)?.map { it.jsonPrimitive.content.toFloat() } ?: return@forEachIndexed
            if (v.size >= 4) full[keep[i]] = Landmark(v[0], v[1], v[2], v[3])
        }
        return full
    }

    private fun kotlinx.serialization.json.JsonPrimitive.int(): Int = content.toInt()
}
