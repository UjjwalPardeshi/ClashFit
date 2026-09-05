package com.clashfit.run

import com.clashfit.core.util.Clock
import com.clashfit.data.ActivityKind
import com.clashfit.data.RunDao
import com.clashfit.data.RunEntity
import com.clashfit.data.RunPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Run and walk data access. */
class RunRepository(
    val dao: RunDao,
    private val clock: Clock,
) {
    fun recentRuns(limit: Int = 50): Flow<List<RunSummary>> = dao.recent(limit).map { runs ->
        runs.map { RunSummary(it) }
    }

    fun recentOfKind(kind: String, limit: Int = 50): Flow<List<RunSummary>> =
        dao.recentOfKind(kind, limit).map { runs -> runs.map { RunSummary(it) } }

    suspend fun getRun(runId: Long): RunSummary? = dao.run(runId)?.let { RunSummary(it) }

    suspend fun getRunPoints(runId: Long) = dao.points(runId)

    /** Distance in metres over the last seven days, runs and walks together. */
    suspend fun weeklyDistanceM(): Float = dao.distanceSince(clock.nowMs() - WEEK_MS)

    /** Everything the run home screen shows above the list, in one pass. */
    suspend fun totals(): ActivityTotals {
        val now = clock.nowMs()
        return ActivityTotals(
            weekDistanceM = dao.distanceSince(now - WEEK_MS),
            weekMovingMs = dao.movingMsSince(now - WEEK_MS),
            weekActivities = dao.countSince(now - WEEK_MS),
            weekSteps = dao.stepsSince(now - WEEK_MS),
            monthDistanceM = dao.distanceSince(now - MONTH_MS),
            monthActivities = dao.countSince(now - MONTH_MS),
        )
    }

    /**
     * The bests every *other* activity has set, for judging the one just finished.
     *
     * Excluding the activity itself matters: by the time a summary screen asks, the row is already
     * in the table, so `MAX(distanceM)` over everything would include the run being judged and no
     * activity could ever be a personal best.
     */
    suspend fun previousBests(exceptId: Long): PreviousBests = PreviousBests(
        distanceM = dao.bestDistanceM(exceptId),
        climbM = dao.bestClimbM(exceptId),
        fastestKmSec = dao.bestFastestKmSec(exceptId),
        steps = dao.bestSteps(exceptId),
    )

    suspend fun insertRun(
        distanceM: Float,
        movingMs: Long,
        pace: Float,
        cadence: Int,
        elev: Float,
        splits: String,
        kind: String = ActivityKind.RUN,
    ): Long = dao.insertRun(
        RunEntity(
            startedAtMs = clock.nowMs(),
            endedAtMs = null,
            distanceM = distanceM,
            movingMs = movingMs,
            avgPaceSecPerKm = pace,
            cadenceSpm = cadence,
            elevationGainM = elev,
            splitsJson = splits,
            kind = kind,
        ),
    )

    suspend fun insertPoints(runId: Long, points: List<RunPointEntity>) {
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
        steps: Int = 0,
        fastestKmSec: Float? = null,
    ) {
        dao.finish(runId, endedAtMs, distanceM, movingMs, pace, cadence, elev, splits, steps, fastestKmSec)
    }

    private companion object {
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
        const val MONTH_MS = 30 * 24 * 60 * 60 * 1000L
    }
}

/** This week and this month, for the goal ring and the totals strip. */
data class ActivityTotals(
    val weekDistanceM: Float = 0f,
    val weekMovingMs: Long = 0L,
    val weekActivities: Int = 0,
    val weekSteps: Int = 0,
    val monthDistanceM: Float = 0f,
    val monthActivities: Int = 0,
)

/** The best every other activity has managed. Nulls mean nothing to beat yet. */
data class PreviousBests(
    val distanceM: Float?,
    val climbM: Float?,
    val fastestKmSec: Float?,
    val steps: Int?,
) {
    val isFirstEver: Boolean get() = distanceM == null

    /**
     * Which records [now] just set. Empty on the very first activity: there is nothing to have
     * beaten, and calling a first run a clean sweep of personal bests devalues the real ones.
     */
    fun recordsSetBy(now: RunSummary): List<String> {
        if (isFirstEver) return emptyList()
        val out = mutableListOf<String>()
        if (distanceM != null && now.distanceM > distanceM + 1f) out += "Longest activity"
        if (climbM != null && now.elevationGainM > climbM + 1f && now.elevationGainM > 0f) out += "Biggest climb"
        val km = now.fastestKmSec
        if (km != null && (fastestKmSec == null || km < fastestKmSec - 0.5f)) out += "Fastest kilometre"
        if (steps != null && now.steps > steps && now.steps > 0) out += "Most steps"
        return out
    }
}

/** Summary of a completed activity for list and detail views. */
data class RunSummary(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceM: Float,
    val movingMs: Long,
    val avgPaceSecPerKm: Float,
    val cadenceSpm: Int,
    val elevationGainM: Float,
    val kind: String = ActivityKind.RUN,
    val steps: Int = 0,
    val fastestKmSec: Float? = null,
) {
    constructor(entity: RunEntity) : this(
        id = entity.id,
        startedAtMs = entity.startedAtMs,
        endedAtMs = entity.endedAtMs,
        distanceM = entity.distanceM,
        movingMs = entity.movingMs,
        avgPaceSecPerKm = entity.avgPaceSecPerKm,
        cadenceSpm = entity.cadenceSpm,
        elevationGainM = entity.elevationGainM,
        kind = entity.kind,
        steps = entity.steps,
        fastestKmSec = entity.fastestKmSec,
    )

    val isWalk: Boolean get() = kind == ActivityKind.WALK

    /**
     * Wall-clock length of a finished activity.
     *
     * Zero while it is still running rather than "now minus the start": this class is a record of
     * something finished, and reading the system clock here would both break the injected-[Clock]
     * rule and make an unfinished row grow every time it was rendered.
     */
    val durationMs: Long get() = (endedAtMs ?: startedAtMs) - startedAtMs

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
