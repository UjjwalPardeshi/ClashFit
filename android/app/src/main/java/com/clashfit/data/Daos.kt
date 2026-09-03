package com.clashfit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun observe(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun get(): ProfileEntity?
    @Upsert suspend fun upsert(p: ProfileEntity)
}

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration WHERE exerciseId = :exerciseId") suspend fun get(exerciseId: String): CalibrationEntity?
    @Upsert suspend fun upsert(c: CalibrationEntity)
    @Query("DELETE FROM calibration") suspend fun clear()
}

@Dao
interface SessionDao {
    @Insert suspend fun insertSession(s: SessionEntity): Long
    @Insert suspend fun insertSet(s: SetEntity): Long
    @Insert suspend fun insertReps(reps: List<RepEntity>)
    @Query("UPDATE sessions SET endedAtMs = :endedAtMs, outcome = :outcome, totalDamage = :damage, totalReps = :reps, formMean = :formMean, peakFatigue = :peakFatigue, peakBand = :peakBand WHERE id = :id")
    suspend fun finish(id: Long, endedAtMs: Long, outcome: String?, damage: Int, reps: Int, formMean: Float, peakFatigue: Float, peakBand: String)

    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: Long): SessionEntity?
    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY setIndex") suspend fun sets(sessionId: Long): List<SetEntity>
    @Query("SELECT * FROM reps WHERE sessionId = :sessionId ORDER BY tStartMs") suspend fun reps(sessionId: Long): List<RepEntity>

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC LIMIT :limit") fun recent(limit: Int = 50): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE exerciseId = :exerciseId AND endedAtMs IS NOT NULL ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun byExercise(exerciseId: String, limit: Int = 20): List<SessionEntity>
    @Query("SELECT * FROM sessions WHERE mode = :mode AND endedAtMs IS NOT NULL ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun byMode(mode: String, limit: Int = 50): List<SessionEntity>
    @Query("SELECT COUNT(*) FROM sessions WHERE endedAtMs IS NOT NULL") fun count(): Flow<Int>
    @Query("SELECT * FROM sessions WHERE endedAtMs IS NOT NULL ORDER BY startedAtMs DESC LIMIT 1") suspend fun last(): SessionEntity?

    /** Older sessions keep their aggregates but drop per-rep telemetry, so a phone never fills up. */
    @Query("DELETE FROM reps WHERE sessionId NOT IN (SELECT id FROM sessions ORDER BY startedAtMs DESC LIMIT :keepFull)")
    suspend fun compact(keepFull: Int = 20)

    @Transaction
    suspend fun recordSet(set: SetEntity, reps: List<RepEntity>): Long {
        val setId = insertSet(set)
        insertReps(reps.map { it.copy(setId = setId) })
        return setId
    }
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak WHERE id = 1") fun observe(): Flow<StreakEntity?>
    @Query("SELECT * FROM streak WHERE id = 1") suspend fun get(): StreakEntity?
    @Upsert suspend fun upsert(s: StreakEntity)
}

@Dao
interface BestDao {
    @Query("SELECT * FROM bests WHERE exerciseId = :exerciseId") suspend fun get(exerciseId: String): PersonalBestEntity?
    @Query("SELECT * FROM bests") fun all(): Flow<List<PersonalBestEntity>>
    @Upsert suspend fun upsert(b: PersonalBestEntity)
}

@Dao
interface LadderDao {
    @Query("SELECT * FROM ladders WHERE ladderId = :ladderId") suspend fun get(ladderId: String): LadderEntity?
    @Query("SELECT * FROM ladders") fun all(): Flow<List<LadderEntity>>
    @Upsert suspend fun upsert(l: LadderEntity)
}

@Dao
interface RunDao {
    @Insert suspend fun insertRun(r: RunEntity): Long
    @Insert suspend fun insertPoints(points: List<RunPointEntity>)
    @Query("UPDATE runs SET endedAtMs = :endedAtMs, distanceM = :distanceM, movingMs = :movingMs, avgPaceSecPerKm = :pace, cadenceSpm = :cadence, elevationGainM = :elev, splitsJson = :splits WHERE id = :id")
    suspend fun finish(id: Long, endedAtMs: Long, distanceM: Float, movingMs: Long, pace: Float, cadence: Int, elev: Float, splits: String)
    @Query("SELECT * FROM runs WHERE id = :id") suspend fun run(id: Long): RunEntity?
    @Query("SELECT * FROM run_points WHERE runId = :runId ORDER BY tMs") suspend fun points(runId: Long): List<RunPointEntity>
    @Query("SELECT * FROM runs WHERE endedAtMs IS NOT NULL ORDER BY startedAtMs DESC LIMIT :limit") fun recent(limit: Int = 50): Flow<List<RunEntity>>
    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM runs WHERE endedAtMs IS NOT NULL AND startedAtMs >= :sinceMs") suspend fun distanceSince(sinceMs: Long): Float
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute") fun all(): Flow<List<AlarmEntity>>
    @Query("SELECT * FROM alarms WHERE enabled = 1") suspend fun enabled(): List<AlarmEntity>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun get(id: Long): AlarmEntity?
    @Upsert suspend fun upsert(a: AlarmEntity): Long
    @Delete suspend fun delete(a: AlarmEntity)
}

@Dao
interface GhostDao {
    @Query("SELECT * FROM ghosts ORDER BY createdAtMs DESC") fun all(): Flow<List<GhostEntity>>
    @Query("SELECT * FROM ghosts WHERE exerciseId = :exerciseId ORDER BY createdAtMs DESC") suspend fun forExercise(exerciseId: String): List<GhostEntity>
    @Query("SELECT * FROM ghosts WHERE id = :id") suspend fun get(id: String): GhostEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(g: GhostEntity)
    @Delete suspend fun delete(g: GhostEntity)
}
