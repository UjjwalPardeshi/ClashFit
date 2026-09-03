package com.clashfit.data

import com.clashfit.engine.summary.LadderCheckResult
import com.clashfit.engine.summary.Ladders
import com.clashfit.engine.summary.PersonalBests
import com.clashfit.engine.summary.Progression
import com.clashfit.engine.summary.StreakState
import kotlinx.coroutines.flow.first

/**
 * Streaks, personal bests and ladders live in Room; the rules live in the pure [Progression]
 * engine. This repository loads the state, runs one session through the rules and writes the
 * result back. It is the only place those three tables are written.
 */
class ProgressionRepository(private val db: ClashDb) {

    /** What changed because of one finished session. The summary shows it. */
    data class Outcome(
        val streak: StreakState,
        val newBests: List<Progression.PersonalBestRef>,
        val ladder: LadderCheckResult?,
    )

    suspend fun load(): Progression {
        val streak = db.streak().get()?.let {
            StreakState(it.current, it.best, it.lastDayKey, it.freezes, it.restDaysUsedThisWeek, it.weekKey)
        } ?: StreakState()
        val bests = db.bests().all().first()
            .associate { it.exerciseId to PersonalBests(it.reps, it.formScore, it.depthCm, it.heightCm, it.holdSec) }
            .toMutableMap()
        val ladder = db.ladders().all().first().associate { it.ladderId to it.rung }.toMutableMap()
        return Progression(streak, bests, ladder)
    }

    /**
     * Banks a finished session. Casual sessions still count for the streak (showing up is the
     * habit) but never set a personal best or move a ladder.
     */
    suspend fun onSessionFinished(
        exerciseId: String,
        reps: Int,
        formMean: Float,
        atMs: Long,
        repDetails: List<Progression.RepDetail>,
        casual: Boolean,
    ): Outcome {
        val p = load()
        p.updateStreak(atMs)
        var newBests: List<Progression.PersonalBestRef> = emptyList()
        var ladder: LadderCheckResult? = null
        val ladderId = Ladders.all.entries.firstOrNull { (_, rungs) -> exerciseId in rungs }?.key
        if (!casual && reps > 0) {
            newBests = p.personalBestsIn(exerciseId, reps, formMean)
            p.updateBests(exerciseId, reps, formMean, repDetails)
            if (ladderId != null) {
                val trend = db.sessions().byExercise(exerciseId, TREND_WINDOW).reversed()
                    .map { Progression.TrendPoint(it.exerciseId, it.formMean) }
                ladder = p.ladderCheck(ladderId, Ladders.all.getValue(ladderId), exerciseId, trend = trend)
            }
        }
        save(p, exerciseId, ladderId)
        return Outcome(p.getStreak(), newBests, ladder)
    }

    private suspend fun save(p: Progression, exerciseId: String, ladderId: String?) {
        val s = p.getStreak()
        db.streak().upsert(StreakEntity(1, s.current, s.best, s.lastDayKey, s.freezes, s.restDaysUsedThisWeek, s.weekKey))
        val b = p.getBests(exerciseId)
        db.bests().upsert(PersonalBestEntity(exerciseId, b.reps ?: 0, b.formScore ?: 0f, b.depthCm, b.heightCm, b.holdSec))
        if (ladderId != null) p.getLadder(ladderId)?.let { db.ladders().upsert(LadderEntity(ladderId, it)) }
    }

    private companion object {
        const val TREND_WINDOW = 3
    }
}
