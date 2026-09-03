package com.clashfit.engine.core

import com.clashfit.core.model.Landmark

import kotlin.math.abs
import kotlin.math.PI

/** Adaptive low-pass filter for landmark coordinates per axis. */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) {
    private class LowPass {
        private var y: Float? = null
        fun filter(x: Float, a: Float): Float {
            val out = if (y == null) x else a * x + (1 - a) * y!!
            y = out
            return out
        }
        fun reset() { y = null }
    }

    private val xf = LowPass()
    private val dxf = LowPass()
    private var prev: Float? = null
    private var tPrev: Long? = null

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    /** Filter one value at a timestamp. Handles non-monotonic timestamps gracefully. */
    fun filter(value: Float, tMs: Long): Float {
        val last = tPrev
        if (last == null) {
            tPrev = tMs
            prev = value
            return xf.filter(value, alpha(minCutoff, 1f / 30f))
        }
        var dt = (tMs - last) / 1000f
        if (dt <= 0f) dt = 1f / 30f
        tPrev = tMs

        val dxRaw = (value - (prev ?: value)) / dt
        prev = value
        val dxHat = dxf.filter(dxRaw, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(dxHat)
        return xf.filter(value, alpha(cutoff, dt))
    }

    /** Reset to the initial state. */
    fun reset() {
        xf.reset()
        dxf.reset()
        prev = null
        tPrev = null
    }
}

/** One OneEuro filter per landmark per axis, for the 33 MediaPipe Pose landmarks. */
class LandmarkFilter(
    minCutoff: Float = 1.0f,
    beta: Float = 0.007f,
    dCutoff: Float = 1.0f,
    landmarkCount: Int = 33,
) {
    private data class AxisFilters(
        val x: OneEuroFilter,
        val y: OneEuroFilter,
        val z: OneEuroFilter,
    )

    private val filters = Array(landmarkCount) {
        AxisFilters(
            OneEuroFilter(minCutoff, beta, dCutoff),
            OneEuroFilter(minCutoff, beta, dCutoff),
            OneEuroFilter(minCutoff, beta, dCutoff),
        )
    }

    /** Apply filter to a list of landmarks. Never filter the angle itself — filtering inputs preserves geometric consistency. */
    fun apply(landmarks: List<Landmark>, tMs: Long): List<Landmark> =
        landmarks.mapIndexed { i, lm ->
            Landmark(
                x = filters[i].x.filter(lm.x, tMs),
                y = filters[i].y.filter(lm.y, tMs),
                z = filters[i].z.filter(lm.z, tMs),
                visibility = lm.visibility,
            )
        }

    /** Reset all filters to the initial state. */
    fun reset() {
        filters.forEach { af -> af.x.reset(); af.y.reset(); af.z.reset() }
    }
}
