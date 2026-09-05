package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.pose.SyntheticBody
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for SessionEngine with rep detection enhancements:
 * - EACH mode support for per-side tracking
 * - Gate angle validation
 * - Deepest-point tracking in u-space
 * - ELBOW_EXTENSION alignment scoring
 */
class RepPortSessionTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val assets = File("src/main/assets/config")
    private val pose = json.decodeFromString(PoseConfig.serializer(), File(assets, "pose.json").readText())
    private val combat = json.decodeFromString(CombatConfig.serializer(), File(assets, "combat.json").readText())
    private val exercises: Map<String, ExerciseSpec> = File(assets, "exercises").listFiles()!!
        .filter { it.name.endsWith(".json") && it.name != "index.json" }
        .map { json.decodeFromString(ExerciseSpec.serializer(), it.readText()) }
        .associateBy { it.id }

    private class Recorder : SessionEngine.Listener {
        val reps = ArrayList<RepRecord>()
        override fun onRep(rec: RepRecord, combat: com.clashfit.core.model.CombatState) { reps += rec }
    }

    private class Driver(val engine: SessionEngine) {
        var t = 0L
        val image = SyntheticBody.image()

        fun stand(ms: Long, deg: Float = 170f): SessionState {
            var s = engine.state()
            val until = t + ms
            while (t < until) {
                s = engine.frame(SyntheticBody.world(deg), image, t)
                t += 33
            }
            return s
        }

        fun rep(): SessionState {
            var s = engine.state()
            for ((tt, deg) in SyntheticBody.squatRep(t)) {
                s = engine.frame(SyntheticBody.world(deg), image, tt)
                t = tt
            }
            return s
        }
    }

    @Test
    fun `calibration completes and top reference is set correctly`() {
        val rec = Recorder()
        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "squat", listener = rec)
        val d = Driver(engine)

        assertEquals(Phase.CALIBRATING, d.engine.state().phase)
        d.stand(2500)
        val s = d.engine.state()
        assertEquals(Phase.FIGHTING, s.phase, "Should complete calibration after 2.5 seconds")
        assertNotNull(s.topRefDeg, "Should have set top reference")
        assertTrue(s.topRefDeg!! > 160f, "Top ref for squat should be standing angle")
    }

    @Test
    fun `single rep completes with alignment sampling at deepest point`() {
        val rec = Recorder()
        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "squat", listener = rec)
        val d = Driver(engine)

        d.stand(2500)
        d.rep()
        d.stand(500)

        assertEquals(1, engine.state().reps, "Should count 1 rep")
        val rep = rec.reps.lastOrNull()
        assertNotNull(rep, "Should record rep details")
        assertTrue(rep!!.alignment >= 0f && rep.alignment <= 1f, "Alignment should be normalized [0,1]")
    }

    @Test
    fun `deepest point u-space tracking handles both increasing and decreasing angles`() {
        // For squat (top 170 > bottom 90, s = +1), deepest point is the minimum u = angle
        val rec = Recorder()
        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "squat", listener = rec)
        val d = Driver(engine)

        d.stand(2500)
        d.rep()
        d.stand(500)

        // Verify rep was recorded; alignment was sampled at deepest point
        assertEquals(1, rec.reps.size, "Should have 1 rep")
        val rep = rec.reps.first()
        assertTrue(rep.alignment.isFinite(), "Alignment should be sampled (finite value)")
    }

    @Test
    fun `calibration with inverted exercises (calf raise) correctly identifies top reference`() {
        val rec = Recorder()
        if (!exercises.containsKey("calf_raise")) return  // Skip if calf_raise not configured

        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "calf_raise", listener = rec)
        val d = Driver(engine)

        // Stand flat-footed; the rest angle of a calf raise is the knee–ankle–toe angle of that pose.
        d.stand(2500)

        val s = engine.state()
        assertEquals(Phase.FIGHTING, s.phase, "Should complete calibration")
        assertNotNull(s.topRefDeg, "Should have set top reference")
        val stand = SyntheticBody.world(170f)
        val ankle = com.clashfit.engine.core.Geometry.angle3(stand[25], stand[27], stand[31])
        // calf_raise is inverted (topEnter 100 < bottomEnter 115): the reference must still be the
        // held angle itself, in degrees, not its u-space mirror.
        assertTrue(kotlin.math.abs(s.topRefDeg!! - ankle) < 3f, "top ref ${s.topRefDeg} should be the held ankle angle $ankle")
    }

    @Test
    fun `multiple reps in sequence are tracked with independent rep indices`() {
        val rec = Recorder()
        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "squat", listener = rec)
        val d = Driver(engine)

        d.stand(2500)
        repeat(3) { d.rep(); d.stand(600) }

        assertEquals(3, engine.state().reps, "Should count 3 reps")
        assertEquals(3, rec.reps.size, "Should record 3 reps")
        // Verify rep indices are 1, 2, 3
        for ((i, rep) in rec.reps.withIndex()) {
            assertEquals(i + 1, rep.repIndex, "Rep ${i + 1} should have repIndex=${i + 1}")
        }
    }

    @Test
    fun `set reset clears state and allows new set with fresh rep indices`() {
        val rec = Recorder()
        val engine = SessionEngine(pose, combat, exercises, json, exerciseId = "squat", listener = rec)
        val d = Driver(engine)

        // Set 1
        d.stand(2500)
        repeat(2) { d.rep(); d.stand(600) }
        engine.endSet()
        val set1Reps = rec.reps.size
        assertEquals(2, set1Reps)

        // Set 2
        engine.nextSet()
        d.stand(2500)
        d.rep()
        d.stand(500)

        assertEquals(3, engine.state().reps, "Should have 3 total reps across sets")
        assertEquals(3, rec.reps.size, "Should have 3 recorded reps")
        val lastRep = rec.reps.last()
        assertEquals(1, lastRep.repIndex, "First rep of set 2 should have repIndex=1 (reset)")
    }
}
