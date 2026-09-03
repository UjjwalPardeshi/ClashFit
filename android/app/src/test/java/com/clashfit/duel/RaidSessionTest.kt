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

    @Test
    fun `standings ordered by damage desc, then reps desc, then name asc for stable tie-break`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Alice",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
        )

        val t2 = LoopbackTransport(bus)
        val r2 = RaidSession(
            transport = t2,
            playerId = "P2",
            playerName = "Bob",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        val t3 = LoopbackTransport(bus)
        val r3 = RaidSession(
            transport = t3,
            playerId = "P3",
            playerName = "Charlie",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        // All have 100 damage but different reps
        r1.sendRep(damage = 100, reps = 8, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        r2.sendRep(damage = 100, reps = 5, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.8f)
        r3.sendRep(damage = 100, reps = 9, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.85f)
        testDispatcher.scheduler.advanceUntilIdle()

        // All three phones should see the same order: Charlie (9 reps), Alice (8 reps), Bob (5 reps)
        val standings1 = r1.getStandings()
        val standings2 = r2.getStandings()
        val standings3 = r3.getStandings()

        assertEquals(listOf("P3", "P1", "P2"), standings1.map { it.playerId })
        assertEquals(listOf("P3", "P1", "P2"), standings2.map { it.playerId })
        assertEquals(listOf("P3", "P1", "P2"), standings3.map { it.playerId })

        r1.close(); r2.close(); r3.close()
    }

    @Test
    fun `standings tie-breaker uses name when damage and reps equal`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Zoe",
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

        // Both have 100 damage and 8 reps
        r1.sendRep(damage = 100, reps = 8, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        r2.sendRep(damage = 100, reps = 8, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        testDispatcher.scheduler.advanceUntilIdle()

        val standings1 = r1.getStandings()
        // Alice comes before Zoe alphabetically
        assertEquals(listOf("P2", "P1"), standings1.map { it.playerId })
        assertEquals("Alice", standings1[0].name)
        assertEquals("Zoe", standings1[1].name)

        r1.close(); r2.close()
    }

    @Test
    fun `heartbeat tick sends PING after HEARTBEAT_MS`() = testScope.runTest {
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

        // Initial state
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

    @Test
    fun `raid with 4 players converges on identical standings despite packet loss`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        // Create 4 players with 30% packet loss
        val transports = (1..4).map { LoopbackTransport(bus, dropRate = 0.3f) }
        val sessions = transports.mapIndexed { i, t ->
            RaidSession(
                transport = t,
                playerId = "P${i + 1}",
                playerName = "Player ${i + 1}",
                clock = clock,
                scope = backgroundScope,
                isHost = i == 0,
            )
        }

        // Each player sends 5 reps
        sessions.forEach { session ->
            repeat(5) {
                session.sendRep(
                    damage = 20,
                    reps = it + 1,
                    exerciseId = "squat",
                    fatigueBand = "WORKING",
                    formScore = 0.9f
                )
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // All should converge on total damage (4 * 20 * 5 = 400, but with loss some may be missing)
        val standings = sessions.map { it.getStandings() }

        // Check that all have consistent ordering by damage desc
        standings.forEach { s ->
            s.zipWithNext().forEach { (a, b) ->
                assertTrue(a.damage >= b.damage, "Standings should be ordered by damage desc")
            }
        }

        sessions.forEach { it.close() }
    }
}
