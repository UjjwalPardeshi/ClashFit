package com.clashfit.duel

import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for RaidSession: N-player raid with live leaderboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaidSessionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `raid leaderboard tracks damage and reps`() = testScope.runTest {
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

        val t2 = LoopbackTransport(bus)
        val r2 = RaidSession(
            transport = t2,
            playerId = "P2",
            playerName = "Player 2",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        // P1 sends a rep
        r1.sendRep(damage = 100, reps = 1, exerciseId = "squat", fatigueBand = "FRESH", formScore = 0.9f)
        testDispatcher.scheduler.advanceUntilIdle()

        // P2 sends a rep
        r2.sendRep(damage = 80, reps = 1, exerciseId = "squat", fatigueBand = "FRESH", formScore = 0.8f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Check leaderboards
        val standings1 = r1.getStandings()
        assertTrue(standings1.isNotEmpty(), "P1 should have standings")

        // P1 should be first (100 > 80)
        val p1Standing = standings1.find { it.playerId == "P1" }
        assertEquals(100, p1Standing?.damage)

        // P2's rep crossed the bus too, so P1 sees both players.
        assertEquals(80, standings1.find { it.playerId == "P2" }?.damage)

        r1.close(); r2.close()
    }

    @Test
    fun `player out removes from raid`() = testScope.runTest {
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

        val t2 = LoopbackTransport(bus)
        val r2 = RaidSession(
            transport = t2,
            playerId = "P2",
            playerName = "Player 2",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        // Both send reps
        r1.sendRep(damage = 100, reps = 5, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        r2.sendRep(damage = 80, reps = 4, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.8f)
        testDispatcher.scheduler.advanceUntilIdle()

        // P2 is out
        r2.sendOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Check that P2 is marked out in standings
        val standings1 = r1.getStandings()
        val p2Standing = standings1.find { it.playerId == "P2" }
        assertEquals(true, p2Standing?.out, "P2 should be marked as out")

        r1.close(); r2.close()
    }

    @Test
    fun `standing change callback fired on rep`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val changes = mutableListOf<List<RaidStanding>>()

        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
            onStandingChanged = { changes.add(it) },
        )

        r1.sendRep(damage = 100, reps = 1, exerciseId = "squat", fatigueBand = "FRESH", formScore = 0.9f)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(changes.isNotEmpty(), "Standing change callback should fire")

        r1.close()
    }
}
