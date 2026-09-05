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

    // ------------------------------------------------------------------ fitmon's counter, end to end

    private fun curl(left: Float, right: Float) = SyntheticBody.world(170f, leftElbowDeg = left, rightElbowDeg = right)
    private fun raise(abduction: Float, elbow: Float = 170f) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = abduction)
    private fun overhead(elbow: Float) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = 175f)
    private fun press(elbow: Float, abduction: Float) = SyntheticBody.world(170f, elbowDeg = elbow, shoulderAbductionDeg = abduction)

    /** One curl on one arm, the other left hanging open. fitmon: open past 150, closed under 40. */
    private fun oneArmCurl(d: Driver, arm: String) {
        fun pose(e: Float) = if (arm == "left") curl(e, 175f) else curl(175f, e)
        d.ramp(700, 175f, 20f, ::pose); d.hold(200, pose(20f))
        d.ramp(600, 20f, 175f, ::pose); d.hold(400, pose(175f))
    }

    @Test
    fun `each arm counts its own curl`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        oneArmCurl(d, "left"); oneArmCurl(d, "right"); oneArmCurl(d, "left"); oneArmCurl(d, "right")
        assertEquals(4, rec.reps.size, "four arm-curls are four reps")
        assertEquals(listOf(1, 2, 3, 4), rec.reps.map { it.repIndex })
    }

    @Test
    fun `both arms curling together count twice, as fitmon counts them`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        repeat(3) {
            d.ramp(700, 175f, 20f) { e -> curl(e, e) }; d.hold(200, curl(20f, 20f))
            d.ramp(600, 20f, 175f) { e -> curl(e, e) }; d.hold(400, curl(175f, 175f))
        }
        assertEquals(6, rec.reps.size, "each arm is counted, so three cycles of both arms are six reps")
    }

    @Test
    fun `a half curl is not a rep`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        repeat(2) {
            d.ramp(700, 175f, 90f) { e -> curl(e, e) }; d.hold(200, curl(90f, 90f))
            d.ramp(600, 90f, 175f) { e -> curl(e, e) }; d.hold(400, curl(175f, 175f))
        }
        assertEquals(0, rec.reps.size, "ninety degrees never reaches fitmon's forty")
    }

    @Test
    fun `a rep needs the arm to open again first, so a bounce at the bottom counts once`() {
        val rec = Recorder(); val d = Driver(engine("bicep_curl", rec = rec))
        calibrate(d, curl(175f, 175f))
        d.ramp(700, 175f, 20f) { e -> curl(e, e) }
        repeat(3) { d.ramp(200, 20f, 60f) { e -> curl(e, e) }; d.ramp(200, 60f, 20f) { e -> curl(e, e) } }
        d.ramp(600, 20f, 175f) { e -> curl(e, e) }; d.hold(400, curl(175f, 175f))
        assertEquals(2, rec.reps.size, "one rep per arm, however much the bottom is bounced")
    }

    /** One raise. fitmon watches the wrists cross the shoulders, not the angle. */
    private fun oneRaise(d: Driver, top: Float, elbow: Float = 170f) {
        d.ramp(700, 5f, top) { a -> raise(a, elbow) }; d.hold(300, raise(top, elbow))
        d.ramp(600, top, 5f) { a -> raise(a, elbow) }; d.hold(400, raise(5f, elbow))
    }

    @Test
    fun `a lateral raise counts when both wrists pass the shoulders and not at sixty degrees`() {
        val rec = Recorder(); val d = Driver(engine("lateral_raise", rec = rec))
        calibrate(d, raise(5f))
        oneRaise(d, 100f)
        assertEquals(1, rec.reps.size, "wrists over the shoulders is a rep")
        oneRaise(d, 60f)
        assertEquals(1, rec.reps.size, "sixty degrees leaves the wrists below the shoulders")
        oneRaise(d, 100f)
        assertEquals(2, rec.reps.size)
    }

    @Test
    fun `bent elbows score lower alignment on a lateral raise`() {
        val straight = Recorder(); val d1 = Driver(engine("lateral_raise", rec = straight))
        calibrate(d1, raise(5f)); oneRaise(d1, 100f, elbow = 170f)
        val bent = Recorder(); val d2 = Driver(engine("lateral_raise", rec = bent))
        calibrate(d2, raise(5f, 105f)); oneRaise(d2, 100f, elbow = 105f)
        assertEquals(1, straight.reps.size); assertEquals(1, bent.reps.size)
        assertTrue(bent.reps[0].alignment < straight.reps[0].alignment - 0.3f,
            "elbow at 105° should lose most alignment marks: ${bent.reps[0].alignment} vs ${straight.reps[0].alignment}")
    }

    @Test
    fun `a triceps extension counts overhead and never at the sides`() {
        val rec = Recorder(); val d = Driver(engine("triceps_extension", rec = rec))
        calibrate(d, overhead(170f))
        repeat(2) {
            d.ramp(600, 175f, 80f, ::overhead); d.hold(200, overhead(80f))
            d.ramp(500, 80f, 175f, ::overhead); d.hold(500, overhead(175f))
        }
        assertEquals(2, rec.reps.size, "overhead extensions count")
        // The same elbow motion with the arms hanging is a curl: the wrists never clear the
        // shoulders, so the start position is never reached and nothing can count.
        repeat(2) {
            d.ramp(600, 175f, 80f) { e -> curl(e, e) }; d.hold(200, curl(80f, 80f))
            d.ramp(500, 80f, 175f) { e -> curl(e, e) }; d.hold(500, curl(175f, 175f))
        }
        assertEquals(2, rec.reps.size, "curls at the sides must not count as triceps extensions")
    }

    @Test
    fun `a shoulder press counts overhead and is refused out in front`() {
        val rec = Recorder(); val d = Driver(engine("shoulder_press", rec = rec))
        calibrate(d, press(80f, 90f))
        // Up and overhead: the elbow locks out and the wrist clears the shoulder.
        d.ramp(700, 0f, 1f) { f -> press(80f + 98f * f, 90f + 88f * f) }
        d.hold(300, press(178f, 178f))
        d.ramp(600, 1f, 0f) { f -> press(80f + 98f * f, 90f + 88f * f) }
        d.hold(400, press(80f, 90f))
        assertEquals(1, rec.reps.size, "a press to lockout overhead is a rep")
        // Pushed out in front: the elbow locks out at shoulder height, so the wrist never clears it.
        d.ramp(700, 80f, 178f) { e -> press(e, 90f) }
        val atLockout = d.hold(300, press(178f, 90f))
        d.ramp(600, 178f, 80f) { e -> press(e, 90f) }
        val after = d.hold(300, press(80f, 90f))
        assertEquals(1, rec.reps.size, "a press pushed out in front is refused")
        assertNotNull(after.cue, "the refusal is explained")
        assertTrue(after.cue!!.contains("overhead", ignoreCase = true), "cue was: ${after.cue}")
        assertEquals(Phase.FIGHTING, atLockout.phase)
    }

    @Test
    fun `a squat counts on the way back up and a half squat does not`() {
        val rec = Recorder(); val d = Driver(engine("squat", rec = rec))
        calibrate(d, SyntheticBody.world(175f))
        repeat(2) {
            d.ramp(800, 175f, 90f) { k -> SyntheticBody.world(k) }; d.hold(300, SyntheticBody.world(90f))
            d.ramp(700, 90f, 175f) { k -> SyntheticBody.world(k) }; d.hold(400, SyntheticBody.world(175f))
        }
        assertEquals(2, rec.reps.size, "down past 110 and back over 160 is a rep")
        d.ramp(800, 175f, 130f) { k -> SyntheticBody.world(k) }; d.hold(300, SyntheticBody.world(130f))
        d.ramp(700, 130f, 175f) { k -> SyntheticBody.world(k) }; d.hold(400, SyntheticBody.world(175f))
        assertEquals(2, rec.reps.size, "a squat to 130 never reaches the bottom")
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
