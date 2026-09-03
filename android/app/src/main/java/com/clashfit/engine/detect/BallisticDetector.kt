package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.JumpEvent
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import com.clashfit.engine.core.Geometry
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * F5 · BALLISTIC — airborne phase, jump height, landing softness.
 *
 * The catch: MediaPipe world landmarks are HIP-CENTRED, so they cannot see vertical translation
 * at all — during flight the body's internal geometry barely changes and the origin travels with
 * you. Jump height has to come from image-space hip Y.
 *
 * To get that in real units we calibrate a scale from a segment we can measure both ways: the
 * hip-to-ankle distance is metric in world space and normalised in image space, so their ratio
 * converts image displacement into metres.
 */
class BallisticDetector(
    private val spec: ExerciseSpec,
) : ExerciseDetector {

    override val family = "BALLISTIC"

    private val takeoffRise: Float
    private val landingWindowMs: Long
    private val standingKnee: Float
    private val softLandingFlexionDeg: Float
    private val targetHeightCm: Float

    private var groundY: Float? = null // image-space hip Y while standing
    private var scale: Float? = null // metres per normalised image unit
    private var airborne = false
    private var tTakeoff: Long? = null
    private var peakY: Float? = null
    private val settle = mutableListOf<SettleSample>()
    private var landing: LandingState? = null
    private var _last: ExerciseDetector.LastObservation? = null

    override val last: ExerciseDetector.LastObservation?
        get() = _last

    init {
        val detector = spec.detector
        takeoffRise = detector["takeoffRise"]?.jsonPrimitive?.floatOrNull ?: 0.035f
        landingWindowMs = detector["landingWindowMs"]?.let {
            it.toString().toLongOrNull() ?: 300L
        } ?: 300L
        standingKnee = detector["standingKnee"]?.jsonPrimitive?.floatOrNull ?: 170f
        softLandingFlexionDeg = detector["softLandingFlexionDeg"]?.jsonPrimitive?.floatOrNull ?: 35f
        targetHeightCm = detector["targetHeightCm"]?.jsonPrimitive?.floatOrNull ?: 25f
    }

    override fun reset() {
        groundY = null
        scale = null
        airborne = false
        tTakeoff = null
        peakY = null
        settle.clear()
        landing = null
        _last = null
    }

    override fun onFrame(
        world: List<Landmark>?,
        image: List<Landmark>?,
        tMs: Long,
        side: Side,
    ): MovementEvent? {
        if (image == null) return null
        val hip = getJoint(image, "HIP", side) ?: return null

        if (scale == null) {
            scale = getScale(world, image, side)
        }

        // Establish the ground reference from a settled standing pose.
        settle.add(SettleSample(t = tMs, y = hip.y))
        while (settle.isNotEmpty() && tMs - settle.first().t > 900) {
            settle.removeAt(0)
        }
        if (groundY == null && settle.size > 20) {
            val ys = settle.map { it.y }
            if (ys.maxOrNull()!! - ys.minOrNull()!! < 0.012f) {
                groundY = ys.average().toFloat()
            }
            return null
        }
        if (groundY == null) return null

        val rise = groundY!! - hip.y // image y grows downward
        val takeoffRiseThreshold = takeoffRise

        if (!airborne && rise > takeoffRiseThreshold) {
            airborne = true
            tTakeoff = tMs
            peakY = hip.y
            landing = null
            return null
        }

        if (airborne) {
            if (hip.y < peakY!!) peakY = hip.y
            if (rise <= takeoffRiseThreshold * 0.4f) {
                // Back on the ground
                airborne = false
                val kneeAtContact = getKneeAngle(world, side)
                landing = LandingState(t = tMs, kneeAtContact = kneeAtContact, minKnee = kneeAtContact)
                return null
            }
            return null
        }

        // Landing window: watch knee flexion for landingWindowMs after contact, then score the jump.
        landing?.let { l ->
            val k = getKneeAngle(world, side)
            if (k.isFinite() && k < l.minKnee) {
                l.minKnee = k
            }
            if (tMs - l.t >= landingWindowMs) {
                return complete(tMs, l)
            }
        }
        return null
    }

    private fun getScale(world: List<Landmark>?, image: List<Landmark>?, side: Side): Float? {
        if (world == null) return null
        val wh = getJoint(world, "HIP", side) ?: return null
        val wa = getJoint(world, "ANKLE", side) ?: return null
        val ih = getJoint(image, "HIP", side) ?: return null
        val ia = getJoint(image, "ANKLE", side) ?: return null

        val wd = hypot(hypot(wh.x - wa.x, wh.y - wa.y), wh.z - wa.z)
        val id = hypot(ih.x - ia.x, ih.y - ia.y)
        return if (id > 1e-4f) wd / id else null
    }

    private fun getJoint(lms: List<Landmark>?, joint: String, side: Side): Landmark? {
        if (lms == null) return null
        val idx = getJointIndex(joint, side)
        return if (idx >= 0 && idx < lms.size) lms[idx] else null
    }

    private fun getKneeAngle(world: List<Landmark>?, side: Side): Float {
        if (world == null) return Float.NaN
        val hip = getJoint(world, "HIP", side) ?: return Float.NaN
        val knee = getJoint(world, "KNEE", side) ?: return Float.NaN
        val ankle = getJoint(world, "ANKLE", side) ?: return Float.NaN
        return angle3(hip, knee, ankle)
    }

    private fun complete(tMs: Long, l: LandingState): MovementEvent? {
        landing = null
        val riseNorm = groundY!! - peakY!!
        val heightCm = if (scale != null) riseNorm * scale!! * 100f else Float.NaN
        if (!(heightCm > 3f)) return null // a shuffle is not a jump

        val softness = if (l.minKnee.isFinite()) {
            maxOf(0f, minOf(1f, (standingKnee - l.minKnee) / softLandingFlexionDeg))
        } else {
            0.5f
        }

        val heightScore = maxOf(0f, minOf(1f, heightCm / targetHeightCm))
        val formScore = maxOf(0f, minOf(1f, 0.55f * heightScore + 0.45f * softness))
        val reason = when {
            softness < 0.5f -> "landing"
            heightScore < 0.6f -> "height"
            else -> "none"
        }

        return JumpEvent(
            tStartMs = tTakeoff!!,
            tEndMs = tMs,
            formScore = formScore,
            signals = mapOf("height" to heightCm, "softness" to softness),
            heightCm = heightCm,
            softness = softness,
            airborneMs = l.t - tTakeoff!!,
        )
    }

    private fun getJointIndex(joint: String, side: Side): Int =
        Geometry.idx(joint, side)

    private data class SettleSample(val t: Long, val y: Float)

    private data class LandingState(val t: Long, val kneeAtContact: Float, var minKnee: Float)
}

private fun angle3(a: Landmark, b: Landmark, c: Landmark): Float {
    val v1x = a.x - b.x
    val v1y = a.y - b.y
    val v1z = a.z - b.z
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val v2z = c.z - b.z
    val n1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
    val n2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
    if (n1 < 1e-9f || n2 < 1e-9f) return Float.NaN
    val cos = ((v1x * v2x + v1y * v2y + v1z * v2z) / (n1 * n2)).coerceIn(-1f, 1f)
    return Math.toDegrees(kotlin.math.acos(cos.toDouble()).toFloat().toDouble()).toFloat()
}
