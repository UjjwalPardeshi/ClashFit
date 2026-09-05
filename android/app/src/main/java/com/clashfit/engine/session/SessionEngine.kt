package com.clashfit.engine.session

import com.clashfit.core.config.ClinicProtocol
import com.clashfit.core.config.CombatConfig
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.GhostData
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.CadenceEvent
import com.clashfit.core.model.CalibState
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.Family
import com.clashfit.core.model.FamilyGameState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.FormScore
import com.clashfit.core.model.Framing
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.HoldEvent
import com.clashfit.core.model.JumpEvent
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.MovementEvent
import com.clashfit.core.model.Phase
import com.clashfit.core.model.PoseEvent
import com.clashfit.core.model.RepEvent
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Side
import com.clashfit.core.model.Verdict
import com.clashfit.engine.coach.TelemetrySummariser
import com.clashfit.engine.core.BossConfig
import com.clashfit.engine.core.BossPhase
import com.clashfit.engine.core.CasualConfig
import com.clashfit.engine.core.CombatEngine
import com.clashfit.engine.core.ComboConfig
import com.clashfit.engine.core.ComboTracker
import com.clashfit.engine.core.FatigueConfig
import com.clashfit.engine.core.FatigueEstimator
import com.clashfit.engine.core.FatigueResponse
import com.clashfit.engine.core.FormScorer
import com.clashfit.engine.core.alignmentSample
import com.clashfit.engine.core.Geometry
import com.clashfit.engine.core.GhostSource
import com.clashfit.engine.core.LandmarkFilter
import com.clashfit.engine.core.RepDetectorConfig
import com.clashfit.engine.core.RepStateMachine
import com.clashfit.engine.core.StageCounter
import com.clashfit.engine.core.SignalTables
import com.clashfit.engine.core.AsymmetryTracker
import com.clashfit.engine.core.summariseAsymmetry
import com.clashfit.engine.detect.DetectorFactory
import com.clashfit.engine.detect.ExerciseDetector
import com.clashfit.engine.games.BreakerEvent
import com.clashfit.engine.games.BreakerGame
import com.clashfit.engine.games.FamilyGame
import com.clashfit.engine.games.FamilyGames
import com.clashfit.engine.games.PursuitEvent
import com.clashfit.engine.games.PursuitGame
import com.clashfit.engine.games.SiegeEvent
import com.clashfit.engine.games.SiegeGame
import com.clashfit.engine.games.SigilEvent
import com.clashfit.engine.games.SigilGame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The integration layer. Owns calibration, the rep machine (or a family detector), form scoring,
 * fatigue, the combat engine and the family game, and exposes one `frame()` entry point plus a
 * `state()` snapshot. Pure Kotlin, time-free: every timestamp comes in through `frame()`.
 *
 * The coach is asynchronous and lives outside: on set end this engine computes the telemetry and
 * the rest length and tells the listener; the caller obtains the lines and hands them back via
 * `setCoach()`. Port of `src/engine.js`.
 */
class SessionEngine(
    private val poseCfg: PoseConfig,
    private val combatCfg: CombatConfig,
    private val exercises: Map<String, ExerciseSpec>,
    @Suppress("unused") private val json: Json,
    exerciseId: String,
    mode: GameMode = GameMode.BOSS_FIGHT,
    private val casual: Boolean = false,
    private val clinic: ClinicProtocol? = null,
    /** Rep Race and other timed modes can carry their own duration. */
    private val durationOverrideSec: Int? = null,
    private val listener: Listener = Listener.NONE,
) {
    /** Timed by the mode itself, or by an explicit duration (a pass-the-phone turn, a rep race). */
    private val timed: Boolean = mode.timed || durationOverrideSec != null
    interface Listener {
        fun onRep(rec: RepRecord, combat: CombatState) {}
        fun onBand(band: FatigueBand) {}
        fun onSetEnd(telemetry: SetTelemetry) {}
        fun onEnd(reason: EndReason, state: SessionState) {}
        fun onPlayerHit(damage: Int, playerHp: Int) {}
        companion object { val NONE = object : Listener {} }
    }

    var mode: GameMode = mode
        private set
    lateinit var exercise: ExerciseSpec
        private set
    var family: Family = Family.REP_CYCLE
        private set

    private var nextAttackAtMs: Long? = null
    private var attacksEnabled = false

    private var detector: ExerciseDetector? = null
    private var jointNames: List<String> = emptyList()
    private var fsm: RepStateMachine? = null
    private var fsmLeft: RepStateMachine? = null
    private var fsmRight: RepStateMachine? = null
    private var primaryAngle: Triple<String, String, String>? = null
    private lateinit var filter: LandmarkFilter
    private lateinit var fatigue: FatigueEstimator
    private val asymmetryTracker = AsymmetryTracker()
    private val bossConfig: BossConfig = combatCfg.boss.let { b ->
        BossConfig(b.id, b.name, b.maxHp, b.phases.map { BossPhase(it.fromHpPct, it.modifier, it.label) })
    }
    private val combat: CombatEngine = buildCombat()
    private var game: FamilyGame? = null
    private var ghost: GhostSource? = null
    private var ghostData: GhostData? = null

    private var romBaselineU: Float? = null

    /** The landmarks at the deepest point of the rep in progress, and how deep that was (in u-space). */
    private var deepestLms: Landmarks? = null
    private var deepestU: Float? = null
    private var deepestSide: Side = Side.LEFT
    /**
     * Frame time at the deepest point, alongside the landmarks from it. -1 until the first descent.
     *
     * The referee's eyes want the camera frame from this moment: a rep completes on the way back
     * up, so by then the picture worth looking at is a second old. The landmarks here were already
     * being kept for alignment; this is the timestamp of the same frame.
     */
    private var deepestTMs: Long = -1L

    // Per-arm deepest tracking for sidesEach=true (when arms move independently)
    private var deepestLmsLeft: Landmarks? = null
    private var deepestULeft: Float? = null
    private var deepestGateLeft = Float.NaN
    private var deepestTMsLeft: Long = -1L
    private var deepestLmsRight: Landmarks? = null
    private var deepestURight: Float? = null
    private var deepestGateRight = Float.NaN
    private var deepestTMsRight: Long = -1L

    private val topRefSamples = ArrayList<Float>(128)
    private var topRef: Float? = null

    // Rep detection options from the exercise's detector block. docs/19-EXERCISE-LIBRARY.md
    private var stageCounter: StageCounter? = null              // fitmon's counter, for the exercises configured for it

    /**
     * A place to watch the rep counter decide, one line per frame. Null in tests; the app points it
     * at logcat on debug builds, which is how a threshold gets checked against a real shoulder
     * instead of against a drawing.
     */
    var diagnostics: ((String) -> Unit)? = null
        set(value) { field = value; stageCounter?.diagnostics = value }
    private var repCfg: RepDetectorConfig? = null               // kept for form scoring under stage counting
    private var lastLeftDeg = Float.NaN
    private var lastRightDeg = Float.NaN
    private var sidesEach = false                              // one machine per arm, merged when they move together
    private var dirSign = 1f                                   // +1 the movement lowers the angle (squat); -1 it raises it (raises, calf raise)
    private var gateAngleName: Triple<String, String, String>? = null
    private var gateMinDeg = Float.NEGATIVE_INFINITY
    private var gateMaxDeg = Float.POSITIVE_INFINITY
    private var gateAtEnd = false                              // END: judged at the deepest point; otherwise every frame
    private var mergeWindowMs = 500L
    private var lastLeftRepEndMs: Long? = null
    private var lastRightRepEndMs: Long? = null
    private var nextRepIndex = 1
    private var deepestGate = Float.NaN                        // the gate angle at the deepest point
    private var gateFailedAtMs: Long? = null                   // a rep refused by an END gate, for the cue

    private var phase = Phase.CALIBRATING
    private var invalidFrames = 0
    private var angle = Float.NaN
    private var side: Side = Side.LEFT
    private var framing = Framing.OK
    private var calib = CalibState.SEARCHING
    private var calibHoldMs = 0L
    private var calibSince: Long? = null
    private var missing: List<String> = emptyList()

    private var fightStartMs: Long? = null
    private var lastTMs = 0L
    private var ended = false
    private var endReason: EndReason? = null
    private var playerDamage = 0
    private var ghostDamage = 0
    private val spanSamples = ArrayDeque<Pair<Long, Float>>()
    private var wave = 1
    private var rushIndex = 0
    private var formThresholdBonus = 0f
    private var setIndex = 1
    private val allReps = ArrayList<RepRecord>()
    private val setReps = ArrayList<RepRecord>()
    private var lastRepEndMs: Long? = null
    private var lastRep: RepRecord? = null
    private var combatBand: FatigueBand = FatigueBand.FRESH
    private var telemetry: SetTelemetry? = null
    private var coach: CoachOutput? = null

    val reps: List<RepRecord> get() = allReps
    val currentSetReps: List<RepRecord> get() = setReps

    init {
        setExercise(exerciseId)
        reset()
    }

    // ------------------------------------------------------------------ construction

    private fun buildCombat(): CombatEngine {
        val c = combatCfg
        val responses = c.fatigueResponse.entries.mapNotNull { (k, v) ->
            runCatching { FatigueBand.valueOf(k) }.getOrNull()?.let { band ->
                band to FatigueResponse(v.modifier, v.regenPerRep, v.staggerReps, v.mercyRepsToFinish)
            }
        }.toMap()
        val combo = ComboTracker(ComboConfig(c.combo.step, c.combo.cap, c.combo.threshold, c.combo.graceAtStreak))
        val playerMaxHpDefault = c.bossAttack?.playerMaxHp ?: Int.MAX_VALUE / 2
        val engine = CombatEngine(
            baseDamage = c.baseDamage, formFloor = c.formFloor, formExponent = c.formExponent,
            boss = bossConfig, responses = responses, combo = combo, casual = casual,
            casualConfig = CasualConfig(c.casual.damageMultiplier, c.casual.formFloor, c.casual.bossHpMultiplier),
            playerMaxHpDefault = playerMaxHpDefault,
        )
        engine.playerMaxHp = playerMaxHpDefault
        engine.playerHp = playerMaxHpDefault
        return engine
    }

    private fun buildFatigue(): FatigueEstimator {
        val f = poseCfg.fatigue
        return FatigueEstimator(
            FatigueConfig(
                baselineReps = f.baselineReps, ema = f.ema,
                working = f.bands.working, fading = f.bands.fading, gassed = f.bands.gassed,
                bandLatchReps = f.bandLatchReps, pauseGrowthNormSec = f.pauseGrowthNormSec,
            ),
            SignalTables.forFamily(family.name),
        )
    }

    // ------------------------------------------------------------------ setup

    fun setMode(next: GameMode) { mode = next; reset() }

    /** A ghost is just a recorded rep timeline. It rides the duel's own code path. */
    fun loadGhost(data: GhostData) {
        ghostData = data
        ghost = GhostSource(data)
        mode = GameMode.GHOST_RACE
        reset()
    }

    fun setExercise(id: String) {
        exercise = exercises[id] ?: error("unknown exercise: $id")
        family = exercise.familyEnum
        detector = if (family == Family.REP_CYCLE) null else DetectorFactory.create(exercise)
        val det = exercise.detector
        require(det != null) { "exercise $id missing detector configuration" }
        jointNames = det["requiredJoints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: defaultJoints(exercise)
        primaryAngle = det["primaryAngle"]?.jsonObject?.let {
            Triple(it.getValue("a").jsonPrimitive.content, it.getValue("b").jsonPrimitive.content, it.getValue("c").jsonPrimitive.content)
        }

        if (family == Family.REP_CYCLE) {
            val cfg = repConfig(det)
            repCfg = cfg
            // fitmon's stage counter, where the exercise asks for it: a rep is the move from the
            // rest position past the counting threshold, debounced, with no dwell or duration gate.
            stageCounter = StageCounter.Config.from(det)?.let { c ->
                // Named, so a capture can never be read as the wrong exercise's numbers.
                diagnostics?.invoke("counter armed for ${exercise.id}: $c")
                StageCounter(c).apply { diagnostics = this@SessionEngine.diagnostics }
            }
            dirSign = if (cfg.topEnter > cfg.bottomEnter) 1f else -1f
            sidesEach = det["sides"]?.jsonPrimitive?.content == "EACH"
            fsm = if (sidesEach) null else RepStateMachine(cfg)
            fsmLeft = if (sidesEach) RepStateMachine(cfg) else null
            fsmRight = if (sidesEach) RepStateMachine(cfg) else null
            val gate = det["gate"]?.jsonObject
            val names = gate?.get("angle")?.jsonArray?.map { it.jsonPrimitive.content }
            gateAngleName = if (names != null && names.size >= 3) Triple(names[0], names[1], names[2]) else null
            gateMinDeg = gate?.get("min")?.jsonPrimitive?.floatOrNull ?: Float.NEGATIVE_INFINITY
            gateMaxDeg = gate?.get("max")?.jsonPrimitive?.floatOrNull ?: Float.POSITIVE_INFINITY
            gateAtEnd = gate?.get("at")?.jsonPrimitive?.content == "END"
            mergeWindowMs = det["mergeWindowMs"]?.jsonPrimitive?.longOrNull ?: 500L
        } else {
            fsm = null; fsmLeft = null; fsmRight = null
            stageCounter = null; repCfg = null
            sidesEach = false; gateAngleName = null
        }
        filter = LandmarkFilter(poseCfg.filter.minCutoff, poseCfg.filter.beta, poseCfg.filter.dCutoff)
        fatigue = buildFatigue()
        romBaselineU = null
        topRefSamples.clear()
    }

    private fun repConfig(d: JsonObject): RepDetectorConfig = RepDetectorConfig(
        topEnter = d.getValue("topEnter").jsonPrimitive.float,
        topExit = d.getValue("topExit").jsonPrimitive.float,
        bottomEnter = d.getValue("bottomEnter").jsonPrimitive.float,
        bottomExit = d.getValue("bottomExit").jsonPrimitive.float,
        targetAngle = d.getValue("targetAngle").jsonPrimitive.float,
        minDescendMs = d["minDescendMs"]?.jsonPrimitive?.long ?: 200,
        minBottomMs = d["minBottomMs"]?.jsonPrimitive?.long ?: 120,
        minRepMs = d["minRepMs"]?.jsonPrimitive?.long ?: 800,
        maxRepMs = d["maxRepMs"]?.jsonPrimitive?.long ?: 8000,
    )

    /** Families other than REP_CYCLE declare their joints implicitly, through their targets. */
    private fun defaultJoints(spec: ExerciseSpec): List<String> {
        val d = spec.detector
        val set = LinkedHashSet<String>()
        val refs = d["targets"] ?: d["reference"]
        refs?.jsonArray?.forEach { t -> t.jsonObject["angle"]?.jsonArray?.forEach { set += it.jsonPrimitive.content } }
        d["signalJoint"]?.jsonPrimitive?.content?.let { set += it; set += "HIP" }
        if (spec.familyEnum == Family.BALLISTIC) set += listOf("HIP", "KNEE", "ANKLE")
        return set.toList()
    }

    /** The game a family's movement is naturally shaped for. */
    val familyGame: GameMode get() = GameMode.familyGameFor(family)

    fun reset() {
        phase = Phase.CALIBRATING
        invalidFrames = 0
        angle = Float.NaN
        lastRep = null
        allReps.clear()
        fsm?.reset()
        fsmLeft?.reset()
        fsmRight?.reset()
        stageCounter?.reset()
        detector?.reset()
        fatigue.reset()
        combat.reset(bossConfig)
        combat.resetPlayer()
        lastLeftRepEndMs = null; lastRightRepEndMs = null; nextRepIndex = 1
        deepestU = null; deepestLms = null; deepestGate = Float.NaN; gateFailedAtMs = null
        deepestULeft = null; deepestLmsLeft = null; deepestGateLeft = Float.NaN
        deepestURight = null; deepestLmsRight = null; deepestGateRight = Float.NaN
        romBaselineU = null
        topRefSamples.clear()
        filter.reset()

        calib = CalibState.SEARCHING
        calibHoldMs = 0
        calibSince = null
        missing = emptyList()
        fightStartMs = null
        nextAttackAtMs = null
        ended = false
        endReason = null
        playerDamage = 0
        ghostDamage = 0
        spanSamples.clear()
        wave = 1
        rushIndex = 0
        formThresholdBonus = 0f
        setIndex = 1
        setReps.clear()
        lastRepEndMs = null
        coach = null
        telemetry = null
        combatBand = FatigueBand.FRESH
        ghost?.reset()

        // Attacks enabled for certain modes when not timed and no family game is active.
        val spec = combatCfg.bossAttack
        attacksEnabled = spec != null && mode.name in spec.modes && !timed && game == null

        // Modes scored on a clock must not have the boss die out from under them.
        if (timed) { combat.maxHp = 10_000_000; combat.hp = 10_000_000 }
        // Family games own their own state. SIGIL has no boss, no damage and no ranking.
        game = FamilyGames.create(mode.name, combatCfg)
        if (mode == GameMode.SURVIVAL) {
            val hp = combatCfg.modes.survival.hpPerWave
            combat.maxHp = hp; combat.hp = hp
            combat.mercyDisabled = combatCfg.modes.survival.mercyDisabled
        }
    }

    // ------------------------------------------------------------------ time

    val durationMs: Long
        get() = when {
            durationOverrideSec != null -> durationOverrideSec * 1000L
            mode == GameMode.CLINIC_STS -> (clinic?.durationSec ?: 30) * 1000L
            else -> combatCfg.modes.timeAttack.durationSec * 1000L
        }

    val timeLeftMs: Long?
        get() {
            val start = fightStartMs
            if (!timed || start == null) return null
            return max(0L, durationMs - (lastTMs - start))
        }

    val tempoTarget: Float get() = combatCfg.modes.tempoTrial.targetEccSec

    // ------------------------------------------------------------------ frame

    /**
     * @param world world landmarks (metric, hip-centred) or null when nothing was detected
     * @param image normalised image landmarks, framing only
     */
    fun frame(world: Landmarks?, image: Landmarks?, tMs: Long): SessionState {
        // A long gap in frames (the app paused, the camera restarted) is time the boss's clock must
        // not charge for: the grace period starts again from the first frame back.
        if (attacksEnabled && phase == Phase.FIGHTING && lastTMs > 0L && tMs - lastTMs > FRAME_GAP_MS) {
            combatCfg.bossAttack?.let { nextAttackAtMs = tMs + (it.graceSec * 1000f).toLong() }
        }
        lastTMs = tMs
        if (world.isNullOrEmpty()) return lost()

        val lms = filter.apply(world, tMs)
        val thr = poseCfg.visibilityThreshold
        val pa = primaryAngle
        val valid: Boolean
        var leftAngle = Float.NaN
        var rightAngle = Float.NaN
        var bothSides = false
        if (pa != null) {
            val result = Geometry.primaryAngle(lms, pa.first, pa.second, pa.third, jointNames, thr)
            angle = result.angle; side = result.side; valid = result.valid
            leftAngle = result.left
            rightAngle = result.right
            bothSides = result.both
            lastLeftDeg = result.left
            lastRightDeg = result.right
            if (phase == Phase.FIGHTING) {
                asymmetryTracker.onFrame(result.left, result.right, tMs)
            }
        } else {
            val sel = Geometry.chooseSide(lms, jointNames, thr)
            angle = Float.NaN; side = sel.side; valid = sel.valid
        }
        framing = image?.let { framingOf(it) } ?: Framing.OK

        if (!valid) return lost()

        if (phase == Phase.FRAMING_LOST) {
            phase = Phase.FIGHTING
            fatigue.unfreeze()
            // Re-arm attacks 1500ms after FRAMING_LOST clears
            if (attacksEnabled && combatCfg.bossAttack != null) {
                nextAttackAtMs = tMs + 1500L
            }
        }
        invalidFrames = 0

        if (phase == Phase.CALIBRATING) return calibrate(lms, tMs)

        val g = game
        if (g is PursuitGame && phase == Phase.FIGHTING) {
            val gs = g.tick(tMs)
            if (gs.outcome != GameOutcome.RUNNING) { phase = Phase.DEAD; end(EndReason.GAME_LOST) }
        }

        if (pa != null && phase == Phase.FIGHTING) {
            val span = Geometry.spanMetres(lms, pa.first, pa.third, side)
            if (!span.isNaN()) {
                spanSamples.addLast(tMs to span)
                if (spanSamples.size > 900) spanSamples.removeFirst()     // ~30s at 30fps
            }
        }

        if (!ended) {
            // A ghost's damage enters through the same idempotent path a remote player's does.
            val gh = ghost
            if (mode == GameMode.GHOST_RACE && gh != null) {
                for (e in gh.due(tMs)) {
                    combat.onRemoteDamage("GHOST", e.seq, e.damage)
                    ghostDamage += e.damage
                }
            }
            if (timed && timeLeftMs == 0L) end(EndReason.TIME)
        }
        if (ended) return state()

        tickBossAttack(tMs)
        if (ended) return state()

        if (family == Family.REP_CYCLE) {
            if (phase == Phase.FIGHTING) {
                // The gate follows the primary angle's side selection. An ALWAYS gate out of range
                // hides the frame from the machine; an END gate is judged at the deepest point, in
                // completeRep.
                val gate = gateAngleName?.let { gateAngle(lms, it, side, bothSides) } ?: Float.NaN
                val gateOk = gate.isNaN() || (gate >= gateMinDeg && gate <= gateMaxDeg)
                val blocked = gateAngleName != null && !gateAtEnd && !gateOk

                val sc = stageCounter
                if (sc != null) {
                    // fitmon reads raw landmarks, so the counter does too: no filter between the
                    // model and the threshold, which is what makes its counts feel immediate.
                    for (r in sc.onFrame(world, tMs, side)) completeStageRep(r, lms)
                    sc.angles[Side.LEFT]?.let { lastLeftDeg = it }
                    sc.angles[Side.RIGHT]?.let { lastRightDeg = it }
                    return state()
                }
                // A blocked frame is skipped, not fed as invalid: the machine never sees a curl at
                // the sides as a triceps extension, and a gate that flickers for a few frames of a
                // real overhead extension cannot sink that rep's valid-frame ratio. Per-arm machines
                // each keep their own deepest frame, so one arm finishing never loses the other's.
                if (sidesEach) {
                    val gateLeft = gateAngleName?.let { gateAngle(lms, it, Side.LEFT, bothSides) } ?: Float.NaN
                    val gateRight = gateAngleName?.let { gateAngle(lms, it, Side.RIGHT, bothSides) } ?: Float.NaN
                    val leftOk = gateLeft.isNaN() || (gateLeft >= gateMinDeg && gateLeft <= gateMaxDeg)
                    val rightOk = gateRight.isNaN() || (gateRight >= gateMinDeg && gateRight <= gateMaxDeg)
                    if (gateAtEnd || leftOk) {
                        trackDeepest(leftAngle, lms, Side.LEFT, gateLeft, tMs, Side.LEFT)
                        fsmLeft?.onFrame(leftAngle, tMs)?.let { creditSide(it, Side.LEFT, tMs, lms) }
                    }
                    if (gateAtEnd || rightOk) {
                        trackDeepest(rightAngle, lms, Side.RIGHT, gateRight, tMs, Side.RIGHT)
                        fsmRight?.onFrame(rightAngle, tMs)?.let { creditSide(it, Side.RIGHT, tMs, lms) }
                    }
                } else if (!blocked) {
                    trackDeepest(angle, lms, side, gate, tMs)
                    fsm?.onFrame(angle, tMs)?.let { completeRep(it, lms) }
                }
            }
        } else {
            val det = detector
            if (det != null && phase == Phase.FIGHTING) {
                // BALLISTIC receives both world and image landmarks; other families receive only world.
                // This matches the JavaScript implementation and allows detectors to handle null image gracefully.
                val detImage = if (family == Family.BALLISTIC) image else null
                det.onFrame(lms, detImage, tMs, side)?.let { completeEvent(it) }
            }
        }
        return state()
    }

    /**
     * The boss hits back on a clock, only while a set is live. Fatigue mercy holds the attacks: a
     * GASSED player, or one the combat engine is already going easy on, is not hit. The clock is
     * armed with a grace period when the fight starts and when a set resumes. docs/04-GAME-DESIGN.md
     */
    private fun tickBossAttack(tMs: Long) {
        val spec = combatCfg.bossAttack ?: return
        if (!attacksEnabled || phase != Phase.FIGHTING || ended) return
        val at = nextAttackAtMs ?: return
        if (tMs < at) return
        nextAttackAtMs = tMs + (spec.everySec * 1000f).toLong()
        if (fatigue.state().band == FatigueBand.GASSED || combat.mercyActive) return
        val damage = combat.bossAttack(spec.damage)
        listener.onPlayerHit(damage, combat.playerHp)
        if (combat.playerDead) { phase = Phase.DEAD; end(EndReason.DEFEATED) }
    }

    /** The gate angle in the primary angle's side selection: the mean when both sides are visible. */
    private fun gateAngle(lms: Landmarks, names: Triple<String, String, String>, s: Side, bothVisible: Boolean): Float {
        fun one(sd: Side): Float {
            val ai = Geometry.idx(names.first, sd); val bi = Geometry.idx(names.second, sd); val ci = Geometry.idx(names.third, sd)
            if (ai < 0 || bi < 0 || ci < 0 || ai >= lms.size || bi >= lms.size || ci >= lms.size) return Float.NaN
            return Geometry.angle3(lms[ai], lms[bi], lms[ci])
        }
        if (!bothVisible) return one(s)
        val l = one(Side.LEFT); val r = one(Side.RIGHT)
        return if (l.isNaN() || r.isNaN()) Float.NaN else (l + r) / 2f
    }

    /**
     * Remember the frame at the deepest point of the rep in progress. A rep completes when you come
     * back up, so the landmarks at that moment are of someone standing; the deepest point is where
     * alignment holds or fails and where an END gate is judged. Deepest is the minimum of
     * u = dirSign * angle, so it is right both for exercises that lower the angle (squat) and for
     * those that raise it (calf raise, lateral raise).
     *
     * When sidesEach=true (per-arm machines), tracks the deepest point separately for each arm,
     * so completeRep can use the correct arm's deepest frame.
     */
    private fun trackDeepest(a: Float, lms: Landmarks, s: Side, gate: Float, tMs: Long, side: Side = s) {
        if (a.isNaN()) return
        val u = dirSign * a

        // Per-arm tracking when sidesEach=true
        if (sidesEach) {
            if (side == Side.LEFT) {
                val d = deepestULeft
                if (d == null || u < d) { deepestULeft = u; deepestLmsLeft = lms; deepestGateLeft = gate; deepestTMsLeft = tMs }
            } else {
                val d = deepestURight
                if (d == null || u < d) { deepestURight = u; deepestLmsRight = lms; deepestGateRight = gate; deepestTMsRight = tMs }
            }
        } else {
            // Shared tracking for non-per-arm machines
            val d = deepestU
            if (d == null || u < d) { deepestU = u; deepestLms = lms; deepestSide = s; deepestGate = gate; deepestTMs = tMs }
        }
    }

    /**
     * Per-arm counting. A rep is credited the moment its arm completes it. If the other arm completed
     * one inside the merge window the arms moved together and this is the same rep; alternating
     * curls are always further apart than the window, so each arm's curl counts.
     */
    private fun creditSide(raw: RepEvent, s: Side, tMs: Long, lms: Landmarks) {
        val otherEnd = if (s == Side.LEFT) lastRightRepEndMs else lastLeftRepEndMs
        if (s == Side.LEFT) lastLeftRepEndMs = tMs else lastRightRepEndMs = tMs
        if (otherEnd != null && tMs - otherEnd <= mergeWindowMs) return
        completeRep(raw.copy(repIndex = nextRepIndex++), lms, s)
    }

    /** Depth travel for one rep, in centimetres, from the metric span samples. */
    private fun depthCm(raw: RepEvent): Float {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var n = 0
        for ((t, v) in spanSamples) {
            if (t < raw.tStartMs || t > raw.tEndMs) continue
            if (v < lo) lo = v
            if (v > hi) hi = v
            n++
        }
        return if (n < 3) Float.NaN else (hi - lo) * 100f
    }

    // ------------------------------------------------------------------ sets

    /**
     * Ends the current set and rolls straight into the next one. There is no rest: a set ends only
     * because the player asked for it, and the fight never leaves the screen. The listener is called
     * before the reset so it can still read [currentSetReps]; it fetches the coach, which is spoken
     * over the top of the set that has already started.
     */
    fun endSet() {
        if (phase != Phase.FIGHTING || setReps.isEmpty()) return
        val t = TelemetrySummariser.summarise(setReps, combat.state(), exercise.id, setIndex)
        telemetry = t
        coach = null
        listener.onSetEnd(t)
        nextSet()
    }

    fun setCoach(output: CoachOutput) { coach = output }

    /** Begin the next set. Fatigue baselines reset; boss HP and combo carry. */
    fun nextSet() {
        if (phase != Phase.FIGHTING) return
        setIndex += 1
        setReps.clear()
        lastRepEndMs = null
        coach = null
        telemetry = null
        fatigue.reset()
        fsm?.reset()
        fsmLeft?.reset()
        fsmRight?.reset()
        stageCounter?.reset()
        topRef?.let { ref ->
            fsm?.setTopRef(ref)
            fsmLeft?.setTopRef(ref)
            fsmRight?.setTopRef(ref)
        }
        detector?.reset()
        asymmetryTracker.reset()
        deepestLms = null; deepestU = null; deepestGate = Float.NaN; gateFailedAtMs = null; deepestTMs = -1L
        deepestULeft = null; deepestLmsLeft = null; deepestGateLeft = Float.NaN; deepestTMsLeft = -1L
        deepestURight = null; deepestLmsRight = null; deepestGateRight = Float.NaN; deepestTMsRight = -1L
        lastLeftRepEndMs = null; lastRightRepEndMs = null
        nextRepIndex = 1
        // The filter too. Everything else that carries state across a rep was reset above, and
        // leaving this one holding the previous set's last sample and timestamp means the first
        // frame after a rest is blended with wherever you were standing when the last set ended.
        filter.reset()
        phase = Phase.FIGHTING
        // Re-arm attacks at the same grace period after set resume (use lastTMs as the resume time)
        if (attacksEnabled && combatCfg.bossAttack != null) {
            nextAttackAtMs = lastTMs + (combatCfg.bossAttack.graceSec * 1000).toLong()
        }
    }

    /** Recover fatigue from a completed breathing session. Bounded; never rewrites baselines. */
    fun recoverFatigue(fraction: Float): FatigueState {
        fatigue.recover(fraction)
        val st = fatigue.state()
        combatBand = st.band
        return st
    }

    /** The player stopped. There is no fail state for being tired. */
    fun stop() { if (!ended) end(EndReason.STOPPED) }

    /**
     * Damage from another phone (duel, raid) or a ghost. Idempotent by (playerId, seq), so
     * order and duplicates are irrelevant by construction. docs/07-MULTIPLAYER-SPEC.md §1
     */
    fun applyRemoteDamage(playerId: String, seq: Int, damage: Int) {
        if (ended || phase == Phase.CALIBRATING) return
        combat.onRemoteDamage(playerId, seq, damage)
        if (game == null && combat.dead) onBossDown()
    }

    /** The boss as the combat engine sees it right now. */
    fun combatState(): CombatState = combat.state()

    private fun end(reason: EndReason) {
        if (ended) return
        ended = true
        endReason = reason
        listener.onEnd(reason, state())
    }

    // ------------------------------------------------------------------ reps

    /**
     * Tempo Trial: damage scales with how closely the eccentric matches the target, in
     * MEASURED-WINDOW terms — the same units form.tempo uses. docs/17-GAME-MODES.md §5
     */
    fun tempoAccuracy(raw: RepEvent): Float {
        val c = combatCfg.modes.tempoTrial
        return (1f - abs(raw.tEccSec - c.targetEccSec) / max(1e-6f, c.tolerance)).coerceIn(0f, 1f)
    }

    /** Four named quantities against this exercise's weights and targets; the weakest names the fault. */
    private fun scoreRep(raw: RepEvent, alignment: Float): FormScore {
        val f = exercise.form ?: ExerciseSpec.FormSpec()
        val w = f.weights
        val d = FormScorer.depth(raw, f.depthExponent)
        val r = FormScorer.rom(raw, romBaselineU ?: 0f)
        val t = FormScorer.tempo(raw, f.tempo.eccentricTargetSec, f.tempo.bottomPauseSec)
        val a = if (w.alignment > 0f) alignment else 1f
        val sum = (w.depth + w.rom + w.tempo + w.alignment).takeIf { it > 0f } ?: 1f
        val total = ((w.depth * d + w.rom * r + w.tempo * t + w.alignment * a) / sum).coerceIn(0f, 1f)
        val parts = mutableListOf("depth" to d, "rom" to r, "tempo" to t)
        if (w.alignment > 0f) parts += "alignment" to a
        val reason = parts.minByOrNull { it.second }?.first ?: "depth"
        return FormScore(d, r, t, a, total, reason)
    }

    /**
     * A rep the stage counter found, scored by the same form model as every other rep. The counter
     * measures no pause at the bottom, so the tempo score is given the target pause and grades the
     * eccentric alone rather than punishing something nobody measured.
     */
    private fun completeStageRep(r: StageCounter.Rep, lms: Landmarks) {
        val cfg = repCfg ?: return
        val lo = minOf(dirSign * r.thetaMin, dirSign * r.thetaMax)
        val hi = maxOf(dirSign * r.thetaMin, dirSign * r.thetaMax)
        val ecc = ((r.tDeepestMs - r.tStartMs).coerceAtLeast(0L)) / 1000f
        val con = ((r.tEndMs - r.tDeepestMs).coerceAtLeast(0L)) / 1000f
        val pause = exercise.form?.tempo?.bottomPauseSec ?: 0.12f
        completeRep(
            RepEvent(
                repIndex = nextRepIndex++,
                exerciseId = exercise.id,
                tStartMs = r.tStartMs,
                tEndMs = r.tEndMs,
                thetaMin = minOf(r.thetaMin, r.thetaMax),
                thetaMax = maxOf(r.thetaMin, r.thetaMax),
                uMin = lo,
                uMax = hi,
                uTopRef = dirSign * cfg.topEnter,
                uTarget = dirSign * cfg.targetAngle,
                tEccSec = ecc,
                tBottomSec = pause,
                tConSec = con,
                concentricVelocity = if (con > 0f) abs(hi - lo) / con else 0f,
                gapSec = lastRepEndMs?.let { (r.tStartMs - it) / 1000f } ?: 0f,
                validFrameRatio = 1f,
            ),
            lms,
            r.side ?: side,
        )
    }

    private fun completeRep(raw: RepEvent, lms: Landmarks, repSide: Side = side) {
        // The bottom of this rep, falling back to the current frame if nothing was recorded.
        // When sidesEach=true, use the completing side's deepest data to ensure alignment
        // is scored at that arm's deepest point, not at a shared deepest point.
        val (alignAt, alignSide, gateAtDeepest) = if (sidesEach && repSide == Side.LEFT) {
            Triple(deepestLmsLeft ?: lms, Side.LEFT, deepestGateLeft)
        } else if (sidesEach && repSide == Side.RIGHT) {
            Triple(deepestLmsRight ?: lms, Side.RIGHT, deepestGateRight)
        } else {
            Triple(deepestLms ?: lms, if (deepestLms != null) deepestSide else repSide, deepestGate)
        }
        // The frame time from the same branch, so a per-arm rep points at that arm's own moment.
        val deepestAt = when {
            sidesEach && repSide == Side.LEFT -> deepestTMsLeft
            sidesEach && repSide == Side.RIGHT -> deepestTMsRight
            else -> deepestTMs
        }

        // Clear the deepest state for this side
        if (sidesEach && repSide == Side.LEFT) {
            deepestLmsLeft = null; deepestULeft = null; deepestGateLeft = Float.NaN; deepestTMsLeft = -1L
        } else if (sidesEach && repSide == Side.RIGHT) {
            deepestLmsRight = null; deepestURight = null; deepestGateRight = Float.NaN; deepestTMsRight = -1L
        } else {
            deepestLms = null; deepestU = null; deepestGate = Float.NaN; deepestTMs = -1L
        }
        // An END gate refuses a rep whose deepest point was in the wrong place: a shoulder press
        // pushed out in front instead of overhead. The cue says so for a moment.
        if (gateAtEnd && gateAngleName != null && !gateAtDeepest.isNaN() &&
            (gateAtDeepest < gateMinDeg || gateAtDeepest > gateMaxDeg)) {
            gateFailedAtMs = raw.tEndMs
            return
        }
        // First completed rep sets the ROM baseline — every score is relative to this player.
        if (romBaselineU == null) romBaselineU = raw.uMax - raw.uMin
        val align = alignmentSample(alignAt, alignSide, exercise.form?.alignment?.type, exercise.form?.alignment)

        var score = scoreRep(raw, align)
        if (mode == GameMode.TEMPO_TRIAL) {
            val c = combatCfg.modes.tempoTrial
            val acc = tempoAccuracy(raw)
            score = score.copy(
                tempoAccuracy = acc,
                formScore = c.floor + (1f - c.floor) * acc,
                reason = if (acc < 0.6f) "tempo" else score.reason,
            )
        }
        if (formThresholdBonus > 0f) {
            combat.combo.config = combat.combo.config.copy(threshold = min(0.95f, combatCfg.combo.threshold + formThresholdBonus))
        }
        val f = fatigue.onRep(raw)
        val prevBand = combatBand
        combatBand = f.band
        val dmg = combat.onRep(score.formScore, f.band)
        val c = combat.state()
        if (f.band != prevBand) { combat.onFatigueBand(f.band); listener.onBand(f.band) }

        val asymmetry = asymmetryTracker.forRep(raw.tStartMs, raw.tEndMs)
        val rec = RepRecord(
            repIndex = raw.repIndex, exerciseId = exercise.id, family = family,
            tStartMs = raw.tStartMs, tEndMs = raw.tEndMs,
            formScore = score.formScore, depth = score.depth, rom = score.rom, tempo = score.tempo, alignment = score.alignment,
            reason = score.reason, verdict = score.verdict(),
            concentricVelocity = raw.concentricVelocity, fatigue = f,
            damage = dmg, combo = c.comboMultiplier,
            validFrameRatio = raw.validFrameRatio, depthCm = depthCm(raw),
            asymmetry = asymmetry,
            tDeepestMs = deepestAt,
        )
        record(rec, c)
    }

    /** Feed a family game the event shape it understands. Null when the event is the wrong family. */
    private fun feedGame(g: FamilyGame, ev: MovementEvent): FamilyGameState? = when (g) {
        is SiegeGame -> (ev as? HoldEvent)?.let { g.onEvent(SiegeEvent(it.holdSec, it.quality, it.completed)) }
        is PursuitGame -> (ev as? CadenceEvent)?.let { g.onEvent(PursuitEvent(it.amplitude, it.formScore)) }
        is BreakerGame -> (ev as? JumpEvent)?.let { g.onEvent(BreakerEvent(it.heightCm, it.softness)) }
        is SigilGame -> (ev as? PoseEvent)?.let { g.onEvent(SigilEvent(it.accuracy, it.heldSec)) }
        else -> null
    }

    /** Non-REP_CYCLE families land here. Same downstream contract, same combat engine. */
    private fun completeEvent(ev: MovementEvent) {
        val f = fatigue.onSignals(ev.signals)
        val prevBand = combatBand
        combatBand = f.band

        val g = game
        val dmg: Int
        val c: CombatState
        if (g != null) {
            val gs = feedGame(g, ev) ?: g.state()
            // SIGIL is non-combat by design; the others still surface a damage-shaped number for
            // the HUD, but the mechanic that decides the outcome lives in the family game.
            if (mode == GameMode.SIGIL) { dmg = 0; c = combat.state().copy(lastDamage = 0, comboMultiplier = 1f) }
            else { dmg = combat.onRep(ev.formScore, f.band); c = combat.state() }
            if (gs.outcome != GameOutcome.RUNNING) {
                phase = Phase.DEAD
                end(if (gs.outcome == GameOutcome.WON) EndReason.GAME_WON else EndReason.GAME_LOST)
            }
        } else {
            dmg = combat.onRep(ev.formScore, f.band); c = combat.state()
        }
        if (f.band != prevBand) { if (g == null) combat.onFatigueBand(f.band); listener.onBand(f.band) }

        val verdict = FormScore(ev.formScore, ev.formScore, ev.formScore, ev.formScore, ev.formScore, "form").verdict()
        val rec = RepRecord(
            repIndex = allReps.size + 1, exerciseId = exercise.id, family = family,
            tStartMs = ev.tStartMs, tEndMs = ev.tEndMs,
            formScore = ev.formScore, depth = ev.formScore, rom = ev.formScore, tempo = ev.formScore, alignment = ev.formScore,
            reason = "form", verdict = verdict,
            concentricVelocity = 0f, fatigue = f,
            damage = dmg, combo = c.comboMultiplier,
            heightCm = (ev as? JumpEvent)?.heightCm ?: Float.NaN,
            holdSec = (ev as? HoldEvent)?.holdSec ?: Float.NaN,
            accuracy = (ev as? PoseEvent)?.accuracy ?: Float.NaN,
        )
        record(rec, c, gameOwned = g != null)
    }

    private fun record(rec: RepRecord, c: CombatState, gameOwned: Boolean = false) {
        allReps += rec
        setReps += rec
        lastRepEndMs = rec.tEndMs
        lastRep = rec
        playerDamage += rec.damage
        // Heal player on each counted rep if attacks are enabled
        if (attacksEnabled && combatCfg.bossAttack != null) {
            combat.healPlayer(combatCfg.bossAttack.healPerRep)
        }
        if (!gameOwned && combat.dead) onBossDown()
        listener.onRep(rec, combat.state())
    }

    /**
     * Survival keeps going with a harder boss; Boss Rush advances the sequence; everything else
     * ends. Survival deliberately disables the mercy rule — the one mode where fatigue ends the run.
     */
    private fun onBossDown() {
        game = FamilyGames.create(mode.name, combatCfg)
        if (mode == GameMode.SURVIVAL) {
            val w = combatCfg.modes.survival
            wave += 1
            formThresholdBonus += w.formThresholdStep
            val hp = (w.hpPerWave * (1f + 0.35f * (wave - 1))).roundToInt()
            combat.reset(bossConfig.copy(maxHp = hp))
            combat.mercyDisabled = w.mercyDisabled
            return
        }
        if (mode == GameMode.BOSS_RUSH) {
            val seq = combatCfg.modes.bossRush.sequence
            rushIndex += 1
            if (rushIndex < seq.size) {
                val id = seq[rushIndex]
                combat.reset(bossConfig.copy(
                    id = id, name = id.replace('_', ' ').uppercase(),
                    maxHp = (combatCfg.boss.maxHp * (1f + 0.2f * rushIndex)).roundToInt(),
                ))
                return
            }
        }
        phase = Phase.DEAD
        end(EndReason.BOSS_DOWN)
    }

    // ------------------------------------------------------------------ calibration

    /**
     * Framing is gated, not hoped for: the fight does not start until every required joint has
     * been visible for a continuous window. Holding requires VISIBILITY, not stillness.
     */
    private fun calibrate(lms: Landmarks, tMs: Long): SessionState {
        val thr = poseCfg.visibilityThreshold
        val f = poseCfg.framing
        missing = Geometry.missingJoints(lms, jointNames, thr, side)

        val next = when {
            missing.size == jointNames.size -> CalibState.SEARCHING
            missing.isNotEmpty() -> CalibState.PARTIAL
            framing == Framing.TOO_FAR -> CalibState.TOO_FAR
            framing == Framing.TOO_CLOSE -> CalibState.TOO_CLOSE
            else -> CalibState.HOLDING
        }
        if (next != CalibState.HOLDING) {
            calib = next; calibSince = null; calibHoldMs = 0; topRefSamples.clear()
            return state()
        }

        val since = calibSince ?: tMs.also { calibSince = it }
        calib = CalibState.HOLDING
        calibHoldMs = tMs - since

        if (fsm != null) {
            topRefSamples += angle
            if (topRefSamples.size > 120) topRefSamples.removeAt(0)
        }

        if (calibHoldMs >= f.holdToStartMs) {
            if ((fsm != null || (fsmLeft != null && fsmRight != null)) && topRefSamples.size >= 15) {
                val ref = topPercentile()
                topRef = ref
                fsm?.setTopRef(ref)
                fsmLeft?.setTopRef(ref)
                fsmRight?.setTopRef(ref)
            }
            calib = CalibState.READY
            phase = Phase.FIGHTING
            fightStartMs = tMs
            // Schedule first attack after grace period if attacks are enabled
            if (attacksEnabled && combatCfg.bossAttack != null) {
                nextAttackAtMs = tMs + (combatCfg.bossAttack.graceSec * 1000).toLong()
            }
            ghost?.start(tMs)
        }
        return state()
    }

    /** The rest position as the 90th percentile in the direction of "top" for this exercise. */
    private fun topPercentile(): Float {
        val d = exercise.detector
        val dec = d.getValue("topEnter").jsonPrimitive.float > d.getValue("bottomEnter").jsonPrimitive.float
        return topPercentile(topRefSamples, dec)
    }

    private fun framingOf(image: Landmarks): Framing {
        val h = Geometry.boxHeight(image)
        val f = poseCfg.framing
        return when {
            h < f.targetBoxHeightMin -> Framing.TOO_FAR
            h > f.targetBoxHeightMax -> Framing.TOO_CLOSE
            else -> Framing.OK
        }
    }

    private fun lost(): SessionState {
        invalidFrames++
        if (invalidFrames >= poseCfg.framingLostFrames && phase == Phase.FIGHTING) {
            phase = Phase.FRAMING_LOST
            fatigue.freeze()                       // a pause must never read as fatigue
        }
        return state()
    }

    // ------------------------------------------------------------------ snapshot

    fun state(): SessionState {
        val spec = combatCfg.bossAttack
        val countdown = if (attacksEnabled && phase == Phase.FIGHTING) nextAttackAtMs?.let { max(0L, it - lastTMs) } else null
        val interval = if (attacksEnabled && spec != null) (spec.everySec * 1000f).toLong() else null
        val combatState = combat.state().copy(nextAttackInMs = countdown, attackIntervalMs = interval)
        return SessionState(
        mode = mode,
        exerciseId = exercise.id,
        family = family,
        phase = phase,
        calib = calib,
        calibProgress = min(1f, calibHoldMs.toFloat() / poseCfg.framing.holdToStartMs),
        missingJoints = missing,
        framing = framing,
        cue = cue(),
        angleDeg = angle,
        angleLeftDeg = lastLeftDeg,
        angleRightDeg = lastRightDeg,
        topRefDeg = topRef,
        setIndex = setIndex,
        setReps = setReps.size,
        reps = allReps.size,
        lastRep = lastRep,
        fatigue = fatigue.state(),
        combat = combatState,
        game = game?.state(),
        wave = wave,
        rushIndex = rushIndex,
        timeLeftMs = timeLeftMs,
        playerDamage = playerDamage,
        ghostDamage = ghostDamage,
        ghostName = ghostData?.meta?.name,
        ghostFinished = ghost?.finished,
        telemetry = telemetry,
        coach = coach,
        ended = ended,
        endReason = endReason,
    )
    }

    private fun cue(): String? {
        val c = exercise.cues
        if (phase == Phase.FRAMING_LOST) {
            return if (missing.isNotEmpty()) "I can't see your ${Geometry.jointPhrase(missing)}."
            else c["framing"] ?: "I lost you — step back into frame."
        }
        if (phase == Phase.CALIBRATING) {
            return when (calib) {
                CalibState.SEARCHING -> c["enter"] ?: "Step into frame."
                CalibState.PARTIAL -> "I can't see your ${Geometry.jointPhrase(missing)} — step back."
                CalibState.TOO_FAR -> "Come closer."
                CalibState.TOO_CLOSE -> "Step back."
                CalibState.HOLDING -> "Hold still…"
                CalibState.READY -> c["enter"] ?: "Ready."
            }
        }
        detector?.last?.cue?.let { return it }               // pose-match names the joint
        gateFailedAtMs?.let { if (lastTMs - it < 2500) return c["gate"] ?: c["tooHigh"] ?: "Finish the movement." }
        stageCounter?.lastMissMs?.let { if (lastTMs - it < 2500) return c["gate"] ?: c["tooHigh"] ?: "Finish the movement." }
        if (framing == Framing.TOO_FAR) return "Come closer."
        if (framing == Framing.TOO_CLOSE) return "Step back."
        val r = lastRep
        if (r != null && r.verdict == Verdict.SHALLOW) {
            return if (r.reason == "tempo") c["tooFast"] ?: "Control the way down." else c["tooHigh"] ?: "Go lower."
        }
        return null
    }
}

/**
 * The 90th percentile of calibration samples, in the direction of "top" for the exercise.
 *
 * Nearest-rank, never interpolated: sort so the top-most angles land LAST, then take index
 * `min(n - 1, floor(n * 0.9))`. Port of `src/engine.js:470-473`.
 *
 * @param dec true when the exercise's top is the LARGER angle (squat, push-up: topEnter >
 *   bottomEnter), so an ascending sort puts "top" last; false for increasing-angle exercises
 *   (calf raise, glute bridge), which sort descending so the smaller "top" angles land last.
 */
internal fun topPercentile(samples: List<Float>, dec: Boolean): Float {
    val sorted = if (dec) samples.sorted() else samples.sortedDescending()
    return sorted[min(sorted.size - 1, floor(sorted.size * 0.9).toInt())]
}

/** Frames further apart than this were not delivered: the session was paused or the camera restarted. */
private const val FRAME_GAP_MS = 1_500L
