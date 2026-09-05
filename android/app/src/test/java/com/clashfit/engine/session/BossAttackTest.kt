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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boss attack mechanics: player HP, periodic attacks, healing on reps, mercy rule, DEFEATED.
 * Verifies that attacks are enabled in the right modes, deal the right damage scaled by phase,
 * heal the player on reps, and end the session when player dies.
 */
class BossAttackTest {
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
        val bands = ArrayList<FatigueBand>()
        val playerHits = ArrayList<Pair<Int, Int>>()  // (damage, hp)
        var telemetry: SetTelemetry? = null
        var restSec: Int? = null
        var end: EndReason? = null
        override fun onRep(rec: RepRecord, combatState: com.clashfit.core.model.CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) { bands += band }
        override fun onSetEnd(telemetry: SetTelemetry, restSec: Int) { this.telemetry = telemetry; this.restSec = restSec }
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
        fun blind(frames: Int): SessionState {
            var s = engine.state()
            repeat(frames) { s = engine.frame(null, null, t); t += 33 }
            return s
        }
    }

    private fun engine(mode: GameMode = GameMode.BOSS_FIGHT, rec: Recorder = Recorder()) =
        SessionEngine(pose, combat, exercises, json, exerciseId = "squat", mode = mode, listener = rec)

    @Test
    fun `no attack before graceSec`() {
        val spec = combat.bossAttack ?: return  // skip if attacks are off
        val grace = (spec.graceSec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        // Calibrate
        d.stand(2500)
        val startSec = d.t
        assertEquals(Phase.FIGHTING, d.engine.state().phase, "started fighting")
        assertEquals(spec.playerMaxHp, d.engine.state().combat.playerHp, "player starts at full HP")

        // Calibration ends somewhere inside the first hold, so read the clock the engine armed
        // rather than assuming when it started: nothing lands before it, the first hit after it.
        val countdown = d.engine.state().combat.nextAttackInMs!!
        assertTrue(countdown in 1..grace.toLong(), "the first attack waits at most the grace period: $countdown")
        d.stand(countdown - 200)
        assertEquals(0, rec.playerHits.size, "no attack before grace")
        assertEquals(spec.playerMaxHp, d.engine.state().combat.playerHp)
        d.stand(400)
        assertEquals(1, rec.playerHits.size, "the first attack lands when the grace ends")
    }

    @Test
    fun `attacks every everySec after grace`() {
        val spec = combat.bossAttack ?: return
        val grace = (spec.graceSec * 1000).toInt()
        val interval = (spec.everySec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        d.stand(2500)
        val hpAtStart = d.engine.state().combat.playerHp

        // Advance to first attack
        d.stand((grace + 100).toLong())
        assertEquals(1, rec.playerHits.size, "first attack after grace")
        val (damage1, hp1) = rec.playerHits[0]
        assertEquals(hpAtStart - damage1, hp1)

        // Advance to second attack
        d.stand((interval + 100).toLong())
        assertEquals(2, rec.playerHits.size, "second attack after interval")
        val (damage2, hp2) = rec.playerHits[1]
        assertEquals(hp1 - damage2, hp2)
    }

    @Test
    fun `healPerRep on each counted rep`() {
        val spec = combat.bossAttack ?: return
        val grace = (spec.graceSec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        d.stand(2500)
        // Let first attack happen
        d.stand((grace + 500).toLong())
        val hpAfterAttack = d.engine.state().combat.playerHp

        // Complete one rep; the smoothed angle needs a moment standing to settle back past topEnter
        d.rep()
        d.stand(700)
        val hpAfterRep = d.engine.state().combat.playerHp
        assertEquals(1, rec.reps.size)
        assertEquals(hpAfterAttack + spec.healPerRep, hpAfterRep, "player healed by healPerRep")
    }

    @Test
    fun `TIME_ATTACK never attacks`() {
        val spec = combat.bossAttack ?: return
        assertTrue("TIME_ATTACK" !in spec.modes, "TIME_ATTACK should not be in attack modes")

        val rec = Recorder()
        val d = Driver(engine(GameMode.TIME_ATTACK, rec))
        d.stand(2500)
        d.stand(5000)  // plenty of time
        assertEquals(0, rec.playerHits.size, "no attacks in TIME_ATTACK")
    }

    @Test
    fun `DUEL never attacks`() {
        val spec = combat.bossAttack ?: return
        assertTrue("DUEL" !in spec.modes, "DUEL should not be in attack modes")

        val rec = Recorder()
        val d = Driver(engine(GameMode.DUEL, rec))
        d.stand(2500)
        d.stand(5000)
        assertEquals(0, rec.playerHits.size, "no attacks in DUEL")
    }

    @Test
    fun `modes list respected`() {
        val spec = combat.bossAttack ?: return
        assertTrue("BOSS_FIGHT" in spec.modes, "BOSS_FIGHT should be in attack modes")
        assertTrue("BOSS_RUSH" in spec.modes, "BOSS_RUSH should be in attack modes")
        assertTrue("SURVIVAL" in spec.modes, "SURVIVAL should be in attack modes")
        assertFalse("TIME_ATTACK" in spec.modes, "TIME_ATTACK should not be in attack modes")
        assertFalse("DUEL" in spec.modes, "DUEL should not be in attack modes")
    }

    @Test
    fun `no attack during FRAMING_LOST`() {
        val spec = combat.bossAttack ?: return
        val grace = (spec.graceSec * 1000).toInt()
        val rec = Recorder()
        val d = Driver(engine(rec = rec))

        d.stand(2500)
        // First attack
        d.stand((grace + 500).toLong())
        val hitsBeforeLoss = rec.playerHits.size

        // Cause framing loss
        d.blind(pose.framingLostFrames + 1)
        assertEquals(Phase.FRAMING_LOST, d.engine.state().phase)

        // Wait a bit while framing is lost, then recover
        d.stand(500)
        assertEquals(hitsBeforeLoss, rec.playerHits.size, "no new attacks during framing loss")

        // Recover and verify attacks resume
        d.stand(3000)  // enough for next attack after 1500ms delay
        assertTrue(rec.playerHits.size > hitsBeforeLoss, "attacks resume after framing recovers")
    }
}
