package com.clashfit.engine.summary

import kotlin.math.roundToInt

/**
 * Streak, personal bests, and ladder progression. All rules are from store.js:
 * streaks earn from consistency and rest days, ladders require sustained performance over a window.
 * Never punitive — a broken streak never shows a number lost.
 */

const val MAX_FREEZE_GAP_DAYS = 3
const val DAY_MS = 86_400_000L

/** One protected rest day per week plus three earned freezes. */
data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val lastDayKey: String? = null,
    val freezes: Int = 1,
    val restDaysUsedThisWeek: Int = 0,
    val weekKey: String? = null,
)

/** Personal bests per exercise. Keys: reps, formScore, depthCm, heightCm, holdSec. */
data class PersonalBests(
    val reps: Int? = null,
    val formScore: Float? = null,
    val depthCm: Float? = null,
    val heightCm: Float? = null,
    val holdSec: Float? = null,
)

/** Result of a ladder check. */
data class LadderCheckResult(
    val rung: String,
    val changed: Boolean,
    val promoted: Boolean = false,
    val mean: Float = 0f,
)

/** Ladder rungs: each ladder is a progression of exercises by difficulty. */
object Ladders {
    val PUSH = listOf("wall_push_up", "knee_push_up", "push_up", "pike_push_up")
    val SQUAT = listOf("chair_squat", "squat", "lunge", "jump_squat")
    val CORE = listOf("glute_bridge", "sit_up", "hollow_hold")
    val CARDIO = listOf("torso_twists", "jumping_jacks", "high_knees", "burpee")
    val YOGA = listOf("tadasana", "utkatasana", "vrikshasana", "natarajasana")

    val all = mapOf(
        "PUSH" to PUSH, "SQUAT" to SQUAT, "CORE" to CORE,
        "CARDIO" to CARDIO, "YOGA" to YOGA
    )
}

/** The day key (YYYY-MM-DD) for a timestamp. */
fun dayKey(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
}

/** The week key (YYYY-Wxx) per ISO 8601 for a timestamp. */
fun weekKey(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        firstDayOfWeek = java.util.Calendar.SUNDAY
    }
    val week = ((cal.timeInMillis - java.util.Calendar.getInstance().apply {
        set(cal.get(java.util.Calendar.YEAR), 0, 1)
        firstDayOfWeek = java.util.Calendar.SUNDAY
    }.timeInMillis) / DAY_MS + cal.getFirstDayOfWeek() + 1) / 7
    return String.format("%04d-W%02d", cal.get(java.util.Calendar.YEAR), week.toInt() + 1)
}

/** Manage streak state: current streak, freezes, rest day tracking per week. */
class Progression(
    private var streakState: StreakState = StreakState(),
    private var bests: MutableMap<String, PersonalBests> = mutableMapOf(),
    private var ladder: MutableMap<String, Int> = mutableMapOf(),
) {
    fun getStreak(): StreakState = streakState.copy()

    fun updateStreak(atMs: Long) {
        val st = streakState
        val today = dayKey(atMs)
        val wk = weekKey(atMs)

        if (st.weekKey != wk) {
            streakState = st.copy(weekKey = wk, restDaysUsedThisWeek = 0)
        }
        if (st.lastDayKey == today) return

        if (st.lastDayKey == null) {
            streakState = streakState.copy(current = 1)
        } else {
            val gapDays = ((java.util.Date(dayKey(atMs)).time - java.util.Date(st.lastDayKey!!).time) / DAY_MS).toInt()
            val newStreakState = when {
                gapDays == 1 -> st.copy(current = st.current + 1)
                gapDays == 2 && st.restDaysUsedThisWeek < 1 ->
                    st.copy(current = st.current + 1, restDaysUsedThisWeek = st.restDaysUsedThisWeek + 1)
                gapDays > 1 && gapDays <= MAX_FREEZE_GAP_DAYS && st.freezes > 0 ->
                    st.copy(current = st.current + 1, freezes = st.freezes - 1)
                else -> st.copy(current = 1)
            }
            streakState = newStreakState
        }
        streakState = streakState.copy(lastDayKey = today)
        if (streakState.current > st.best) {
            streakState = streakState.copy(best = streakState.current)
        }
        if (streakState.current > 0 && streakState.current % 10 == 0) {
            streakState = streakState.copy(freezes = minOf(3, streakState.freezes + 1))
        }
    }

    /** A broken streak never shows a number lost. */
    fun streakLabel(): String {
        val st = streakState
        return when {
            st.current == 0 -> "Back at it"
            st.current == 1 -> "Day 1"
            else -> "${st.current} day streak"
        }
    }

    fun getBests(exerciseId: String): PersonalBests = bests[exerciseId] ?: PersonalBests()

    fun updateBests(exerciseId: String, reps: Int, formMean: Float, repDetails: List<RepDetail> = emptyList()) {
        val b = bests.getOrPut(exerciseId) { PersonalBests() }
        var updated = false

        if (reps > (b.reps ?: 0)) {
            bests[exerciseId] = b.copy(reps = reps)
            updated = true
        }
        if (formMean > (b.formScore ?: 0f)) {
            bests[exerciseId] = bests[exerciseId]!!.copy(formScore = formMean)
            updated = true
        }
        for (r in repDetails) {
            if (r.depthCm.isFinite()) {
                if (r.depthCm > (b.depthCm ?: 0f)) {
                    bests[exerciseId] = bests[exerciseId]!!.copy(depthCm = r.depthCm)
                    updated = true
                }
            }
            if (r.heightCm.isFinite()) {
                if (r.heightCm > (b.heightCm ?: 0f)) {
                    bests[exerciseId] = bests[exerciseId]!!.copy(heightCm = r.heightCm)
                    updated = true
                }
            }
            if (r.holdSec.isFinite()) {
                if (r.holdSec > (b.holdSec ?: 0f)) {
                    bests[exerciseId] = bests[exerciseId]!!.copy(holdSec = r.holdSec)
                    updated = true
                }
            }
        }
    }

    fun personalBestsIn(exerciseId: String, reps: Int, formMean: Float): List<PersonalBestRef> {
        val b = bests[exerciseId] ?: PersonalBests()
        val out = mutableListOf<PersonalBestRef>()
        if (reps >= (b.reps ?: 0)) out.add(PersonalBestRef("reps", reps))
        if (formMean >= (b.formScore ?: 0f)) out.add(PersonalBestRef("form", formMean))
        return out
    }

    fun getLadder(ladderId: String): Int? = ladder[ladderId]

    fun setLadder(ladderId: String, rungIndex: Int) {
        ladder[ladderId] = rungIndex
    }

    /** Promotion requires sustained average over a window; demotion is quiet. */
    fun ladderCheck(
        ladderId: String,
        rungs: List<String>,
        exerciseId: String,
        promoteAt: Float = 0.85f,
        demoteBelow: Float = 0.5f,
        window: Int = 3,
        trend: List<TrendPoint>,
    ): LadderCheckResult {
        val idx = ladder[ladderId] ?: rungs.indexOf(exerciseId)
        if (idx < 0) return LadderCheckResult(exerciseId, false)

        val recent = trend.filter { it.exerciseId == exerciseId }.takeLast(window)
        if (recent.size < window) return LadderCheckResult(rungs[idx], false)

        val mean = recent.map { it.form }.average().toFloat()
        var next = idx
        if (mean >= promoteAt && idx < rungs.size - 1) next = idx + 1
        else if (mean < demoteBelow && idx > 0) next = idx - 1

        if (next != idx) {
            ladder[ladderId] = next
        }
        return LadderCheckResult(rungs[next], next != idx, next > idx, mean)
    }

    data class RepDetail(
        val depthCm: Float = Float.NaN,
        val heightCm: Float = Float.NaN,
        val holdSec: Float = Float.NaN,
    )

    data class PersonalBestRef(val key: String, val value: Number)

    data class TrendPoint(val exerciseId: String, val form: Float)
}
