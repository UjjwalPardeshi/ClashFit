package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory transport for tests and deterministic replay of duels.
 * Messages sent to this transport are delivered to all other transports on the same bus.
 * Supports configurable packet loss and duplication for testing resilience.
 *
 * Loss and duplication are *deterministic*, not random: the nth send is dropped when the running
 * count of expected drops (`n * dropRate`) ticks over an integer. At 0.5 that drops every second
 * packet, at 0.3 roughly every third. A replay of the same script therefore loses exactly the same
 * packets every time, which is the only way a self-healing protocol can be asserted on exactly.
 */
class LoopbackTransport(
    private val bus: MutableSet<LoopbackTransport>,
    private val dropRate: Float = 0f,
    private val duplicateRate: Float = 0f,
) : DuelTransport {

    private companion object {
        /**
         * Messages emitted before anyone subscribes must not vanish: a session's collector is
         * launched on a scheduler that may not have run yet, and the very first HELLO is sent
         * before any peer has attached. A replay cache makes delivery independent of subscribe
         * order; the extra buffer absorbs a burst that a suspended collector has not drained.
         */
        const val REPLAY = 64
        const val EXTRA_BUFFER = 256
    }

    private val _state = MutableStateFlow(LinkState.IDLE)
    private val _incoming = MutableSharedFlow<DuelMessage>(
        replay = REPLAY,
        extraBufferCapacity = EXTRA_BUFFER,
    )

    override val state: StateFlow<LinkState> = _state.asStateFlow()
    override val incoming: SharedFlow<DuelMessage> = _incoming.asSharedFlow()

    // Deterministic loss/duplication bookkeeping.
    private var sends = 0L
    private var drops = 0L
    private var duplicates = 0L

    init {
        bus.add(this)
    }

    override suspend fun host(roomName: String): Result<Unit> {
        _state.value = LinkState.ADVERTISING
        return Result.success(Unit)
    }

    override suspend fun join(): Result<Unit> {
        _state.value = LinkState.SEARCHING
        return Result.success(Unit)
    }

    override fun send(msg: DuelMessage) {
        sends += 1
        val dropped = tick(dropRate, drops).also { if (it) drops += 1 }
        val duplicated = tick(duplicateRate, duplicates).also { if (it) duplicates += 1 }
        if (dropped) return

        // Snapshot the bus: a peer's collector may close a transport while we are fanning out.
        for (peer in bus.toList()) {
            if (peer === this) continue
            peer._incoming.tryEmit(msg)
            if (duplicated) peer._incoming.tryEmit(msg)
        }
    }

    /** True when the expected count of events at [rate] has ticked past [soFar] on this send. */
    private fun tick(rate: Float, soFar: Long): Boolean {
        if (rate <= 0f) return false
        return (sends * rate.toDouble()).toLong() > soFar
    }

    override fun close() {
        bus.remove(this)
    }

    // Test support: simulate state transitions
    fun simulateLinked() {
        _state.value = LinkState.LINKED
    }

    fun simulateLost() {
        _state.value = LinkState.LOST
    }
}
