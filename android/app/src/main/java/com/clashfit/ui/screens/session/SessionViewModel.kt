package com.clashfit.ui.screens.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clashfit.AppGraph
import com.clashfit.core.model.CombatState
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
import com.clashfit.data.RepEntity
import com.clashfit.data.SessionEntity
import com.clashfit.data.SetEntity
import com.clashfit.engine.session.SessionEngine
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

data class SessionArgs(
    val mode: GameMode,
    val exerciseId: String,
    val casual: Boolean = false,
    val ghostId: String? = null,
    val durationSec: Int? = null,
)

/** Transient things the HUD animates once. Never state — they are fired and forgotten. */
sealed interface HudEvent {
    data class Hit(val id: Long, val damage: Int, val verdict: Verdict, val combo: Float) : HudEvent
    data class Band(val band: FatigueBand) : HudEvent
    data class PhaseShift(val label: String) : HudEvent
    data object BossDown : HudEvent
    data object FramingLost : HudEvent
    data object Milestone : HudEvent
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

    private val _restRemainingSec = MutableStateFlow<Int?>(null)
    val restRemainingSec: StateFlow<Int?> = _restRemainingSec.asStateFlow()

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
    private var restJob: Job? = null
    private var frameJob: Job? = null
    private val completedSets = ArrayList<SetSnapshot>()
    private var setStartedAtMs = startedAtMs
    private var saved = false

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
        args.ghostId?.let { id -> cfg.ghosts.value[id]?.let { engine.loadGhost(it) } }
        _state.value = engine.state()
        startCamera()
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
    }

    private fun afterFrame(before: Phase, s: SessionState) {
        if (before != Phase.FRAMING_LOST && s.phase == Phase.FRAMING_LOST) {
            deps.sfx.framingLost(); _events.tryEmit(HudEvent.FramingLost)
        }
        if (s.phase == Phase.CALIBRATING || s.phase == Phase.FRAMING_LOST) {
            val cue = s.cue
            if (cue != null && cue != lastCue && !cue.startsWith("Hold")) { lastCue = cue; deps.speech.speak(cue) }
        }
        if (before != Phase.REST && s.phase == Phase.REST) deps.pose.setLowPower(true)
        if (before == Phase.REST && s.phase == Phase.FIGHTING) deps.pose.setLowPower(false)
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
        }

        override fun onBand(band: FatigueBand) {
            deps.sfx.band(band)
            _events.tryEmit(HudEvent.Band(band))
        }

        override fun onSetEnd(telemetry: SetTelemetry, restSec: Int) {
            val setReps = engine.currentSetReps.toList()
            val now = graph.clock.nowMs()
            viewModelScope.launch {
                val coach = deps.coach.speakFor(telemetry)
                withContext(engineThread) { engine.setCoach(coach) }
                _state.value = engine.state()
                deps.speech.speak(coach.coachLine, flush = true)
                completedSets += SetSnapshot(
                    index = telemetry.sessionSetIndex, reps = setReps, startedAtMs = setStartedAtMs, endedAtMs = now,
                    telemetry = telemetry, restSec = restSec, coachLine = coach.coachLine, bossLine = coach.bossLine,
                    coachSource = coach.source.name,
                )
            }
            startRest(restSec)
        }

        override fun onEnd(reason: EndReason, state: SessionState) {
            restJob?.cancel()
            if (reason == EndReason.BOSS_DOWN || reason == EndReason.GAME_WON) deps.sfx.bossDown()
            _events.tryEmit(HudEvent.BossDown)
            viewModelScope.launch { persist(reason) }
        }
    }

    /** Rest length is fatigue-derived, not fixed: 30s fresh → 75s gassed. Skippable. */
    private fun startRest(restSec: Int) {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            var left = restSec
            _restRemainingSec.value = left
            while (left > 0) {
                delay(1000)
                left -= 1
                _restRemainingSec.value = left
                if (left in 1..3) deps.sfx.tick()
            }
            nextSet()
        }
    }

    fun skipRest() { restJob?.cancel(); nextSet() }

    private fun nextSet() {
        _restRemainingSec.value = null
        setStartedAtMs = graph.clock.nowMs()
        deps.speech.stop()
        viewModelScope.launch(engineThread) {
            engine.nextSet()
            _state.value = engine.state()
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
        val id = withContext(Dispatchers.IO) {
            val sessionId = dao.insertSession(SessionEntity(
                startedAtMs = startedAtMs, endedAtMs = now, mode = args.mode.name, exerciseId = args.exerciseId,
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
                    ),
                    set.reps.map { r -> r.toEntity(sessionId) },
                )
            }
            dao.compact()
            sessionId
        }
        Log.i(TAG, "session $id saved: ${all.size} reps, ${s.playerDamage} damage, $reason")
        _finished.tryEmit(id)
    }

    override fun onCleared() {
        frameJob?.cancel()
        restJob?.cancel()
        deps.pose.stop()
        deps.speech.stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ClashFit/session"
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
