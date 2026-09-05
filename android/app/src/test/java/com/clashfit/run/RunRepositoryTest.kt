package com.clashfit.run

import com.clashfit.core.util.FakeClock
import com.clashfit.data.RunEntity
import com.clashfit.data.RunPointEntity
import com.clashfit.data.RunDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Unit tests for RunRepository. */
class RunRepositoryTest {
    private val clock = FakeClock()
    private val dao = FakeRunDao()
    private val repo = RunRepository(dao, clock)

    @Test
    fun insertRun_returnId() = runTest {
        val id = repo.insertRun(1000f, 60_000, 360f, 160, 50f, "360")
        assertEquals(1L, id)
    }

    @Test
    fun finishRun_updates() = runTest {
        val id = repo.insertRun(1000f, 60_000, 360f, 160, 50f, "360")
        repo.finishRun(id, clock.nowMs() + 60_000, 1000f, 60_000, 360f, 160, 50f, "360")
        val run = dao.run(id)
        assertNotNull(run)
        assertEquals(1000f, run.distanceM)
    }

    @Test
    fun weeklyDistanceM_sums_last_7days() = runTest {
        // weeklyDistanceM only counts finished runs (RunDao.distanceSince requires
        // endedAtMs IS NOT NULL in production, matching FakeRunDao here), so each run
        // must be finished, not just inserted, to be picked up by the weekly sum.
        clock.set(1000)
        val id1 = repo.insertRun(1000f, 10_000, 300f, 170, 0f, "")
        repo.finishRun(id1, clock.nowMs(), 1000f, 10_000, 300f, 170, 0f, "")
        clock.set(2000)
        val id2 = repo.insertRun(2000f, 20_000, 300f, 170, 0f, "")
        repo.finishRun(id2, clock.nowMs(), 2000f, 20_000, 300f, 170, 0f, "")
        clock.set(10_000_000) // 10 seconds later (well within 7 days for this test)

        val distance = repo.weeklyDistanceM()
        assertEquals(3000f, distance)
    }

    @Test
    fun recentRuns_flows() = runTest {
        repo.insertRun(1000f, 10_000, 300f, 170, 0f, "")
        repo.insertRun(2000f, 20_000, 300f, 170, 0f, "")

        val runs = dao.recent(50)
        var collected = emptyList<RunEntity>()
        runs.collect { collected = it }
        assertEquals(2, collected.size)
    }

    private class FakeRunDao : RunDao {
        private val runs = mutableMapOf<Long, RunEntity>()
        private val points = mutableListOf<RunPointEntity>()
        private var nextId = 1L

        override suspend fun insertRun(r: RunEntity): Long {
            val id = nextId++
            runs[id] = r.copy(id = id)
            return id
        }

        override suspend fun insertPoints(points: List<RunPointEntity>) {
            this.points.addAll(points)
        }

        override suspend fun finish(
            id: Long,
            endedAtMs: Long,
            distanceM: Float,
            movingMs: Long,
            pace: Float,
            cadence: Int,
            elev: Float,
            splits: String,
            steps: Int,
            fastestKmSec: Float?,
        ) {
            runs[id]?.let {
                runs[id] = it.copy(
                    endedAtMs = endedAtMs,
                    distanceM = distanceM,
                    movingMs = movingMs,
                    avgPaceSecPerKm = pace,
                    cadenceSpm = cadence,
                    elevationGainM = elev,
                    splitsJson = splits,
                    steps = steps,
                    fastestKmSec = fastestKmSec,
                )
            }
        }

        override suspend fun run(id: Long) = runs[id]

        override suspend fun points(runId: Long) = points.filter { it.runId == runId }

        override fun recent(limit: Int) = flowOf(runs.values.sortedByDescending { it.startedAtMs }.take(limit))

        override fun recentOfKind(kind: String, limit: Int) =
            flowOf(runs.values.filter { it.kind == kind }.sortedByDescending { it.startedAtMs }.take(limit))

        private fun finished(sinceMs: Long) =
            runs.values.filter { it.startedAtMs >= sinceMs && it.endedAtMs != null }

        override suspend fun distanceSince(sinceMs: Long) =
            finished(sinceMs).sumOf { it.distanceM.toDouble() }.toFloat()

        override suspend fun stepsSince(sinceMs: Long) = finished(sinceMs).sumOf { it.steps }

        override suspend fun movingMsSince(sinceMs: Long) = finished(sinceMs).sumOf { it.movingMs }

        override suspend fun countSince(sinceMs: Long) = finished(sinceMs).size

        private fun others(exceptId: Long) = runs.values.filter { it.id != exceptId && it.endedAtMs != null }

        override suspend fun bestDistanceM(exceptId: Long) = others(exceptId).maxOfOrNull { it.distanceM }

        override suspend fun bestClimbM(exceptId: Long) = others(exceptId).maxOfOrNull { it.elevationGainM }

        override suspend fun bestFastestKmSec(exceptId: Long) =
            others(exceptId).mapNotNull { it.fastestKmSec }.minOrNull()

        override suspend fun bestSteps(exceptId: Long) = others(exceptId).maxOfOrNull { it.steps }
    }
}
