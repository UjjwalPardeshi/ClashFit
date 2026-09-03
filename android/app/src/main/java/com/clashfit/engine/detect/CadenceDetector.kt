package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.CadenceEvent
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * F4 · CADENCE — periodic motion, for cardio.
 * Peak detection with a minimum prominence and a refractory period on one landmark axis.
 * Amplitude is the form analogue: it is what catches someone doing tiny fake high-knees.
 */
class CadenceDetector(
    private val spec: ExerciseSpec,
) : ExerciseDetector {

    override val family = "CADENCE"

    private val signalJoint: String
    private val signalAxis: String
    private val minProminence: Float
    private val refractoryMs: Long
    private val targetCadenceMin: Float
    private val targetCadenceMax: Float

    private var repIndex = 0
    private val buf = mutableListOf<Sample>()
    private var lastPeakMs: Long? = null
    private var calibAmplitude: Float? = null
    private var _last: ExerciseDetector.LastObservation? = null

    override val last: ExerciseDetector.LastObservation?
        get() = _last

    init {
        val detector = spec.detector
        signalJoint = detector["signalJoint"]?.toString()?.trim('"')?.trim('\\') ?: "WRIST"
        signalAxis = detector["signalAxis"]?.toString()?.trim('"')?.trim('\\') ?: "y"
        minProminence = detector["minProminence"]?.jsonPrimitive?.floatOrNull ?: 0.03f

        refractoryMs = detector["refractoryMs"]?.let {
            it.toString().toLongOrNull() ?: 260L
        } ?: 260L

        val targetCadence = detector["targetCadence"]?.jsonObject
        if (targetCadence != null) {
            targetCadenceMin = targetCadence["min"]?.jsonPrimitive?.floatOrNull ?: 60f
            targetCadenceMax = targetCadence["max"]?.jsonPrimitive?.floatOrNull ?: 200f
        } else {
            targetCadenceMin = 60f
            targetCadenceMax = 200f
        }
    }

    override fun reset() {
        repIndex = 0
        buf.clear()
        lastPeakMs = null
        calibAmplitude = null
        _last = null
    }

    override fun onFrame(
        world: List<Landmark>?,
        image: List<Landmark>?,
        tMs: Long,
        side: Side,
    ): MovementEvent? {
        val lms = image ?: return null
        val v = getSignal(lms, side)
        if (!v.isFinite()) return null

        buf.add(Sample(t = tMs, v = v))
        while (buf.isNotEmpty() && tMs - buf.first().t > 4000) {
            buf.removeAt(0)
        }
        if (buf.size < 9) return null

        // Local maximum at the centre of a five-sample window, with prominence and refractory gates
        val n = buf.size
        val i = n - 3
        if (i < 2) return null

        val w = buf.subList(i - 2, i + 3)
        val mid = w[2]
        val isPeak = w.withIndex().all { (k, p) -> k == 2 || p.v <= mid.v }
        if (!isPeak) return null

        val recent = buf.takeLast(minOf(45, buf.size))
        val lo = recent.minOf { it.v }
        val hi = recent.maxOf { it.v }
        val prominence = hi - lo
        if (prominence < minProminence) return null

        val last = lastPeakMs
        if (last != null && mid.t - last < refractoryMs) return null

        val interval = if (last == null) null else mid.t - last
        lastPeakMs = mid.t
        if (interval == null) return null // first peak only establishes the clock

        if (calibAmplitude == null) {
            calibAmplitude = prominence
        }
        val cadence = 60000f / interval
        val amplitude = minOf(1.5f, prominence / calibAmplitude!!)

        val inBand = when {
            cadence >= targetCadenceMin && cadence <= targetCadenceMax -> 1f
            else -> maxOf(
                0f,
                1f - abs(cadence - if (cadence < targetCadenceMin) targetCadenceMin else targetCadenceMax) / targetCadenceMin,
            )
        }

        repIndex++
        val formScore = maxOf(0f, minOf(1f, 0.45f * inBand + 0.55f * minOf(1f, amplitude)))
        val reason = when {
            amplitude < 0.7f -> "amplitude"
            inBand < 0.7f -> "cadence"
            else -> "none"
        }

        return CadenceEvent(
            tStartMs = mid.t - interval,
            tEndMs = mid.t,
            formScore = formScore,
            signals = mapOf("cadence" to cadence, "amplitude" to amplitude),
            cadence = cadence,
            amplitude = amplitude,
        )
    }

    private fun getSignal(lms: List<Landmark>, side: Side): Float {
        val idx = getJointIndex(signalJoint, side)
        if (idx < 0 || idx >= lms.size) return Float.NaN
        val p = lms[idx]
        return when (signalAxis) {
            "x" -> p.x
            "z" -> p.z
            else -> p.y
        }
    }

    private fun getJointIndex(joint: String, side: Side): Int {
        val leftIndices = mapOf(
            "SHOULDER" to 11, "ELBOW" to 13, "WRIST" to 15,
            "HIP" to 23, "KNEE" to 25, "ANKLE" to 27,
        )
        val rightIndices = mapOf(
            "SHOULDER" to 12, "ELBOW" to 14, "WRIST" to 16,
            "HIP" to 24, "KNEE" to 26, "ANKLE" to 28,
        )
        val indices = if (side == Side.LEFT) leftIndices else rightIndices
        return indices[joint] ?: -1
    }

    private data class Sample(val t: Long, val v: Float)
}
