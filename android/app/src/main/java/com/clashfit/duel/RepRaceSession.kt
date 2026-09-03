package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.util.Clock
import kotlinx.coroutines.CoroutineScope
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
    }

    // Race state
    private val _standings = MutableStateFlow<List<RaceStanding>>(emptyList())
    val standings: StateFlow<List<RaceStanding>> = _standings.asStateFlow()

    private val _raceStartedMs = MutableStateFlow<Long?>(null)
    val raceStartedMs: StateFlow<Long?> = _raceStartedMs.asStateFlow()

    // Player tracking
    private val playerReps = mutableMapOf<String, Int>()  // playerId -> reps count
    private val playerSeqs = mutableMapOf<String, MutableSet<Int>>()  // dedupe by seq

    init {
        playerReps[playerId] = 0
        playerSeqs[playerId] = mutableSetOf()

        // Start listening for messages
        scope.launch {
            transport.incoming.collect { msg ->
                processMessage(msg)
            }
        }
    }

    /** Host broadcasts the race start with a 3-second offset. */
    suspend fun startRace(): Result<Unit> {
        val startOffsetMs = clock.nowMs() + 3000L
        _raceStartedMs.value = startOffsetMs

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.START,
                playerId = playerId,
                tMs = clock.nowMs(),
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

    /** Tear down the race. */
    fun close() {
        transport.close()
    }

    // Private implementation

    private fun processMessage(msg: DuelMessage) {
        when (msg.type) {
            DuelMessage.START -> {
                if (msg.playerId != playerId) {
                    val startOffsetMs = msg.payload.toLongOrNull() ?: return
                    _raceStartedMs.value = startOffsetMs
                }
            }

            DuelMessage.REP -> {
                if (msg.playerId == playerId) return  // Ignore self-rep messages

                val seqs = playerSeqs.getOrPut(msg.playerId) { mutableSetOf() }
                if (msg.seq !in seqs) {
                    seqs.add(msg.seq)
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
