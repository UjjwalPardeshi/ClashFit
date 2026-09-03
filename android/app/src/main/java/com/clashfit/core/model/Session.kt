package com.clashfit.core.model

/**
 * The session engine's public snapshot. The UI renders this and nothing else; the engine
 * itself never touches Android. Mirrors `SessionEngine.state()` in the prototype.
 */
data class SessionState(
    val mode: GameMode,
    val exerciseId: String,
    val family: Family,
    val phase: Phase,
    val calib: CalibState,
    val calibProgress: Float,
    val missingJoints: List<String>,
    val framing: Framing,
    val cue: String?,
    val angleDeg: Float,
    val topRefDeg: Float?,
    val setIndex: Int,
    val setReps: Int,
    val reps: Int,
    val lastRep: RepRecord?,
    val fatigue: FatigueState,
    val combat: CombatState,
    val game: FamilyGameState?,
    val wave: Int,
    val rushIndex: Int,
    val timeLeftMs: Long?,
    val playerDamage: Int,
    val ghostDamage: Int,
    val ghostName: String?,
    val ghostFinished: Boolean?,
    val telemetry: SetTelemetry?,
    val coach: CoachOutput?,
    val restSec: Int?,
    val ended: Boolean,
    val endReason: EndReason?,
)

/** Compact set payload for the coach, ~150 tokens. The model never sees landmarks. */
data class SetTelemetry(
    val exercise: String,
    val reps: Int,
    val formMean: Float,
    val formFirst3: Float,
    val formLast3: Float,
    val formMeanPct: Int,
    val formFirst3Pct: Int,
    val formLast3Pct: Int,
    val depthCm: Int?,
    val depthDropCm: Int?,
    val velocityLossPct: Int,
    val romLossPct: Int,
    val fatigueBand: FatigueBand,
    val bestRep: RepRef?,
    val worstRep: RepRef?,
    val comboMax: Float,
    val comboReps: Int,
    val bossHpPct: Int,
    val sessionSetIndex: Int,
    val restSec: Int,
    val trend: Trend,
    val asymmetryPct: Int? = null,
    val weakerSide: String? = null,
) {
    data class RepRef(val index: Int, val form: Float, val reason: String? = null)
    enum class Trend { IMPROVING, DECLINING, FLAT }
}

data class CoachOutput(val coachLine: String, val bossLine: String, val source: CoachSource)
