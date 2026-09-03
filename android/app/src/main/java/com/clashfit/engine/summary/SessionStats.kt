package com.clashfit.engine.summary

import com.clashfit.core.model.RepRecord
import kotlin.math.roundToInt

/**
 * Per-session statistics: fatigue curve, best/worst rep, CSV and JSON export.
 * Data structures are kept as strings (no file I/O) for portability.
 */

data class FatigueSeries(
    val repIndex: Int,
    val formScore: Float,
    val fatigueValue: Float,
    val band: String,
    val verdict: String,
)

/** Compact representation of a session for exporting. */
data class SessionStats(
    val exercise: String,
    val reps: Int,
    val formMean: Float,
    val formFirst3: Float,
    val formLast3: Float,
    val formMeanPct: Int,
    val formFirst3Pct: Int,
    val formLast3Pct: Int,
    val depthCm: Int? = null,
    val depthDropCm: Int? = null,
    val velocityLossPct: Int,
    val romLossPct: Int,
    val fatigueSeries: List<FatigueSeries> = emptyList(),
)

/** Compute session statistics from a list of rep records. */
fun computeSessionStats(reps: List<RepRecord>, exercise: String): SessionStats {
    if (reps.isEmpty()) {
        return SessionStats(
            exercise = exercise,
            reps = 0,
            formMean = 0f,
            formFirst3 = 0f,
            formLast3 = 0f,
            formMeanPct = 0,
            formFirst3Pct = 0,
            formLast3Pct = 0,
            velocityLossPct = 0,
            romLossPct = 0,
        )
    }

    val formMean = reps.map { it.formScore }.average().toFloat()
    val first3 = reps.take(3).map { it.formScore }.average().toFloat()
    val last3 = reps.takeLast(3).map { it.formScore }.average().toFloat()

    val lastRep = reps.last()
    val velocityLossPct = (lastRep.fatigue.velocityLoss * 100).roundToInt()
    val romLossPct = (lastRep.fatigue.romLoss * 100).roundToInt()

    val fatigueSeries = reps.map { r ->
        FatigueSeries(
            repIndex = r.repIndex,
            formScore = r.formScore,
            fatigueValue = r.fatigue.value,
            band = r.fatigue.band.toString(),
            verdict = r.verdict.toString(),
        )
    }

    return SessionStats(
        exercise = exercise,
        reps = reps.size,
        formMean = formMean,
        formFirst3 = if (reps.size >= 3) first3 else if (reps.isNotEmpty()) reps[0].formScore else 0f,
        formLast3 = if (reps.size >= 3) last3 else if (reps.isNotEmpty()) reps.last().formScore else 0f,
        formMeanPct = (formMean * 100).roundToInt(),
        formFirst3Pct = ((if (reps.size >= 3) first3 else if (reps.isNotEmpty()) reps[0].formScore else 0f) * 100).roundToInt(),
        formLast3Pct = ((if (reps.size >= 3) last3 else if (reps.isNotEmpty()) reps.last().formScore else 0f) * 100).roundToInt(),
        depthCm = if (reps.any { it.depthCm.isFinite() }) reps.filter { it.depthCm.isFinite() }.maxOf { it.depthCm }.roundToInt() else null,
        velocityLossPct = velocityLossPct,
        romLossPct = romLossPct,
        fatigueSeries = fatigueSeries,
    )
}

/** Export reps as CSV: rep,form,verdict,depth,rom,tempo,alignment,depth_cm,ecc_s,pause_s,con_s,vel_deg_s,fatigue,band,damage,combo */
fun exportCsv(reps: List<RepRecord>): String {
    val header = "rep,form,verdict,depth,rom,tempo,alignment,depth_cm," +
        "ecc_s,pause_s,con_s,vel_deg_s,fatigue,band,damage,combo"
    val rows = reps.map { r ->
        // RepRecord (unlike RepEvent) does not carry phase-level timings, so approximate them
        // from the total rep duration: split evenly across eccentric/concentric, no pause data.
        val durationSec = (r.tEndMs - r.tStartMs) / 1000f
        listOf(
            r.repIndex,
            "%.3f".format(r.formScore),
            r.verdict,
            "%.3f".format(r.depth),
            "%.3f".format(r.rom),
            "%.3f".format(r.tempo),
            "%.3f".format(r.alignment),
            if (r.depthCm.isFinite()) "%.1f".format(r.depthCm) else "",
            "%.2f".format(durationSec / 2f),
            "%.2f".format(0f),
            "%.2f".format(durationSec / 2f),
            "%.1f".format(r.concentricVelocity),
            "%.3f".format(r.fatigue.value),
            r.fatigue.band,
            r.damage,
            "%.2f".format(r.combo),
        ).joinToString(",")
    }
    return (listOf(header) + rows).joinToString("\n") + "\n"
}

/** Export reps as JSON. Simple JSON array of rep records. */
fun exportJson(reps: List<RepRecord>): String {
    val jsonReps = reps.map { r ->
        mapOf(
            "repIndex" to r.repIndex,
            "formScore" to r.formScore,
            "verdict" to r.verdict.toString(),
            "depth" to r.depth,
            "rom" to r.rom,
            "tempo" to r.tempo,
            "alignment" to r.alignment,
            "fatigue" to mapOf(
                "value" to r.fatigue.value,
                "band" to r.fatigue.band.toString(),
            ),
            "damage" to r.damage,
            "combo" to r.combo,
        )
    }
    return jsonReps.joinToString(",", "[", "]\n") { rep ->
        rep.entries.joinToString(",", "{", "}") { (k, v) ->
            when (v) {
                is String -> "\"$k\":\"$v\""
                is Map<*, *> -> {
                    val inner = (v as Map<String, Any>).entries.joinToString(",") { (ik, iv) ->
                        when (iv) {
                            is String -> "\"$ik\":\"$iv\""
                            else -> "\"$ik\":$iv"
                        }
                    }
                    "\"$k\":{$inner}"
                }
                else -> "\"$k\":$v"
            }
        }
    }
}
