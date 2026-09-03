package com.clashfit.engine.core

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Side

import com.clashfit.core.model.FormScore
import com.clashfit.core.model.RepEvent
import kotlin.math.abs
import kotlin.math.pow

/** Form scoring: four named geometric quantities, never a vague "form quality". */
object FormScorer {
    /** Mirrors the JS `clamp01`: a non-finite input is worth zero, never NaN downstream. */
    private fun clamp01(v: Float): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f

    /** Depth: how far past the target, relative to the player's own calibrated rest position. */
    fun depth(event: RepEvent, exponent: Float = 1.5f): Float {
        val span = event.uTopRef - event.uTarget
        if (abs(span) < 1e-6f) return 0f
        val linear = clamp01((event.uTopRef - event.uMin) / span)
        return clamp01(linear.pow(exponent))
    }

    /** Range of motion against the player's own calibrated baseline. Fair across body types. */
    fun rom(event: RepEvent, baselineU: Float): Float {
        if (baselineU < 1e-6f) return 1f
        return clamp01((event.uMax - event.uMin) / baselineU)
    }

    /** Tempo: reward controlled eccentric and real pause, punish bouncing. */
    fun tempo(event: RepEvent, eccentricTargetSec: Float = 0.35f, pauseTargetSec: Float = 0.12f): Float {
        val tEcc = if (event.tEccSec >= eccentricTargetSec) 1f else event.tEccSec / eccentricTargetSec
        val tPause = if (event.tBottomSec >= pauseTargetSec) 1f else 0.6f
        return clamp01(0.7f * tEcc + 0.3f * tPause)
    }

    /** Squat alignment: horizontal knee-to-ankle offset normalised by shin length. */
    fun kneeTracking(landmarks: List<Landmark>, side: Side, fullMarks: Float = 0.15f, zeroMarks: Float = 0.45f): Float {
        val offset = Geometry.kneeTracking(landmarks, side)
        if (!offset.isFinite()) return 1f
        return clamp01((zeroMarks - offset) / (zeroMarks - fullMarks))
    }

    /** Push-up alignment: torso line angle shoulder-hip-ankle. */
    fun torsoLine(landmarks: List<Landmark>, side: Side, fullMarks: Float = 172f, zeroMarks: Float = 150f): Float {
        val deg = Geometry.torsoLine(landmarks, side)
        if (!deg.isFinite()) return 1f
        return clamp01((deg - zeroMarks) / (fullMarks - zeroMarks))
    }

    /** Combine four sub-scores into one 0..1 form score plus the reason for the weakest link. */
    fun score(
        event: RepEvent,
        weights: FormWeights,
        romBaselineU: Float,
        alignmentScore: Float = 1f,
        depthExponent: Float = 1.5f,
    ): FormScore {
        val d = depth(event, depthExponent)
        val r = rom(event, romBaselineU)
        val t = tempo(event)
        // The JS computes alignment through alignmentScore(), which clamps. Here it arrives as a
        // caller-supplied number, so the clamp has to happen on the way in.
        val a = clamp01(alignmentScore)

        val sum = weights.depth + weights.rom + weights.tempo + weights.alignment
        val norm = if (sum > 0f) sum else 1f
        val total = clamp01((weights.depth * d + weights.rom * r + weights.tempo * t + weights.alignment * a) / norm)

        // The weakest sub-score names the fault — we diagnose, the coach phrases it.
        val parts = mutableListOf("depth" to d, "rom" to r, "tempo" to t)
        if (weights.alignment > 0f) parts += "alignment" to a
        val reason = parts.minByOrNull { it.second }?.first ?: "depth"

        return FormScore(d, r, t, a, total, reason)
    }
}

/** Form scoring weights. */
data class FormWeights(
    val depth: Float = 0.4f,
    val rom: Float = 0.25f,
    val tempo: Float = 0.2f,
    val alignment: Float = 0.15f,
)

/** Alignment sample taken at the deepest point of the rep. */
fun alignmentSample(landmarks: List<Landmark>, side: Side, alignmentType: String?): Float =
    when (alignmentType) {
        "KNEE_TRACKING" -> FormScorer.kneeTracking(landmarks, side)
        "TORSO_LINE" -> FormScorer.torsoLine(landmarks, side)
        else -> 1f
    }
