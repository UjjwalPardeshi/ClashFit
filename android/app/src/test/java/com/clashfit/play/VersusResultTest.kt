package com.clashfit.play

import com.clashfit.core.model.GameMode
import com.clashfit.core.util.FakeClock
import com.clashfit.duel.LoopbackTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who won, and whether the answer survives long enough to be read.
 *
 * A rep race always ends because the clock ran out, so the end panel and the summary both used to
 * close on the word "Time" and never named a winner. The scoreboard lives on the transport's
 * sessions and those go when the fight's view model is cleared — on the way to the summary — so
 * the result has to be frozen before the link is torn down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VersusResultTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun race(bus: MutableSet<LoopbackTransport>, scope: kotlinx.coroutines.CoroutineScope) =
        PlayHub(FakeClock(), scope) { LoopbackTransport(bus) }

    @Test
    fun `a rep race is settled on reps, and the winner is named`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)
        val guest = race(bus, backgroundScope)

        host.playerName = "UJJWAL"
        guest.playerName = "OMKAR"
        host.host("room"); guest.join()
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        guest.onLocalRep(reps = 11, damage = 0, formScore = 0.9f, band = com.clashfit.core.model.FatigueBand.FRESH)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 14, myDamage = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val r = host.result.value
        assertNotNull(r, "The fight must leave a scoreboard behind")
        assertEquals(14, r.myScore)
        assertEquals(11, r.theirScore)
        assertEquals("UJJWAL", r.myName)
        assertEquals("OMKAR", r.theirName)
        assertEquals("reps", r.unit)
        assertTrue(r.won, "14 beats 11")
        assertFalse(r.lost)

        host.release(); guest.release()
    }

    @Test
    fun `the loser is told they lost`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)
        val guest = race(bus, backgroundScope)

        host.host("room"); guest.join()
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        guest.onLocalRep(reps = 20, damage = 0, formScore = 0.9f, band = com.clashfit.core.model.FatigueBand.FRESH)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 9, myDamage = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val r = host.result.value
        assertNotNull(r)
        assertTrue(r.lost)
        assertFalse(r.won)
        assertFalse(r.drew)

        host.release(); guest.release()
    }

    @Test
    fun `an equal race is a draw, not a win`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)
        val guest = race(bus, backgroundScope)

        host.host("room"); guest.join()
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        guest.onLocalRep(reps = 12, damage = 0, formScore = 0.9f, band = com.clashfit.core.model.FatigueBand.FRESH)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 12, myDamage = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val r = host.result.value
        assertNotNull(r)
        assertTrue(r.drew)
        assertFalse(r.won)
        assertFalse(r.lost)

        host.release(); guest.release()
    }

    /** The reason the result exists at all: the summary reads it after the link has gone. */
    @Test
    fun `the scoreboard outlives the link`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)
        val guest = race(bus, backgroundScope)

        host.host("room"); guest.join()
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()
        guest.onLocalRep(reps = 7, damage = 0, formScore = 0.9f, band = com.clashfit.core.model.FatigueBand.FRESH)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 8, myDamage = 0)
        host.tagResult(sessionId = 42L)
        testDispatcher.scheduler.advanceUntilIdle()

        host.release()  // the fight's view model is cleared on the way to the summary

        val r = host.result.value
        assertNotNull(r, "release() must not take the scoreboard with it")
        assertEquals(8, r.myScore)
        assertEquals(7, r.theirScore)
        assertEquals(42L, r.sessionId, "Tagged, so an older match cannot label this summary")

        guest.release()
    }

    /** A solo run has a score but no winner, and must not claim one. */
    @Test
    fun `a solo host has no opponent to beat`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)

        host.host("room")
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 10, myDamage = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val r = host.result.value
        assertNotNull(r)
        assertFalse(r.hadOpponent, "Nobody joined")
        assertFalse(r.won, "You cannot beat an empty room")
        assertFalse(r.lost)
        assertFalse(r.drew)

        host.release()
    }

    /** A duel is decided on damage, so the deeper set beats the faster one. */
    @Test
    fun `a duel is settled on damage`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)
        val guest = race(bus, backgroundScope)

        host.host("room"); guest.join()
        host.arm(GameMode.DUEL, "squat", null)
        guest.arm(GameMode.DUEL, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        guest.onLocalRep(reps = 30, damage = 120, formScore = 0.8f, band = com.clashfit.core.model.FatigueBand.WORKING)
        testDispatcher.scheduler.advanceUntilIdle()

        host.settle(myReps = 12, myDamage = 300)
        testDispatcher.scheduler.advanceUntilIdle()

        val r = host.result.value
        assertNotNull(r)
        assertEquals("damage", r.unit)
        assertEquals(300, r.myScore, "Damage, not the rep count")
        assertTrue(r.won, "300 damage beats 120, though they did more reps")

        host.release(); guest.release()
    }

    /** A new match must never inherit the last one's scoreboard. */
    @Test
    fun `arming a new match clears the old result`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val host = race(bus, backgroundScope)

        host.host("room")
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        host.settle(myReps = 10, myDamage = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(host.result.value)

        host.arm(GameMode.REP_RACE, "squat", 60)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(host.result.value, "A fresh contest starts with no score")

        host.release()
    }
}
