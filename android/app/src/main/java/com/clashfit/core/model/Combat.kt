package com.clashfit.core.model

/** Snapshot of the combat engine. `hp` is never negative and never above `maxHp`. */
data class CombatState(
    val bossId: String,
    val bossName: String,
    val hp: Int,
    val maxHp: Int,
    val phaseLabel: String,
    val phaseModifier: Float,
    val reps: Int,
    val totalDamage: Int,
    val dead: Boolean,
    val comboStreak: Int,
    val comboMultiplier: Float,
    val lastDamage: Int?,
    val staggered: Boolean,
    val mercyActive: Boolean,
    val playerHp: Int = Int.MAX_VALUE / 2,
    val playerMaxHp: Int = Int.MAX_VALUE / 2,
    val playerDead: Boolean = false,
    val lastPlayerHit: Int? = null,
    val nextAttackInMs: Long? = null,
    val attackIntervalMs: Long? = null,        // null when this mode has no boss attacks
) {
    val hpPct: Float get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f
    val playerHpPct: Float get() = if (playerMaxHp > 0) playerHp.toFloat() / playerMaxHp else 0f
}

/** Family game state. Sigil deliberately exposes no combat fields at all. */
sealed interface FamilyGameState {
    val outcome: GameOutcome
    val progress: Float
}

data class SiegeState(
    val playerHp: Int, val playerHpPct: Float,
    val bossHp: Int, val bossHpPct: Float,
    val holds: Int, val totalHeldSec: Float,
    override val outcome: GameOutcome,
) : FamilyGameState {
    override val progress: Float get() = 1f - bossHpPct
}

data class PursuitState(
    val distanceM: Float, val pursuerM: Float, val gapM: Float,
    val cycles: Int,
    override val progress: Float,
    override val outcome: GameOutcome,
) : FamilyGameState

data class BreakerState(
    val broken: Int, val floors: Int, val jumps: Int, val bestCm: Float,
    override val progress: Float,
    override val outcome: GameOutcome,
    val lastFloors: Int = 0,
) : FamilyGameState

data class SigilSegment(val index: Int, val brightness: Float, val heldSec: Float)

data class SigilState(
    val lit: Int, val segments: Int, val brightness: Float,
    val segmentsDetail: List<SigilSegment>,
    override val progress: Float,
    override val outcome: GameOutcome,
) : FamilyGameState

// ── Zombie Run ────────────────────────────────────────────────────────────────
// The outdoor chase. Positions are metres east and north of wherever the chase started, never
// latitude and longitude: the rules are a flat-frame problem and keeping coordinates out of the
// engine is what stops a coordinate ever reaching the coach, the record, or the network.
// docs/33-FEATURE-ZOMBIE-RUN.md

/** Where a chase is in its own arc. */
enum class ZombieRunPhase { HEAD_START, CHASE, ESCAPED, CAUGHT }

/** One zombie, in the local metric frame. Dead ones are kept so the map can show the kill. */
data class Zombie(
    val id: Int,
    val eastM: Float,
    val northM: Float,
    val alive: Boolean,
)

/** One ammo cache. Taken caches stay in the list so the map can grey them out. */
data class AmmoCache(
    val id: Int,
    val eastM: Float,
    val northM: Float,
    val taken: Boolean,
)

data class ZombieRunState(
    val phase: ZombieRunPhase,
    val outcome: GameOutcome,
    val elapsedSec: Float,
    val headStartLeftSec: Float,
    val secondsLeft: Float,
    val playerEastM: Float,
    val playerNorthM: Float,
    val zombies: List<Zombie>,
    val caches: List<AmmoCache>,
    val ammo: Int,
    val neutralised: Int,
    val cachesCollected: Int,
    val distanceM: Float,
    /** Metres to the closest living zombie, or null when the map is clear. */
    val nearestZombieM: Float?,
    val progress: Float,
) {
    val zombiesLeft: Int get() = zombies.count { it.alive }
}
