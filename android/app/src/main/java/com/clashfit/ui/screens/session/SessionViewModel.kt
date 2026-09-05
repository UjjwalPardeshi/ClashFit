package com.clashfit.ui.screens.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clashfit.AppGraph
import com.clashfit.core.config.GhostData
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.ModeKind
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Verdict
import com.clashfit.core.pose.CameraFacing
import com.clashfit.data.GhostEntity
import com.clashfit.data.ProgressionRepository
import com.clashfit.data.RepEntity
import com.clashfit.data.SessionEntity
import com.clashfit.data.SetEntity
import com.clashfit.engine.session.SessionEngine
import com.clashfit.engine.summary.Progression
import com.clashfit.play.LinkHud
import com.clashfit.perception.gesture.GestureIntent
import com.clashfit.perception.gesture.GestureSource
import com.clashfit.perception.gesture.HandGesture
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.clashfit.perception.vision.FrameSource
import com.clashfit.perception.MediaPipePoseSource

data class SessionArgs(
    val mode: GameMode,
    val exerciseId: String,
    val casual: Boolean = false,
    val ghostId: String? = null,
    val durationSec: Int? = null,
    /** Replay a recorded trace instead of opening the camera. Always labelled on screen. */
    val replay: Boolean = false,
)

/** Transient things the HUD animates once. Never state — they are fired and forgotten. */
sealed interface HudEvent {
    data class Hit(val id: Long, val damage: Int, val verdict: Verdict, val combo: Float) : HudEvent
    data class Band(val band: FatigueBand) : HudEvent
    data class PhaseShift(val label: String) : HudEvent
    data class PlayerHit(val damage: Int, val hpPct: Float) : HudEvent
    data object BossDown : HudEvent
    data object FramingLost : HudEvent
    data object Milestone : HudEvent
    /** A hand shape the referee obeyed — named on screen for a moment so the player knows why. */
    data class Gesture(val gesture: HandGesture, val label: String) : HudEvent
}

/**
 * Drives one session: pumps pose frames into the engine on a single thread, publishes the
 * engine's snapshot, fires audio before pixels, fetches the coach between sets, and writes the
 * session to Room when it ends. No damage is ever applied outside FIGHTING — the engine holds
 * that invariant; this class only schedules.
 */
class SessionViewModel(
    private val graph: AppGraph,
    private val deps: SessionDeps,
    val args: SessionArgs,
) : ViewModel() {

    private val engineThread = Dispatchers.Default.limitedParallelism(1)

    private val _state = MutableStateFlow<SessionState?>(null)
    val state: StateFlow<SessionState?> = _state.asStateFlow()

    private val _skeleton = MutableStateFlow<Landmarks?>(null)
    val skeleton: StateFlow<Landmarks?> = _skeleton.asStateFlow()

    private val _events = MutableSharedFlow<HudEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<HudEvent> = _events.asSharedFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Emits the persisted session id once, when the session has ended and been saved. */
    private val _finished = MutableSharedFlow<Long>(replay = 1)
    val finished: SharedFlow<Long> = _finished.asSharedFlow()

    val exerciseName: String get() = engine.exercise.name
    val isProne: Boolean get() = PRONE_HINTS.any { engine.exercise.id.contains(it) }

    private lateinit var engine: SessionEngine
    private val startedAtMs = graph.clock.nowMs()
    private var hitCounter = 0L
    private var lastPhaseLabel = "phase1"
    private var lastCue: String? = null
    private var frameJob: Job? = null
    private var gestureJob: Job? = null

    /** Hold detection for hand gestures. Pure; the recogniser's noise stops here. */
    private val gestureIntent = GestureIntent()

    /** The gesture being held and how far along the hold is, for the ring the HUD fills. Null when no hand. */
    private val _gestureHold = MutableStateFlow<Pair<HandGesture, Float>?>(null)
    val gestureHold: StateFlow<Pair<HandGesture, Float>?> = _gestureHold.asStateFlow()

    /**
     * What the referee saw in the worst rep of the set just finished.
     *
     * Null while it is thinking, and null forever if there is no on-device model, no frame kept, or
     * the model would not commit to an answer. The rest panel shows it when it arrives and shows
     * the numbers regardless.
     */
    private val _refereeNote = MutableStateFlow<String?>(null)
    val refereeNote: StateFlow<String?> = _refereeNote.asStateFlow()
    private var refereeJob: Job? = null
    private val completedSets = ArrayList<SetSnapshot>()
    private var setStartedAtMs = startedAtMs
    private var saved = false

    private val hub = graph.playHub
    /** The other phones, when this is a duel, race or raid. Null otherwise. */
    val link: StateFlow<LinkHud?> = hub.link
    /** True when this session is one turn of a pass-the-phone game. */
    val rosterTurn: Boolean = hub.roster.value != null
    private var linkJob: Job? = null

    /** What the session changed in streaks, bests and ladders. Set once, with [finished]. */
    private val _progress = MutableStateFlow<ProgressionRepository.Outcome?>(null)
    val progress: StateFlow<ProgressionRepository.Outcome?> = _progress.asStateFlow()

    private class SetSnapshot(
        val index: Int, val reps: List<RepRecord>, val startedAtMs: Long, val endedAtMs: Long,
        val telemetry: SetTelemetry?, val restSec: Int, val coachLine: String?, val bossLine: String?, val coachSource: String,
    )

    init {
        val cfg = graph.config
        engine = SessionEngine(
            poseCfg = cfg.pose.value,
            combatCfg = cfg.combat.value,
            exercises = cfg.exercises.value,
            json = graph.json,
            exerciseId = args.exerciseId,
            mode = args.mode,
            casual = args.casual,
            clinic = cfg.clinic.value.values.firstOrNull(),
            durationOverrideSec = args.durationSec,
            listener = EngineListener(),
        )
        args.ghostId?.let { id ->
            val shipped = cfg.ghosts.value[id]
            if (shipped != null) engine.loadGhost(shipped) else viewModelScope.launch { loadSavedGhost(id) }
        }
        if ((args.mode.kind == ModeKind.VERSUS || args.mode.kind == ModeKind.GROUP) && hub.active) attachLink()
        _state.value = engine.state()
        startCamera()
    }

    /** A ghost saved from an earlier fight, or accepted from a challenge code. */
    private suspend fun loadSavedGhost(id: String) {
        val entity = withContext(Dispatchers.IO) { graph.db.ghosts().get(id) } ?: return
        val data = runCatching { graph.json.decodeFromString(GhostData.serializer(), entity.eventsJson) }.getOrElse {
            runCatching {
                val events = graph.json.decodeFromString(ListSerializer(GhostData.Event.serializer()), entity.eventsJson)
                GhostData(meta = GhostData.Meta(entity.name, entity.exerciseId, entity.reps, entity.totalDamage), events = events)
            }.getOrNull()
        } ?: return
        withContext(engineThread) { engine.loadGhost(data); _state.value = engine.state() }
    }

    /** Remote hits flow into the engine's idempotent path; local reps go out through the hub. */
    private fun attachLink() {
        linkJob = viewModelScope.launch {
            hub.remoteHits.collect { applyRemoteDamage(it.playerId, it.seq, it.damage) }
        }
    }

    private fun startCamera() {
        frameJob = viewModelScope.launch {
            val arena = graph.prefs.settings.first().arenaMode
            deps.pose.start(if (arena) CameraFacing.BACK else CameraFacing.FRONT)
            deps.pose.frames.collect { f ->
                if (_paused.value) return@collect
                withContext(engineThread) {
                    val before = engine.state().phase
                    val s = engine.frame(f.world, f.image, f.tMs)
                    _skeleton.value = f.image
                    _state.value = s
                    afterFrame(before, s)
                }
            }
        }
        startGestures()
        startFrameKeeping()
    }

    /**
     * Hand control. The player is two metres away and cannot reach the phone; in a loud room voice
     * does not work either. A hand held up to the camera does.
     *
     * The recogniser only runs while a gesture could mean something — fighting, or resting — and
     * only if the setting is on. A shape must be held (see [GestureIntent]) before it fires, so a
     * hand passing the lens on the way up from a squat pauses nothing. The mapping is small on
     * purpose: three shapes, each with one meaning per phase, and nothing that ends the whole
     * session — that stays a deliberate tap on the screen.
     */
    /** Keep recent frames only while there is a model that could look at one. */
    private fun startFrameKeeping() {
        val frames = deps.pose as? MediaPipePoseSource ?: return
        if (graph.refereeEyes.available) frames.setKeepFrames(true)
    }

    private fun startGestures() {
        val source = deps.pose as? GestureSource ?: return
        gestureJob = viewModelScope.launch {
            if (!graph.prefs.settings.first().gestures) return@launch
            // Follow the phase: read the hand when it could mean something, ignore it otherwise.
            launch {
                _state.collect { s ->
                    val wanted = s != null && !s.ended && s.phase == Phase.FIGHTING
                    source.setGesturesEnabled(wanted)
                    if (!wanted) { gestureIntent.reset(); _gestureHold.value = null }
                }
            }
            source.gestures.collect { reading ->
                val fired = gestureIntent.offer(reading)
                _gestureHold.value = gestureIntent.holding?.let { it to gestureIntent.progress }
                if (fired != null) onGesture(fired)
            }
        }
    }

    /**
     * The referee looks at the worst rep of the set that just ended.
     *
     * Everything needed is already on the phone: the rep the scorer liked least, the moment it was
     * deepest, and a few seconds of small camera frames the source kept for exactly this. The
     * frame goes to the on-device model and is recycled the moment the answer comes back — it is
     * never stored, and there is no path from here to the network.
     *
     * Silent about every failure. No model, no kept frame, a rep with no deepest moment, a refusal:
     * the rest panel shows the numbers it always showed and nobody is told the AI declined.
     */
    private fun lookAtWorstRep(setReps: List<RepRecord>) {
        _refereeNote.value = null
        refereeJob?.cancel()
        val eyes = graph.refereeEyes
        if (!eyes.available) return
        val frames = deps.pose as? FrameSource ?: return
        val worst = setReps.filter { it.tDeepestMs > 0 }.minByOrNull { it.formScore } ?: return

        refereeJob = viewModelScope.launch {
            val frame = withContext(Dispatchers.Main) { frames.frameNear(worst.tDeepestMs) } ?: return@launch
            try {
                _refereeNote.value = eyes.critique(frame, worst, engine.exercise.name)
            } finally {
                frame.recycle()
            }
        }
    }

    private fun onGesture(g: HandGesture) {
        _state.value ?: return
        val label = when {
            g == HandGesture.OPEN_PALM -> {
                if (_paused.value) { resume(); "Resumed" } else { pause(); "Paused" }
            }
            g == HandGesture.THUMB_UP -> {
                // The only way a set ends. The next one starts underneath it; the coach talks over it.
                viewModelScope.launch(engineThread) {
                    val cur = engine.state()
                    if (cur.phase == Phase.FIGHTING && cur.setReps > 0) { engine.endSet(); _state.value = engine.state() }
                }
                "Set done"
            }
            else -> return
        }
        deps.haptics.milestone()
        _events.tryEmit(HudEvent.Gesture(g, label))
        _gestureHold.value = null
    }

    private fun afterFrame(before: Phase, s: SessionState) {
        if (before != Phase.FRAMING_LOST && s.phase == Phase.FRAMING_LOST) {
            deps.sfx.framingLost(); _events.tryEmit(HudEvent.FramingLost)
        }
        if (s.phase == Phase.CALIBRATING || s.phase == Phase.FRAMING_LOST) {
            val cue = s.cue
            if (cue != null && cue != lastCue && !cue.startsWith("Hold")) { lastCue = cue; deps.speech.speak(cue) }
        }
        val label = s.combat.phaseLabel
        if (label != lastPhaseLabel) {
            lastPhaseLabel = label
            if (s.phase == Phase.FIGHTING) { deps.sfx.phase(); deps.haptics.phase(); _events.tryEmit(HudEvent.PhaseShift(label)) }
        }
    }

    private inner class EngineListener : SessionEngine.Listener {
        override fun onRep(rec: RepRecord, combat: CombatState) {
            // Audio fires first, not last. docs/03-UI-UX-SPEC.md §3
            deps.sfx.rep(rec.verdict, rec.combo)
            deps.haptics.rep(rec.verdict)
            _events.tryEmit(HudEvent.Hit(++hitCounter, rec.damage, rec.verdict, rec.combo))
            if (combat.comboStreak > 0 && combat.comboStreak % 5 == 0) {
                deps.sfx.milestone(); deps.haptics.milestone(); _events.tryEmit(HudEvent.Milestone)
            }
            if (hub.active) hub.onLocalRep(combat.reps, rec.damage, rec.formScore, rec.fatigue.band)
        }

        override fun onBand(band: FatigueBand) {
            deps.sfx.band(band)
            _events.tryEmit(HudEvent.Band(band))
        }

        override fun onPlayerHit(damage: Int, playerHp: Int) {
            deps.sfx.playerHit()
            deps.haptics.playerHit()
            val s = engine.state()
            _events.tryEmit(HudEvent.PlayerHit(damage, s.combat.playerHpPct))
        }

        /**
         * Called by the engine before it resets the set, so [currentSetReps] is still the set that
         * just finished. The next set has already begun by the time the coach answers, so the line
         * is spoken over a fight that never left the screen.
         */
        override fun onSetEnd(telemetry: SetTelemetry) {
            val setReps = engine.currentSetReps.toList()
            val now = graph.clock.nowMs()
            lookAtWorstRep(setReps)
            viewModelScope.launch {
                val coach = deps.coach.speakFor(telemetry)
                withContext(engineThread) { engine.setCoach(coach) }
                _state.value = engine.state()
                deps.speech.speak(coach.coachLine, flush = true)
                completedSets += SetSnapshot(
                    index = telemetry.sessionSetIndex, reps = setReps, startedAtMs = setStartedAtMs, endedAtMs = now,
                    telemetry = telemetry, restSec = 0, coachLine = coach.coachLine, bossLine = coach.bossLine,
                    coachSource = coach.source.name,
                )
            }
            setStartedAtMs = now
        }

        override fun onEnd(reason: EndReason, state: SessionState) {
            if (reason == EndReason.BOSS_DOWN || reason == EndReason.GAME_WON) deps.sfx.bossDown()
            _events.tryEmit(HudEvent.BossDown)
            if (hub.active) hub.onLocalOut()
            viewModelScope.launch { persist(reason) }
        }
    }

    fun pause() { _paused.value = true }
    fun resume() { _paused.value = false }

    /** The player stops. Ends the session and saves what we have. */
    fun stop() {
        viewModelScope.launch(engineThread) {
            val s = engine.state()
            if (s.phase == Phase.FIGHTING && s.setReps > 0) engine.endSet()
            engine.stop()
            _state.value = engine.state()
        }
    }

    fun applyRemoteDamage(playerId: String, seq: Int, damage: Int) {
        viewModelScope.launch(engineThread) {
            engine.applyRemoteDamage(playerId, seq, damage)
            _state.value = engine.state()
        }
    }

    private suspend fun persist(reason: EndReason) {
        if (saved) return
        saved = true
        val s = engine.state()
        val now = graph.clock.nowMs()
        // The set in progress when the session ended still counts.
        val trailing = engine.currentSetReps.toList()
        if (trailing.isNotEmpty() && completedSets.none { it.index == s.setIndex }) {
            completedSets += SetSnapshot(s.setIndex, trailing, setStartedAtMs, now, s.telemetry, 0, null, null, "TEMPLATE")
        }
        val all = completedSets.flatMap { it.reps }
        val formMean = if (all.isEmpty()) 0f else all.map { it.formScore }.average().toFloat()
        val last = all.lastOrNull()
        val dao = graph.db.sessions()
        // One transaction for the whole session.
        //
        // The insert, every set with its reps, and the compaction used to be separate writes. A
        // crash or a kill between them left a session row with some of its sets and none of its
        // reps — a session that says 31 reps and can show you none of them, which is worse than no
        // session at all because it looks like data.
        val id = withContext(Dispatchers.IO) {
            graph.db.withTransaction {
            val sessionId = dao.insertSession(SessionEntity(
                // A replayed session is labelled in the data, not only on screen, so History can
                // never present a recording as something you did.
                startedAtMs = startedAtMs, endedAtMs = now,
                mode = if (args.replay) REPLAY_MODE else args.mode.name, exerciseId = args.exerciseId,
                bossId = s.combat.bossId, outcome = reason.name, totalDamage = s.playerDamage, totalReps = all.size,
                formMean = formMean, peakFatigue = last?.fatigue?.value ?: 0f, peakBand = (last?.fatigue?.band ?: FatigueBand.FRESH).name,
                casual = args.casual, ghostId = args.ghostId,
            ))
            for (set in completedSets) {
                val setMean = if (set.reps.isEmpty()) 0f else set.reps.map { it.formScore }.average().toFloat()
                val endF = set.reps.lastOrNull()?.fatigue
                dao.recordSet(
                    SetEntity(
                        sessionId = sessionId, setIndex = set.index, exerciseId = args.exerciseId,
                        startedAtMs = set.startedAtMs, endedAtMs = set.endedAtMs, reps = set.reps.size, formMean = setMean,
                        fatigueEnd = endF?.value ?: 0f, fatigueBandEnd = (endF?.band ?: FatigueBand.FRESH).name,
                        coachLine = set.coachLine, bossLine = set.bossLine, coachSource = set.coachSource, restSec = set.restSec,
                        asymmetryPct = set.telemetry?.asymmetryPct, weakerSide = set.telemetry?.weakerSide,
                    ),
                    set.reps.map { r -> r.toEntity(sessionId) },
                )
            }
            dao.compact()
            sessionId
            }
        }
        Log.i(TAG, "session $id saved: ${all.size} reps, ${s.playerDamage} damage, $reason")
        // A replay is a recording, so nothing it does counts.
        //
        // Without this a demo rescue would advance the streak, award XP, unlock badges, set
        // personal bests and push a score to the shared leaderboard — every one of them earned by
        // a file rather than by a body. On a board other people are on, that is not a rounding
        // error, it is cheating. The session is still written so the summary can show the fatigue
        // curve, which is the whole reason to run a replay in front of anyone.
        if (!args.replay) {
            bank(id, all, formMean, now, s.playerDamage, reason, startedAtMs)
        } else {
            Log.i(TAG, "session $id was a replay: no xp, no streak, no board")
        }
        _finished.tryEmit(id)
    }

    /** Streaks, bests, ladders, the session's own ghost, the roster turn, and meta-progression — none of it blocks the summary. */
    private suspend fun bank(sessionId: Long, all: List<RepRecord>, formMean: Float, now: Long, damage: Int, reason: EndReason, startedAtMs: Long) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                ProgressionRepository(graph.db).onSessionFinished(
                    exerciseId = args.exerciseId, reps = all.size, formMean = formMean, atMs = now, casual = args.casual,
                    repDetails = all.map { Progression.RepDetail(it.depthCm, it.heightCm, it.holdSec) },
                )
            }.onFailure { Log.w(TAG, "progression not banked", it) }.getOrNull()
        }
        _progress.value = outcome
        val metaReward = withContext(Dispatchers.IO) {
            runCatching {
                val cleanReps = all.count { it.verdict == Verdict.CLEAN }
                val family = all.firstOrNull()?.family
                val facts = com.clashfit.meta.SessionFacts(
                    sessionId = sessionId,
                    atMs = now,
                    mode = args.mode.name,
                    exerciseId = args.exerciseId,
                    reps = all.size,
                    cleanReps = cleanReps,
                    formMean = formMean,
                    damage = damage,
                    won = reason == EndReason.BOSS_DOWN || reason == EndReason.GAME_WON,
                    casual = args.casual,
                    durationSec = ((now - startedAtMs) / 1000).toInt(),
                    streakAfter = outcome?.streak?.current ?: 0,
                    newPersonalBest = outcome?.newBests?.isNotEmpty() ?: false,
                    family = family?.name,
                )
                graph.meta.onSessionFinished(facts).also { graph.rewards.put(sessionId, it) }
            }.onFailure { Log.w(TAG, "meta not banked", it) }.getOrNull()
        }
        if (all.size >= MIN_GHOST_REPS && !args.casual && args.mode.kind == ModeKind.SOLO) {
            val t0 = all.first().tStartMs
            val ghost = GhostData(
                meta = GhostData.Meta(name = "You · ${java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(java.util.Date(now))}",
                    exercise = args.exerciseId, reps = all.size, totalDamage = damage),
                events = all.map { GhostData.Event(t = it.tEndMs - t0, damage = it.damage) },
            )
            withContext(Dispatchers.IO) {
                runCatching {
                    graph.db.ghosts().upsert(GhostEntity(
                        id = "session-$sessionId", name = ghost.meta.name, exerciseId = args.exerciseId, reps = all.size,
                        totalDamage = damage, createdAtMs = now, eventsJson = graph.json.encodeToString(GhostData.serializer(), ghost), shipped = false,
                    ))
                }.onFailure { Log.w(TAG, "ghost not saved", it) }
            }
        }
        val gassed = all.lastOrNull()?.fatigue?.band == FatigueBand.GASSED
        hub.onSessionEnded(reps = all.size, damage = damage, bestForm = all.maxOfOrNull { it.formScore } ?: 0f, gassed = gassed)
    }

    override fun onCleared() {
        frameJob?.cancel()
        linkJob?.cancel()
        deps.pose.stop()
        deps.speech.stop()
        // The session owned the link once the lobby handed it over; nothing outlives the fight.
        if (hub.active) hub.release()
    }

    companion object {
        private const val TAG = "ClashFit/session"
        private const val MIN_GHOST_REPS = 3
        private val PRONE_HINTS = listOf("push_up", "pushup", "plank", "sit_up", "situp", "bridge", "superman", "hollow", "bird_dog", "dead_bug")

        fun factory(graph: AppGraph, deps: SessionDeps, args: SessionArgs) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(graph, deps, args) as T
        }
    }
}

private fun RepRecord.toEntity(sessionId: Long) = RepEntity(
    sessionId = sessionId, setId = 0, repIndex = repIndex, tStartMs = tStartMs, tEndMs = tEndMs,
    formScore = formScore, depth = depth, rom = rom, tempo = tempo, alignment = alignment, reason = reason,
    verdict = verdict.name, concentricVelocity = concentricVelocity, damage = damage, comboAtRep = combo,
    fatigueValue = fatigue.value, fatigueBand = fatigue.band.name, validFrameRatio = validFrameRatio,
    depthCm = depthCm.takeUnless { it.isNaN() }, heightCm = heightCm.takeUnless { it.isNaN() }, holdSec = holdSec.takeUnless { it.isNaN() },
)

/**
 * The mode a replayed session is recorded under.
 *
 * Not a GameMode: a replay is not a way to play, it is a recording being pushed through the engine
 * so a demo can carry on when the room beats the camera. Giving it its own name keeps it out of
 * every count that groups by mode, and keeps History honest about what it is.
 */
const val REPLAY_MODE = "REPLAY"
