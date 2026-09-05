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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fitmon port, end to end through SessionEngine with the shipped configs: per-arm curls,
 * overhead gates, raises, and the boss's attack clock as the HUD sees it. Every pose is built by
 * SyntheticBody, whose arm model gives exact shoulder and elbow angles.
 */
class RepGateSessionTest {
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

    /** Feeds poses at 30 fps. `pose(t)` returns the world landmarks for the frame at time t. */
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
        /** Linear ramp of one parameter from `from` to `to` over `ms`. */
        fun ramp(ms: Long, from: Float, to: Float, pose: (Float) -> Landmarks): SessionState {
            val t0 = t
            return feed(ms) { now -> pose(from + (to - from) * ((now - t0).toFloat() / ms)) }
        }
    }

    private fun engine(id: String, mode: GameMode = GameMode.BOSS_FIGHT, rec: Recorder = Recorder(), poseCfg: PoseConfig = pose) =
        SessionEngine(poseCfg, combat, exercises, json, exerciseId = id, mode = mode, listener = rec)

    /** Calibration: hold the start pose until the engine is FIGHTING. */
    private fun calibrate(d: Driver, start: Landmarks): SessionState {
        val s = d.hold(3_000, start)
        assertEquals(Phase.FIGHTING, s.phase, "calibration should finish on a steady start pose")
        return s
    }

    // ------------------------------------------------------------------ bicep curl, per arm

    private fun curl(left: Float, right: Float) = SyntheticBody.world(170f, leftElbowDeg = left, rightElbowDeg = right)

    private fun oneArmCurl(d: Driver, arm: String) {
        fun pose(e: Float) = if (arm == "left") curl(e, 175f) else curl(175f, e)
        // The engine smooths landmarks with a 1 Hz One Euro filter (~160 ms lag), so each end is
        // held long enough for the filtered angle to settle past the threshold, as a real curl
        // that pauses at the top and the bottom does.
        d.ramp(700, 175f, 20f, ::pose)
        d.hold(500, pose(20f))
        d.ramp(600, 20f, 175f, ::pose)
        d.hold(500, pose(175f))
    }

    @Test
    fun `alternating curls credit each arm`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        oneArmCurl(d, "left"); oneArmCurl(d, "right"); oneArmCurl(d, "left"); oneArmCurl(d, "right")
        assertEquals(4, rec.reps.size, "four arm-curls are four reps")
        assertEquals(listOf(1, 2, 3, 4), rec.reps.map { it.repIndex })
    }

    @Test
    fun `simultaneous curls credit one rep per cycle`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        repeat(3) {
            d.ramp(700, 175f, 20f) { e -> curl(e, e) }
            d.hold(500, curl(20f, 20f))
            d.ramp(600, 20f, 175f) { e -> curl(e, e) }
            d.hold(500, curl(175f, 175f))
        }
        assertEquals(3, rec.reps.size, "both arms moving together is one rep per cycle")
    }

    @Test
    fun `a half curl is not a rep`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        d.ramp(700, 175f, 90f) { e -> curl(e, e) }
        d.hold(150, curl(90f, 90f))
        d.ramp(600, 90f, 175f) { e -> curl(e, e) }
        d.hold(500, curl(175f, 175f))
        assertEquals(0, rec.reps.size)
    }

    // ------------------------------------------------------------------ lateral raise

    private fun raise(abduction: Float, elbow: Float = 170f) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = abduction)

    /** Rest is abduction 5 (hip–shoulder–elbow reads ~13, inside the 10–20 rest band); `top` 95 reads ~103. */
    private fun oneRaise(d: Driver, top: Float, elbow: Float = 170f) {
        d.ramp(700, 5f, top) { a -> raise(a, elbow) }
        d.hold(500, raise(top, elbow))
        d.ramp(600, top, 5f) { a -> raise(a, elbow) }
        d.hold(500, raise(5f, elbow))
    }

    @Test
    fun `a lateral raise to shoulder height counts and a raise to sixty degrees does not`() {
        val rec = Recorder(); val d = Driver(engine("lateral_raise", rec = rec))
        calibrate(d, raise(5f))
        oneRaise(d, 95f)
        assertEquals(1, rec.reps.size, "arms level with the shoulders is a rep")
        oneRaise(d, 60f)
        assertEquals(1, rec.reps.size, "sixty degrees stops short of bottomEnter (90)")
        oneRaise(d, 95f)
        assertEquals(2, rec.reps.size)
    }

    @Test
    fun `bent elbows score lower alignment on a lateral raise`() {
        val straight = Recorder(); val d1 = Driver(engine("lateral_raise", rec = straight))
        calibrate(d1, raise(5f)); oneRaise(d1, 95f, elbow = 170f)
        val bent = Recorder(); val d2 = Driver(engine("lateral_raise", rec = bent))
        calibrate(d2, raise(5f, 105f)); oneRaise(d2, 95f, elbow = 105f)
        assertEquals(1, straight.reps.size); assertEquals(1, bent.reps.size)
        assertTrue(bent.reps[0].alignment < straight.reps[0].alignment - 0.3f,
            "elbow at 105° should lose most alignment marks: ${bent.reps[0].alignment} vs ${straight.reps[0].alignment}")
    }

    // ------------------------------------------------------------------ overhead gates

    /** Arms overhead (abduction 175): the elbow angle bends behind the head. */
    private fun overhead(elbow: Float) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = 175f)

    @Test
    fun `a triceps extension counts overhead and never at the sides`() {
        val rec = Recorder(); val d = Driver(engine("triceps_extension", rec = rec))
        calibrate(d, overhead(170f))
        repeat(2) {
            d.ramp(600, 170f, 80f, ::overhead); d.hold(150, overhead(80f)); d.ramp(500, 80f, 170f, ::overhead); d.hold(300, overhead(170f))
        }
        assertEquals(2, rec.reps.size, "overhead extensions count")
        // The same elbow motion with the arms hanging is a curl: the ALWAYS gate blocks every frame.
        repeat(2) {
            d.ramp(600, 170f, 80f) { e -> curl(e, e) }; d.hold(150, curl(80f, 80f)); d.ramp(500, 80f, 170f) { e -> curl(e, e) }; d.hold(300, curl(170f, 170f))
        }
        assertEquals(2, rec.reps.size, "curls at the sides must not count as triceps extensions")
    }

    @Test
    fun `a gate that flickers for a few frames does not sink a real overhead extension`() {
        // Smoothing off, so each frame's angles are exactly the pose's: the flicker below is then a
        // clean one-third of the frames outside the gate, which the old valid-frame ratio would refuse.
        val sharp = pose.copy(filter = PoseConfig.FilterSpec(minCutoff = 10_000f, beta = 0f, dCutoff = 10_000f))
        val rec = Recorder(); val d = Driver(engine("triceps_extension", rec = rec, poseCfg = sharp))
        calibrate(d, overhead(170f))
        // Four frames in every twelve the upper arm drops to abduction 100 (hip–shoulder–elbow ≈ 108,
        // under the 120° gate): those frames are hidden from the machine and the rep is judged on the rest.
        var n = 0
        fun flicker(e: Float): Landmarks = SyntheticBody.world(170f, elbowDeg = e, shoulderAbductionDeg = if ((n++ % 12) < 4) 100f else 175f)
        d.ramp(600, 170f, 80f, ::flicker); d.hold(200, flicker(80f)); d.ramp(500, 80f, 170f, ::flicker); d.hold(300, flicker(170f))
        assertEquals(1, rec.reps.size, "frames outside the gate are skipped, not counted as invalid")
    }

    /** A press: elbows bent at the start, arm swung out to `abduction` as it extends. */
    private fun press(elbow: Float, abduction: Float) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = abduction)

    @Test
    fun `a shoulder press counts overhead and is refused out in front`() {
        val rec = Recorder(); val d = Driver(engine("shoulder_press", rec = rec))
        calibrate(d, press(80f, 90f))
        // Overhead: the arm extends while swinging to straight up, so at lockout hip-shoulder-wrist is near 175.
        d.ramp(700, 0f, 1f) { f -> press(80f + 98f * f, 90f + 88f * f) }
        d.hold(500, press(178f, 178f))
        d.ramp(600, 1f, 0f) { f -> press(80f + 98f * f, 90f + 88f * f) }
        d.hold(500, press(80f, 90f))
        assertEquals(1, rec.reps.size, "a press to lockout overhead is a rep")
        // Out in front: the elbow extends but the arm stays level, so the END gate (hip-shoulder-wrist ≥ 140) fails.
        d.ramp(700, 80f, 178f) { e -> press(e, 90f) }
        val atLockout = d.hold(500, press(178f, 90f))
        d.ramp(600, 178f, 80f) { e -> press(e, 90f) }
        val after = d.hold(500, press(80f, 90f))
        assertEquals(1, rec.reps.size, "a press pushed out in front is refused")
        assertNotNull(after.cue, "the refusal is explained")
        assertTrue(after.cue!!.contains("overhead", ignoreCase = true), "cue was: ${after.cue}")
        assertEquals(Phase.FIGHTING, atLockout.phase)
    }

    // ------------------------------------------------------------------ the boss's clock, as the HUD sees it

    @Test
    fun `the attack countdown is a countdown and the interval is exposed`() {
        val rec = Recorder(); val d = Driver(engine("squat", rec = rec))
        val stand = SyntheticBody.world(170f)
        val s0 = calibrate(d, stand)
        val spec = combat.bossAttack!!
        assertEquals((spec.everySec * 1000f).toLong(), s0.combat.attackIntervalMs)
        val first = s0.combat.nextAttackInMs
        assertNotNull(first)
        assertTrue(first <= (spec.graceSec * 1000f).toLong(), "first attack waits the grace period: $first")
        val s1 = d.hold(1_000, stand)
        val later = s1.combat.nextAttackInMs
        assertNotNull(later)
        assertTrue(later < first, "the countdown falls as time passes: $first -> $later")
        assertTrue(later >= 0)
    }

    @Test
    fun `timed modes expose no attack clock`() {
        val d = Driver(engine("squat", mode = GameMode.TIME_ATTACK))
        val s = calibrate(d, SyntheticBody.world(170f))
        assertNull(s.combat.attackIntervalMs)
        assertNull(s.combat.nextAttackInMs)
    }

    @Test
    fun `standing still until the boss wins ends in defeat with full health restored on reset`() {
        val rec = Recorder(); val e = engine("squat", rec = rec); val d = Driver(e)
        val stand = SyntheticBody.world(170f)
        calibrate(d, stand)
        val spec = combat.bossAttack!!
        val s = d.hold(((spec.graceSec + spec.everySec * 20f) * 1000f).toLong(), stand)
        assertEquals(EndReason.DEFEATED, rec.end)
        assertTrue(s.combat.playerDead)
        assertTrue(rec.hits.isNotEmpty())
        e.reset()
        assertEquals(e.state().combat.playerMaxHp, e.state().combat.playerHp, "a new fight starts at full health")
    }
}
