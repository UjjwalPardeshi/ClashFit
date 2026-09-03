package com.clashfit.engine.core

/**
 * Bilateral asymmetry — the signal a physiotherapist actually looks for.
 *
 * We already compute the joint angle on both sides of the body every frame, and we were averaging
 * them and throwing the difference away. That difference is the most clinically meaningful thing
 * this pipeline can see, and it costs almost nothing to keep.
 *
 * The metric is the Limb Symmetry Index, which is standard in rehabilitation and
 * return-to-activity assessment: the weaker side expressed as a percentage of the stronger.
 *
 *     LSI = (weaker / stronger) x 100
 */

enum class Confidence {
    GOOD,   // >= 20 samples & mean gap < 25°
    FAIR,   // >= 12 samples & mean gap < 40°
    POOR,   // everything else
}

data class RepAsymmetry(
    val leftRom: Float,
    val rightRom: Float,
    val weakerSide: String,  // "left" or "right"
    val lsi: Float,
    val deficitPct: Float,
    val spreadDeg: Float,
    val confidence: Confidence,
    val samples: Int,
)

data class AsymmetrySummary(
    val usable: Int,
    val enough: Boolean,
    val note: String? = null,
    val meanLsi: Float = Float.NaN,
    val deficitPct: Float = Float.NaN,
    val weakerSide: String? = null,
    val consistency: Float = Float.NaN,
    val consistent: Boolean = false,
)

/** Per-rep tracker. Fed the same filtered angles the rep machine sees. */
class AsymmetryTracker {
    private data class Sample(val t: Long, val left: Float, val right: Float)

    private var samples = mutableListOf<Sample>()

    fun reset() {
        samples.clear()
    }

    /**
     * Feed one frame with left and right angles in degrees.
     * @param left angle in degrees
     * @param right angle in degrees
     * @param tMs timestamp in milliseconds
     */
    fun onFrame(left: Float, right: Float, tMs: Long) {
        if (!left.isFinite() || !right.isFinite()) return
        samples.add(Sample(tMs, left, right))
        if (samples.size > 900) samples.removeAt(0)  // ~30s at 30fps
    }

    /**
     * Range of motion achieved by each side within one rep's window, and the ratio between them.
     * Returns null when the window is too thin to say anything honest about.
     */
    fun forRep(tStartMs: Long, tEndMs: Long): RepAsymmetry? {
        val window = samples.filter { it.t >= tStartMs && it.t <= tEndMs }
        if (window.size < 8) return null

        val lRom = window.maxOf { it.left } - window.minOf { it.left }
        val rRom = window.maxOf { it.right } - window.minOf { it.right }
        if (!(lRom > 1f) || !(rRom > 1f)) return null

        val weaker = minOf(lRom, rRom)
        val stronger = maxOf(lRom, rRom)
        val lsi = (weaker / stronger) * 100f

        // A side-on camera foreshortens the far limb, which manufactures asymmetry that is not there.
        // Report how much to trust the reading rather than quietly presenting a number as fact.
        val meanGap = window.map { kotlin.math.abs(it.left - it.right) }.average().toFloat()
        val spread = kotlin.math.abs(lRom - rRom)

        val confidence = when {
            window.size >= 20 && meanGap < 25f -> Confidence.GOOD
            window.size >= 12 && meanGap < 40f -> Confidence.FAIR
            else -> Confidence.POOR
        }

        return RepAsymmetry(
            leftRom = lRom,
            rightRom = rRom,
            weakerSide = if (lRom <= rRom) "left" else "right",
            lsi = lsi,
            deficitPct = 100f - lsi,
            spreadDeg = spread,
            confidence = confidence,
            samples = window.size,
        )
    }
}

/**
 * Session-level roll-up. One rep proves nothing; a consistent lean across a set is the signal.
 */
fun summariseAsymmetry(reps: List<com.clashfit.core.model.RepRecord>): AsymmetrySummary {
    val usable = reps
        .mapNotNull { it.asymmetry }
        .filter { it.confidence != Confidence.POOR }

    if (usable.size < 3) {
        return AsymmetrySummary(
            usable = usable.size,
            enough = false,
            note = "Not enough clean bilateral frames to say anything about symmetry.",
        )
    }

    val meanLsi = usable.map { it.lsi }.average().toFloat()
    val leftWeak = usable.count { it.weakerSide == "left" }
    val consistency = maxOf(leftWeak, usable.size - leftWeak).toFloat() / usable.size
    val side = if (leftWeak > usable.size / 2f) "left" else "right"

    return AsymmetrySummary(
        usable = usable.size,
        enough = true,
        note = null,
        meanLsi = meanLsi,
        deficitPct = 100f - meanLsi,
        weakerSide = side,
        consistency = consistency,
        consistent = consistency >= 0.7f,
    )
}

/**
 * Phrasing. Observational only — never a verdict, never a diagnosis, never "cleared" or "at risk".
 * The number and the trend are the product; interpretation belongs to a professional.
 */
fun describeAsymmetry(sum: AsymmetrySummary): String {
    if (!sum.enough) return sum.note ?: "Unable to assess symmetry."
    val d = kotlin.math.round(sum.deficitPct).toInt()
    if (!sum.consistent) {
        return "Sides are within noise of each other across this set — no consistent lean."
    }
    if (d < 8) return "Both sides moved through the same range, within $d percent."
    return "Your ${sum.weakerSide} side moved through $d percent less range than the other, " +
           "consistently across the set. Worth mentioning to a physiotherapist if it persists."
}
