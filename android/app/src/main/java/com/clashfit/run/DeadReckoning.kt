package com.clashfit.run

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where you are, from your own footsteps, when the sky is not available.
 *
 * Indoors a GPS receiver reports an accuracy circle of thirty to a hundred metres, which
 * [FixFilter] correctly refuses, so a run in a lab or a basement records nothing at all. The
 * phone still knows two things in there: how many steps you have taken, and which way it is
 * pointing. Multiplied together those give a displacement, and that is all a route is.
 *
 * This is deliberately the simplest form of pedestrian dead reckoning: step count times stride
 * length, projected along the current heading. It drifts — every dead-reckoning system does,
 * because errors integrate — which is exactly why [PositionFuser] hands back to GPS the moment a
 * good fix returns, and why the stride is *learned* from GPS rather than assumed.
 *
 * ### Stride
 *
 * A fixed stride is wrong for almost everybody: it varies with leg length and, for the same
 * person, with pace. So the stride starts at a population average and is corrected whenever GPS
 * and the step counter can both be trusted over the same stretch — the satellite measures the
 * distance, the pedometer counts the steps, and the ratio is the truth about this person's legs.
 * Ten metres of walking outdoors is enough to make the indoor track markedly better.
 */
class DeadReckoning(
    initialStrideM: Float = DEFAULT_STRIDE_M,
    private val learningRate: Float = LEARNING_RATE,
) {
    /** Metres per step. Starts at a population average and is corrected against GPS. */
    var strideM: Float = initialStrideM
        private set

    /** True once GPS has taught us this person's stride rather than a textbook's. */
    var calibrated: Boolean = false
        private set

    private var lastSteps: Int? = null

    /** Heading in degrees clockwise from north, or null before the first reading. */
    var headingDeg: Float? = null
        private set

    fun setHeading(degrees: Float) {
        if (degrees.isNaN()) return
        headingDeg = ((degrees % 360f) + 360f) % 360f
    }

    /**
     * Displacement since the previous call, from a since-start step total.
     *
     * The first call only seeds the counter: it establishes where the count began and contributes
     * no movement, exactly as the GPS filter's first fix contributes no distance.
     *
     * A step total that goes backwards means the counter was reset — a reboot, or a new
     * activity — so it re-seeds rather than reporting a large negative walk.
     */
    fun onSteps(totalSteps: Int): Displacement {
        val previous = lastSteps
        lastSteps = totalSteps
        if (previous == null || totalSteps < previous) return Displacement.NONE

        val taken = totalSteps - previous
        if (taken <= 0) return Displacement.NONE

        val heading = headingDeg ?: return Displacement.NONE
        val metres = taken * strideM
        val radians = heading * PI.toFloat() / 180f
        // Heading is clockwise from north, so north is cos and east is sin. Getting this the
        // usual maths way round would mirror every indoor route about the diagonal.
        return Displacement(
            eastM = metres * sin(radians),
            northM = metres * cos(radians),
            metres = metres,
            steps = taken,
        )
    }

    /**
     * Teaches the stride from a stretch where GPS was trustworthy.
     *
     * Guarded on both sides: too few steps and the ratio is noise, and an implausible stride is
     * rejected outright rather than averaged in, because one bad fix pair would otherwise poison
     * the estimate for the rest of the activity.
     */
    fun learnStride(gpsDistanceM: Float, steps: Int) {
        if (steps < MIN_STEPS_TO_LEARN || gpsDistanceM <= 0f) return
        val observed = gpsDistanceM / steps
        if (observed < MIN_STRIDE_M || observed > MAX_STRIDE_M) return
        strideM = if (calibrated) {
            strideM + (observed - strideM) * learningRate
        } else {
            observed
        }
        calibrated = true
    }

    fun reset() {
        lastSteps = null
        headingDeg = null
    }

    /** One increment of movement. */
    data class Displacement(
        val eastM: Float,
        val northM: Float,
        val metres: Float,
        val steps: Int,
    ) {
        val moved: Boolean get() = metres > 0f

        companion object {
            val NONE = Displacement(0f, 0f, 0f, 0)
        }
    }

    companion object {
        /** Metres per step for an average adult walking. Only a starting point; GPS corrects it. */
        const val DEFAULT_STRIDE_M = 0.72f

        /** Shortest believable stride. Below this the "steps" are a phone being shaken. */
        const val MIN_STRIDE_M = 0.4f

        /** Longest believable stride, at a run. */
        const val MAX_STRIDE_M = 1.6f

        /** Fewer steps than this and the measured ratio says more about GPS noise than legs. */
        const val MIN_STEPS_TO_LEARN = 15

        /** How fast the estimate follows a new observation. Slow, because legs do not change. */
        const val LEARNING_RATE = 0.25f
    }
}
