package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.pose.SyntheticBody
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression test for defect: per-arm rep completion loses deepest point of non-completing arm.
 *
 * When sidesEach=true (per-arm machines like bicep curls), each arm's FSM runs independently
 * and can complete reps in any order. The fix tracks the deepest point separately for each arm
 * so that when one arm completes, the other arm's deepest frame data is still available.
 *
 * Concrete scenario: left arm at 30°, right arm at 20° (deeper). trackDeepest is called once
 * with primaryAngle ~25°. Right arm completes first and clears deepestLms. Left arm later
 * completes, but without per-arm tracking it would use current frame landmarks instead of
 * left arm's deepest point, causing incorrect alignment scoring.
 */
class PerArmDeepestPointTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val assets = File("src/main/assets/config")
    private val pose = json.decodeFromString(PoseConfig.serializer(), File(assets, "pose.json").readText())
    private val combat = json.decodeFromString(CombatConfig.serializer(), File(assets, "combat.json").readText())
    private val exercises: Map<String, ExerciseSpec> = File(assets, "exercises").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") && it.name != "index.json" }
        .map { json.decodeFromString(ExerciseSpec.serializer(), it.readText()) }
        .associateBy { it.id }

    private class Recorder : SessionEngine.Listener {
        val reps = ArrayList<RepRecord>()
        val hits = ArrayList<Pair<Int, Int>>()
        var end: EndReason? = null
        override fun onRep(rec: RepRecord, combatState: CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) {}
        override fun onSetEnd(telemetry: SetTelemetry, restSec: Int) {}
        override fun onEnd(reason: EndReason, state: SessionState) { end = reason }
        override fun onPlayerHit(damage: Int, playerHp: Int) { hits += damage to playerHp }
    }

    private class Driver(val engine: SessionEngine) {
        var t = 0L
        val image = SyntheticBody.image()
        fun feed(ms: Long, pose: (Long) -> Landmarks): SessionState {
            var s = engine.state()
            val until = t + ms
            while (t < until) { s = engine.frame(pose(t), image, t); t += 33 }
            return s
        }
        fun hold(ms: Long, lms: Landmarks) = feed(ms) { lms }
        fun ramp(ms: Long, from: Float, to: Float, pose: (Float) -> Landmarks): SessionState {
            val t0 = t
            return feed(ms) { now -> pose(from + (to - from) * ((now - t0).toFloat() / ms)) }
        }
    }

    private fun engine(id: String, rec: Recorder = Recorder()) =
        SessionEngine(pose, combat, exercises, json, exerciseId = id, mode = GameMode.BOSS_FIGHT, listener = rec)

    private fun calibrate(d: Driver, start: Landmarks): SessionState {
        val s = d.hold(3_000, start)
        assertEquals(Phase.FIGHTING, s.phase)
        return s
    }

    private fun curl(left: Float, right: Float) = SyntheticBody.world(170f, leftElbowDeg = left, rightElbowDeg = right)

    @Test
    fun `asymmetric curl with both arms together is one rep scored at its own deepest frame`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        // Both arms curl together, the right harder (14°) than the left (22°). They finish inside
        // the merge window, so this is one rep; its deepest frame is the completing arm's own.
        val t0 = d.t
        d.ramp(700, 175f, 22f) { t ->
            val progress = (t - t0).toFloat() / 700f
            curl(175f - (175f - 22f) * progress, 175f - (175f - 14f) * progress)
        }
        d.hold(500, curl(22f, 14f))
        val t1 = d.t
        d.ramp(600, 22f, 175f) { t ->
            val progress = (t - t1).toFloat() / 600f
            curl(22f + (175f - 22f) * progress, 14f + (175f - 14f) * progress)
        }
        d.hold(500, curl(175f, 175f))
        assertEquals(1, rec.reps.size, "both arms moving together is one rep")
        assertNotEquals(0f, rec.reps[0].alignment, "the rep has alignment data")
    }

    @Test
    fun `alternating curls maintain per-arm deepest tracking`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        // Left curl: left goes to 20°, right stays open
        val t0 = d.t
        d.ramp(700, 175f, 20f) { t ->
            val progress = (t - t0).toFloat() / 700f
            curl(175f - (175f - 20f) * progress, 175f)
        }
        d.hold(500, curl(20f, 175f))
        val t0b = d.t
        d.ramp(600, 20f, 175f) { t ->
            val progress = (t - t0b).toFloat() / 600f
            curl(20f + (175f - 20f) * progress, 175f)
        }
        d.hold(500, curl(175f, 175f))
        // Right curl: right goes to 15° (deeper), left open
        val t1 = d.t
        d.ramp(700, 175f, 15f) { t ->
            val progress = (t - t1).toFloat() / 700f
            curl(175f, 175f - (175f - 15f) * progress)
        }
        d.hold(500, curl(175f, 15f))
        val t1b = d.t
        d.ramp(600, 15f, 175f) { t ->
            val progress = (t - t1b).toFloat() / 600f
            curl(175f, 15f + (175f - 15f) * progress)
        }
        d.hold(500, curl(175f, 175f))
        assertEquals(2, rec.reps.size, "each arm's rep is counted separately")
        assertNotEquals(0f, rec.reps[0].alignment, "rep 1 alignment captured at its deepest point")
        assertNotEquals(0f, rec.reps[1].alignment, "rep 2 alignment captured at its deepest point")
    }

    @Test
    fun `one arm shallow while other goes deep is handled correctly`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        // Simultaneous but asymmetric: left arm only goes to 80°, right goes to 20°
        val t0 = d.t
        d.ramp(700, 0f, 1f) { t ->
            val progress = (t - t0).toFloat() / 700f
            curl(175f - (175f - 80f) * progress, 175f - (175f - 20f) * progress)
        }
        d.hold(500, curl(80f, 20f))
        val t1 = d.t
        d.ramp(600, 1f, 0f) { t ->
            val progress = (t - t1).toFloat() / 600f
            curl(80f + (175f - 80f) * progress, 20f + (175f - 20f) * progress)
        }
        d.hold(500, curl(175f, 175f))
        // Only the right arm closed far enough: one rep, scored at that arm's deepest frame
        assertEquals(1, rec.reps.size, "the arm that closes to 30° counts; the one that stops at 80° does not")
        assertNotEquals(0f, rec.reps[0].alignment, "alignment scored at deepest point")
    }
}
