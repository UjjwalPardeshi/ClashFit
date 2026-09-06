package com.clashfit.play

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.LinkState
import com.clashfit.core.util.Clock
import com.clashfit.duel.DuelSession
import com.clashfit.duel.DuelTransport
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

/**
 * Everything a screen needs to show about the other phones. Null when nothing is armed.
 *
 * [exerciseId] and [durationSec] are the host's lobby settings as this phone currently knows
 * them. On a guest they are blank/null until the host's SETUP arrives, and the lobby draws that
 * gap as "waiting for the host to choose" rather than guessing at a movement.
 */
data class LinkHud(
    val mode: GameMode,
    val state: LinkState,
    val isHost: Boolean,
    val peers: Int,
    val opponentDamage: Int,
    val opponentReps: Int,
    val standings: List<Standing>,
    val raceStartedMs: Long?,
    val exerciseId: String,
    val durationSec: Int?,
)

/**
 * How a versus session finished, kept after the link is torn down.
 *
 * The scoreboard lives on the transport's sessions, and those go when the fight's view model is
 * cleared — which happens on the way to the summary. Without a frozen copy the one screen the
 * player actually reads afterwards has nothing left to name a winner from.
 */
data class VersusResult(
    val mode: GameMode,
    val myName: String,
    val myScore: Int,
    val theirName: String,
    val theirScore: Int,
    /** False when nobody else ever linked: a solo run has no winner, only a score. */
    val hadOpponent: Boolean,
    /** What the two numbers are, for the line under them. */
    val unit: String,
    /** The persisted session these numbers belong to, so a summary cannot show an older match. */
    val sessionId: Long = 0,
) {
    val won: Boolean get() = hadOpponent && myScore > theirScore
    val lost: Boolean get() = hadOpponent && myScore < theirScore
    val drew: Boolean get() = hadOpponent && myScore == theirScore
}

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
 * only multiplayer transport is Nearby Connections, deliberately serverless. The app's INTERNET
 * permission is for accounts and score sync only, never for game play.
 */
class PlayHub(
    private val clock: Clock,
    private val scope: CoroutineScope,
    /**
     * How a link is opened. Injected so the whole host -> arm -> start path is drivable on the
     * JVM over [com.clashfit.duel.LoopbackTransport]; the app passes a Nearby transport.
     */
    private val openTransport: () -> DuelTransport,
) {
    val playerId: String = newPlayerId()
    @Volatile var playerName: String = "YOU"

    // ------------------------------------------------------------------ link

    private var transport: DuelTransport? = null
    private var duel: DuelSession? = null
    private var race: RepRaceSession? = null
    private var raid: RaidSession? = null
    private val jobs = mutableListOf<Job>()
    /**
     * A control message that arrived before this phone had armed, held until it has.
     *
     * Both handlers need an armed signal to copy into, and the lobby arms from a Compose effect
     * while messages arrive on the transport's own thread — nothing orders those two. A dropped
     * SETUP only cost the guest the movement name; a dropped START left it sitting in the lobby
     * while the host raced. One slot is enough: a START supersedes a SETUP, and carries the same
     * movement and clock.
     */
    private var pending: DuelMessage? = null
    /** Held apart from [jobs] so a re-arm replaces it instead of stacking another loop. */
    private var heartbeat: Job? = null
    private var armed: StartSignal? = null
    private var isHost = false

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _link = MutableStateFlow<LinkHud?>(null)
    val link: StateFlow<LinkHud?> = _link.asStateFlow()

    /**
     * The final scoreboard of the last versus session, or null if there has not been one.
     *
     * Outlives [release] on purpose — the summary reads it after the fight's view model has been
     * cleared. Cleared when a new match arms, so it can never name the wrong contest.
     */
    private val _result = MutableStateFlow<VersusResult?>(null)
    val result: StateFlow<VersusResult?> = _result.asStateFlow()

    /** My own reps and damage as the fight ended. Non-null once settled; the trigger for [_result]. */
    private var finalScore: Pair<Int, Int>? = null

    private val _remoteHits = MutableSharedFlow<RemoteHit>(extraBufferCapacity = 128)
    val remoteHits: SharedFlow<RemoteHit> = _remoteHits.asSharedFlow()

    private val _started = MutableSharedFlow<StartSignal>(extraBufferCapacity = 4)
    val started: SharedFlow<StartSignal> = _started.asSharedFlow()

    /** True while a mode is armed on a live transport — the session then reports here. */
    val active: Boolean get() = armed != null && transport != null

    /** What is armed right now, if anything. */
    val armedSignal: StartSignal? get() = armed

    /** Opens a fresh transport, tearing down whatever was there. */
    fun open(): DuelTransport {
        release()
        val t = openTransport()
        transport = t
        jobs += scope.launch {
            t.state.collect { s ->
                val linked = s == LinkState.LINKED && _linkState.value != LinkState.LINKED
                _linkState.value = s
                // Say it again to whoever just arrived. The backlog already replays the last
                // SETUP, but a re-announce does not depend on the backlog having survived, and
                // it costs one 120-byte packet per link.
                if (linked) announceSetup()
                refresh()
            }
        }
        jobs += scope.launch {
            t.incoming.filter { it.type == DuelMessage.START }.collect { onStartMessage(it) }
        }
        jobs += scope.launch {
            t.incoming.filter { it.type == DuelMessage.SETUP }.collect { onSetupMessage(it) }
        }
        return t
    }

    /**
     * Claim the host role, then open the link.
     *
     * The order matters and used to be the other way round: `isHost = true` ran first, then
     * `open()` -> `release()` reset it straight back to false. The phone that pressed Host was
     * therefore never marked as the host, so the lobby drew a guest's "Waiting for host..." and
     * the Start button never appeared for anyone.
     */
    suspend fun host(roomName: String): Result<Unit> {
        val t = transport ?: open()
        isHost = true
        refresh()
        return t.host(roomName)
    }

    suspend fun join(): Result<Unit> {
        val t = transport ?: open()
        isHost = false
        refresh()
        return t.join()
    }

    /**
     * Builds the per-mode session on the open transport. Call as soon as a link exists — the host
     * arms while it is still advertising, so the lobby can offer Start before anyone has joined.
     *
     * Safe to call repeatedly. Re-arming the same signal is a no-op, and a genuine re-arm
     * *detaches* the old session rather than closing it: a session's close() also closes the
     * transport, and PlayHub owns the link. Closing it here dropped the very connection the lobby
     * was sitting on the moment the link flapped LINKED -> LOST -> LINKED. The heartbeat is held
     * in its own job for the same reason: it used to be appended to [jobs] on every arm, so each
     * re-arm left another loop ticking the sessions forever.
     */
    fun arm(mode: GameMode, exerciseId: String, durationSec: Int?) {
        val next = StartSignal(mode, exerciseId, durationSec)
        if (armed == next && transport != null) {
            refresh()
            deliverPending()
            return
        }
        val t = transport ?: open()
        detachSessions()
        // A new contest: the last one's scoreboard must not survive into it.
        finalScore = null
        _result.value = null
        armed = next
        val onRemote = { p: String, s: Int, d: Int -> _remoteHits.tryEmit(RemoteHit(p, s, d)); refresh() }
        when (mode) {
            GameMode.REP_RACE -> race = RepRaceSession(t, playerId, playerName, clock, scope, durationSec ?: 60) { refresh() }
            GameMode.RAID, GameMode.TEAM_VS_TEAM ->
                raid = RaidSession(t, playerId, playerName, clock, scope, isHost, onRemote) { refresh() }
            else -> duel = DuelSession(t, playerId, clock, scope, onRemote) { refresh() }
        }
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive && armed != null) {
                duel?.tick()
                race?.tick()
                raid?.tick()
                refresh()
                delay(HEARTBEAT_MS)
            }
        }
        announceSetup()
        refresh()
        deliverPending()
    }

    /** Hand over whatever arrived too early. Cleared before dispatch, so a replay cannot loop. */
    private fun deliverPending() {
        val held = pending ?: return
        pending = null
        when (held.type) {
            DuelMessage.SETUP -> onSetupMessage(held)
            DuelMessage.START -> onStartMessage(held)
        }
    }

    /**
     * Tell the other phones what the host has chosen. Host only — the guest has no say, so a
     * guest that announced anything would be racing the host's lobby against its own.
     */
    private fun announceSetup() {
        if (!isHost) return
        val a = armed ?: return
        val t = transport ?: return
        t.send(
            DuelMessage(
                type = DuelMessage.SETUP, playerId = playerId, name = playerName,
                exerciseId = a.exerciseId, reps = a.durationSec ?: 0, tMs = clock.nowMs(),
            ),
        )
    }

    /**
     * The host's lobby settings landed. Re-arm on them so the guest's session is built for the
     * movement and the clock it is actually about to race, and so the lobby can name them.
     */
    private fun onSetupMessage(m: DuelMessage) {
        if (isHost) return
        val a = armed
        if (a == null) {
            pending = m
            return
        }
        val exerciseId = m.exerciseId.ifBlank { a.exerciseId }
        val durationSec = m.reps.takeIf { it > 0 } ?: a.durationSec
        arm(a.mode, exerciseId, durationSec)
    }

    /** Host only. Rep Race carries its own START; every other mode sends a plain go signal. */
    suspend fun start(): Result<Unit> {
        val a = armed ?: return Result.failure(IllegalStateException("Nothing is armed."))
        val t = transport ?: return Result.failure(IllegalStateException("No link."))
        val r = race?.startRace(a.exerciseId) ?: runCatching {
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
        val a = armed
        if (a == null) {
            pending = m
            return
        }
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

    /**
     * The fight is over: freeze the scoreboard.
     *
     * It keeps tracking until [release], because the two clocks are not the same clock — the
     * opponent's last rep can land a moment after mine ran out, and a result frozen dead on the
     * buzzer would report a score they had already beaten.
     */
    fun settle(myReps: Int, myDamage: Int) {
        finalScore = myReps to myDamage
        refresh()
    }

    /** Bind the frozen result to the session row it belongs to, once that row has an id. */
    fun tagResult(sessionId: Long) {
        _result.value = _result.value?.copy(sessionId = sessionId)
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
            exerciseId = a.exerciseId,
            durationSec = a.durationSec,
        )
        finalScore?.let { (myReps, myDamage) ->
            // A rep race is settled on reps; everything else on damage, so the deeper set beats
            // the faster one exactly as it does during the fight.
            val onReps = a.mode == GameMode.REP_RACE
            val best = others.maxByOrNull { if (onReps) it.reps else it.damage }
            _result.value = VersusResult(
                mode = a.mode,
                myName = playerName,
                myScore = if (onReps) myReps else myDamage,
                theirName = best?.name ?: "The other phone",
                theirScore = if (onReps) (_link.value?.opponentReps ?: 0) else (_link.value?.opponentDamage ?: 0),
                hadOpponent = (_link.value?.peers ?: 0) > 0,
                unit = if (onReps) "reps" else "damage",
                sessionId = _result.value?.sessionId ?: 0,
            )
        }
    }

    /** Drops the sessions but keeps the link — used when re-arming an already open transport. */
    private fun detachSessions() {
        duel?.detach(); race?.detach(); raid?.detach()
        duel = null; race = null; raid = null
    }

    private fun closeSessions() {
        duel?.close(); race?.close(); raid?.close()
        duel = null; race = null; raid = null
    }

    /** Tears the link down. Safe to call twice. */
    fun release() {
        closeSessions()
        heartbeat?.cancel()
        heartbeat = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        transport?.close()
        transport = null
        armed = null
        pending = null
        // finalScore goes, but _result stays: the summary reads it after the fight is torn down.
        finalScore = null
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
