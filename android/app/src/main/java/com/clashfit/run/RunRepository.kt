package com.clashfit.run

import com.clashfit.core.util.Clock
import com.clashfit.data.RunDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Run data access and business logic. */
class RunRepository(
    val dao: RunDao,
    private val clock: Clock,
) {
    fun recentRuns(limit: Int = 50): Flow<List<RunSummary>> = dao.recent(limit).map { runs ->
        runs.map { RunSummary(it) }
    }

    suspend fun getRun(runId: Long): RunSummary? = dao.run(runId)?.let { RunSummary(it) }

    suspend fun getRunPoints(runId: Long) = dao.points(runId)

    /** Weekly distance in metres. */
    suspend fun weeklyDistanceM(): Float {
        val nowMs = clock.nowMs()
        val weekAgoMs = nowMs - 7 * 24 * 60 * 60 * 1000L
        return dao.distanceSince(weekAgoMs)
    }

    suspend fun insertRun(distanceM: Float, movingMs: Long, pace: Float, cadence: Int, elev: Float, splits: String): Long {
        val entity = com.clashfit.data.RunEntity(
            startedAtMs = clock.nowMs(),
            endedAtMs = null,
            distanceM = distanceM,
            movingMs = movingMs,
            avgPaceSecPerKm = pace,
            cadenceSpm = cadence,
            elevationGainM = elev,
            splitsJson = splits,
        )
        return dao.insertRun(entity)
    }

    suspend fun insertPoints(runId: Long, points: List<com.clashfit.data.RunPointEntity>) {
        dao.insertPoints(points)
    }

    suspend fun finishRun(
        runId: Long,
        endedAtMs: Long,
        distanceM: Float,
        movingMs: Long,
        pace: Float,
        cadence: Int,
        elev: Float,
        splits: String,
    ) {
        dao.finish(runId, endedAtMs, distanceM, movingMs, pace, cadence, elev, splits)
    }
}

/** Summary of a completed run for list/detail views. */
data class RunSummary(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceM: Float,
    val movingMs: Long,
    val avgPaceSecPerKm: Float,
    val cadenceSpm: Int,
    val elevationGainM: Float,
) {
    constructor(entity: com.clashfit.data.RunEntity) : this(
        id = entity.id,
        startedAtMs = entity.startedAtMs,
        endedAtMs = entity.endedAtMs,
        distanceM = entity.distanceM,
        movingMs = entity.movingMs,
        avgPaceSecPerKm = entity.avgPaceSecPerKm,
        cadenceSpm = entity.cadenceSpm,
        elevationGainM = entity.elevationGainM,
    )

    val durationMs: Long get() = (endedAtMs ?: System.currentTimeMillis()) - startedAtMs
    val paceStr: String get() = formatPace(avgPaceSecPerKm)

    companion object {
        fun formatPace(secPerKm: Float): String {
            if (secPerKm <= 0) return "0:00"
            val mins = secPerKm.toInt() / 60
            val secs = secPerKm.toInt() % 60
            return "%d:%02d".format(mins, secs)
        }
    }
}
