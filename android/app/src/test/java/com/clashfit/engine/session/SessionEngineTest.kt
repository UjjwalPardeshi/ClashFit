package com.clashfit.engine.session

import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.CalibState
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Verdict
import com.clashfit.core.pose.SyntheticBody
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the hub with a synthetic side-on squatter through the real shipped config. If these
 * pass, calibration, the rep machine, scoring, fatigue, combat and set handling agree with the
 * prototype's behaviour end to end.
 */
class SessionEngineTest {

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
        var telemetry: SetTelemetry? = null
        var restSec: Int? = null
        var end: EndReason? = null
        override fun onRep(rec: RepRecord, combat: com.clashfit.core.model.CombatState) { reps += rec }
        override fun onBand(band: FatigueBand) { bands += band }
        override fun onSetEnd(telemetry: SetTelemetry, restSec: Int) { this.telemetry = telemetry; this.restSec = restSec }
        override fun onEnd(reason: EndReason, state: SessionState) { end = reason }
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

    private fun engine(mode: GameMode = GameMode.BOSS_FIGHT, rec: Recorder = Recorder(), exercise: String = "squat") =
        SessionEngine(pose, combat, exercises, json, exerciseId = exercise, mode = mode, listener = rec)

    @Test
    fun `calibration gates the fight behind two seconds of visibility`() {
        val d = Driver(engine())
        assertEquals(Phase.CALIBRATING, d.engine.state().phase)
        val mid = d.stand(1000)
        assertEquals(CalibState.HOLDING, mid.calib)
        assertEquals(Phase.CALIBRATING, mid.phase)
        val ready = d.stand(1500)
        assertEquals(Phase.FIGHTING, ready.phase)
        assertNotNull(ready.topRefDeg)
        assertTrue(ready.topRefDeg!! > 165f, "top reference should sit at the standing angle, was ${ready.topRefDeg}")
    }

    @Test
    fun `ten identical clean reps count, land damage, and stay FRESH`() {
        val rec = Recorder()
        val d = Driver(engine(rec = rec))
        d.stand(2500)
        repeat(10) { d.rep(); d.stand(900) }
        val s = d.engine.state()
        assertEquals(10, s.reps)
        assertEquals(10, rec.reps.size)
        assertTrue(rec.reps.all { it.verdict == Verdict.CLEAN }, "verdicts: ${rec.reps.map { it.formScore }}")
        assertEquals(FatigueBand.FRESH, s.fatigue.band)
        assertTrue(s.combat.hp < s.combat.maxHp)
        assertEquals(rec.reps.sumOf { it.damage }, s.playerDamage)
        assertTrue(s.combat.comboMultiplier > 1f, "ten clean reps must build a combo")
        assertEquals(Phase.FIGHTING, s.phase)
    }

    @Test
    fun `no damage is ever applied during calibration`() {
        val rec = Recorder()
        val d = Driver(engine(rec = rec))
        d.rep()                       // moving before calibration completes
        val s = d.engine.state()
        assertEquals(0, s.reps)
        assertEquals(s.combat.maxHp, s.combat.hp)
        assertTrue(rec.reps.isEmpty())
    }

    @Test
    fun `twelve idle seconds end the set with telemetry and a fatigue-derived rest`() {
        val rec = Recorder()
        val d = Driver(engine(rec = rec))
        d.stand(2500)
        repeat(5) { d.rep(); d.stand(900) }
        val s = d.stand(12_500)
        assertEquals(Phase.REST, s.phase)
        val t = assertNotNull(rec.telemetry)
        assertEquals(5, t.reps)
        assertEquals("squat", t.exercise)
        // Five clean reps still carry a sliver of fatigue, so the rest sits just above the fresh floor.
        val fresh = combat.rest.freshSeconds
        assertTrue(rec.restSec in fresh..(fresh + 5), "fresh rest is the short end of the range, got ${rec.restSec}")
        assertNull(s.coach, "the coach is fetched outside the engine")

        d.engine.nextSet()
        assertEquals(Phase.FIGHTING, d.engine.state().phase)
        assertEquals(2, d.engine.state().setIndex)
        d.rep(); d.stand(300)   // a rep completes on the first standing frame after the ascent
        assertEquals(6, d.engine.state().reps, "after the first rep of set 2: setReps=${d.engine.state().setReps}")
    }

    @Test
    fun `framing loss freezes the fight and recovers without navigating away`() {
        val d = Driver(engine())
        d.stand(2500)
        d.rep()
        val lost = d.blind(pose.framingLostFrames + 1)
        assertEquals(Phase.FRAMING_LOST, lost.phase)
        assertNotNull(lost.cue)
        val back = d.stand(200)
        assertEquals(Phase.FIGHTING, back.phase)
        assertEquals(1, back.reps)
    }

    @Test
    fun `time attack ends on the clock and the boss never dies first`() {
        val rec = Recorder()
        val d = Driver(engine(GameMode.TIME_ATTACK, rec))
        d.stand(2500)
        val start = d.t
        while (d.t - start < 61_000 && rec.end == null) { d.rep(); d.stand(600) }
        assertEquals(EndReason.TIME, rec.end)
        val s = d.engine.state()
        assertTrue(s.ended)
        assertTrue(s.reps > 5)
        assertTrue(s.combat.hp > 0, "a timed mode must not let the boss die out from under the clock")
        assertEquals(0L, s.timeLeftMs)
    }

    @Test
    fun `stopping mid-set ends the session and keeps the reps`() {
        val rec = Recorder()
        val d = Driver(engine(rec = rec))
        d.stand(2500)
        repeat(3) { d.rep(); d.stand(600) }
        d.engine.endSet()
        d.engine.stop()
        assertEquals(EndReason.STOPPED, rec.end)
        assertEquals(3, d.engine.state().reps)
        assertEquals(3, rec.telemetry?.reps)
    }
}
