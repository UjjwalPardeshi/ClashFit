package com.clashfit.engine.core

import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.RepEvent
import kotlin.math.abs

/** Signal definition for a family's fatigue model. */
data class Signal(
    val key: String,
    val decay: Boolean,  // true: loss = 1 - current/baseline; false: loss = (current - baseline) / norm
    val weight: Float,
    val norm: Float = 3f,
)

/** Signal tables for all five families. One model, five families. */
object SignalTables {
    val REP_CYCLE = listOf(
        Signal("velocity", decay = true, weight = 0.45f),
        Signal("rom", decay = true, weight = 0.35f),
        Signal("gap", decay = false, weight = 0.20f, norm = 3f),
    )
    val ISOMETRIC_HOLD = listOf(
        Signal("tremor", decay = false, weight = 0.60f, norm = 1f),
        Signal("quality", decay = true, weight = 0.40f),
    )
    val CADENCE = listOf(
        Signal("cadence", decay = true, weight = 0.55f),
        Signal("amplitude", decay = true, weight = 0.45f),
    )
    val BALLISTIC = listOf(
        Signal("height", decay = true, weight = 0.60f),
        Signal("softness", decay = true, weight = 0.40f),
    )
    val POSE_MATCH = listOf(Signal("accuracy", decay = true, weight = 1.0f))

    fun forFamily(family: String): List<Signal> = when (family) {
        "REP_CYCLE" -> REP_CYCLE
        "ISOMETRIC_HOLD" -> ISOMETRIC_HOLD
        "CADENCE" -> CADENCE
        "BALLISTIC" -> BALLISTIC
        "POSE_MATCH" -> POSE_MATCH
        else -> REP_CYCLE
    }
}

/** Configuration for fatigue estimation. */
data class FatigueConfig(
    val baselineReps: Int = 3,
    val ema: Float = 0.4f,
    val working: Float = 0.15f,
    val fading: Float = 0.30f,
    val gassed: Float = 0.50f,
    val bandLatchReps: Int = 1,
    val pauseGrowthNormSec: Float = 3.0f,
)

/** Fatigue estimator: decay of the family's primary output against the player's own early-set baseline. */
class FatigueEstimator(
    private val config: FatigueConfig,
    private val signals: List<Signal> = SignalTables.REP_CYCLE,
) {
    private val samples = mutableListOf<Map<String, Float>>()
    private var baseline: Map<String, Float>? = null
    private var value = 0f
    private var band = FatigueBand.FRESH
    private var pendingBand: FatigueBand? = null
    private var pendingCount = 0
    private var losses: Map<String, Float> = emptyMap()
    private var frozen = false

    /** A pause must never read as fatigue. Freezes the value until unfreeze(). */
    fun freeze() {
        frozen = true
    }

    fun unfreeze() {
        frozen = false
    }

    /** Recover a fraction of fatigue. Used between sets. Baseline is untouched. */
    fun recover(fraction: Float) {
        val f = fraction.coerceIn(0f, 0.5f)
        value = maxOf(0f, value * (1 - f))
        latch(bandFor(value))
    }

    /** Reset to the initial state. */
    fun reset() {
        samples.clear()
        baseline = null
        value = 0f
        band = FatigueBand.FRESH
        pendingBand = null
        pendingCount = 0
        losses = emptyMap()
        frozen = false
    }

    /** Feed a completed rep. Convenience wrapper for REP_CYCLE. */
    fun onRep(event: RepEvent): FatigueState = onSignals(
        mapOf(
            "velocity" to event.concentricVelocity,
            "rom" to (event.uMax - event.uMin),
            "gap" to event.gapSec,
        )
    )

    /** Feed a signal sample from any family. Every family goes through this general path. */
    fun onSignals(sample: Map<String, Float>): FatigueState {
        if (frozen) return state()
        samples += sample

        if (samples.size < config.baselineReps) return state()

        if (baseline == null) {
            val first = samples.take(config.baselineReps)
            baseline = signals.associate { sig ->
                sig.key to first.map { it[sig.key] ?: 0f }.average().toFloat()
            }
        }
        val b = baseline!!

        var total = 0f
        var wsum = 0f
        val out = mutableMapOf<String, Float>()

        for (sig in signals) {
            val cur = sample[sig.key] ?: 0f
            val base = b[sig.key] ?: 0f
            val loss = if (sig.decay) {
                if (base > 1e-6f) (1f - cur / base).coerceIn(0f, 1f) else 0f
            } else {
                ((cur - base) / sig.norm).coerceIn(0f, 1f)
            }
            out[sig.key] = loss
            total += sig.weight * loss
            wsum += sig.weight
        }

        val raw01 = (if (wsum > 0f) total / wsum else 0f).coerceIn(0f, 1f)
        value = if (samples.size == config.baselineReps) raw01 else config.ema * raw01 + (1 - config.ema) * value
        losses = out
        latch(bandFor(value))

        return state()
    }

    private fun bandFor(v: Float): FatigueBand = when {
        v >= config.gassed -> FatigueBand.GASSED
        v >= config.fading -> FatigueBand.FADING
        v >= config.working -> FatigueBand.WORKING
        else -> FatigueBand.FRESH
    }

    /** Band latching prevents flickering on a boundary. Latched in both directions. */
    private fun latch(candidate: FatigueBand) {
        if (candidate == band) {
            pendingBand = null
            pendingCount = 0
            return
        }
        if (candidate == pendingBand) {
            pendingCount++
            if (pendingCount > config.bandLatchReps) {
                band = candidate
                pendingBand = null
                pendingCount = 0
            }
        } else {
            pendingBand = candidate
            pendingCount = 1
        }
    }

    /** Snapshot of the current fatigue state. */
    fun state(): FatigueState = FatigueState(
        value = value,
        band = band,
        losses = losses,
        baselineReps = minOf(samples.size, config.baselineReps),
        hasBaseline = baseline != null,
    )
}
