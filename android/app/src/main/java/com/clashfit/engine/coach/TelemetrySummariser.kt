package com.clashfit.engine.coach

import com.clashfit.core.model.CombatState
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SetTelemetry
import com.clashfit.engine.core.summariseAsymmetry
import kotlin.math.max
import kotlin.math.round

/** Summarises a set into a compact telemetry payload for the coach model. */
object TelemetrySummariser {
    fun summarise(
        reps: List<RepRecord>,
        combatState: CombatState?,
        exerciseId: String,
        setIndex: Int = 1,
        restSec: Int = 45,
    ): SetTelemetry {
        val asymmetrySummary = summariseAsymmetry(reps)
        val asymmetryPct = if (asymmetrySummary.enough && asymmetrySummary.consistent) {
            round(asymmetrySummary.deficitPct).toInt()
        } else {
            null
        }
        val weakerSide = if (asymmetrySummary.enough && asymmetrySummary.consistent) {
            asymmetrySummary.weakerSide
        } else {
            null
        }

        if (reps.isEmpty()) {
            return SetTelemetry(
                exercise = exerciseId,
                reps = 0,
                formMean = 0f,
                formFirst3 = 0f,
                formLast3 = 0f,
                formMeanPct = 0,
                formFirst3Pct = 0,
                formLast3Pct = 0,
                depthCm = null,
                depthDropCm = null,
                velocityLossPct = 0,
                romLossPct = 0,
                fatigueBand = com.clashfit.core.model.FatigueBand.FRESH,
                bestRep = null,
                worstRep = null,
                comboMax = 0f,
                comboReps = 0,
                bossHpPct = round((combatState?.hpPct ?: 1f) * 100).toInt(),
                sessionSetIndex = setIndex,
                restSec = restSec,
                trend = SetTelemetry.Trend.FLAT,
                asymmetryPct = asymmetryPct,
                weakerSide = weakerSide,
            )
        }

        val first3 = reps.take(3)
        val last3 = reps.takeLast(3)
        val best = reps.maxByOrNull { it.formScore } ?: reps[0]
        val worst = reps.minByOrNull { it.formScore } ?: reps[0]
        val lastFatigue = reps.last().fatigue

        val depthFirst = mean(first3, RepRecord::depthCm)
        val depthLast = mean(last3, RepRecord::depthCm)
        val drop = if (depthFirst.isFinite() && depthLast.isFinite()) {
            max(0f, depthFirst - depthLast)
        } else {
            null
        }

        return SetTelemetry(
            exercise = exerciseId,
            reps = reps.size,
            formMean = round2(mean(reps, RepRecord::formScore)),
            formFirst3 = round2(mean(first3, RepRecord::formScore)),
            formLast3 = round2(mean(last3, RepRecord::formScore)),
            formMeanPct = round(mean(reps, RepRecord::formScore) * 100).toInt(),
            formFirst3Pct = round(mean(first3, RepRecord::formScore) * 100).toInt(),
            formLast3Pct = round(mean(last3, RepRecord::formScore) * 100).toInt(),
            depthCm = if (drop != null) round(depthLast).toInt() else null,
            depthDropCm = if (drop != null) round(drop).toInt() else null,
            velocityLossPct = round((lastFatigue.velocityLoss) * 100).toInt(),
            romLossPct = round((lastFatigue.romLoss) * 100).toInt(),
            fatigueBand = lastFatigue.band,
            bestRep = SetTelemetry.RepRef(best.repIndex, round2(best.formScore)),
            worstRep = SetTelemetry.RepRef(worst.repIndex, round2(worst.formScore), worst.reason),
            comboMax = round((reps.maxOf { it.combo } * 100) / 100f * 100) / 100f,
            comboReps = longestStreak(reps),
            bossHpPct = round((combatState?.hpPct ?: 1f) * 100).toInt(),
            sessionSetIndex = setIndex,
            restSec = restSec,
            trend = trendOf(reps),
            asymmetryPct = asymmetryPct,
            weakerSide = weakerSide,
        )
    }

    private fun mean(reps: List<RepRecord>, selector: (RepRecord) -> Float): Float {
        if (reps.isEmpty()) return Float.NaN
        return reps.map(selector).sum() / reps.size
    }

    private fun trendOf(reps: List<RepRecord>): SetTelemetry.Trend {
        if (reps.size < 4) return SetTelemetry.Trend.FLAT
        val splitIndex = kotlin.math.ceil(reps.size / 2f).toInt().coerceAtLeast(1)
        val a = reps.subList(0, splitIndex)
        val b = reps.subList(reps.size - splitIndex, reps.size)
        val meanA = a.map { it.formScore }.sum() / a.size
        val meanB = b.map { it.formScore }.sum() / b.size
        val d = meanB - meanA
        return when {
            d > 0.05f -> SetTelemetry.Trend.IMPROVING
            d < -0.05f -> SetTelemetry.Trend.DECLINING
            else -> SetTelemetry.Trend.FLAT
        }
    }

    private fun longestStreak(reps: List<RepRecord>): Int {
        var best = 0
        var cur = 0
        for (r in reps) {
            if (r.formScore >= 0.75f) {
                cur++
                best = maxOf(best, cur)
            } else {
                cur = 0
            }
        }
        return best
    }

    private fun round2(v: Float): Float {
        return round(v * 100) / 100
    }
}
