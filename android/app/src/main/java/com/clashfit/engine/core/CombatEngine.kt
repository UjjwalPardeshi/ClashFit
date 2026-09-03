package com.clashfit.engine.core

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.CombatState
import kotlin.math.pow
import kotlin.math.round

/** Tracks consecutive high-quality reps for the combo multiplier. */
class ComboTracker(var config: ComboConfig) {
    var streak = 0
        private set
    private var graceUsed = false

    /** Process one rep's form score and return the new multiplier. */
    fun onRep(formScore: Float): Float {
        when {
            formScore >= config.threshold -> {
                streak++
                graceUsed = false
            }
            streak >= config.graceAtStreak && !graceUsed -> {
                graceUsed = true  // One forgiven rep at a long streak
            }
            else -> {
                streak = 0
                graceUsed = false
            }
        }
        return multiplier
    }

    /** Compute the current multiplier from the streak. */
    val multiplier: Float
        get() = if (streak <= 0) 1f else minOf(1f + config.step * (streak - 1), config.cap)

    /** Reset to the initial state. */
    fun reset() {
        streak = 0
        graceUsed = false
    }
}

/** Configuration for combo scoring. */
data class ComboConfig(
    val step: Float = 0.12f,
    val cap: Float = 2.5f,
    val threshold: Float = 0.75f,
    val graceAtStreak: Int = 6,
)

/** Boss phase configuration. */
data class BossPhase(val fromHpPct: Float, val modifier: Float, val label: String)

/** Boss configuration. */
data class BossConfig(
    val id: String,
    val name: String,
    val maxHp: Int,
    val phases: List<BossPhase>,
)

/** Fatigue response configuration for a band. */
data class FatigueResponse(
    val modifier: Float = 1f,
    val regenPerRep: Int = 0,
    val staggerReps: Int = 0,
    val mercyRepsToFinish: Int = 0,
)

/** Combat engine: damage, combo, boss phases, fatigue-adaptive behaviour. */
class CombatEngine(
    private val baseDamage: Int = 100,
    private val formFloor: Float = 0.35f,
    private val formExponent: Float = 1.2f,
    private var boss: BossConfig,
    private val responses: Map<FatigueBand, FatigueResponse>,
    val combo: ComboTracker,
    private val casual: Boolean = false,
) {
    var maxHp = boss.maxHp
        internal set
    var hp = maxHp
        internal set
    var reps = 0
        private set
    var totalDamage = 0
        private set
    var dead = false
        private set
    var staggerRepsLeft = 0
        private set
    var mercyActive = false
        private set
    var mercyDisabled = false

    private val applied = HashSet<String>()  // Duel dedupe: "player:seq"
    private val recent = ArrayDeque<Float>()  // Combo-neutral, for mercy estimate

    val hpPct: Float get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f

    /** Get the current phase modifier and label. */
    fun phaseModifier(): Pair<Float, String> {
        var mod = 1f
        var label = "phase1"
        for (phase in boss.phases) {
            if (hpPct <= phase.fromHpPct) {
                mod = phase.modifier
                label = phase.label
            }
        }
        return mod to label
    }

    /** Compute damage for a form score at a fatigue band. */
    fun damageFor(formScore: Float, band: FatigueBand): Int {
        val factor = formFloor + (1 - formFloor) * maxOf(0f, formScore).pow(formExponent)
        val fr = responses[band] ?: FatigueResponse()
        val fatigueMod = if (staggerRepsLeft > 0) {
            responses[FatigueBand.FADING]?.modifier ?: 1f
        } else {
            fr.modifier
        }
        val phase = phaseModifier().first
        return round(baseDamage * factor * combo.multiplier * phase * fatigueMod).toInt()
    }

    /** Process one local rep. */
    fun onRep(formScore: Float, band: FatigueBand): Int {
        if (dead) return 0
        combo.onRep(formScore)
        val dmg = damageFor(formScore, band)
        applyDamage(dmg)
        reps++

        // Fresh players get a boss that regenerates; that's the motivation for "go harder"
        val fr = responses[band]
        if (fr != null && fr.regenPerRep > 0 && !dead) {
            hp = minOf(maxHp, hp + fr.regenPerRep)
        }
        if (staggerRepsLeft > 0) staggerRepsLeft--

        recent.addLast(dmg / maxOf(1f, combo.multiplier))  // Combo-neutral
        if (recent.size > 5) recent.removeFirst()

        return dmg
    }

    /** Process damage from another player in a duel. Idempotent by (playerId, seq). */
    fun onRemoteDamage(playerId: String, seq: Int, damage: Int) {
        if (!applied.add("$playerId:$seq")) return
        applyDamage(damage)
    }

    private fun applyDamage(d: Int) {
        totalDamage += d
        hp = maxOf(0, hp - d)
        if (hp <= 0) dead = true
    }

    private fun recentMeanDamage(): Float =
        if (recent.isEmpty()) baseDamage * formFloor else recent.average().toFloat()

    /** The boss cannot outlast you, but it makes you earn the ending. Safest demo behaviour. */
    fun onFatigueBand(band: FatigueBand, expectedDamagePerRep: Float = recentMeanDamage()) {
        if (band == FatigueBand.FADING && staggerRepsLeft == 0) {
            staggerRepsLeft = responses[FatigueBand.FADING]?.staggerReps ?: 5
        }
        if (band == FatigueBand.GASSED && !mercyActive && !mercyDisabled) {
            mercyActive = true
            val n = responses[FatigueBand.GASSED]?.mercyRepsToFinish ?: 4
            hp = minOf(hp, maxOf(1, round(n * expectedDamagePerRep).toInt()))
            if (hp <= 0) dead = true
        }
    }

    /** Reset with an optional new boss. */
    fun reset(newBoss: BossConfig = boss) {
        boss = newBoss
        maxHp = if (casual) (newBoss.maxHp * 0.5f).toInt() else newBoss.maxHp
        hp = maxHp
        reps = 0
        totalDamage = 0
        dead = false
        staggerRepsLeft = 0
        mercyActive = false
        combo.reset()
        applied.clear()
        recent.clear()
    }

    /** Snapshot of the current combat state. */
    fun state(): CombatState {
        val (phaseMod, phaseLabel) = phaseModifier()
        return CombatState(
            bossId = boss.id,
            bossName = boss.name,
            hp = hp,
            maxHp = maxHp,
            phaseLabel = phaseLabel,
            phaseModifier = phaseMod,
            reps = reps,
            totalDamage = totalDamage,
            dead = dead,
            comboStreak = combo.streak,
            comboMultiplier = combo.multiplier,
            lastDamage = null,
            staggered = staggerRepsLeft > 0,
            mercyActive = mercyActive,
        )
    }
}
