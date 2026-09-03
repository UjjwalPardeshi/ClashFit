package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test suite for RepRaceSession: timed race by rep count.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepRaceSessionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `race standings sorted by reps descending`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        val t2 = LoopbackTransport(bus)
        val r2 = RepRaceSession(
            transport = t2,
            playerId = "P2",
            playerName = "Player 2",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        // P1 does 5 reps
        r1.sendRep(currentReps = 5, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        // P2 does 3 reps
        r2.sendRep(currentReps = 3, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        // Check standings: P1 should be first
        val standings = r1.getStandings()
        assertEquals(2, standings.size)
        assertEquals("P1", standings[0].playerId, "P1 with 5 reps should be first")
        assertEquals(5, standings[0].reps)
        assertEquals("P2", standings[1].playerId, "P2 with 3 reps should be second")
        assertEquals(3, standings[1].reps)

        r1.close(); r2.close()
    }

    @Test
    fun `race start sets race time`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        assertEquals(null, r1.raceStartedMs.value, "Race should not be started yet")

        val result = r1.startRace()
        assertEquals(true, result.isSuccess)

        testDispatcher.scheduler.advanceUntilIdle()

        val startMs = r1.raceStartedMs.value
        assertTrue(startMs != null && startMs > 0, "Race should be started")
        // Should be about 3000ms offset from now
        assertEquals(3000, startMs - clock.nowMs(), "Race should start 3 seconds from now")

        r1.close()
    }

    @Test
    fun `race finishes after duration`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 30,
        )

        r1.startRace()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(r1.isRaceFinished(), "Race should not be finished immediately")

        // Advance time past the race duration
        clock.advance(35000)  // 35 seconds

        assertTrue(r1.isRaceFinished(), "Race should be finished after 35 seconds")

        r1.close()
    }

    @Test
    fun `standings updated on new rep`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val changes = mutableListOf<List<RaceStanding>>()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
            onStandingChanged = { changes.add(it) },
        )

        r1.sendRep(currentReps = 3, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(changes.isNotEmpty(), "Standing change callback should fire")
        assertEquals(1, changes[0].size)
        assertEquals(3, changes[0][0].reps)

        r1.close()
    }

    @Test
    fun `multiple reps from same player update count`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        // Send multiple rep updates
        r1.sendRep(currentReps = 1, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        r1.sendRep(currentReps = 2, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        r1.sendRep(currentReps = 5, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        val standings = r1.getStandings()
        assertEquals(1, standings.size)
        assertEquals(5, standings[0].reps, "Final rep count should be 5")

        r1.close()
    }

    @Test
    fun `heartbeat tick sends PING after HEARTBEAT_MS`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        // Create a peer transport to listen for messages sent by r1
        val t2 = LoopbackTransport(bus)
        val messages = mutableListOf<DuelMessage>()

        // Collect messages sent by r1 (received on t2)
        backgroundScope.launch {
            t2.incoming.collect { msg ->
                messages.add(msg)
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial tick
        r1.tick()
        testDispatcher.scheduler.advanceUntilIdle()
        val beforeAdvance = messages.size

        // Advance time to trigger heartbeat
        clock.advance(1300) // > 1200ms HEARTBEAT_MS
        r1.tick()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify PING was sent after advancing time
        assertTrue(messages.size > beforeAdvance, "Messages should have been sent after heartbeat timer")
        val pingMessages = messages.filter { it.type == "PING" }
        assertTrue(pingMessages.isNotEmpty(), "PING message should have been sent after HEARTBEAT_MS")

        r1.close(); t2.close()
    }

    @Test
    fun `player timeout after LOST_AFTER_MS removes player from standings`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        val t2 = LoopbackTransport(bus)
        val r2 = RepRaceSession(
            transport = t2,
            playerId = "P2",
            playerName = "Player 2",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        // Both send reps
        r1.sendRep(currentReps = 5, exerciseId = "squat")
        r2.sendRep(currentReps = 3, exerciseId = "squat")
        testDispatcher.scheduler.advanceUntilIdle()

        // P1 sees both players
        var standings1 = r1.getStandings()
        assertEquals(2, standings1.size, "P1 should see both players")

        // Advance time past LOST_AFTER_MS (4000ms)
        clock.advance(4100)
        r1.tick()
        testDispatcher.scheduler.advanceUntilIdle()

        // P1 should have only itself now (P2 was lost)
        standings1 = r1.getStandings()
        assertEquals(1, standings1.size, "P1 should see only itself after P2 times out")
        assertEquals("P1", standings1[0].playerId)

        r1.close(); r2.close()
    }

}
