package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.ZombieRunPhase
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * The chase, exercised without going outside.
 *
 * Every test drives [ZombieRunGame] with a scripted path in the local metric frame, which is the
 * whole reason the game takes metres rather than a `Location`.
 */
class ZombieRunGameTest {

    private fun game(
        headStartSec: Int = 60,
        survivalSec: Int = 600,
        pursuers: Int = 4,
        ammoPickups: Int = 6,
        captureRadiusM: Int = 12,
    ) = ZombieRunGame(
        ZombieRunGame.ZombieRunConfig(
            headStartSec = headStartSec,
            survivalSec = survivalSec,
            pursuers = pursuers,
            ammoPickups = ammoPickups,
            captureRadiusM = captureRadiusM,
        ),
    )

    @Test
    fun `spawns the configured number of pursuers and caches`() {
        val g = game()
        g.start(seed = 42L, tMs = 0L)
        val s = g.state(0L)
        assertEquals(4, s.pursuers.size)
        assertEquals(6, s.caches.size)
        assertTrue(s.pursuers.all { it.alive })
        assertTrue(s.caches.none { it.taken })
    }

    @Test
    fun `pursuers spawn on the ring, never inside the minimum radius`() {
        val cfg = ZombieRunGame.ZombieRunConfig(pursuers = 24)
        val g = ZombieRunGame(cfg)
        g.start(seed = 7L, tMs = 0L)
        g.state(0L).pursuers.forEach {
            val r = hypot(it.eastM, it.northM)
            assertTrue(r >= cfg.spawnMinRadiusM - 0.5f, "pursuer spawned at ${r}m, inside the ring")
            assertTrue(r <= cfg.spawnRadiusM + 0.5f, "pursuer spawned at ${r}m, outside the ring")
        }
    }

    @Test
    fun `the same seed lays out the same chase`() {
        val a = game().also { it.start(99L, 0L) }.state(0L)
        val b = game().also { it.start(99L, 0L) }.state(0L)
        assertEquals(a.pursuers, b.pursuers)
        assertEquals(a.caches, b.caches)
    }

    @Test
    fun `a different seed lays out a different chase`() {
        val a = game().also { it.start(1L, 0L) }.state(0L)
        val b = game().also { it.start(2L, 0L) }.state(0L)
        assertTrue(a.pursuers != b.pursuers)
    }

    @Test
    fun `nothing moves during the head start`() {
        val g = game(headStartSec = 60)
        g.start(5L, 0L)
        val before = g.state(0L).pursuers
        // Half a minute of standing perfectly still, which after the head start would be fatal.
        val s = g.onTick(30_000L, 0f, 0f, cadenceSpm = 0)
        assertEquals(ZombieRunPhase.HEAD_START, s.phase)
        assertEquals(before, s.pursuers)
        assertEquals(GameOutcome.RUNNING, s.outcome)
    }

    @Test
    fun `head start countdown reaches zero and the phase becomes chase`() {
        val g = game(headStartSec = 10)
        g.start(5L, 0L)
        assertEquals(10f, g.onTick(0L, 0f, 0f, 150).headStartLeftSec)
        val s = g.onTick(11_000L, 0f, 0f, 150)
        assertEquals(0f, s.headStartLeftSec)
        assertEquals(ZombieRunPhase.CHASE, s.phase)
    }

    @Test
    fun `standing still after the head start gets you caught`() {
        val g = game(headStartSec = 1, ammoPickups = 0)
        g.start(11L, 0L)
        var t = 1_000L
        var caught = false
        // Ten minutes of not moving. The pursuers start ~220-400 m out, so this takes a while.
        while (t < 600_000L) {
            val s = g.onTick(t, 0f, 0f, cadenceSpm = 0)
            if (s.outcome == GameOutcome.LOST) {
                caught = true
                assertEquals(ZombieRunPhase.CAUGHT, s.phase)
                break
            }
            t += 1_000L
        }
        assertTrue(caught, "a stationary player with no ammo was never caught")
    }

    @Test
    fun `a low cadence closes the gap faster than a high one`() {
        fun gapAfter(cadence: Int): Float {
            val g = game(headStartSec = 0, ammoPickups = 0)
            g.start(3L, 0L)
            g.onTick(0L, 0f, 0f, cadence)
            g.onTick(60_000L, 0f, 0f, cadence)
            return g.nearestZombieM() ?: 0f
        }
        // Same standing-still path, different measured effort: the pack is nearer when the legs
        // stopped, which is the mode's entire argument for existing.
        assertTrue(gapAfter(0) < gapAfter(150), "cadence did not change how fast the pack closed")
    }

    @Test
    fun `effort multiplier is bounded by the configured extremes`() {
        val cfg = ZombieRunGame.ZombieRunConfig(targetCadenceSpm = 150)
        val g = ZombieRunGame(cfg)
        g.start(1L, 0L)
        g.onTick(0L, 0f, 0f, cadenceSpm = 0)
        assertEquals(cfg.slackestMultiplier, g.effortMultiplier(), 0.001f)
        g.onTick(1_000L, 0f, 0f, cadenceSpm = 150)
        assertEquals(cfg.keenestMultiplier, g.effortMultiplier(), 0.001f)
        // Sprinting past the target buys nothing more.
        g.onTick(2_000L, 0f, 0f, cadenceSpm = 400)
        assertEquals(cfg.keenestMultiplier, g.effortMultiplier(), 0.001f)
    }

    @Test
    fun `walking onto a cache banks a round`() {
        val g = game(headStartSec = 600)
        g.start(21L, 0L)
        val cache = g.state(0L).caches.first()
        val s = g.onTick(1_000L, cache.eastM, cache.northM, 150)
        assertEquals(1, s.ammo)
        assertEquals(1, s.cachesCollected)
        assertTrue(s.caches.first { it.id == cache.id }.taken)
    }

    @Test
    fun `a cache is only worth one round however long you stand on it`() {
        val g = game(headStartSec = 600)
        g.start(21L, 0L)
        val cache = g.state(0L).caches.first()
        g.onTick(1_000L, cache.eastM, cache.northM, 150)
        val s = g.onTick(2_000L, cache.eastM, cache.northM, 150)
        assertEquals(1, s.ammo)
    }

    @Test
    fun `a round spends itself on a pursuer that reaches you`() {
        val g = game(headStartSec = 600, pursuers = 1)
        g.start(33L, 0L)
        val cache = g.state(0L).caches.first()
        // Collect a round while the head start still holds the pursuer in place.
        g.onTick(1_000L, cache.eastM, cache.northM, 150)
        // Then walk into the pursuer, after the head start.
        val p = g.state(0L).pursuers.first()
        val g2 = g.onTick(700_000L, p.eastM, p.northM, 150)
        assertEquals(0, g2.ammo)
        assertEquals(1, g2.neutralised)
        assertEquals(0, g2.zombiesLeft)
        // Clearing the map is an escape.
        assertEquals(GameOutcome.WON, g2.outcome)
    }

    @Test
    fun `surviving the clock wins`() {
        val g = game(headStartSec = 0, survivalSec = 10, pursuers = 0, ammoPickups = 0)
        g.start(4L, 0L)
        val s = g.onTick(11_000L, 0f, 0f, 150)
        assertEquals(GameOutcome.WON, s.outcome)
        assertEquals(ZombieRunPhase.ESCAPED, s.phase)
    }

    @Test
    fun `distance accumulates from the path walked`() {
        val g = game(headStartSec = 600)
        g.start(8L, 0L)
        g.onTick(1_000L, 0f, 0f, 150)
        g.onTick(2_000L, 30f, 40f, 150) // 3-4-5 triangle: 50 m
        val s = g.onTick(3_000L, 30f, 90f, 150) // then 50 m due north
        assertEquals(100f, s.distanceM, 0.5f)
    }

    @Test
    fun `a finished chase ignores further ticks`() {
        val g = game(headStartSec = 0, survivalSec = 5, pursuers = 0, ammoPickups = 0)
        g.start(4L, 0L)
        val won = g.onTick(6_000L, 0f, 0f, 150)
        assertEquals(GameOutcome.WON, won.outcome)
        val after = g.onTick(9_000L, 500f, 500f, 0)
        assertEquals(GameOutcome.WON, after.outcome)
        assertEquals(won.distanceM, after.distanceM)
    }

    @Test
    fun `a pursuer never overshoots the player`() {
        // One pursuer, an absurd tick gap: without the clamp it would sail past and read as a miss.
        val g = ZombieRunGame(ZombieRunGame.ZombieRunConfig(headStartSec = 0, pursuers = 1, ammoPickups = 0))
        g.start(5L, 0L)
        g.onTick(0L, 0f, 0f, 150)
        val s = g.onTick(600_000L, 0f, 0f, 150)
        val p = s.pursuers.first()
        // Either it caught the player or it is standing on them; it is never on the far side.
        assertTrue(hypot(p.eastM, p.northM) <= 0.01f || s.outcome == GameOutcome.LOST)
    }

    @Test
    fun `score rewards survival, ground covered and caches`() {
        val g = game(headStartSec = 600, survivalSec = 600)
        g.start(12L, 0L)
        val idle = g.score(0L)
        g.onTick(60_000L, 300f, 400f, 150) // 500 m covered, one minute in
        assertTrue(g.score(60_000L) > idle, "covering ground did not score")
    }

    @Test
    fun `state before start is inert rather than crashing`() {
        val g = game()
        val s = g.state(0L)
        assertEquals(GameOutcome.RUNNING, s.outcome)
        assertTrue(s.pursuers.isEmpty())
        assertFalse(s.nearestZombieM != null)
        val ticked = g.onTick(1_000L, 5f, 5f, 100)
        assertNotNull(ticked)
    }
}
