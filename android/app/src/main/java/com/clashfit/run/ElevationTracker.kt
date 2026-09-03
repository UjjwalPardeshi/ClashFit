package com.clashfit.run

import kotlin.math.max

/**
 * Accumulates elevation gain from a stream of noisy GPS altitudes.
 *
 * Two defences against barometric/GPS noise, in this order:
 *
 * 1. A short median window. A median rejects the single-sample spikes that dominate GPS altitude
 *    while costing only one sample of lag, where a mean would smear the spike across the window.
 * 2. A hysteresis band around a moving reference. The reference only steps up once the smoothed
 *    altitude has risen clear of the band (and that whole rise is credited), and only steps down
 *    once it has fallen clear of it. A descent therefore never subtracts, and re-climbing ground
 *    already descended counts again — that is total ascent, what every run tracker reports.
 *
 * The first sample seeds the reference. It is a starting altitude, not a hundred metres of climb.
 *
 * [gainM] also carries the rise that has not yet cleared the band, so a long steady climb is not
 * under-reported by up to the hysteresis at the moment the run ends. It is held at a high-water
 * mark so the number a runner is watching never ticks backwards.
 */
class ElevationTracker(
    private val hysteresisM: Double = HYSTERESIS_M,
    private val smoothingWindow: Int = SMOOTHING_WINDOW,
) {
    private val window = ArrayDeque<Double>()
    private var referenceM: Double? = null
    private var committedGainM = 0.0
    private var reportedGainM = 0.0

    /** Median-filtered altitude, or null before the first sample. */
    var smoothedAltitudeM: Double? = null
        private set

    val gainM: Float get() = reportedGainM.toFloat()

    /** Feeds one altitude sample and returns the gain so far, in metres. */
    fun onAltitude(altM: Double): Float {
        if (altM.isNaN()) return gainM

        window.addLast(altM)
        while (window.size > smoothingWindow) window.removeFirst()
        val smoothed = window.sorted()[window.size / 2]
        smoothedAltitudeM = smoothed

        val reference = referenceM
        if (reference == null) {
            referenceM = smoothed
            return gainM
        }

        if (smoothed > reference + hysteresisM) {
            committedGainM += smoothed - reference
            referenceM = smoothed
        } else if (smoothed < reference - hysteresisM) {
            referenceM = smoothed
        }

        val pendingM = max(0.0, smoothed - (referenceM ?: smoothed))
        reportedGainM = max(reportedGainM, committedGainM + pendingM)
        return gainM
    }

    fun reset() {
        window.clear()
        referenceM = null
        committedGainM = 0.0
        reportedGainM = 0.0
        smoothedAltitudeM = null
    }

    companion object {
        /** Metres of hysteresis. GPS altitude noise runs to a couple of metres, so 3 clears it. */
        const val HYSTERESIS_M = 3.0

        /** Median over three samples: kills lone spikes, costs one sample of lag. */
        const val SMOOTHING_WINDOW = 3
    }
}
