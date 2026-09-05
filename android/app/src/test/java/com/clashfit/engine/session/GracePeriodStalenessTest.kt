package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.pose.SyntheticBody
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for defect: nextSet() grace period uses stale timestamp.
 *
 * When the first frame of set 2 arrives much later than expected (due to app pause,
 * camera restart, etc.), nextAttackAtMs computed from the stale lastTMs should be
 * detected and re-armed from the actual set start time.
 */
class GracePeriodStalenessTest {
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
        val playerHits = ArrayList<Pair<Int, Int>>()  // (damage, hp)
        var telemetry: SetTelemetry? = null
        var end: EndReason? = null
        override fun onRep(rec: RepRecord, combatState: com.clashfit.core.model.CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) {}
        override fun onSetEnd(telemetry: SetTelemetry) { this.telemetry = telemetry }
        override fun onEnd(reason: EndReason, state: SessionState) { end = reason }
        override fun onPlayerHit(damage: Int, playerHp: Int) { playerHits += damage to playerHp }
    }

    private class Driver(val engine: SessionEngine) {
        var t = 0L
        val image = SyntheticBody.image()
        fun stand(ms: Long, deg: Float = 170f): SessionState {
            var s = engine.state()
            val until = t + ms
            while (t < until) { s = engine.frame(SyntheticBody.world(deg), image, t); t += 33 }
            return s
        }
        fun rep(): SessionState {
            var s = engine.state()
            for ((tt, deg) in SyntheticBody.squatRep(t)) { s = engine.frame(SyntheticBody.world(deg), image, tt); t = tt }
            return s
        }
    }

    private fun engine(mode: GameMode = GameMode.BOSS_FIGHT, rec: Recorder = Recorder()) =
        SessionEngine(pose, combat, exercises, json, exerciseId = "squat", mode = mode, listener = rec)

    @Test
    fun `stale grace period is re-armed on first set 2 frame`() {
        val spec = combat.bossAttack ?: return  // skip if attacks are off
        val grace = (spec.graceSec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        // Calibrate and complete set 1
        d.stand(2500)
        assertEquals(Phase.FIGHTING, d.engine.state().phase)
        val hpAtStart = d.engine.state().combat.playerHp
        d.rep()
        d.stand(700)
        assertEquals(1, rec.reps.size)
        val t1 = d.t  // Approximate time of end of set 1

        // End the set. Set two begins inside endSet(); there is no rest phase to sit in, and the
        // next attack is armed from lastTMs — which is still t1.
        d.engine.endSet()

        // Simulate a 60-second real-time gap (app paused, camera restarted) WITHOUT calling
        // frame(), so lastTMs stays stale at t1.
        val hitsAfterSet1 = rec.playerHits.size

        // Now jump to 75 seconds later and deliver the first frame of set 2.
        // Without the fix, this would trigger an immediate attack because:
        //   nextAttackAtMs = t1 + grace (~14000)
        //   tMs = t1 + 75000
        //   75000 >= 14000, so attack fires immediately
        // With the fix, the stale nextAttackAtMs is detected and re-armed to:
        //   nextAttackAtMs = 75000 + grace
        d.t = t1 + 75_000L
        d.engine.frame(SyntheticBody.world(170f), d.image, d.t)
        d.t += 33

        // Verify no attack yet (should wait grace from the actual set start)
        assertEquals(hitsAfterSet1, rec.playerHits.size, "no attack immediately after late frame")

        // Advance to within grace of the actual set start (75000 + grace)
        d.stand((grace + 100).toLong())
        val newHits = rec.playerHits.size - hitsAfterSet1
        assertTrue(newHits >= 1, "attack fires at grace from actual set start, not from stale time")
    }

    @Test
    fun `normal set transitions still work with re-arming`() {
        val spec = combat.bossAttack ?: return
        val grace = (spec.graceSec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        // Set 1
        d.stand(2500)
        d.rep()
        d.engine.endSet()
        val hitsAfterSet1 = rec.playerHits.size

        // First frame of set 2 arrives at normal time
        d.stand((grace + 100).toLong())
        val newHits = rec.playerHits.size - hitsAfterSet1
        assertEquals(1, newHits, "attack fires at grace after normal set transition")
    }
}
