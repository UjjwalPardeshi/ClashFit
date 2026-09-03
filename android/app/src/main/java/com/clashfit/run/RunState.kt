package com.clashfit.run

import com.clashfit.data.RunEntity
import com.clashfit.data.RunPointEntity

/** Current running session state. */
data class RunState(
    val runId: Long? = null,
    val startedAtMs: Long? = null,
    val distanceM: Float = 0f,
    val movingMs: Long = 0L,
    val avgPaceSecPerKm: Float = 0f,
    val cadenceSpm: Int = 0,
    val elevationGainM: Float = 0f,
    val isMoving: Boolean = false,
    val isPaused: Boolean = false,
    val points: List<RunPointEntity> = emptyList(),
    val splits: List<Float> = emptyList(), // per-km pace, seconds
) {
    val isActive: Boolean get() = runId != null && !isPaused
    val isRunning: Boolean get() = startedAtMs != null

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
    )
}
