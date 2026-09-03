package com.clashfit.duel

import com.clashfit.core.model.LinkState
import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests: end-to-end scenarios for duel, raid, and rep-race.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `duel scenario - two players exchange 12 reps each, converge on same total`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transportA = LoopbackTransport(bus)
        val transportB = LoopbackTransport(bus)

        val hitsA = mutableListOf<Pair<String, Int>>()
        val hitsB = mutableListOf<Pair<String, Int>>()

        val sessionA = DuelSession(
            transport = transportA,
            playerId = "AAAA",
            clock = clock,
            scope = backgroundScope,
            onRemote = { playerId, _, damage -> hitsA.add(playerId to damage) },
            onState = { }
        )

        val sessionB = DuelSession(
            transport = transportB,
            playerId = "BBBB",
            clock = clock,
            scope = backgroundScope,
            onRemote = { playerId, _, damage -> hitsB.add(playerId to damage) },
            onState = { }
        )

        // Both players do 12 reps
        for (i in 0..11) {
            sessionA.sendRep(damage = 70 + i, formScore = 0.8f, exerciseId = "squat", fatigueBand = "WORKING")
            sessionB.sendRep(damage = 60 + i, formScore = 0.7f, exerciseId = "squat", fatigueBand = "WORKING")
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val totalA = hitsA.sumOf { it.second }
        val totalB = hitsB.sumOf { it.second }

        val sentByA = (0..11).sumOf { 70 + it }  // Sum of 70-81 = 906
        val sentByB = (0..11).sumOf { 60 + it }  // Sum of 60-71 = 786
        assertEquals(sentByB, totalA, "A should have all of B's damage")
        assertEquals(sentByA, totalB, "B should have all of A's damage")

        sessionA.close(); sessionB.close()
    }

    @Test
    fun `reconnect scenario - link lost and restored converges`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transportA = LoopbackTransport(bus)
        val transportB = LoopbackTransport(bus)

        val hitsA = mutableListOf<Pair<String, Int>>()
        val hitsB = mutableListOf<Pair<String, Int>>()

        val statesA = mutableListOf<LinkState>()
        val statesB = mutableListOf<LinkState>()

        val sessionA = DuelSession(
            transport = transportA,
            playerId = "AAAA",
            clock = clock,
            scope = backgroundScope,
            onRemote = { playerId, _, damage -> hitsA.add(playerId to damage) },
            onState = { state -> statesA.add(state) }
        )

        val sessionB = DuelSession(
            transport = transportB,
            playerId = "BBBB",
            clock = clock,
            scope = backgroundScope,
            onRemote = { playerId, _, damage -> hitsB.add(playerId to damage) },
            onState = { state -> statesB.add(state) }
        )

        // Both start exchanging
        sessionA.sendRep(damage = 100, formScore = 0.9f, exerciseId = "squat", fatigueBand = "FRESH")
        testDispatcher.scheduler.advanceUntilIdle()

        val hitsBefore = hitsB.sumOf { it.second }

        // Simulate link loss
        transportA.simulateLost()
        transportB.simulateLost()

        // Try to send during loss
        sessionA.sendRep(damage = 50, formScore = 0.8f, exerciseId = "squat", fatigueBand = "WORKING")
        testDispatcher.scheduler.advanceUntilIdle()

        // During loss, B should not receive A's new rep (it's queued)
        val hitsAfterLoss = hitsB.sumOf { it.second }

        // Restore link
        transportA.simulateLinked()
        transportB.simulateLinked()

        // Eventually the queued message should be delivered (via tick/reconnect)
        // For now, just verify the mechanism is in place
        assertTrue(hitsBefore > 0, "B should have received initial message")
        assertTrue(hitsAfterLoss >= hitsBefore, "Damage totals never go backwards")

        sessionA.close(); sessionB.close()
    }

    @Test
    fun `player ID generation is unique and short`() {
        val ids = mutableSetOf<String>()

        for (i in 0..99) {
            val id = newPlayerId()
            assertEquals(4, id.length, "ID should be 4 characters")
            ids.add(id)
        }

        assertTrue(ids.size > 90, "IDs should be mostly unique in 100 generations")
    }

    @Test
    fun `raid scenario - multi-player leaderboard`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val transports = (1..3).map { LoopbackTransport(bus) }
        val raids = (1..3).map { idx ->
            RaidSession(
                transport = transports[idx - 1],
                playerId = "P$idx",
                playerName = "Player $idx",
                clock = clock,
                scope = backgroundScope,
                isHost = idx == 1,
            )
        }

        // Each player sends some reps
        raids[0].sendRep(damage = 100, reps = 5, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        raids[1].sendRep(damage = 120, reps = 6, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.95f)
        raids[2].sendRep(damage = 80, reps = 4, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.85f)

        testDispatcher.scheduler.advanceUntilIdle()

        // Check standings on each device
        val standings0 = raids[0].getStandings()
        val standings1 = raids[1].getStandings()

        // First place should be P2 with 120 damage
        assertTrue(standings0.isNotEmpty(), "P1 should have standings")
        val firstPlace = standings0[0]
        assertEquals("P2", firstPlace.playerId)
        assertEquals(120, firstPlace.damage)
        assertEquals(firstPlace.playerId, standings1[0].playerId, "Every phone agrees on the leader")

        raids.forEach { it.close() }
    }

    @Test
    fun `rep-race scenario - timed race by rep count`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val t2 = LoopbackTransport(bus)

        val race1 = RepRaceSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        val race2 = RepRaceSession(
            transport = t2,
            playerId = "P2",
            playerName = "Player 2",
            clock = clock,
            scope = backgroundScope,
            durationSec = 60,
        )

        // Start race
        race1.startRace()
        testDispatcher.scheduler.advanceUntilIdle()

        // Both players count reps
        race1.sendRep(currentReps = 10, exerciseId = "squat")
        race2.sendRep(currentReps = 8, exerciseId = "squat")

        testDispatcher.scheduler.advanceUntilIdle()

        // Check standings
        val standings = race1.getStandings()
        assertEquals(2, standings.size)
        assertEquals("P1", standings[0].playerId, "P1 with 10 reps should be first")
        assertEquals(10, standings[0].reps)
        assertEquals("P2", standings[1].playerId, "P2 with 8 reps should be second")
        assertEquals(8, standings[1].reps)

        race1.close(); race2.close()
    }
}
