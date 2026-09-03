package com.clashfit.run

import kotlin.math.sqrt

/**
 * Steps per minute, from the hardware step counter when there is one and from accelerometer peaks
 * when there is not.
 *
 * Step counter: the sensor reports steps since boot, so a run needs a baseline. The baseline and
 * the clock it is measured against are taken from the *same* sample — the first one delivered.
 * Taking the count from the first event but the elapsed time from registration (they can be seconds
 * apart at SENSOR_DELAY_NORMAL) divides a short step delta by a long window and under-reports
 * cadence for the rest of the run.
 *
 * Accelerometer: a fallback, and an unreliable one, so it stays silent until it has seen
 * [minPeaks] footfalls. Reporting a made-up number early is worse than reporting nothing.
 */
class CadenceEstimator(
    private val minPeaks: Int = MIN_PEAKS_FOR_ESTIMATE,
    private val maxCadenceSpm: Int = MAX_CADENCE_SPM,
    private val peakDropRatio: Float = PEAK_DROP_RATIO,
) {
    var cadenceSpm: Int = 0
        private set

    /** Footfalls the accelerometer fallback has seen so far. */
    var peakCount: Int = 0
        private set

    private var baselineSteps: Long? = null
    private var baselineMs: Long = 0L

    private var firstPeakMs: Long? = null
    private var peakMagnitude: Float = 0f
    private var lastMagnitude: Float = 0f

    /** True once a baseline has been taken from the step counter. */
    val hasStepBaseline: Boolean get() = baselineSteps != null

    /**
     * Feeds a step-counter reading ([totalSteps] is the since-boot total) and returns cadence.
     * The first reading only seeds the baseline.
     */
    fun onStepCount(totalSteps: Long, nowMs: Long): Int {
        val baseline = baselineSteps
        if (baseline == null) {
            baselineSteps = totalSteps
            baselineMs = nowMs
            return cadenceSpm
        }

        val stepsSinceBaseline = totalSteps - baseline
        val elapsedSec = (nowMs - baselineMs) / 1000f
        if (stepsSinceBaseline > 0L && elapsedSec > 0f) {
            cadenceSpm = (stepsSinceBaseline * 60 / elapsedSec).toInt().coerceIn(0, maxCadenceSpm)
        }
        return cadenceSpm
    }

    /**
     * Feeds one accelerometer sample. Cadence stays 0 until [minPeaks] footfalls have been seen;
     * it is then the rate across the intervals between them.
     */
    fun onAccelerometer(x: Float, y: Float, z: Float, nowMs: Long): Int {
        val magnitude = sqrt(x * x + y * y + z * z)

        if (magnitude > peakMagnitude && magnitude > lastMagnitude) {
            // Still rising: remember how high this footfall went.
            peakMagnitude = magnitude
        } else if (peakMagnitude > 0f && magnitude < peakMagnitude * peakDropRatio) {
            // Fallen clear of the peak: one footfall, and re-arm for the next.
            peakCount++
            peakMagnitude = 0f
            if (firstPeakMs == null) firstPeakMs = nowMs
            updateFromPeaks(nowMs)
        }

        lastMagnitude = magnitude
        return cadenceSpm
    }

    private fun updateFromPeaks(nowMs: Long) {
        if (peakCount < minPeaks) return
        val first = firstPeakMs ?: return
        val elapsedSec = (nowMs - first) / 1000f
        if (elapsedSec <= 1f) return
        // peakCount footfalls span peakCount - 1 intervals.
        cadenceSpm = ((peakCount - 1) * 60 / elapsedSec).toInt().coerceIn(0, maxCadenceSpm)
    }

    fun reset() {
        cadenceSpm = 0
        peakCount = 0
        baselineSteps = null
        baselineMs = 0L
        firstPeakMs = null
        peakMagnitude = 0f
        lastMagnitude = 0f
    }

    companion object {
        /** Fewer footfalls than this and the fallback has nothing worth saying. */
        const val MIN_PEAKS_FOR_ESTIMATE = 10

        /** Sprinters top out well under this; anything higher is noise. */
        const val MAX_CADENCE_SPM = 200

        /** A footfall is counted once the signal has dropped this far below its peak. */
        const val PEAK_DROP_RATIO = 0.8f
    }
}
