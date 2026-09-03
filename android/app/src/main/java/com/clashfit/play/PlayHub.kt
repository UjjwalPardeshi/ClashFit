package com.clashfit.play

import android.content.Context
import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.LinkState
import com.clashfit.core.util.Clock
import com.clashfit.duel.DuelSession
import com.clashfit.duel.DuelTransport
import com.clashfit.duel.NearbyTransport
import com.clashfit.duel.RaidSession
import com.clashfit.duel.RepRaceSession
import com.clashfit.duel.newPlayerId
import com.clashfit.engine.games.Roster
import com.clashfit.engine.games.RosterMode
import com.clashfit.engine.games.RosterPlayer
import com.clashfit.engine.games.RosterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** One row of a live leaderboard, as the fight HUD and the lobbies draw it. */
data class Standing(
    val playerId: String,
    val name: String,
    val reps: Int,
    val damage: Int,
    val band: String,
    val out: Boolean,
    val finished: Boolean,
    val me: Boolean,
)

/** Everything a screen needs to show about the other phones. Null when nothing is armed. */
data class LinkHud(
    val mode: GameMode,
    val state: LinkState,
    val isHost: Boolean,
    val peers: Int,
    val opponentDamage: Int,
    val opponentReps: Int,
    val standings: List<Standing>,
    val raceStartedMs: Long?,
)

/** The host's go signal. Both phones navigate into the session on it. */
data class StartSignal(val mode: GameMode, val exerciseId: String, val durationSec: Int?)

/** Damage that arrived from another phone. Idempotent downstream by (playerId, seq). */
data class RemoteHit(val playerId: String, val seq: Int, val damage: Int)

/**
 * The one object that outlives a screen during multiplayer and pass-the-phone play.
 *
 * A lobby opens a link and arms a mode; the session then reports local reps here and applies
 * remote hits from [remoteHits]. Pass-the-phone keeps its [Roster] here between turns so the
 * fight screen can be an ordinary session that pops back to the board when the turn ends.
 * Nothing here touches the network stack directly beyond the [DuelTransport] contract, and the
 * only transport is Nearby Connections — the app has no INTERNET permission.
 */
class PlayHub(
    private val app: Context,
    private val json: Json,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    val playerId: String = newPlayerId()
    @Volatile var playerName: String = "YOU"

    // ------------------------------------------------------------------ link

    private var transport: DuelTransport? = null
    private var duel: DuelSession? = null
    private var race: RepRaceSession? = null
    private var raid: RaidSession? = null
    private val jobs = mutableListOf<Job>()
    private var armed: StartSignal? = null
    private var isHost = false

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _link = MutableStateFlow<LinkHud?>(null)
    val link: StateFlow<LinkHud?> = _link.asStateFlow()

    private val _remoteHits = MutableSharedFlow<RemoteHit>(extraBufferCapacity = 128)
    val remoteHits: SharedFlow<RemoteHit> = _remoteHits.asSharedFlow()

    private val _started = MutableSharedFlow<StartSignal>(extraBufferCapacity = 4)
    val started: SharedFlow<StartSignal> = _started.asSharedFlow()

    /** True while a mode is armed on a live transport — the session then reports here. */
    val active: Boolean get() = armed != null && transport != null

    /** What is armed right now, if anything. */
    val armedSignal: StartSignal? get() = armed

    /** Opens a fresh Nearby transport, tearing down whatever was there. */
    fun open(): DuelTransport {
        release()
        val t = NearbyTransport(app, json, scope)
        transport = t
        jobs += scope.launch { t.state.collect { s -> _linkState.value = s; refresh() } }
        jobs += scope.launch {
            t.incoming.filter { it.type == DuelMessage.START }.collect { onStartMessage(it) }
        }
        return t
    }

    suspend fun host(roomName: String): Result<Unit> {
        isHost = true
        return (transport ?: open()).host(roomName)
    }

    suspend fun join(): Result<Unit> {
        isHost = false
        return (transport ?: open()).join()
    }

    /** Builds the per-mode session on the linked transport. Call on both phones once LINKED. */
    fun arm(mode: GameMode, exerciseId: String, durationSec: Int?) {
        val t = transport ?: open()
        closeSessions()
        armed = StartSignal(mode, exerciseId, durationSec)
        val onRemote = { p: String, s: Int, d: Int -> _remoteHits.tryEmit(RemoteHit(p, s, d)); refresh() }
        when (mode) {
            GameMode.REP_RACE -> race = RepRaceSession(t, playerId, playerName, clock, scope, durationSec ?: 60) { refresh() }
            GameMode.RAID, GameMode.TEAM_VS_TEAM ->
                raid = RaidSession(t, playerId, playerName, clock, scope, isHost, onRemote) { refresh() }
            else -> duel = DuelSession(t, playerId, clock, scope, onRemote) { refresh() }
        }
        jobs += scope.launch {
            while (isActive && armed != null) {
                duel?.tick()
                race?.tick()
                raid?.tick()
                refresh()
                delay(HEARTBEAT_MS)
            }
        }
        refresh()
    }

    /** Host only. Rep Race carries its own START; every other mode sends a plain go signal. */
    suspend fun start(): Result<Unit> {
        val a = armed ?: return Result.failure(IllegalStateException("Nothing is armed."))
        val t = transport ?: return Result.failure(IllegalStateException("No link."))
        val r = race?.startRace() ?: runCatching {
            t.send(
                DuelMessage(
                    type = DuelMessage.START, playerId = playerId, name = playerName,
                    exerciseId = a.exerciseId, reps = a.durationSec ?: 0, tMs = clock.nowMs(),
                ),
            )
        }
        if (r.isSuccess) _started.tryEmit(a)
        return r
    }

    private fun onStartMessage(m: DuelMessage) {
        if (isHost) return
        val a = armed ?: return
        val sig = a.copy(
            exerciseId = m.exerciseId.ifBlank { a.exerciseId },
            durationSec = m.reps.takeIf { it > 0 } ?: a.durationSec,
        )
        armed = sig
        _started.tryEmit(sig)
    }

    /** The session calls this on every verified local rep. */
    fun onLocalRep(reps: Int, damage: Int, formScore: Float, band: FatigueBand) {
        val ex = armed?.exerciseId ?: return
        duel?.sendRep(damage, formScore, ex, band.name)
        race?.sendRep(reps, ex)
        raid?.sendRep(damage, reps, ex, band.name, formScore)
        refresh()
    }

    /** The session calls this when the local player is done or quits a raid. */
    fun onLocalOut() {
        raid?.sendOut()
        refresh()
    }

    fun refresh() {
        val a = armed
        if (a == null || transport == null) {
            _link.value = null
            return
        }
        val raceNow = race
        val raidNow = raid
        val duelNow = duel
        val standings = when {
            raceNow != null -> raceNow.standings.value.map {
                Standing(it.playerId, it.name, it.reps, 0, FatigueBand.FRESH.name, false, it.finished, it.playerId == playerId)
            }
            raidNow != null -> raidNow.standings.value.map {
                Standing(it.playerId, it.name, it.reps, it.damage, it.fatigueBand, it.out, false, it.playerId == playerId)
            }
            else -> emptyList()
        }.sortedWith(compareByDescending<Standing> { it.damage }.thenByDescending { it.reps }.thenBy { it.name })
        val others = standings.filter { !it.me }
        _link.value = LinkHud(
            mode = a.mode,
            state = _linkState.value,
            isHost = isHost,
            peers = duelNow?.peerCount() ?: others.size,
            opponentDamage = duelNow?.remoteDamage() ?: others.sumOf { it.damage },
            opponentReps = others.maxOfOrNull { it.reps } ?: 0,
            standings = standings,
            raceStartedMs = raceNow?.raceStartedMs?.value,
        )
    }

    private fun closeSessions() {
        duel?.close(); race?.close(); raid?.close()
        duel = null; race = null; raid = null
    }

    /** Tears the link down. Safe to call twice. */
    fun release() {
        closeSessions()
        jobs.forEach { it.cancel() }
        jobs.clear()
        transport?.close()
        transport = null
        armed = null
        isHost = false
        _linkState.value = LinkState.IDLE
        _link.value = null
    }

    // ---------------------------------------------------------------- roster

    private var rosterEngine: Roster? = null
    private val _roster = MutableStateFlow<RosterState?>(null)
    val roster: StateFlow<RosterState?> = _roster.asStateFlow()

    var rosterExerciseId: String = "squat"
        private set
    var rosterMode: GameMode = GameMode.RELAY
        private set

    fun startRoster(names: List<String>, opts: Roster.RosterOptions, exerciseId: String, mode: GameMode) {
        rosterEngine = Roster(names, opts)
        rosterExerciseId = exerciseId
        rosterMode = mode
        publishRoster()
    }

    fun rosterTurnStarted() {
        rosterEngine?.startTurn(clock.nowMs())
        publishRoster()
    }

    fun rosterTurnLeftMs(): Long? = rosterEngine?.turnLeftMs(clock.nowMs())

    fun rosterTurnSec(): Int = rosterEngine?.turnSec ?: 30

    fun rosterSkip(reason: String) {
        val r = rosterEngine ?: return
        r.eliminateCurrent(reason)
        r.next(clock.nowMs())
        publishRoster()
    }

    fun rosterFinish(): RosterPlayer? = rosterEngine?.finish().also { publishRoster() }

    fun clearRoster() {
        rosterEngine = null
        _roster.value = null
    }

    /**
     * The session reports a finished turn. In Last Standing a gassed player is out; otherwise
     * the turn's reps and damage are banked and the phone moves to the next player.
     */
    fun onSessionEnded(reps: Int, damage: Int, bestForm: Float, gassed: Boolean) {
        val r = rosterEngine ?: return
        if (gassed && r.mode == RosterMode.LAST_STANDING) r.eliminateCurrent("GASSED")
        else r.record(Roster.PlayerStats(reps = reps, damage = damage, bestForm = bestForm))
        r.next(clock.nowMs())
        publishRoster()
    }

    private fun publishRoster() {
        _roster.value = rosterEngine?.state()
    }

    private companion object {
        const val HEARTBEAT_MS = 500L
    }
}
