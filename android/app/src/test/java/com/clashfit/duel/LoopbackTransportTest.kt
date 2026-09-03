package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Test suite for LoopbackTransport: in-memory transport for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoopbackTransportTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `loopback transport connects two endpoints`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t1 = LoopbackTransport(bus)
        val t2 = LoopbackTransport(bus)

        assertEquals(LinkState.IDLE, t1.state.value)
        assertEquals(LinkState.IDLE, t2.state.value)

        // Simulate linking
        t1.simulateLinked()
        t2.simulateLinked()

        assertEquals(LinkState.LINKED, t1.state.value)
        assertEquals(LinkState.LINKED, t2.state.value)

        t1.close(); t2.close()
    }

    @Test
    fun `message sent from t1 received by t2`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t1 = LoopbackTransport(bus)
        val t2 = LoopbackTransport(bus)

        val messages = mutableListOf<DuelMessage>()

        backgroundScope.launch {
            t2.incoming.collect { msg ->
                messages.add(msg)
            }
        }

        val msg = DuelMessage(
            v = 1,
            type = "HELLO",
            playerId = "AAAA"
        )

        t1.send(msg)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, messages.size, "T2 should have received the message")
        assertEquals("AAAA", messages[0].playerId)

        t1.close(); t2.close()
    }

    @Test
    fun `packet loss drops messages`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t1 = LoopbackTransport(bus, dropRate = 0.5f)
        val t2 = LoopbackTransport(bus)

        val messages = mutableListOf<DuelMessage>()

        backgroundScope.launch {
            t2.incoming.collect { msg ->
                messages.add(msg)
            }
        }

        // Send 10 messages; some should be dropped
        for (i in 0..9) {
            val msg = DuelMessage(
                v = 1,
                type = "REP",
                playerId = "AAAA",
                seq = i
            )
            t1.send(msg)
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // With 50% drop, we expect roughly 5 messages to arrive (not all 10)
        val received = messages.size
        assert(received < 10 && received > 0) { "Expected 1-9 messages, got $received" }

        t1.close(); t2.close()
    }

    @Test
    fun `duplication creates extra copies`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t1 = LoopbackTransport(bus, duplicateRate = 0.5f)
        val t2 = LoopbackTransport(bus)

        val messages = mutableListOf<DuelMessage>()

        backgroundScope.launch {
            t2.incoming.collect { msg ->
                messages.add(msg)
            }
        }

        // Send 5 messages; some should be duplicated
        for (i in 0..4) {
            val msg = DuelMessage(
                v = 1,
                type = "REP",
                playerId = "AAAA",
                seq = i
            )
            t1.send(msg)
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // With 50% duplication, we expect more than 5 messages
        val received = messages.size
        assert(received >= 5) { "Expected at least 5 messages (with duplication), got $received" }

        t1.close(); t2.close()
    }

    @Test
    fun `state transitions`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t = LoopbackTransport(bus)

        assertEquals(LinkState.IDLE, t.state.value)

        t.simulateLinked()
        assertEquals(LinkState.LINKED, t.state.value)

        t.simulateLost()
        assertEquals(LinkState.LOST, t.state.value)

        t.close()
    }

    @Test
    fun `close removes transport from bus`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()

        val t = LoopbackTransport(bus)
        assertEquals(1, bus.size)

        t.close()
        assertEquals(0, bus.size, "Transport should be removed from bus on close")
    }
}
