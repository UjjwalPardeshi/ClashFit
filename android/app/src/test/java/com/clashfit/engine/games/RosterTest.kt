package com.clashfit.engine.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RosterTest {
    @Test
    fun `pass-the-phone cycles turns and carries totals`() {
        val r = Roster(
            listOf("A", "B", "C"),
            Roster.RosterOptions(mode = RosterMode.PASS_THE_PHONE, turnSec = 30)
        )
        r.startTurn(0)
        r.record(Roster.PlayerStats(reps = 8, damage = 640, bestForm = 0.9f))
        assertEquals("A", r.current?.name)
        r.next(1000)
        r.record(Roster.PlayerStats(reps = 6, damage = 500))
        assertEquals("B", r.current?.name)
        r.next(2000)
        r.record(Roster.PlayerStats(reps = 9, damage = 700))
        assertEquals("C", r.current?.name)
        r.next(3000)
        assertEquals("A", r.current?.name, "did not wrap around")
        assertEquals(640, r.players[0].damage)
        assertEquals(2, r.players[0].turns, "turn count did not increment on the second pass")
    }

    @Test
    fun `a turn clock only exists where a turn is timed`() {
        val pass = Roster(
            listOf("A", "B"),
            Roster.RosterOptions(mode = RosterMode.PASS_THE_PHONE, turnSec = 30)
        )
        pass.startTurn(0)
        assertEquals(20000L, pass.turnLeftMs(10000))

        val last = Roster(
            listOf("A", "B"),
            Roster.RosterOptions(mode = RosterMode.LAST_STANDING)
        )
        last.startTurn(0)
        assertNull(last.turnLeftMs(10000), "last standing should not be on a clock")
    }

    @Test
    fun `LAST_STANDING eliminates on fatigue, not on rep count`() {
        val r = Roster(
            listOf("A", "B", "C"),
            Roster.RosterOptions(mode = RosterMode.LAST_STANDING)
        )
        r.startTurn(0)
        r.eliminateCurrent("GASSED")
        assertTrue(r.players[0].out)
        assertEquals("GASSED", r.players[0].outReason)
        assertFalse(r.finished, "ended with two players still in")
        r.next(1)
        r.eliminateCurrent("GASSED")
        assertTrue(r.finished, "never ended")
        assertEquals("C", r.winner?.name, "wrong survivor")
    }

    @Test
    fun `next() skips eliminated players`() {
        val r = Roster(
            listOf("A", "B", "C", "D"),
            Roster.RosterOptions(mode = RosterMode.LAST_STANDING)
        )
        r.startTurn(0)
        r.players[1].out = true  // B is out
        assertEquals("C", r.next(1)?.name, "did not skip the eliminated player")
    }

    @Test
    fun `the winner is decided on damage, not reps`() {
        val r = Roster(
            listOf("A", "B"),
            Roster.RosterOptions(mode = RosterMode.PASS_THE_PHONE)
        )
        r.startTurn(0)
        r.record(Roster.PlayerStats(reps = 20, damage = 900))  // many sloppy reps
        r.next(1)
        r.record(Roster.PlayerStats(reps = 12, damage = 1100))  // fewer, cleaner reps
        assertEquals("B", r.finish()?.name, "rep count beat damage")
        assertEquals("B", r.leaderboard()[0].name)
    }
}
