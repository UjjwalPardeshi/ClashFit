package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Immutable snapshot of a rep-race player's progress. */
data class RaceStanding(
    val playerId: String,
    val name: String,
    val reps: Int,
    val finished: Boolean,
)

/**
 * Rep-race session: host broadcasts START with a duration; each phone counts local verified reps
 * and broadcasts REP with reps count. Standings are by reps (higher is better). Race ends when
 * the duration expires.
 */
class RepRaceSession(
    private val transport: DuelTransport,
    private val playerId: String,
    private val playerName: String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val durationSec: Int,
    private val onStandingChanged: (List<RaceStanding>) -> Unit = { _ -> },
) {
    private companion object {
        const val TAG = "ClashFit/race"
        const val HEARTBEAT_MS = 1200L
        const val LOST_AFTER_MS = 4000L
    }

    // Race state
    private val _standings = MutableStateFlow<List<RaceStanding>>(emptyList())
    val standings: StateFlow<List<RaceStanding>> = _standings.asStateFlow()

    private val _raceStartedMs = MutableStateFlow<Long?>(null)
    val raceStartedMs: StateFlow<Long?> = _raceStartedMs.asStateFlow()

    // Player tracking
    private val playerReps = mutableMapOf<String, Int>()  // playerId -> reps count
    /** The highest sequence applied per player; an older packet is dropped, not applied. */
    private val playerNewestSeq = mutableMapOf<String, Int>()

    private val playerSeqs = mutableMapOf<String, MutableSet<Int>>()  // dedupe by seq
    private val playerLastSeenMs = mutableMapOf<String, Long>()  // Track last message from each player
    private var lastBeatMs = 0L  // Track last heartbeat send

    /** Subscription to the transport's incoming messages, cancelled on close(). */
    private val incomingJob: Job

    init {
        playerReps[playerId] = 0
        playerSeqs[playerId] = mutableSetOf()
        playerLastSeenMs[playerId] = clock.nowMs()

        // Start listening for messages. UNDISPATCHED so the subscription is live before the
        // constructor returns, instead of whenever the scheduler next gets a turn.
        incomingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { msg ->
                processMessage(msg)
            }
        }
    }

    /**
     * Host broadcasts the race start with a 3-second offset.
     *
     * The clock and the exercise ride along on the START. Without them a guest raced whatever it
     * had picked on its own lobby, so a 30-second host and a 60-second guest ran different races
     * and the standings compared two different contests.
     */
    suspend fun startRace(exerciseId: String = ""): Result<Unit> {
        val startOffsetMs = clock.nowMs() + 3000L
        _raceStartedMs.value = startOffsetMs

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.START,
                playerId = playerId,
                tMs = clock.nowMs(),
                reps = durationSec,
                exerciseId = exerciseId,
                payload = startOffsetMs.toString()
            )
        )

        return Result.success(Unit)
    }

    /** Broadcast a counted rep. */
    fun sendRep(currentReps: Int, exerciseId: String = "") {
        val seq = (playerSeqs[playerId]?.size ?: 0) + 1
        playerReps[playerId] = currentReps
        playerSeqs.getOrPut(playerId) { mutableSetOf() }.add(seq)

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.REP,
                playerId = playerId,
                name = playerName,
                seq = seq,
                tMs = clock.nowMs(),
                reps = currentReps,
                exerciseId = exerciseId
            )
        )

        updateStandings()
    }

    /** Get the current race standings (sorted by reps, descending). */
    fun getStandings(): List<RaceStanding> = _standings.value

    /** Check if the race is finished based on elapsed time. */
    fun isRaceFinished(): Boolean {
        val startMs = _raceStartedMs.value ?: return false
        val elapsedMs = clock.nowMs() - startMs
        return elapsedMs >= durationSec * 1000L
    }

    /** Heartbeat and player timeout detection. Call periodically (e.g., every 500ms). */
    fun tick() {
        val t = clock.nowMs()

        // Send heartbeat PING if it's been > HEARTBEAT_MS since last beat
        if (t - lastBeatMs > HEARTBEAT_MS) {
            lastBeatMs = t
            transport.send(
                DuelMessage(
                    v = 1,
                    type = DuelMessage.PING,
                    playerId = playerId,
                    tMs = t
                )
            )
        }

        // Check for lost players and remove them from standings if silent for too long
        val lostPlayers = playerReps.keys.filter { id ->
            id != playerId && (t - (playerLastSeenMs[id] ?: t)) > LOST_AFTER_MS
        }

        var changed = false
        for (lostId in lostPlayers) {
            playerReps.remove(lostId)
            playerSeqs.remove(lostId)
            playerLastSeenMs.remove(lostId)
            // Same reason as the raid: a reconnecting player restarts their counter, and a stale
            // high-water mark rejects everything they send afterwards.
            playerNewestSeq.remove(lostId)
            changed = true
        }

        if (changed) {
            updateStandings()
        }
    }

    /** Stops listening but leaves the link alone. PlayHub owns the transport across a re-arm. */
    fun detach() {
        incomingJob.cancel()
    }

    /** Tear down the race. Stops the collector; a session must not outlive its close(). */
    fun close() {
        detach()
        transport.close()
    }

    // Private implementation

    private fun processMessage(msg: DuelMessage) {
        // Track when we last saw this player (for non-self messages)
        if (msg.playerId != playerId) {
            playerLastSeenMs[msg.playerId] = clock.nowMs()
        }

        when (msg.type) {
            DuelMessage.START -> {
                if (msg.playerId != playerId) {
                    val startOffsetMs = msg.payload.toLongOrNull() ?: return
                    _raceStartedMs.value = startOffsetMs
                }
            }

            DuelMessage.REP -> {
                if (msg.playerId == playerId) return  // Ignore self-rep messages

                // A rep count is a snapshot, so the newest packet wins rather than the first
                // unseen one. Tracking "have I seen this sequence" let a packet delayed behind a
                // later one overwrite the newer count with an older number, and the opponent's
                // reps visibly counted down.
                val seqs = playerSeqs.getOrPut(msg.playerId) { mutableSetOf() }
                val newest = playerNewestSeq[msg.playerId] ?: Int.MIN_VALUE
                if (msg.seq !in seqs && msg.seq > newest) {
                    seqs.add(msg.seq)
                    playerNewestSeq[msg.playerId] = msg.seq
                    playerReps[msg.playerId] = msg.reps
                    updateStandings()
                }
            }

            else -> {
                // Ignore other types
            }
        }
    }

    private fun updateStandings() {
        val standings = playerReps
            .map { (id, reps) ->
                RaceStanding(
                    playerId = id,
                    name = if (id == playerId) playerName else id,
                    reps = reps,
                    finished = isRaceFinished()
                )
            }
            .sortedByDescending { it.reps }

        _standings.value = standings
        onStandingChanged(standings)
    }
}
