package com.clashfit.engine.core

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Side

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.RepEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.math.abs

class OneEuroFilterTest {
    @Test
    fun `OneEuro filter responds to fast motion`() {
        val f = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f, dCutoff = 1.0f)
        // Fast motion: large value change in short time
        val result1 = f.filter(0f, 0)
        val result2 = f.filter(100f, 33)  // 100 degrees in 33ms — very fast
        assertTrue(result2 > 50f, "Fast motion should not be heavily smoothed")
    }

    @Test
    fun `OneEuro filter smooths slow motion`() {
        val f = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f)
        var value = 50f
        val result = f.filter(value, 0)
        // Many small steps to let the filter settle
        repeat(100) { i ->
            value += 0.5f
            f.filter(value, (i + 1) * 33L)
        }
        value += 0.1f  // Very small step
        val smoothed = f.filter(value, 101 * 33L)
        // Smoothing should absorb the small step
        assertTrue(abs(smoothed - value) > 0.05f, "Should smooth small movements")
    }

    @Test
    fun `LandmarkFilter applies filter per axis`() {
        val f = LandmarkFilter(1.0f, 0.007f, 1.0f, 33)
        val lms = listOf(Landmark(0f, 0f, 0f))
        val filtered = f.apply(lms, 0)
        assertEquals(1, filtered.size)
    }
}

class GeometryTest {
    @Test
    fun `angle3 computes 90 degree angle`() {
        val a = Landmark(1f, 0f, 0f)
        val b = Landmark(0f, 0f, 0f)
        val c = Landmark(0f, 1f, 0f)
        val angle = Geometry.angle3(a, b, c)
        assertEquals(90f, angle, 1f, "Right angle should be ~90 degrees")
    }

    @Test
    fun `angle3 returns NaN for degenerate case`() {
        val a = Landmark(0f, 0f, 0f)
        val b = Landmark(0f, 0f, 0f)
        val c = Landmark(1f, 0f, 0f)
        val angle = Geometry.angle3(a, b, c)
        assertTrue(angle.isNaN(), "Degenerate case should return NaN")
    }

    @Test
    fun `chooseSide prefers better visibility`() {
        val lms = MutableList(33) { Landmark(0f, 0f, 0f, 0.5f) }
        // Set right side to better visibility
        lms[12] = Landmark(0f, 0f, 0f, 0.8f)  // RIGHT_SHOULDER
        val sel = Geometry.chooseSide(lms, listOf("SHOULDER"), 0.6f)
        assertEquals(Side.RIGHT, sel.side, "Should choose better visible side")
    }

    @Test
    fun `kneeTracking returns NaN when shin is too small`() {
        val lms = MutableList(33) { Landmark(0f, 0f, 0f, 0.95f) }
        // Place knee and ankle at the same spot
        lms[25] = Landmark(0f, 0f, 0f)  // LEFT_KNEE
        lms[27] = Landmark(0f, 0f, 0f)  // LEFT_ANKLE
        val offset = Geometry.kneeTracking(lms, Side.LEFT)
        assertTrue(offset.isNaN(), "Should return NaN for degenerate shin")
    }

    @Test
    fun `primaryAngle returns NaN when landmarks are below visibility threshold`() {
        val lms = MutableList(33) { Landmark(0f, 0f, 0f, 0.4f) }  // All below 0.6 threshold
        val result = Geometry.primaryAngle(
            lms,
            aName = "HIP",
            bName = "KNEE",
            cName = "ANKLE",
            jointNames = listOf("HIP", "KNEE", "ANKLE"),
            threshold = 0.6f
        )
        assertTrue(result.angle.isNaN(), "Angle should be NaN when invalid")
        assertTrue(result.left.isNaN(), "Left should be NaN when invalid")
        assertTrue(result.right.isNaN(), "Right should be NaN when invalid")
        assertFalse(result.valid, "Valid should be false when visibility below threshold")
    }
}

class RepStateMachineTest {
    @Test
    fun `FSM detects one clean squat`() {
        val cfg = RepDetectorConfig(
            topEnter = 158f, topExit = 150f,
            bottomEnter = 100f, bottomExit = 110f,
            targetAngle = 90f,
        )
        val fsm = RepStateMachine(cfg)
        fsm.setTopRef(170f)

        val angles = Synth.set(1, 170f, 80f)
        var reps = 0
        for ((angle, time) in angles) {
            val event = fsm.onFrame(angle, time)
            if (event != null) reps++
        }
        assertEquals(1, reps, "Should detect exactly 1 rep")
    }

    @Test
    fun `FSM rejects rep with under 90% valid frames`() {
        val cfg = RepDetectorConfig(
            topEnter = 158f, topExit = 150f,
            bottomEnter = 100f, bottomExit = 110f,
            targetAngle = 90f,
        )
        val fsm = RepStateMachine(cfg)
        fsm.setTopRef(170f)

        // Generate a rep and drop an invalid frame in after every fifth real one, so ~17% of the
        // frames *inside* the rep are invalid. Injecting them at the head of the list instead
        // would put them before the rep even starts, where the FSM does not count them.
        val (samples, _) = Synth.rep(170f, 80f)
        val angles = mutableListOf<Pair<Float, Long>>()
        for ((i, sample) in samples.withIndex()) {
            angles += sample
            if (i % 5 == 4) angles += Float.NaN to sample.second
        }

        var reps = 0
        for ((angle, time) in angles) {
            val event = fsm.onFrame(angle, time)
            if (event != null) reps++
        }
        assertTrue(reps == 0, "Rep with too many invalid frames should be rejected")
    }

    @Test
    fun `FSM rejects too-short rep`() {
        val cfg = RepDetectorConfig(
            topEnter = 158f, topExit = 150f,
            bottomEnter = 100f, bottomExit = 110f,
            targetAngle = 90f,
            minRepMs = 1000,
        )
        val fsm = RepStateMachine(cfg)
        fsm.setTopRef(170f)

        // Single frame rep (instant)
        var reps = 0
        reps += if (fsm.onFrame(170f, 0) != null) 1 else 0
        reps += if (fsm.onFrame(80f, 100) != null) 1 else 0  // Too short
        reps += if (fsm.onFrame(170f, 200) != null) 1 else 0

        assertTrue(reps == 0, "Too-short rep should be rejected")
    }
}

class FormScorerTest {
    @Test
    fun `depth score increases with deeper reps`() {
        // depth() reads uMin against uTopRef and uTarget — u-space, not thetaMin. Rest 170, target 90.
        val e1 = RepEvent(1, "", 0, 1000, 120f, 170f, 120f, 170f, 170f, 90f, 0.5f, 0.1f, 0.4f, 100f, 0f, 1f)
        val e2 = RepEvent(2, "", 1000, 2000, 95f, 170f, 95f, 170f, 170f, 90f, 0.5f, 0.1f, 0.4f, 100f, 0f, 1f)
        val d1 = FormScorer.depth(e1, 1.5f)
        val d2 = FormScorer.depth(e2, 1.5f)
        assertTrue(d2 > d1, "Deeper rep should score higher on depth")
    }

    @Test
    fun `rom score increases with better range`() {
        val base = 80f  // baseline u-space range
        val e1 = RepEvent(1, "", 0, 1000, 100f, 170f, 0f, base / 2, 1f, 0f, 0.5f, 0.1f, 0.4f, 100f, 0f, 1f)
        val e2 = RepEvent(2, "", 1000, 2000, 100f, 170f, 0f, base, 1f, 0f, 0.5f, 0.1f, 0.4f, 100f, 0f, 1f)
        val r1 = FormScorer.rom(e1, base)
        val r2 = FormScorer.rom(e2, base)
        assertTrue(r2 > r1, "Better ROM should score higher")
    }

    @Test
    fun `score clamped between 0 and 1`() {
        val e = RepEvent(1, "", 0, 1000, 20f, 170f, 0f, 1f, 1f, 0f, 0.5f, 0.1f, 0.4f, 100f, 0f, 1f)
        val w = FormWeights()
        val s = FormScorer.score(e, w, 80f, 2f, 1.5f)  // Alignment > 1 should be clamped
        assertTrue(s.formScore in 0f..1f, "Score should be clamped to [0,1]")
        assertTrue(s.alignment <= 1f, "Alignment should be clamped")
    }
}

class FatigueEstimatorTest {
    @Test
    fun `flat set stays FRESH`() {
        val cfg = FatigueConfig(baselineReps = 3, working = 0.15f, fading = 0.30f, gassed = 0.50f)
        val est = FatigueEstimator(cfg)
        val reps = Synth.set(12, 170f, 80f)

        val fsm = RepStateMachine(RepDetectorConfig(158f, 150f, 100f, 110f, 90f))

        fsm.setTopRef(170f)

        for ((angle, time) in reps) {
            val event = fsm.onFrame(angle, time)
            if (event != null) {
                val state = est.onRep(event)
                // Don't stop early, process all
            }
        }
        val state = est.state()
        assertEquals(FatigueBand.FRESH, state.band, "Flat set should stay FRESH")
    }

    @Test
    fun `band latching does not flap on boundary`() {
        val cfg = FatigueConfig(baselineReps = 3, bandLatchReps = 1, working = 0.15f)
        val est = FatigueEstimator(cfg)

        // Pre-set samples and baseline
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))

        val bands = mutableListOf<FatigueBand>()
        for (v in listOf(79f, 81f, 79f, 81f, 79f)) {
            val state = est.onSignals(mapOf("velocity" to v, "rom" to 90f, "gap" to 0.4f))
            bands += state.band
        }
        assertEquals(1, bands.toSet().size, "Should not flap between bands: ${bands.joinToString(",")}")
    }

    @Test
    fun `freeze prevents pause being read as fatigue`() {
        val cfg = FatigueConfig(baselineReps = 3)
        val est = FatigueEstimator(cfg)
        val reps = Synth.set(6, 170f, 80f)

        val fsm = RepStateMachine(RepDetectorConfig(158f, 150f, 100f, 110f, 90f))

        fsm.setTopRef(170f)

        for ((angle, time) in reps) {
            val event = fsm.onFrame(angle, time)
            if (event != null) {
                est.onRep(event)
            }
        }
        val before = est.state().value
        est.freeze()
        est.onSignals(mapOf("velocity" to 1f, "rom" to 5f, "gap" to 30f))  // Huge gap
        val after = est.state().value
        assertEquals(before, after, 1e-6f, "Frozen estimator should not move")
    }

    @Test
    fun `GASSED band is reachable`() {
        val cfg = FatigueConfig(baselineReps = 3, working = 0.15f, fading = 0.30f, gassed = 0.50f)
        val est = FatigueEstimator(cfg)
        val reps = Synth.set(14, 170f, 80f, decay = 0.030f, restGrowth = 0.55f)

        // Thresholds are the shipped squat ones (config/exercises/squat.json): bottomEnter 120,
        // bottomExit 130. A bottomEnter of 100 is below where a fatigued rep bottoms out, so the
        // back half of the set never registers and fatigue stalls at FADING.
        val fsm = RepStateMachine(RepDetectorConfig(158f, 150f, 120f, 130f, 90f))
        fsm.setTopRef(170f)

        var counted = 0
        for ((angle, time) in reps) {
            val event = fsm.onFrame(angle, time)
            if (event != null) {
                est.onRep(event)
                counted++
            }
        }
        assertTrue(counted >= 12, "Set to failure should still count reps, got $counted")
        val state = est.state()
        assertEquals(FatigueBand.GASSED, state.band, "Should reach GASSED with a decaying set")
    }

    @Test
    fun `band progression includes WORKING and FADING`() {
        val cfg = FatigueConfig(baselineReps = 3, working = 0.15f, fading = 0.30f, gassed = 0.50f)
        val est = FatigueEstimator(cfg)
        val reps = Synth.set(14, 170f, 80f, decay = 0.030f, restGrowth = 0.55f)

        val fsm = RepStateMachine(RepDetectorConfig(158f, 150f, 100f, 110f, 90f))
        fsm.setTopRef(170f)

        val bands = mutableListOf<FatigueBand>()
        for ((angle, time) in reps) {
            val event = fsm.onFrame(angle, time)
            if (event != null) {
                val state = est.onRep(event)
                bands += state.band
            }
        }
        assertTrue(bands.contains(FatigueBand.WORKING), "Should see WORKING band")
        assertTrue(bands.contains(FatigueBand.FADING), "Should see FADING band")
    }

    @Test
    fun `bandLatchReps 0 latches immediately`() {
        val cfg = FatigueConfig(baselineReps = 3, bandLatchReps = 0, working = 0.15f)
        val est = FatigueEstimator(cfg)

        // Pre-set samples and baseline
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))
        est.onSignals(mapOf("velocity" to 100f, "rom" to 90f, "gap" to 0.4f))

        val bands = mutableListOf<FatigueBand>()
        // With bandLatchReps=0, band should change immediately on first candidate >= threshold
        for (v in listOf(79f, 81f, 79f, 81f)) {
            val state = est.onSignals(mapOf("velocity" to v, "rom" to 90f, "gap" to 0.4f))
            bands += state.band
        }
        // With latch reps = 0, band should flip on every change, not stay locked
        assertTrue(bands.size > 1, "Band should flip when latchReps is 0")
    }
}

/**
 * Test-only bundle of CombatEngine's constructor args. The shipped `CombatConfig` in
 * core/config/Configs.kt is a JSON-mirroring schema (String-keyed fatigueResponse, ComboSpec,
 * BossSpec) and doesn't match CombatEngine's constructor shape, so tests build this instead.
 */
private data class CombatConfig(
    val baseDamage: Int = 100,
    val formFloor: Float = 0.35f,
    val formExponent: Float = 1.2f,
    val combo: ComboConfig = ComboConfig(),
    val boss: BossConfig,
    val fatigueResponse: Map<FatigueBand, FatigueResponse> = emptyMap(),
)

class CombatEngineTest {
    @Test
    fun `damage curve is monotonic and bounded`() {
        val cfg = CombatConfig(
            baseDamage = 100, formFloor = 0.35f, formExponent = 1.2f,
            combo = ComboConfig(), boss = BossConfig("pacemaker", "PACEMAKER", 3000, emptyList()),
            fatigueResponse = mapOf(FatigueBand.WORKING to FatigueResponse()),
        )
        val engine = CombatEngine(
            baseDamage = cfg.baseDamage, formFloor = cfg.formFloor, formExponent = cfg.formExponent,
            boss = cfg.boss, responses = cfg.fatigueResponse,
            combo = ComboTracker(cfg.combo),
        )
        val damages = listOf(0f, 0.3f, 0.55f, 0.8f, 0.95f, 1.0f)
            .map { engine.damageFor(it, FatigueBand.WORKING) }
        for (i in 1 until damages.size) {
            assertTrue(damages[i] > damages[i - 1], "Damage not monotonic at $i: $damages")
        }
        assertEquals(35, damages[0], "Floor should be 35")
        assertEquals(100, damages[damages.size - 1], "Perfect rep should be 100")
    }

    @Test
    fun `bad rep still does 35% — never zero`() {
        val cfg = CombatConfig(
            baseDamage = 100, formFloor = 0.35f,
            boss = BossConfig("p", "P", 3000, emptyList()),
            fatigueResponse = mapOf(FatigueBand.WORKING to FatigueResponse()),
        )
        val engine = CombatEngine(
            cfg.baseDamage, cfg.formFloor, 1.2f,
            cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo),
        )
        val dmg = engine.damageFor(0f, FatigueBand.WORKING)
        assertEquals(35, dmg, "Zero form should do 35 damage (floor)")
    }

    @Test
    fun `combo builds and caps`() {
        val cfg = ComboConfig(step = 0.12f, cap = 2.5f, threshold = 0.75f, graceAtStreak = 6)
        val c = ComboTracker(cfg)
        for (i in 0 until 3) c.onRep(0.9f)
        val mult3 = c.multiplier
        assertTrue(abs(mult3 - 1.24f) < 0.01f, "Streak 3 should be ~1.24x")

        for (i in 0 until 20) c.onRep(0.9f)
        assertEquals(2.5f, c.multiplier, 0.01f, "Should cap at 2.5x")
    }

    @Test
    fun `combo grace forgives one bad rep at long streak`() {
        val cfg = ComboConfig(step = 0.12f, cap = 2.5f, threshold = 0.75f, graceAtStreak = 6)
        val c = ComboTracker(cfg)
        for (i in 0 until 10) c.onRep(0.9f)  // Build long streak
        assertTrue(c.streak >= 6, "Should have long streak")
        c.onRep(0.2f)  // One bad rep
        assertTrue(c.streak > 0, "Grace should prevent break")
        c.onRep(0.2f)  // Second bad rep
        assertEquals(0, c.streak, "Second bad rep should break")
    }

    @Test
    fun `boss dies and HP never negative`() {
        val cfg = CombatConfig(
            baseDamage = 100, formFloor = 0.35f,
            boss = BossConfig("p", "P", 3000, emptyList()),
            fatigueResponse = mapOf(FatigueBand.WORKING to FatigueResponse()),
        )
        val engine = CombatEngine(
            cfg.baseDamage, cfg.formFloor, 1.2f,
            cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo),
        )
        for (i in 0 until 200) {
            if (engine.dead) break
            engine.onRep(0.95f, FatigueBand.WORKING)
        }
        assertTrue(engine.dead, "Boss should die after 200 clean reps")
        assertEquals(0, engine.hp, "HP should not be negative")
    }

    @Test
    fun `GASSED mercy resolves in ~4 reps`() {
        val cfg = CombatConfig(
            baseDamage = 100, formFloor = 0.35f,
            boss = BossConfig("p", "P", 3000, emptyList()),
            fatigueResponse = mapOf(
                FatigueBand.WORKING to FatigueResponse(),
                FatigueBand.GASSED to FatigueResponse(mercyRepsToFinish = 4),
            ),
        )
        val engine = CombatEngine(
            cfg.baseDamage, cfg.formFloor, 1.2f,
            cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo),
        )
        for (i in 0 until 8) engine.onRep(0.8f, FatigueBand.WORKING)
        engine.onFatigueBand(FatigueBand.GASSED)
        var n = 0
        while (!engine.dead && n < 20) {
            engine.onRep(0.7f, FatigueBand.GASSED)
            n++
        }
        assertTrue(engine.dead, "Mercy should resolve")
        assertTrue(n <= 5, "Should resolve in ~4 reps, got $n")
    }

    @Test
    fun `duel dedupe by player-seq`() {
        val cfg = CombatConfig(
            baseDamage = 100, boss = BossConfig("p", "P", 3000, emptyList()),
            fatigueResponse = mapOf(FatigueBand.WORKING to FatigueResponse()),
        )
        val engine = CombatEngine(
            cfg.baseDamage, cfg.formFloor, 1.2f,
            cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo),
        )
        for (i in 0 until 50) {
            engine.onRemoteDamage("P2", 1, 100)
        }
        assertEquals(100, engine.totalDamage, "Duplicates should be ignored")
    }

    @Test
    fun `duel out-of-order converges to same HP`() {
        val cfg = CombatConfig(
            baseDamage = 100, boss = BossConfig("p", "P", 3000, emptyList()),
            fatigueResponse = mapOf(FatigueBand.WORKING to FatigueResponse()),
        )
        val events = (1..20).map { it to (50 + it) }
        val a = CombatEngine(cfg.baseDamage, cfg.formFloor, 1.2f, cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo))
        val b = CombatEngine(cfg.baseDamage, cfg.formFloor, 1.2f, cfg.boss, cfg.fatigueResponse, ComboTracker(cfg.combo))

        events.forEach { (seq, dmg) -> a.onRemoteDamage("P2", seq, dmg) }
        events.reversed().forEach { (seq, dmg) -> b.onRemoteDamage("P2", seq, dmg) }

        assertEquals(a.hp, b.hp, "Out-of-order should converge")
    }

    @Test
    fun `casual mode uses correct form floor, damage multiplier, and boss HP multiplier`() {
        val casualCfg = CasualConfig(damageMultiplier = 1.6f, formFloor = 0.6f, bossHpMultiplier = 0.5f)
        val normalEngine = CombatEngine(
            baseDamage = 100, formFloor = 0.35f, formExponent = 1.2f,
            boss = BossConfig("p", "P", 1000, emptyList()),
            responses = mapOf(FatigueBand.WORKING to FatigueResponse()),
            combo = ComboTracker(ComboConfig()),
            casual = false,
        )
        val casualEngine = CombatEngine(
            baseDamage = 100, formFloor = 0.35f, formExponent = 1.2f,
            boss = BossConfig("p", "P", 1000, emptyList()),
            responses = mapOf(FatigueBand.WORKING to FatigueResponse()),
            combo = ComboTracker(ComboConfig()),
            casual = true,
            casualConfig = casualCfg,
        )

        // Test form floor: casual floor is 0.6, normal is 0.35
        val casualFloor = casualEngine.damageFor(0.5f, FatigueBand.WORKING)
        val normalFloor = normalEngine.damageFor(0.5f, FatigueBand.WORKING)
        assertTrue(casualFloor > normalFloor, "Casual mode should have higher floor damage")

        // Test damage multiplier: casual uses 1.6x base damage
        val casualDmg = casualEngine.damageFor(1.0f, FatigueBand.WORKING)
        val normalDmg = normalEngine.damageFor(1.0f, FatigueBand.WORKING)
        assertTrue(casualDmg > normalDmg, "Casual mode should have higher base damage multiplier")

        // Test boss HP multiplier: casual uses 0.5x boss HP
        normalEngine.reset()
        casualEngine.reset()
        assertEquals(1000, normalEngine.maxHp, "Normal engine should have full boss HP")
        assertEquals(500, casualEngine.maxHp, "Casual engine should have 0.5x boss HP")
    }

    @Test
    fun `casual mode with zero form score and WORKING band`() {
        val casualCfg = CasualConfig(damageMultiplier = 1.6f, formFloor = 0.6f, bossHpMultiplier = 0.5f)
        val casualEngine = CombatEngine(
            baseDamage = 100, formFloor = 0.35f, formExponent = 1.2f,
            boss = BossConfig("p", "P", 2000, emptyList()),
            responses = mapOf(FatigueBand.WORKING to FatigueResponse()),
            combo = ComboTracker(ComboConfig()),
            casual = true,
            casualConfig = casualCfg,
        )
        val dmg = casualEngine.damageFor(0.0f, FatigueBand.WORKING)
        // With form floor 0.6 and base damage 100 * 1.6 = 160, damage should be 160 * 0.6 = 96
        assertEquals(96, dmg, "Casual zero-form damage should be (100 * 1.6) * 0.6 = 96")
    }
}

class ChallengeCodecTest {
    @Test
    fun `encode and decode round-trip`() {
        val card = ChallengeCard(
            kind = "GHOST",
            exerciseId = "squat",
            mode = "GHOST_RACE",
            name = "Test Challenge",
            target = 500,
            ghost = ChallengeCard.GhostDataPayload(
                events = listOf(
                    ChallengeCard.GhostDataPayload.GhostEventPayload(1000, 85),
                    ChallengeCard.GhostDataPayload.GhostEventPayload(2500, 90),
                )
            ),
        )
        val code = ChallengeCodec.encode(card)
        val decoded = ChallengeCodec.decode(code)
        assertEquals(card.kind, decoded.kind)
        assertEquals(card.exerciseId, decoded.exerciseId)
        assertEquals(card.mode, decoded.mode)
        assertEquals(card.name, decoded.name)
        assertEquals(card.target, decoded.target)
        assertEquals(2, decoded.ghost?.events?.size)
    }

    @Test
    fun `decode detects corrupted checksum`() {
        val card = ChallengeCard("GHOST", "squat", "GHOST_RACE", "Test", null, null)
        val code = ChallengeCodec.encode(card)
        val corrupted = code.substring(0, code.length - 2) + "XX"
        try {
            ChallengeCodec.decode(corrupted)
            assertTrue(false, "Should throw on checksum mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("damaged") == true)
        }
    }
}
