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
) {
    val hpPct: Float get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f
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
