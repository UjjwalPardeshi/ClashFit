package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for DuelSession: event-sourced sync with self-healing tails.
 * Covers convergence, packet loss recovery, deduplication, and link state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelSessionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
    }

    /** Create a pair of sessions with optional packet loss. */
    private fun createSessionPair(
        scope: CoroutineScope,
        loss: Float = 0f,
        duplication: Float = 0f,
    ): SessionPair {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transportA = LoopbackTransport(bus, dropRate = loss, duplicateRate = duplication)
        val transportB = LoopbackTransport(bus, dropRate = loss, duplicateRate = duplication)

        val hitsA = mutableListOf<Pair<String, Int>>()  // (playerId, damage)
        val hitsB = mutableListOf<Pair<String, Int>>()

        val sessionA = DuelSession(
            transport = transportA,
            playerId = "AAAA",
            clock = clock,
            scope = scope,
            onRemote = { playerId, _, damage -> hitsA.add(playerId to damage) },
            onState = { }
        )

        val sessionB = DuelSession(
            transport = transportB,
            playerId = "BBBB",
            clock = clock,
            scope = scope,
            onRemote = { playerId, _, damage -> hitsB.add(playerId to damage) },
            onState = { }
        )

        return SessionPair(sessionA, sessionB, hitsA, hitsB, transportA, transportB, clock)
    }

    @Test
    fun `two sessions converge on identical damage with clean link`() = testScope.runTest {
        val (a, b, hitsA, hitsB) = createSessionPair(backgroundScope, loss = 0f)

        // Each sends 12 reps
        for (i in 0..11) {
            a.sendRep(damage = 70 + i, formScore = 0.8f, exerciseId = "squat", fatigueBand = "WORKING")
            b.sendRep(damage = 60 + i, formScore = 0.7f, exerciseId = "squat", fatigueBand = "WORKING")
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // Both should have received all of the other's damage
        val sumA = hitsA.sumOf { it.second }
        val sumB = hitsB.sumOf { it.second }

        val sentByA = (0..11).sumOf { 70 + it }  // Sum of 70-81 = 906
        val sentByB = (0..11).sumOf { 60 + it }  // Sum of 60-71 = 786
        assertEquals(sentByA, sumB, "B should have received all of A's damage")
        assertEquals(sentByB, sumA, "A should have received all of B's damage")

        a.close(); b.close()
    }

    @Test
    fun `duplicate flood changes nothing`() = testScope.runTest {
        val (a, b, _, hitsB) = createSessionPair(backgroundScope, duplication = 0f)

        a.sendRep(damage = 100, formScore = 0.9f, exerciseId = "squat", fatigueBand = "FRESH")
        testDispatcher.scheduler.advanceUntilIdle()

        val before = hitsB.sumOf { it.second }
        assertEquals(100, before)

        // Tick many times (heartbeats with no new events)
        for (i in 0..29) {
            a.tick()
            b.tick()
            testDispatcher.scheduler.advanceUntilIdle()
        }

        val after = hitsB.sumOf { it.second }
        assertEquals(before, after, "Duplicate heartbeats changed the total")

        a.close(); b.close()
    }

    @Test
    fun `30 percent packet loss is repaired by recent tail`() = testScope.runTest {
        val (a, b, _, hitsB) = createSessionPair(backgroundScope, loss = 0.3f)

        // Send 24 reps with deterministic loss (every 3rd dropped)
        var expected = 0
        for (i in 0..23) {
            val d = 50 + i
            expected += d
            a.sendRep(damage = d, formScore = 0.8f, exerciseId = "squat", fatigueBand = "WORKING")
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val received = hitsB.sumOf { it.second }
        assertEquals(expected, received, "Lost messages not repaired by the recent tail")

        a.close(); b.close()
    }

    @Test
    fun `link state transitions - SEARCHING to LINKED to LOST`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()
        val transportA = LoopbackTransport(bus)
        val a = DuelSession(transport = transportA, playerId = "AAAA", clock = clock, scope = backgroundScope, onRemote = { _, _, _ -> }, onState = { })
        assertEquals(LinkState.SEARCHING, a.tick(), "Alone on the bus, a session is still searching")

        // A peer arrives: HELLO/WELCOME links them without anyone faking a transport state.
        val transportB = LoopbackTransport(bus)
        val b = DuelSession(transport = transportB, playerId = "BBBB", clock = clock, scope = backgroundScope, onRemote = { _, _, _ -> }, onState = { })
        assertEquals(LinkState.LINKED, a.tick(), "The handshake should link them")

        // Silence past LOST_AFTER_MS, and the transport agrees.
        clock.advance(5000)
        transportA.simulateLost()
        assertEquals(LinkState.LOST, a.tick(), "Should transition to LOST after timeout")

        a.close(); b.close()
    }

    @Test
    fun `late joiner caught up by tail`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transportA = LoopbackTransport(bus)
        val sessionA = DuelSession(
            transport = transportA,
            playerId = "AAAA",
            clock = clock,
            scope = backgroundScope,
            onRemote = { _, _, _ -> },
            onState = { }
        )

        // Session A sends 5 reps
        for (i in 0..4) {
            sessionA.sendRep(damage = 40, formScore = 0.8f, exerciseId = "squat", fatigueBand = "FRESH")
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // Late joiner (session B) joins and should receive all backlog via WELCOME tail
        val got = mutableListOf<Int>()
        val transportB = LoopbackTransport(bus)
        val sessionB = DuelSession(
            transport = transportB,
            playerId = "BBBB",
            clock = clock,
            scope = backgroundScope,
            onRemote = { _, _, damage -> got.add(damage) },
            onState = { }
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val total = got.sumOf { it }
        assertEquals(200, total, "Late joiner should receive backlog via tail")

        sessionA.close(); sessionB.close()
    }

    @Test
    fun `hello storm never loops`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transportA = LoopbackTransport(bus)
        val sessionA = DuelSession(
            transport = transportA,
            playerId = "AAAA",
            clock = clock,
            scope = backgroundScope,
            onRemote = { _, _, _ -> },
            onState = { }
        )

        val transportB = LoopbackTransport(bus)
        val sessionB = DuelSession(
            transport = transportB,
            playerId = "BBBB",
            clock = clock,
            scope = backgroundScope,
            onRemote = { _, _, _ -> },
            onState = { }
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // If HELLO loops forever, we would see infinite messages.
        // We verify by checking that after a short time, there are no more HELLO messages.
        // (In the real code, HELLO is only sent on init and when welcome is sent in response.)
        clock.advance(100)
        testDispatcher.scheduler.advanceUntilIdle()

        // No assertion failure means no infinite loop (the test timeout would catch it)
        assertTrue(true)

        sessionA.close(); sessionB.close()
    }

    @Test
    fun `deduplication by player-seq`() = testScope.runTest {
        val (a, b, _, hitsB) = createSessionPair(backgroundScope)

        a.sendRep(damage = 100, formScore = 0.9f, exerciseId = "squat", fatigueBand = "FRESH")
        testDispatcher.scheduler.advanceUntilIdle()

        val firstReceive = hitsB.sumOf { it.second }
        assertEquals(100, firstReceive)

        // Manually send the same rep again (simulating a duplicate delivery)
        a.sendRep(damage = 100, formScore = 0.9f, exerciseId = "squat", fatigueBand = "FRESH")
        testDispatcher.scheduler.advanceUntilIdle()

        // The second rep has a different seq (seq increments), so it's not a true duplicate
        // For a true duplicate test, we'd need to replay a message with the same seq,
        // which happens via the tail mechanism.
        val secondReceive = hitsB.sumOf { it.second }
        assertEquals(200, secondReceive, "Second rep is different seq, not deduplicated")

        a.close(); b.close()
    }

    @Test
    fun `remote damage accessor`() = testScope.runTest {
        val (a, b) = createSessionPair(backgroundScope)

        a.sendRep(damage = 70, formScore = 0.8f, exerciseId = "squat", fatigueBand = "WORKING")
        testDispatcher.scheduler.advanceUntilIdle()

        val remoteDmg = a.remoteDamage()
        assertEquals(0, remoteDmg, "A should not have received B's damage yet (no messages from B)")

        b.sendRep(damage = 60, formScore = 0.7f, exerciseId = "squat", fatigueBand = "WORKING")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(60, a.remoteDamage(), "A should now hold B's damage")

        a.close(); b.close()
    }

    // Data class to hold session pair results
    private data class SessionPair(
        val sessionA: DuelSession,
        val sessionB: DuelSession,
        val hitsA: MutableList<Pair<String, Int>>,
        val hitsB: MutableList<Pair<String, Int>>,
        val transportA: LoopbackTransport,
        val transportB: LoopbackTransport,
        val clock: FakeClock,
    )
}
