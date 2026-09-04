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

/** Immutable snapshot of a raid player's performance. */
data class RaidStanding(
    val playerId: String,
    val name: String,
    val damage: Int,
    val reps: Int,
    val fatigueBand: String,
    val out: Boolean,  // Has this player finished or quit?
)

/**
 * Raid session: N players in a star topology where each phone computes the boss independently.
 * The host relays messages between all players; every phone receives everyone's damage and
 * maintains a live leaderboard. Last Standing sends OUT when local fatigue band hits GASSED.
 */
class RaidSession(
    private val transport: DuelTransport,
    private val playerId: String,
    private val playerName: String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val isHost: Boolean,
    private val onRemote: (playerId: String, seq: Int, damage: Int) -> Unit = { _, _, _ -> },
    private val onStandingChanged: (List<RaidStanding>) -> Unit = { _ -> },
) {
    private companion object {
        const val TAG = "ClashFit/raid"
        const val HEARTBEAT_MS = 1200L
        const val LOST_AFTER_MS = 4000L
    }

    // Leaderboard state
    private val _standings = MutableStateFlow<List<RaidStanding>>(emptyList())
    val standings: StateFlow<List<RaidStanding>> = _standings.asStateFlow()

    // Player tracking
    private val playerStates = mutableMapOf<String, PlayerRaidState>()
    /** The highest sequence seen per player, so a late packet cannot rewind their snapshot. */
    private val newestSeq = mutableMapOf<String, Int>()

    private val appliedDamage = mutableMapOf<String, MutableSet<Int>>()  // playerId -> set of applied seqs
    private val playerLastSeenMs = mutableMapOf<String, Long>()  // Track last message from each player
    private var lastBeatMs = 0L  // Track last heartbeat send

    /** Subscription to the transport's incoming messages, cancelled on close(). */
    private val incomingJob: Job

    init {
        // Register ourselves
        playerStates[playerId] = PlayerRaidState(playerId, playerName)
        appliedDamage[playerId] = mutableSetOf()
        playerLastSeenMs[playerId] = clock.nowMs()

        // Start listening for messages. UNDISPATCHED so the subscription is live before the
        // constructor returns, instead of whenever the scheduler next gets a turn.
        incomingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { msg ->
                processMessage(msg)
            }
        }
    }

    /** Broadcast a rep in the raid. */
    fun sendRep(damage: Int, reps: Int, exerciseId: String, fatigueBand: String, formScore: Float) {
        val playerState = playerStates[playerId] ?: return
        val seq = ++playerState.seq

        appliedDamage.getOrPut(playerId) { mutableSetOf() }.add(seq)

        // Update local state
        playerState.damage += damage
        playerState.reps = reps
        playerState.fatigueBand = fatigueBand

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.REP,
                playerId = playerId,
                name = playerName,
                seq = seq,
                tMs = clock.nowMs(),
                damage = damage,
                reps = reps,
                exerciseId = exerciseId,
                fatigueBand = fatigueBand,
                formScore = formScore
            )
        )

        updateLeaderboard()
    }

    /** Player is out of the raid (finished or quit). */
    fun sendOut() {
        val playerState = playerStates[playerId] ?: return

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.OUT,
                playerId = playerId,
                name = playerName,
                tMs = clock.nowMs()
            )
        )

        playerState.out = true
        updateLeaderboard()
    }

    /** Get the current leaderboard. */
    fun getStandings(): List<RaidStanding> = _standings.value

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

        // Check for lost players and remove them from leaderboard if silent for too long
        val lostPlayers = playerStates.keys.filter { id ->
            id != playerId && (t - (playerLastSeenMs[id] ?: t)) > LOST_AFTER_MS
        }

        var changed = false
        for (lostId in lostPlayers) {
            // Mark as effectively lost by removing from standings
            playerStates.remove(lostId)
            playerLastSeenMs.remove(lostId)
            appliedDamage.remove(lostId)
            changed = true
        }

        if (changed) {
            updateLeaderboard()
        }
    }

    /** Tear down the raid. Stops the collector; a session must not outlive its close(). */
    fun close() {
        incomingJob.cancel()
        transport.close()
    }

    // Private implementation

    private fun processMessage(msg: DuelMessage) {
        if (msg.playerId == playerId) return  // Ignore self

        // Track when we last saw this player
        playerLastSeenMs[msg.playerId] = clock.nowMs()

        when (msg.type) {
            DuelMessage.REP -> {
                val playerState = playerStates.getOrPut(msg.playerId) {
                    PlayerRaidState(msg.playerId, msg.name)
                }

                // Damage accumulates, so it is deduplicated by sequence: applying the same
                // packet twice would invent damage nobody did.
                val seqs = appliedDamage.getOrPut(msg.playerId) { mutableSetOf() }
                if (msg.seq !in seqs) {
                    seqs.add(msg.seq)
                    playerState.damage += msg.damage
                    onRemote(msg.playerId, msg.seq, msg.damage)
                }

                // Reps and fatigue are a snapshot, not an increment, so only a NEWER packet may
                // replace them. These used to be written by every message that arrived, including
                // one delayed behind a later one — which made the board count backwards and the
                // fatigue band flick to a state the player had already left.
                val newest = newestSeq[msg.playerId] ?: Int.MIN_VALUE
                if (msg.seq > newest) {
                    newestSeq[msg.playerId] = msg.seq
                    playerState.reps = msg.reps
                    playerState.fatigueBand = msg.fatigueBand
                }
                updateLeaderboard()
            }

            DuelMessage.OUT -> {
                val playerState = playerStates.getOrPut(msg.playerId) {
                    PlayerRaidState(msg.playerId, msg.name)
                }
                playerState.out = true
                updateLeaderboard()
            }

            else -> {
                // Ignore other message types
            }
        }
    }

    private fun updateLeaderboard() {
        val standings = playerStates.values
            .sortedWith(
                compareByDescending<PlayerRaidState> { it.damage }
                    .thenByDescending { it.reps }
                    .thenBy { it.name }
            )
            .map { state ->
                RaidStanding(
                    playerId = state.playerId,
                    name = state.name,
                    damage = state.damage,
                    reps = state.reps,
                    fatigueBand = state.fatigueBand,
                    out = state.out
                )
            }

        _standings.value = standings
        onStandingChanged(standings)
    }

    private data class PlayerRaidState(
        val playerId: String,
        val name: String,
        var seq: Int = 0,
        var damage: Int = 0,
        var reps: Int = 0,
        var fatigueBand: String = "FRESH",
        var out: Boolean = false,
    )
}
