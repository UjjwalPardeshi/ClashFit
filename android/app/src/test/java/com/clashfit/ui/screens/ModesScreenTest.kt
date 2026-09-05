package com.clashfit.ui.screens

import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModesScreenTest {

    @Test
    fun `game mode grid contains exactly sixteen modes`() {
        assertEquals(16, GameMode.grid.size)
    }

    @Test
    fun `game mode grid order matches site order`() {
        val expectedOrder = listOf(
            GameMode.BOSS_FIGHT,
            GameMode.TIME_ATTACK,
            GameMode.REP_RACE,
            GameMode.GHOST_RACE,
            GameMode.DUEL,
            GameMode.SURVIVAL,
            GameMode.BOSS_RUSH,
            GameMode.TEMPO_TRIAL,
            GameMode.SIEGE,
            GameMode.PURSUIT,
            GameMode.BREAKER,
            GameMode.SIGIL,
            GameMode.LAST_STANDING,
            GameMode.RELAY,
            GameMode.CIRCUIT,
            GameMode.CLINIC_STS,
        )

        assertEquals(expectedOrder, GameMode.grid)
    }

    @Test
    fun `solo modes are six`() {
        val soloModes = GameMode.grid.filter { it.kind == ModeKind.SOLO }
        assertEquals(6, soloModes.size)
    }

    @Test
    fun `versus modes are four`() {
        // The modes screen lists every mode of a kind, not just the sixteen tiles on the
        // site's fixed home grid (GameMode.grid) — REP_RACE, DUEL, TUG_OF_WAR, MIRROR_MATCH.
        val versusModes = GameMode.entries.filter { it.kind == ModeKind.VERSUS }
        assertEquals(4, versusModes.size)
    }

    @Test
    fun `group modes are five`() {
        // Same as above: RAID, RELAY, LAST_STANDING, TEAM_VS_TEAM, CIRCUIT.
        val groupModes = GameMode.entries.filter { it.kind == ModeKind.GROUP }
        assertEquals(5, groupModes.size)
    }

    @Test
    fun `family modes are four`() {
        val familyModes = GameMode.grid.filter { it.kind == ModeKind.FAMILY }
        assertEquals(4, familyModes.size)
    }

    @Test
    fun `clinic mode exists`() {
        val clinicMode = GameMode.grid.find { it.kind == ModeKind.CLINIC }
        assertEquals(GameMode.CLINIC_STS, clinicMode)
    }

    @Test
    fun `all modes have non-empty titles and blurbs`() {
        GameMode.grid.forEach { mode ->
            assertTrue(mode.title.isNotEmpty(), "Mode ${mode.name} has empty title")
            assertTrue(mode.blurb.isNotEmpty(), "Mode ${mode.name} has empty blurb")
        }
    }

    @Test
    fun `rep race has null family`() {
        assertEquals(null, GameMode.REP_RACE.family)
    }

    @Test
    fun `clinic sts is timed`() {
        assertTrue(GameMode.CLINIC_STS.timed)
    }

    @Test
    fun `solo modes are mostly not timed except time attack`() {
        val soloModes = GameMode.grid.filter { it.kind == ModeKind.SOLO }
        val timedCount = soloModes.count { it.timed }
        assertEquals(1, timedCount)
    }

    @Test
    fun `zombie run is present in all modes`() {
        assertTrue(GameMode.entries.contains(GameMode.OUTBREAK))
    }

    @Test
    fun `zombie run ships`() {
        // It was `enabled = false` while the mode was a specification. It is a screen now, so this
        // asserts the opposite: a disabled mode draws a "Coming online" tag and refuses the tap,
        // which would hide a mode that works.
        assertTrue(GameMode.OUTBREAK.enabled)
    }

    @Test
    fun `zombie run is absent from game mode grid`() {
        // Shipping the mode does not put it on the site's fixed sixteen-tile grid. That grid is a
        // promise the landing page makes, and Zombie Run is reached from All modes and from the
        // Outdoors screen instead.
        assertFalse(GameMode.grid.contains(GameMode.OUTBREAK))
    }

    @Test
    fun `zombie run has correct properties`() {
        assertEquals("Zombie Run", GameMode.OUTBREAK.title)
        assertEquals(ModeKind.SOLO, GameMode.OUTBREAK.kind)
        assertEquals(Family.CADENCE, GameMode.OUTBREAK.family)
        assertFalse(GameMode.OUTBREAK.timed)
    }
}
