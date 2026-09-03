package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import kotlin.random.Random
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
 */
class LoopbackTransport(
    private val bus: MutableSet<LoopbackTransport>,
    private val dropRate: Float = 0f,
    private val duplicateRate: Float = 0f,
) : DuelTransport {

    private val _state = MutableStateFlow(LinkState.IDLE)
    private val _incoming = MutableSharedFlow<DuelMessage>(extraBufferCapacity = 64)

    override val state: StateFlow<LinkState> = _state.asStateFlow()
    override val incoming: SharedFlow<DuelMessage> = _incoming.asSharedFlow()

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
        // Deliver to all other transports on the bus, with optional loss/duplication
        for (transport in bus) {
            if (transport !== this) {
                if (Random.nextFloat() >= dropRate) {
                    transport._incoming.tryEmit(msg.copy())
                }
                // Duplicate if the random value is within the duplicate rate
                if (Random.nextFloat() < duplicateRate) {
                    transport._incoming.tryEmit(msg.copy())
                }
            }
        }
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
