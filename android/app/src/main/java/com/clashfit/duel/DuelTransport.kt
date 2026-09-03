package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract transport layer for duel, raid, and rep-race gameplay.
 * Implementations: NearbyTransport, HotspotSocketTransport, BleTransport, LoopbackTransport.
 */
interface DuelTransport {
    /** Current connection state: IDLE, ADVERTISING, SEARCHING, LINKED, or LOST. */
    val state: StateFlow<LinkState>

    /** Incoming messages from peers, broadcast to all subscribers. */
    val incoming: SharedFlow<DuelMessage>

    /**
     * Enter host mode: advertise with a room name. Emits ADVERTISING while searching.
     * Resolves when the first peer connects (transitions to LINKED).
     */
    suspend fun host(roomName: String): Result<Unit>

    /**
     * Enter guest mode: search for a host. Emits SEARCHING while looking.
     * Resolves when a host is found and handshake completes (transitions to LINKED).
     */
    suspend fun join(): Result<Unit>

    /** Send a message to all connected peers. Non-blocking; the message is queued if the link is lost. */
    fun send(msg: DuelMessage)

    /** Tear down the connection and release resources. */
    fun close()
}
