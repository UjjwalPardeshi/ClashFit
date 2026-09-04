package com.clashfit.ui.insight

import com.clashfit.data.SessionEntity

/**
 * Derivations that more than one screen needs from a session history.
 *
 * These lived privately inside the History screen until the Progress dashboard needed the same
 * numbers. Two private copies of the same estimate is how two screens end up disagreeing about
 * how many clean reps you have done, which is worse than either answer alone.
 */
object SessionInsights {

    data class Verdicts(val clean: Int, val ok: Int, val shallow: Int) {
        val total: Int get() = clean + ok + shallow
    }

    /**
     * Rep verdicts across a set of sessions.
     *
     * The per-rep rows are compacted away for older sessions, so this estimates from each
     * session's mean form rather than pretending to count rows that are gone. The thresholds are
     * the ones the rep scorer itself uses to name a verdict, so the estimate drifts toward the
     * truth rather than away from it.
     */
    fun verdicts(sessions: List<SessionEntity>): Verdicts {
        var clean = 0
        var ok = 0
        var shallow = 0
        sessions.forEach { s ->
            val f = s.formMean.coerceIn(0f, 1f)
            val cleanShare = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val shallowShare = ((0.55f - f) / 0.55f).coerceIn(0f, 1f)
            val c = (s.totalReps * cleanShare).toInt()
            val sh = (s.totalReps * shallowShare).toInt()
            clean += c
            shallow += sh
            ok += (s.totalReps - c - sh).coerceAtLeast(0)
        }
        return Verdicts(clean, ok, shallow)
    }

    /** Form for the most recent [count] sessions, oldest first, ready to plot. */
    fun formTrend(sessions: List<SessionEntity>, count: Int = 20): List<Float> =
        sessions.take(count).reversed().map { it.formMean.coerceIn(0f, 1f) }

    /** The average of a trend, or null when there is nothing to average. */
    fun average(points: List<Float>): Float? =
        if (points.isEmpty()) null else points.average().toFloat()
}
