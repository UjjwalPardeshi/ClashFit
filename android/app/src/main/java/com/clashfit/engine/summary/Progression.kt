package com.clashfit.engine.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

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

/**
 * The day key (YYYY-MM-DD) for a timestamp, in UTC. store.js uses `toISOString().slice(0, 10)`,
 * so the key never shifts with the device timezone and is safe to compare across days.
 */
fun dayKey(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

/**
 * The week key (YYYY-Wxx) for a timestamp: weeks start on Sunday and are counted from Jan 1,
 * matching store.js. Computed wholly in UTC so it is stable whatever timezone the device is in.
 */
fun weekKey(ms: Long): String {
    val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    val jan1 = LocalDate.of(date.year, 1, 1)
    // Sunday-based index of Jan 1 (JS Date#getDay: Sunday = 0), so the first partial week counts as 1.
    val jan1Dow = jan1.dayOfWeek.value % 7
    val daysIn = ChronoUnit.DAYS.between(jan1, date).toInt()
    val week = ceil((daysIn + jan1Dow + 1) / 7.0).toInt()
    return String.format(Locale.ROOT, "%04d-W%02d", date.year, week)
}

/**
 * Whole days between two day keys. A key that predates this format (or is otherwise unparseable)
 * reads as an unbounded gap, which is how store.js behaves when Date.parse returns NaN: no branch
 * matches and the streak simply restarts.
 */
private fun dayGap(fromDayKey: String, toDayKey: String): Int = try {
    ChronoUnit.DAYS.between(LocalDate.parse(fromDayKey), LocalDate.parse(toDayKey)).toInt()
} catch (e: DateTimeParseException) {
    Int.MAX_VALUE
}

/** Manage streak state: current streak, freezes, rest day tracking per week. */
class Progression(
    private var streakState: StreakState = StreakState(),
    private var bests: MutableMap<String, PersonalBests> = mutableMapOf(),
    private var ladder: MutableMap<String, Int> = mutableMapOf(),
) {
    fun getStreak(): StreakState = streakState.copy()

    fun updateStreak(atMs: Long) {
        val today = dayKey(atMs)
        val wk = weekKey(atMs)

        var st = streakState
        if (st.weekKey != wk) st = st.copy(weekKey = wk, restDaysUsedThisWeek = 0)
        if (st.lastDayKey == today) {
            streakState = st
            return
        }

        val last = st.lastDayKey
        st = if (last == null) {
            st.copy(current = 1)
        } else {
            val gapDays = dayGap(last, today)
            when {
                gapDays == 1 -> st.copy(current = st.current + 1)
                // One protected rest day per week: rest is training, and breaking a streak
                // because someone took a day off is how fitness apps lose people.
                gapDays == 2 && st.restDaysUsedThisWeek < 1 ->
                    st.copy(current = st.current + 1, restDaysUsedThisWeek = st.restDaysUsedThisWeek + 1)
                // A freeze covers a missed day or two. It does not cover a month away.
                gapDays > 1 && gapDays <= MAX_FREEZE_GAP_DAYS && st.freezes > 0 ->
                    st.copy(current = st.current + 1, freezes = st.freezes - 1)
                else -> st.copy(current = 1)
            }
        }

        st = st.copy(lastDayKey = today)
        if (st.current > st.best) st = st.copy(best = st.current)
        if (st.current > 0 && st.current % 10 == 0) st = st.copy(freezes = minOf(3, st.freezes + 1))
        streakState = st
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
        var b = bests[exerciseId] ?: PersonalBests()

        if (reps > (b.reps ?: 0)) b = b.copy(reps = reps)
        if (formMean > (b.formScore ?: 0f)) b = b.copy(formScore = formMean)
        // Each rep is compared against the running best, not the value this session started with.
        for (r in repDetails) {
            if (r.depthCm.isFinite() && r.depthCm > (b.depthCm ?: 0f)) b = b.copy(depthCm = r.depthCm)
            if (r.heightCm.isFinite() && r.heightCm > (b.heightCm ?: 0f)) b = b.copy(heightCm = r.heightCm)
            if (r.holdSec.isFinite() && r.holdSec > (b.holdSec ?: 0f)) b = b.copy(holdSec = r.holdSec)
        }

        bests[exerciseId] = b
    }

    /**
     * What actually improved, phrased for the coach. Nothing to say is a valid answer: a tie is
     * not an improvement, and an exercise with no history yet has no best to beat.
     */
    fun personalBestsIn(exerciseId: String, reps: Int, formMean: Float): List<PersonalBestRef> {
        val b = bests[exerciseId] ?: return emptyList()
        val out = mutableListOf<PersonalBestRef>()
        val bestReps = b.reps
        val bestForm = b.formScore
        if (bestReps != null && reps > bestReps) out.add(PersonalBestRef("reps", reps))
        if (bestForm != null && formMean > bestForm) out.add(PersonalBestRef("form", formMean))
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
