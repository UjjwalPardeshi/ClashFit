package com.clashfit.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Mirrors of the JSON in assets/config. Every tunable number in the game lives in these files;
// nothing downstream compiles a constant. Unknown keys are ignored so a config edit on the
// phone never crashes the app. docs/08-DATA-MODEL.md §2, docs/adr/ADR-005.

@Serializable
data class PoseConfig(
    val version: Int = 1,
    val filter: FilterSpec = FilterSpec(),
    val visibilityThreshold: Float = 0.6f,
    val framingLostFrames: Int = 45,
    val framing: FramingSpec = FramingSpec(),
    val detector: DetectorSpec = DetectorSpec(),
    val fatigue: FatigueSpec = FatigueSpec(),
    val debugOverlay: Boolean = false,
) {
    @Serializable
    data class FilterSpec(val minCutoff: Float = 1.0f, val beta: Float = 0.007f, val dCutoff: Float = 1.0f)

    @Serializable
    data class FramingSpec(
        val targetBoxHeightMin: Float = 0.55f,
        val targetBoxHeightMax: Float = 0.92f,
        val holdToStartMs: Long = 2000,
        val reacquireAfterLossMs: Long = 2000,
    )

    @Serializable
    data class DetectorSpec(
        val minPoseDetectionConfidence: Float = 0.6f,
        val minPosePresenceConfidence: Float = 0.6f,
        val minTrackingConfidence: Float = 0.6f,
        val model: String = "pose_landmarker_full.task",
        val delegate: String = "GPU",
    )

    @Serializable
    data class FatigueSpec(
        val baselineReps: Int = 3,
        val weights: Weights = Weights(),
        val ema: Float = 0.4f,
        val pauseGrowthNormSec: Float = 3.0f,
        val bands: Bands = Bands(),
        val bandLatchReps: Int = 1,
    ) {
        @Serializable
        data class Weights(val velocityLoss: Float = 0.45f, val romLoss: Float = 0.35f, val pauseGrowth: Float = 0.2f)

        @Serializable
        data class Bands(val working: Float = 0.15f, val fading: Float = 0.30f, val gassed: Float = 0.50f)
    }
}

@Serializable
data class CombatConfig(
    val version: Int = 1,
    val baseDamage: Int = 100,
    val formFloor: Float = 0.35f,
    val formExponent: Float = 1.2f,
    val combo: ComboSpec = ComboSpec(),
    val boss: BossSpec = BossSpec(),
    val fatigueResponse: Map<String, FatigueResponseSpec> = emptyMap(),
    val casual: CasualSpec = CasualSpec(),
    val rest: RestSpec = RestSpec(),
    val setEnd: SetEndSpec = SetEndSpec(),
    val modes: ModesSpec = ModesSpec(),
    val familyGames: FamilyGamesSpec = FamilyGamesSpec(),
) {
    @Serializable
    data class ComboSpec(val step: Float = 0.12f, val cap: Float = 2.5f, val threshold: Float = 0.75f, val graceAtStreak: Int = 6)

    @Serializable
    data class BossPhaseSpec(val fromHpPct: Float, val modifier: Float, val label: String)

    @Serializable
    data class BossSpec(
        val id: String = "pacemaker",
        val name: String = "THE PACEMAKER",
        val maxHp: Int = 3000,
        val phases: List<BossPhaseSpec> = listOf(BossPhaseSpec(1f, 1f, "phase1")),
    )

    @Serializable
    data class FatigueResponseSpec(
        val modifier: Float = 1f,
        val regenPerRep: Int = 0,
        val staggerReps: Int = 0,
        val mercyRepsToFinish: Int = 0,
    )

    @Serializable
    data class CasualSpec(val damageMultiplier: Float = 1.6f, val formFloor: Float = 0.6f, val bossHpMultiplier: Float = 0.5f)

    @Serializable
    data class RestSpec(val freshSeconds: Int = 30, val gassedSeconds: Int = 75)

    @Serializable
    data class SetEndSpec(val noRepTimeoutSec: Int = 12, val noFrameTimeoutSec: Int = 30)

    @Serializable
    data class ModesSpec(
        @SerialName("TIME_ATTACK") val timeAttack: TimeAttackSpec = TimeAttackSpec(),
        @SerialName("GHOST_RACE") val ghostRace: GhostRaceSpec = GhostRaceSpec(),
        @SerialName("SURVIVAL") val survival: SurvivalSpec = SurvivalSpec(),
        @SerialName("BOSS_RUSH") val bossRush: BossRushSpec = BossRushSpec(),
        @SerialName("DUEL") val duel: DuelSpec = DuelSpec(),
        @SerialName("TEMPO_TRIAL") val tempoTrial: TempoTrialSpec = TempoTrialSpec(),
        @SerialName("REP_RACE") val repRace: RepRaceSpec = RepRaceSpec(),
        @SerialName("OUTBREAK") val outbreak: OutbreakSpec = OutbreakSpec(),
    ) {
        @Serializable data class TimeAttackSpec(val enabled: Boolean = true, val durationSec: Int = 60, val bossHpUncapped: Boolean = true)
        @Serializable data class GhostRaceSpec(val enabled: Boolean = true, val defaultGhost: String = "pacer_silver")
        @Serializable data class SurvivalSpec(val enabled: Boolean = true, val hpPerWave: Int = 900, val formThresholdStep: Float = 0.03f, val mercyDisabled: Boolean = true)
        @Serializable data class BossRushSpec(val enabled: Boolean = true, val sequence: List<String> = listOf("pacemaker"))
        @Serializable data class DuelSpec(val enabled: Boolean = true, val rules: String = "TIME_ATTACK")
        @Serializable data class TempoTrialSpec(val enabled: Boolean = true, val targetEccSec: Float = 0.55f, val tolerance: Float = 0.35f, val floor: Float = 0.3f)
        @Serializable data class RepRaceSpec(val enabled: Boolean = true, val durationsSec: List<Int> = listOf(30, 60, 180))
        @Serializable data class OutbreakSpec(
            val enabled: Boolean = false,
            val requiresNetwork: Boolean = true,
            val requiresLocation: Boolean = true,
            val headStartSec: Int = 60,
            val spawnRadiusM: Int = 400,
            val captureRadiusM: Int = 12,
            val ammoPickups: Int = 6,
            val note: String = "The one mode that leaves the device. See docs/33-FEATURE-OUTBREAK.md."
        )
    }

    @Serializable
    data class FamilyGamesSpec(
        @SerialName("SIEGE") val siege: SiegeSpec = SiegeSpec(),
        @SerialName("PURSUIT") val pursuit: PursuitSpec = PursuitSpec(),
        @SerialName("BREAKER") val breaker: BreakerSpec = BreakerSpec(),
        @SerialName("SIGIL") val sigil: SigilSpec = SigilSpec(),
    ) {
        @Serializable data class SiegeSpec(val playerHp: Int = 100, val bossHp: Int = 1200, val dpsPerQuality: Float = 26f, val hitOnBreak: Int = 18)
        @Serializable data class PursuitSpec(val startGapM: Float = 12f, val escapeAtM: Float = 200f, val pursuerMps: Float = 2.6f, val metresPerCycle: Float = 1.6f)
        @Serializable data class BreakerSpec(val floors: Int = 12, val cmPerFloor: Float = 14f)
        @Serializable data class SigilSpec(val segments: Int = 8)
    }
}

/**
 * One exercise record. `detector` is family-specific and decoded by that family's detector,
 * so adding a family never touches this file.
 */
@Serializable
data class ExerciseSpec(
    val id: String,
    val family: String = "REP_CYCLE",
    val name: String,
    val tags: List<String> = emptyList(),
    val framing: String = "side",
    val difficulty: Int = 1,
    val games: List<String> = emptyList(),
    val detector: JsonObject,
    val form: FormSpec? = null,
    val fatigue: FatigueSignalSpec = FatigueSignalSpec(),
    val cues: Map<String, String> = emptyMap(),
) {
    val familyEnum: com.clashfit.core.model.Family
        get() = runCatching { com.clashfit.core.model.Family.valueOf(family) }
            .getOrDefault(com.clashfit.core.model.Family.REP_CYCLE)

    @Serializable
    data class FormSpec(
        val weights: Weights = Weights(),
        val tempo: TempoSpec = TempoSpec(),
        val alignment: AlignmentSpec? = null,
        val depthExponent: Float = 1.5f,
    ) {
        @Serializable data class Weights(val depth: Float = 0.4f, val rom: Float = 0.25f, val tempo: Float = 0.2f, val alignment: Float = 0.15f)
        @Serializable data class TempoSpec(val eccentricTargetSec: Float = 0.35f, val bottomPauseSec: Float = 0.12f)
        @Serializable data class AlignmentSpec(val type: String = "KNEE_TRACKING", val fullMarksOffset: Float = 0.15f, val zeroMarksOffset: Float = 0.45f)
    }

    @Serializable
    data class FatigueSignalSpec(val signal: String = "VELOCITY_LOSS", val baselineReps: Int = 3, val baselineSec: Float = 3f)
}

@Serializable
data class ExerciseIndex(val count: Int = 0, val exercises: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val id: String, val name: String, val family: String, val difficulty: Int = 1,
                     val games: List<String> = emptyList(), val tags: List<String> = emptyList())
}

/** A recorded rep timeline. A ghost is a file you can send. */
@Serializable
data class GhostData(
    val type: String = "clashfit-ghost",
    val v: Int = 1,
    val meta: Meta,
    val events: List<Event>,
) {
    @Serializable
    data class Meta(
        val name: String,
        val exercise: String,
        val reps: Int,
        val totalDamage: Int,
        val note: String = "",
        val shipped: Boolean = false,
    )

    @Serializable
    data class Event(val t: Long, val damage: Int)
}

@Serializable
data class ClinicProtocol(
    val id: String,
    val name: String,
    val protocol: String,
    val exercise: String,
    val durationSec: Int,
    val measures: String = "",
    val notNested: String = "",
    val reporting: Reporting = Reporting(),
) {
    @Serializable
    data class Reporting(
        val showRawCount: Boolean = true,
        val showPersonalTrend: Boolean = true,
        val showNormComparison: Boolean = false,
        val phrasing: Map<String, String> = emptyMap(),
    )
}
