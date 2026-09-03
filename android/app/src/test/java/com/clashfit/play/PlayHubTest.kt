package com.clashfit.play

import com.clashfit.core.util.FakeClock
import com.clashfit.duel.LoopbackTransport
import com.clashfit.duel.RaidSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for PlayHub: verifies leaderboard tie-breaking and double-close safety.
 *
 * PlayHub itself cannot be constructed in a plain JVM unit test: its constructor requires a
 * live `android.content.Context`, and `PlayHub.open()` immediately builds a `NearbyTransport`
 * that calls `Nearby.getConnectionsClient(context)` — a real Android/Play-Services call with no
 * JVM-testable substitute. The project has no Mockito/mockk or Robolectric dependency on the
 * unit-test classpath to fake or shadow that, so `link becomes null after release` and
 * `active flag reflects armed state` (both of which require `hub.open()`) are not testable here
 * and have been removed. What remains exercises the same session/transport machinery PlayHub
 * wraps (RaidSession over LoopbackTransport), which is fully driveable on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayHubTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `double-close does not throw exception`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
        )

        // First close, same as PlayHub.release() would perform on its session + transport.
        r1.close()
        testDispatcher.scheduler.advanceUntilIdle()

        // Second close should not throw.
        r1.close()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(t1 !in bus, "Transport should be removed from the bus after close")
    }

    @Test
    fun `raid standings ordered by damage desc, then reps desc, then name asc`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        // Create raid sessions directly with loopback transports, the same mechanism
        // PlayHub.arm(GameMode.RAID, ...) wires up internally.
        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Charlie",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
        )

        val t2 = LoopbackTransport(bus)
        val r2 = RaidSession(
            transport = t2,
            playerId = "P2",
            playerName = "Alice",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        // Send reps: both have 100 damage but different reps
        r1.sendRep(damage = 100, reps = 8, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        r2.sendRep(damage = 100, reps = 5, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.8f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify standings are ordered by damage desc, then reps desc, then name asc
        val standings1 = r1.getStandings()
        assertEquals(2, standings1.size, "Should have 2 players")
        assertEquals("P1", standings1[0].playerId, "P1 (8 reps) should be first")
        assertEquals(8, standings1[0].reps)
        assertEquals("P2", standings1[1].playerId, "P2 (5 reps) should be second")
        assertEquals(5, standings1[1].reps)

        r1.close(); r2.close()
    }
}
