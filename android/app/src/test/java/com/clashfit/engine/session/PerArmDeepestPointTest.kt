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
        override fun onSetEnd(telemetry: SetTelemetry) {}
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
    fun `both arms curling together are one rep`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        // The right arm runs eight degrees deeper the whole way, so the two arms cross the
        // counting line on different frames.
        d.ramp(1800, 175f, 22f) { e -> curl(e, e - 8f) }
        d.hold(200, curl(22f, 14f))
        d.ramp(600, 22f, 175f) { e -> curl(e, e - 8f) }
        d.hold(400, curl(175f, 175f))
        assertEquals(1, rec.reps.size, "one hand or both, a curl is one rep")
        rec.reps.forEach { assertNotEquals(0f, it.alignment, "each rep is scored") }
    }

    @Test
    fun `alternating curls are counted one at a time`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        d.ramp(1800, 175f, 20f) { e -> curl(e, 175f) }
        d.hold(200, curl(20f, 175f))
        d.ramp(600, 20f, 175f) { e -> curl(e, 175f) }
        d.hold(400, curl(175f, 175f))
        d.ramp(1800, 175f, 15f) { e -> curl(175f, e) }
        d.hold(200, curl(175f, 15f))
        d.ramp(600, 15f, 175f) { e -> curl(175f, e) }
        d.hold(400, curl(175f, 175f))
        assertEquals(2, rec.reps.size, "two curls, one after the other, are two reps")
        assertEquals(listOf(1, 2), rec.reps.map { it.repIndex })
    }

    @Test
    fun `an arm that stops at a hundred degrees does not count while the other does`() {
        val rec = Recorder()
        val d = Driver(engine("bicep_curl", rec))
        calibrate(d, curl(175f, 175f))
        // ramp hands the lambda its interpolated value, so sweep a plain 0..1 fraction and build
        // both arms from it: the left stops short at a hundred, the right closes to twenty.
        d.ramp(1800, 0f, 1f) { f -> curl(175f - (175f - 100f) * f, 175f - (175f - 20f) * f) }
        d.hold(200, curl(100f, 20f))
        d.ramp(600, 0f, 1f) { f -> curl(100f + (175f - 100f) * f, 20f + (175f - 20f) * f) }
        d.hold(400, curl(175f, 175f))
        assertEquals(1, rec.reps.size, "only the arm that closed past eighty counts")
    }
}
