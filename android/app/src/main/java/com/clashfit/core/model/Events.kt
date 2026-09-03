package com.clashfit.core.model

/**
 * Raw kinematics of one completed REP_CYCLE rep, straight out of the rep state machine.
 * Angles are in signed "u" space (u = s * theta) so every comparison is written once.
 */
data class RepEvent(
    val repIndex: Int,
    val exerciseId: String,
    val tStartMs: Long,
    val tEndMs: Long,
    val thetaMin: Float,
    val thetaMax: Float,
    val uMin: Float,
    val uMax: Float,
    val uTopRef: Float,
    val uTarget: Float,
    val tEccSec: Float,
    val tBottomSec: Float,
    val tConSec: Float,
    val concentricVelocity: Float,
    val gapSec: Float,
    val validFrameRatio: Float,
) {
    val durationMs: Long get() = tEndMs - tStartMs
}

/** Four named geometric quantities, never a vague "form quality". The weakest names the fault. */
data class FormScore(
    val depth: Float,
    val rom: Float,
    val tempo: Float,
    val alignment: Float,
    val formScore: Float,
    val reason: String,
    val tempoAccuracy: Float? = null,
) {
    fun verdict(clean: Float = 0.80f, ok: Float = 0.55f): Verdict = when {
        formScore >= clean -> Verdict.CLEAN
        formScore >= ok -> Verdict.OK
        else -> Verdict.SHALLOW
    }
}

/** One fatigue model, five families: decay of the family's primary output against the player's own baseline. */
data class FatigueState(
    val value: Float,
    val band: FatigueBand,
    val losses: Map<String, Float>,
    val baselineReps: Int,
    val hasBaseline: Boolean,
) {
    val velocityLoss: Float get() = losses["velocity"] ?: 0f
    val romLoss: Float get() = losses["rom"] ?: 0f
    val pauseGrowth: Float get() = losses["gap"] ?: 0f

    companion object {
        val FRESH = FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false)
    }
}

/**
 * What the non-REP_CYCLE detectors emit. Same downstream contract for every family: a scored
 * event, a fatigue sample through the shared estimator, damage through the same combat engine.
 */
sealed interface MovementEvent {
    val tStartMs: Long
    val tEndMs: Long
    val formScore: Float
    /** Family-specific fatigue signals, e.g. "tremor", "cadence", "height". */
    val signals: Map<String, Float>
}

data class HoldEvent(
    override val tStartMs: Long,
    override val tEndMs: Long,
    override val formScore: Float,
    override val signals: Map<String, Float>,
    val holdSec: Float,
    val quality: Float,
    val completed: Boolean,
    val tremor: Float,
) : MovementEvent

data class CadenceEvent(
    override val tStartMs: Long,
    override val tEndMs: Long,
    override val formScore: Float,
    override val signals: Map<String, Float>,
    val cadence: Float,
    val amplitude: Float,
) : MovementEvent

data class JumpEvent(
    override val tStartMs: Long,
    override val tEndMs: Long,
    override val formScore: Float,
    override val signals: Map<String, Float>,
    val heightCm: Float,
    val softness: Float,
    val airborneMs: Long,
) : MovementEvent

data class PoseEvent(
    override val tStartMs: Long,
    override val tEndMs: Long,
    override val formScore: Float,
    override val signals: Map<String, Float>,
    val accuracy: Float,
    val heldSec: Float,
    val worstJoint: String?,
    val cue: String?,
) : MovementEvent

/**
 * The per-rep record everything downstream reads: the summary chart, the coach payload, the
 * store, and any judge asking what was actually measured. NaN means "not applicable".
 */
data class RepRecord(
    val repIndex: Int,
    val exerciseId: String,
    val family: Family,
    val tStartMs: Long,
    val tEndMs: Long,
    val formScore: Float,
    val depth: Float,
    val rom: Float,
    val tempo: Float,
    val alignment: Float,
    val reason: String,
    val verdict: Verdict,
    val concentricVelocity: Float,
    val fatigue: FatigueState,
    val damage: Int,
    val combo: Float,
    val validFrameRatio: Float = 1f,
    val depthCm: Float = Float.NaN,
    val heightCm: Float = Float.NaN,
    val holdSec: Float = Float.NaN,
    val accuracy: Float = Float.NaN,
)
