package com.clashfit.engine.games

import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.WinBy
import com.clashfit.core.model.ZombieRunPhase
import com.clashfit.core.model.Zombie
import com.clashfit.core.model.ZombieRunState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * ZOMBIE RUN — the outdoor chase, on a real map. `docs/33-FEATURE-ZOMBIE-RUN.md`.
 *
 * This class is the whole rule set and it is deliberately Android-free: it knows nothing about
 * GPS, nothing about map tiles and nothing about Compose. It works in a flat local frame of
 * **metres east and north of the origin**, which is wherever the player stood when the mode
 * started. The caller projects; this file decides.
 *
 * That seam is what makes the mode testable at all. A chase cannot be verified by walking around
 * outside twice and hoping, so every rule here is exercised on the JVM by feeding it a scripted
 * path — see `ZombieRunGameTest`.
 *
 * ### The rules, in the order they matter
 *
 * 1. **A sixty-second head start.** Zombies exist from the first frame but do not move until it
 *    expires, so the player can read the map and choose a direction before anything closes.
 * 2. **Zombies spawn on a ring**, not uniformly in a disc. Uniform-in-a-disc spawning clusters
 *    near the player (area grows with r²), which means a mode whose whole promise is a head start
 *    would routinely open with something already breathing down your neck. A ring between
 *    [ZombieRunConfig.spawnMinRadiusM] and the configured spawn radius keeps the opening honest.
 * 3. **Effort, not just position, decides the gap.** This is the point of the mode and the reason
 *    it belongs in this app rather than in a map toy: a zombie speeds up when the player's
 *    *cadence* falls, so standing still on a street corner is punished the moment the phone stops
 *    seeing footfalls — not thirty seconds later when the GPS finally agrees you have not moved.
 * 4. **Caches arm you.** Reaching one inside [ZombieRunConfig.pickupRadiusM] banks one round. A
 *    zombie that closes to the capture radius is neutralised if you have a round to spend, and
 *    catches you if you do not.
 * 5. **Survive the clock and you are out.**
 *
 * ### Determinism
 *
 * Spawns come from a seeded linear congruential generator rather than the platform RNG, so the
 * same seed lays out the same chase on both phones and in every test run. That is what lets a
 * chase be replayed from a challenge code later, and it is why nothing here calls `Math.random`.
 */
class ZombieRunGame(private val cfg: ZombieRunConfig = ZombieRunConfig()) {

    /**
     * Every number the chase runs on.
     *
     * The first four mirror `modes.OUTBREAK` in `config/combat.json` exactly; the rest are this
     * file's own and are stated here rather than buried in the code so a bad chase can be retuned
     * without reading it.
     */
    data class ZombieRunConfig(
        /** Seconds before zombies begin to move. */
        val headStartSec: Int = 60,
        /** Outer radius of the spawn ring, in metres. */
        val spawnRadiusM: Int = 400,
        /** A zombie this close has you. There is nothing to spend against it. */
        val captureRadiusM: Int = 12,

        /** Inner radius of the spawn ring. Nothing may open the mode closer than this. */
        val spawnMinRadiusM: Int = 220,
        /** How many zombies spawn. */
        val zombies: Int = 4,
        /** Whether this chase is won by outlasting a clock or by covering ground. */
        val winBy: WinBy = WinBy.TIME,
        /** Survive this long and the chase is won, when [winBy] is [WinBy.TIME]. */
        val survivalSec: Int = 600,
        /** Cover this far and the chase is won, when [winBy] is [WinBy.DISTANCE]. */
        val winDistanceM: Int = 3_000,
        /** A zombie's ground speed at the player's target cadence, metres per second. */
        val zombieBaseMps: Float = 2.35f,
        /**
         * The cadence a chase is priced against, in steps per minute. Roughly a steady jog: below
         * it the zombies gain, above it they lose ground.
         */
        val targetCadenceSpm: Int = 150,
        /** Speed multiplier at zero effort. Above 1, so stopping is always losing ground. */
        val slackestMultiplier: Float = 1.35f,
        /** Speed multiplier at or above the target cadence. Below 1, so honest work pays. */
        val keenestMultiplier: Float = 0.9f,
    )

    private var seed: Long = 1L
    private var startedAtMs: Long? = null
    private var lastTickMs: Long? = null

    private var playerEastM: Float = 0f
    private var playerNorthM: Float = 0f
    private var cadenceSpm: Int = 0

    private var zombies: MutableList<Zombie> = mutableListOf()
    private var distanceM: Float = 0f
    private var outcome: GameOutcome = GameOutcome.RUNNING

    /**
     * Lays out a chase around the origin and opens the head start.
     *
     * [seed] fixes the layout. Pass a stable value (the run's start time works) and the same
     * chase can be reproduced exactly.
     */
    fun start(seed: Long, tMs: Long) {
        this.seed = if (seed == 0L) 1L else seed
        startedAtMs = tMs
        lastTickMs = tMs
        playerEastM = 0f
        playerNorthM = 0f
        cadenceSpm = 0
        distanceM = 0f
        outcome = GameOutcome.RUNNING

        val rng = Lcg(this.seed)
        zombies = MutableList(cfg.zombies) { i ->
            val (e, n) = rng.onRing(cfg.spawnMinRadiusM.toFloat(), cfg.spawnRadiusM.toFloat())
            Zombie(id = i, eastM = e, northM = n, alive = true)
        }
    }

    /**
     * Advances the chase to [tMs] with the player at ([eastM], [northM]) moving at [cadenceSpm].
     *
     * Safe to call at any rate. GPS delivers about one fix a second, which is far too coarse for a
     * zombie to look alive on screen, so the UI ticks this on the frame clock and only updates
     * the position when a fix actually lands.
     */
    fun onTick(tMs: Long, eastM: Float, northM: Float, cadenceSpm: Int): ZombieRunState {
        if (outcome != GameOutcome.RUNNING) return state(tMs)
        val startedAt = startedAtMs ?: return state(tMs)

        val previousE = playerEastM
        val previousN = playerNorthM
        playerEastM = eastM
        playerNorthM = northM
        this.cadenceSpm = cadenceSpm
        distanceM += hypot(eastM - previousE, northM - previousN)

        val last = lastTickMs ?: tMs
        val deltaMs = (tMs - last).coerceAtLeast(0L)
        lastTickMs = tMs

        val elapsedSec = (tMs - startedAt) / 1000f
        if (elapsedSec >= cfg.headStartSec) {
            advancePursuers(deltaMs)
            resolveContact()
        }

        if (outcome == GameOutcome.RUNNING && reachedTarget(elapsedSec)) {
            outcome = GameOutcome.WON
        }
        return state(tMs)
    }

    /**
     * Whether the goal the player set has been met.
     *
     * The two are exclusive on purpose. A chase with a clock and a distance both ticking gives the
     * runner two questions to answer at once, and the one number on the HUD stops meaning anything.
     */
    private fun reachedTarget(elapsedSec: Float): Boolean = when (cfg.winBy) {
        WinBy.TIME -> elapsedSec >= cfg.survivalSec
        WinBy.DISTANCE -> distanceM >= cfg.winDistanceM
    }

    /** How far through the chosen goal the chase is, 0..1. Drives the bar and nothing else. */
    private fun progressToward(elapsedSec: Float): Float = when (cfg.winBy) {
        WinBy.TIME -> (elapsedSec / cfg.survivalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
        WinBy.DISTANCE -> (distanceM / cfg.winDistanceM.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /**
     * Every living zombie homes on the player at a speed set by the player's own effort.
     *
     * The chase closes on cadence, not on coordinates. A player who stops moving their legs is
     * caught even if the receiver has not yet noticed they stopped moving through space.
     */
    private fun advancePursuers(deltaMs: Long) {
        if (deltaMs <= 0L) return
        val speed = cfg.zombieBaseMps * effortMultiplier()
        val step = speed * (deltaMs / 1000f)
        for (i in zombies.indices) {
            val p = zombies[i]
            if (!p.alive) continue
            val dE = playerEastM - p.eastM
            val dN = playerNorthM - p.northM
            val gap = hypot(dE, dN)
            if (gap <= 0.001f) continue
            // Never overshoot the player: a zombie that stepped past would read as a miss.
            val advance = min(step, gap)
            zombies[i] = p.copy(
                eastM = p.eastM + dE / gap * advance,
                northM = p.northM + dN / gap * advance,
            )
        }
    }

    /**
     * How much faster than base the pack is moving, from the player's cadence.
     *
     * Linear between [ZombieRunConfig.slackestMultiplier] at a standstill and
     * [ZombieRunConfig.keenestMultiplier] at the target cadence, then flat. There is no reward for
     * sprinting past the target, because a mode that scales indefinitely with effort punishes the
     * shorter legs in the room rather than the lazier ones.
     */
    internal fun effortMultiplier(): Float {
        val effort = (cadenceSpm.toFloat() / cfg.targetCadenceSpm).coerceIn(0f, 1f)
        return cfg.slackestMultiplier + (cfg.keenestMultiplier - cfg.slackestMultiplier) * effort
    }

    /**
     * A zombie inside the capture radius takes you.
     *
     * There is nothing to spend against it any more. The chase used to hand out caches that armed
     * you, which made the mode partly about walking over the right patch of ground; now the only
     * answer to a zombie is not being where it is, which is the answer the mode was always about.
     */
    private fun resolveContact() {
        for (p in zombies) {
            if (!p.alive) continue
            if (hypot(p.eastM - playerEastM, p.northM - playerNorthM) <= cfg.captureRadiusM) {
                outcome = GameOutcome.LOST
                return
            }
        }
    }

    /** Metres to the closest living zombie, or null when none is left. */
    fun nearestZombieM(): Float? = zombies.filter { it.alive }
        .minOfOrNull { hypot(it.eastM - playerEastM, it.northM - playerNorthM) }

    fun state(tMs: Long): ZombieRunState {
        val startedAt = startedAtMs
        val elapsedSec = if (startedAt == null) 0f else (tMs - startedAt) / 1000f
        val headStartLeft = max(0f, cfg.headStartSec - elapsedSec)
        val phase = when {
            outcome == GameOutcome.WON -> ZombieRunPhase.ESCAPED
            outcome == GameOutcome.LOST -> ZombieRunPhase.CAUGHT
            headStartLeft > 0f -> ZombieRunPhase.HEAD_START
            else -> ZombieRunPhase.CHASE
        }
        return ZombieRunState(
            phase = phase,
            outcome = outcome,
            elapsedSec = elapsedSec,
            headStartLeftSec = headStartLeft,
            secondsLeft = max(0f, cfg.survivalSec - elapsedSec),
            playerEastM = playerEastM,
            playerNorthM = playerNorthM,
            zombies = zombies.toList(),
            distanceM = distanceM,
            nearestZombieM = nearestZombieM(),
            progress = progressToward(elapsedSec),
        )
    }

    /**
     * What the chase was worth, for XP and the session record.
     *
     * Getting to the goal is the bulk of it and ground covered is the rest, whichever goal was
     * set. Being caught still scores what was earned up to that point — the mode is meant to be
     * attempted, not feared.
     */
    fun score(tMs: Long): Int {
        val s = state(tMs)
        return (s.progress * 400).toInt() +
            (s.distanceM / 10f).toInt() +
            if (s.outcome == GameOutcome.WON) 250 else 0
    }

    /**
     * A tiny linear congruential generator.
     *
     * The platform RNG is not used anywhere in the engine: the same seed has to lay out the same
     * chase on a teammate's phone and in a test, and `java.util.Random` is a guarantee about a
     * platform rather than about this file.
     */
    private class Lcg(seedValue: Long) {
        private var s: Long = seedValue

        fun nextFloat(): Float {
            // Numerical Recipes' constants; the top bits are the ones worth having.
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return (s ushr 8).toFloat() / (1 shl 24).toFloat()
        }

        /** A point on a ring between [innerM] and [outerM], uniform in angle. */
        fun onRing(innerM: Float, outerM: Float): Pair<Float, Float> {
            val angle = nextFloat() * 2f * PI.toFloat()
            val radius = innerM + nextFloat() * max(0f, outerM - innerM)
            return Pair(radius * cos(angle), radius * sin(angle))
        }
    }
}
