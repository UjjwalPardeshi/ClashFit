package com.clashfit.duel

import android.util.Log
import com.clashfit.core.model.CompactEvent
import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import com.clashfit.core.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Event-sourced duel session with self-healing via recent event tails.
 * Both sides run identical computations over deduplicated events; boss HP emerges as a pure
 * function of the union of all received events. No lockstep, no rollback, no authoritative host.
 *
 * Ported from src/duel.js with identical semantics and edge cases.
 */
class DuelSession(
    private val transport: DuelTransport,
    private val playerId: String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val onRemote: (playerId: String, seq: Int, damage: Int) -> Unit = { _, _, _ -> },
    private val onState: (LinkState) -> Unit = { _ -> },
) {
    private companion object {
        const val TAG = "ClashFit/duel"
        const val TAIL = 8
        const val HEARTBEAT_MS = 1200L
        const val LOST_AFTER_MS = 4000L
    }

    // State tracking
    private var seq = 0
    private val sent = mutableListOf<CompactEvent>()  // recent 8 events from us
    private val applied = mutableSetOf<String>()       // "playerId:seq" dedup keys
    private val peers = mutableMapOf<String, PeerState>()

    // Link state machine
    private var state = LinkState.SEARCHING
    private var lastBeat = 0L

    // Subscription to the transport's incoming messages, cancelled on close()
    private val incomingJob: Job

    init {
        // UNDISPATCHED so the subscription is live before this constructor returns: a collector
        // that only attaches when the scheduler next runs would miss every message sent in the
        // meantime, including the peer's opening HELLO.
        incomingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { msg ->
                receive(msg)
            }
        }
        // Send initial HELLO
        announce()
    }

    /** Broadcast one local rep. */
    fun sendRep(damage: Int, formScore: Float, exerciseId: String, fatigueBand: String) {
        seq += 1
        val ev = CompactEvent(seq, damage)
        sent.add(ev)
        if (sent.size > TAIL) sent.removeAt(0)

        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.REP,
                playerId = playerId,
                seq = seq,
                tMs = clock.nowMs(),
                damage = damage,
                formScore = formScore,
                exerciseId = exerciseId,
                fatigueBand = fatigueBand,
                recent = sent.toList(),
            )
        )
    }

    /** Heartbeat and link state detection. Call periodically (e.g., every 100ms on the UI thread). */
    fun tick(): LinkState {
        val t = clock.nowMs()

        // Send heartbeat if it's been > 1200ms
        if (t - lastBeat > HEARTBEAT_MS) {
            lastBeat = t
            transport.send(
                DuelMessage(
                    v = 1,
                    type = DuelMessage.PING,
                    playerId = playerId,
                    tMs = t
                )
            )
        }

        // Update state based on peer timeouts
        var anyLive = false
        for ((_, peer) in peers) {
            if (t - peer.lastSeenMs < LOST_AFTER_MS) {
                anyLive = true
                break
            }
        }

        // The peer table is the source of truth once anyone has spoken to us; before that (and
        // when the transport itself reports the link gone) we mirror the transport's own state,
        // which is what tells us we are linked but not yet talking.
        val next = when {
            transport.state.value == LinkState.LOST -> LinkState.LOST
            peers.isNotEmpty() -> if (anyLive) LinkState.LINKED else LinkState.LOST
            transport.state.value == LinkState.LINKED -> LinkState.LINKED
            else -> LinkState.SEARCHING
        }

        if (next != state) {
            state = next
            onState(next)
        }

        return state
    }

    /** Get remote damage sum for the current peers. */
    fun remoteDamage(): Int = peers.values.sumOf { it.damage }

    /** Get the count of connected peers. */
    fun peerCount(): Int = peers.size

    /** Clean up and close the transport. */
    /** Stops listening but leaves the link alone. PlayHub owns the transport across a re-arm. */
    fun detach() {
        incomingJob.cancel()
    }

    fun close() {
        detach()
        transport.close()
    }

    // Private implementation

    private fun announce() {
        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.HELLO,
                playerId = playerId,
                tMs = clock.nowMs()
            )
        )
    }

    private fun welcome() {
        transport.send(
            DuelMessage(
                v = 1,
                type = DuelMessage.WELCOME,
                playerId = playerId,
                seq = seq,
                tMs = clock.nowMs(),
                recent = sent.toList()
            )
        )
    }

    private fun receive(msg: DuelMessage) {
        // Ignore self-messages
        if (msg.playerId == playerId) return

        val peer = peers.getOrPut(msg.playerId) {
            PeerState(playerId = msg.playerId)
        }
        peer.lastSeenMs = clock.nowMs()

        // HELLO: do not answer with HELLO (would loop forever). Reply with WELCOME if we know them.
        if (msg.type == DuelMessage.HELLO) {
            welcome()
            return
        }

        // PING and other non-data messages
        if (msg.type == DuelMessage.PING) return
        if (msg.type != DuelMessage.REP && msg.type != DuelMessage.WELCOME) return

        // Update peer state
        peer.fatigueBand = msg.fatigueBand

        // Apply the message and its tail (idempotent by construction)
        // duel.js: `m.recent ?? [[m.seq, m.damage]]` — an empty tail carries nothing (a WELCOME
        // from a peer who has not scored yet); only a REP without a tail falls back to its own event.
        val events = when {
            msg.recent.isNotEmpty() -> msg.recent
            msg.type == DuelMessage.REP -> listOf(CompactEvent(msg.seq, msg.damage))
            else -> emptyList()
        }
        for ((eventSeq, eventDamage) in events) {
            apply(msg.playerId, eventSeq, eventDamage)
        }
    }

    private fun apply(playerId: String, seq: Int, damage: Int) {
        val key = "$playerId:$seq"
        if (key in applied) return  // Already applied

        applied.add(key)
        val peer = peers[playerId]
        if (peer != null) {
            peer.damage += damage
        }
        onRemote(playerId, seq, damage)
    }

    // Peer state
    private data class PeerState(
        val playerId: String,
        var lastSeenMs: Long = 0,
        var damage: Int = 0,
        var fatigueBand: String = "FRESH",
    )
}
