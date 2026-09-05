package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.RepRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Textbook form has to count.
 *
 * Every threshold in this app was measured on one person in one room, and the risk that carries is
 * silent: a number tuned a little too tight still counts *that* person and quietly refuses everyone
 * else. Nothing in the suite noticed, because every other engine test builds its own synthetic
 * frames and therefore agrees with whatever the config happens to say.
 *
 * These four traces are the shipped pose model's own reading of the reference animations used in
 * the exercise picker — a correct rep, performed the way the drawing shows it. On 6 Sep 2026 three
 * of the four counted **nothing**: the shoulder press asked for an elbow past 160 degrees where the
 * model reads 146, the curl asked for 80 where it reads 85, and the lateral raise asked for a wrist
 * above the shoulder when a lateral raise ends level with it. The squat was the only one that
 * worked.
 *
 * So the assertion is deliberately weak and deliberately absolute: a correct rep counts. It says
 * nothing about how many, or how well scored, because those are allowed to move. If this fails,
 * a threshold has drifted somewhere a real person cannot reach.
 */
class ReferenceFormTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class Recorder : SessionEngine.Listener {
        val reps = mutableListOf<RepRecord>()
        override fun onRep(rec: RepRecord, combat: CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) {}
    }

    @Test
    fun `a textbook rep counts for every measured exercise`() {
        // Deliberately not assumeTrue: these traces are committed, so a missing one is a broken
        // checkout. Skipping here once hid the whole test, which passed green while measuring nothing.
        val missing = TRACES.filterValues { traceFile(it) == null }
        assertTrue("reference traces missing from the repository: ${missing.values}", missing.isEmpty())
        val traces = TRACES.map { (id, file) -> id to traceFile(file)!! }

        val counted = traces.map { (id, file) -> id to replay(id, file) }
        val silent = counted.filter { it.second == 0 && it.first !in SHALLOWER_THAN_A_REP }
        assertTrue(
            "these count nothing on a correct rep, so the thresholds are out of reach: " +
                silent.joinToString { it.first } + " (counts: $counted)",
            silent.isEmpty(),
        )
        // The other half of the same guard. If one of these starts counting, somebody has re-drawn
        // the animation deeper, and it belongs back in the set above rather than in the exception.
        for (id in SHALLOWER_THAN_A_REP) {
            val n = counted.first { it.first == id }.second
            assertTrue(
                "$id now counts $n on an animation that was too shallow to be a rep. " +
                    "Re-check the animation against the config and move $id out of SHALLOWER_THAN_A_REP.",
                n == 0,
            )
        }
    }

    /** Replays one trace through the real engine and the real shipped configuration. */
    private fun replay(exerciseId: String, trace: File): Int {
        val lines = trace.readLines().filter { it.isNotBlank() }
        val header = json.parseToJsonElement(lines.first()).jsonObject
        val keep = header["keep"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }

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
        for (i in 1 until lines.size) {
            val obj = json.parseToJsonElement(lines[i]).jsonObject
            val t = obj["t"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            val lms = landmarks(obj["lm"], keep) ?: continue
            engine.frame(world = lms, image = null, tMs = t)
        }
        return recorder.reps.size
    }

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

    private fun exercises(): Map<String, ExerciseSpec> =
        File(assets(), "config/exercises").listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name != "index.json" }
            .mapNotNull { f -> runCatching { json.decodeFromString(ExerciseSpec.serializer(), f.readText()) }.getOrNull() }
            .associateBy { it.id }

    /** The traces live at the repository root; the module can be the working directory or its parent. */
    private fun traceFile(name: String): File? =
        listOf(
            File(assets(), "traces/$name"),
            File("../../traces/$name"),
            File("../traces/$name"),
            File("traces/$name"),
        ).firstOrNull { it.isFile }

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

    private companion object {
        /**
         * Animations that do not reach the rep the config asks for, so refusing them is correct.
         *
         * Measured off these very traces on 6 Sep 2026:
         *
         *  - `bicep_curl` closes only to 85 degrees. A player's real curl closes to 55-78, and this
         *    mode's own rule is that half reps do not count, so counting at 93 to satisfy the
         *    drawing would have counted half curls for everybody.
         *  - `lateral_raise` turns at 93.7 degrees on the weaker arm and never brings the arms below
         *    20.4, where the player's rule counts in a 95-105 band from a 15-20 rest. It misses at
         *    both ends, so it is refused twice over.
         *
         * Both are the drawing disagreeing with a number measured on a person, and the person wins.
         * **Neither is settled.** The raise's band is only ten degrees wide, and if it turns out on
         * a real shoulder that a correct raise cannot reach 95, the threshold is what should move
         * — not this set. Do not add to this list to make a red test green.
         */
        val SHALLOWER_THAN_A_REP = setOf("bicep_curl", "lateral_raise")

        val TRACES = mapOf(
            "lateral_raise" to "reference-lateral-raise.jsonl",
            "bicep_curl" to "reference-bicep-curl.jsonl",
            "shoulder_press" to "reference-shoulder-press.jsonl",
            "squat" to "reference-squat.jsonl",
        )
    }
}
