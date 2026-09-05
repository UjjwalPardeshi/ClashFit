package com.clashfit.run

import com.clashfit.data.ActivityKind
import com.clashfit.data.RunEntity
import com.clashfit.data.RunPointEntity

/** Current running session state. */
data class RunState(
    val runId: Long? = null,
    val startedAtMs: Long? = null,
    /** `RUN` or `WALK`. Chosen when the activity starts and never changes mid-activity. */
    val kind: String = ActivityKind.RUN,
    val steps: Int = 0,
    val distanceM: Float = 0f,
    val movingMs: Long = 0L,
    val avgPaceSecPerKm: Float = 0f,
    val cadenceSpm: Int = 0,
    val elevationGainM: Float = 0f,
    val isMoving: Boolean = false,
    val isPaused: Boolean = false,
    /**
     * True while the position is coming from the step counter rather than from satellites.
     *
     * Surfaced rather than hidden: a route drawn from footsteps is an estimate, and the athlete
     * should be able to see which of the two they are looking at.
     */
    val indoors: Boolean = false,
    val points: List<RunPointEntity> = emptyList(),
    val splits: List<Float> = emptyList(), // per-km pace, seconds
) {
    val isActive: Boolean get() = runId != null && !isPaused
    val isRunning: Boolean get() = startedAtMs != null
    val isWalk: Boolean get() = kind == ActivityKind.WALK
    val isZombieRun: Boolean get() = kind == ActivityKind.ZOMBIE_RUN

    fun asEntity(): RunEntity? = if (runId == null || startedAtMs == null) null
    else RunEntity(
        id = runId,
        startedAtMs = startedAtMs,
        endedAtMs = null,
        distanceM = distanceM,
        movingMs = movingMs,
        avgPaceSecPerKm = avgPaceSecPerKm,
        cadenceSpm = cadenceSpm,
        elevationGainM = elevationGainM,
        splitsJson = splits.joinToString(","),
        kind = kind,
        steps = steps,
    )
}
