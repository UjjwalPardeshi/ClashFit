package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import com.clashfit.core.model.PoseEvent
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * F3 · POSE_MATCH — whole-skeleton template matching, for yoga.
 *
 * The difference between a yoga feature and a yoga toy is the cue. We surface the single worst
 * joint by name — "straighten your left arm" — rather than an accuracy percentage, because a
 * percentage tells you that you are wrong without telling you what to move.
 *
 * Mirror tolerant: a tree pose on either leg is the same pose.
 */
class PoseMatchDetector(
    private val spec: ExerciseSpec,
) : ExerciseDetector {

    override val family = "POSE_MATCH"

    private data class Reference(
        val angle: List<String>,
        val value: Float,
        val tolerance: Float,
        val weight: Float,
    )

    private data class AccuracyResult(
        val accuracy: Float,
        val worst: WorstJoint?,
    )

    private data class WorstJoint(
        val joint: String,
        val side: Side,
        val deg: Float,
        val target: Float,
    )

    private val reference: List<Reference>
    private val enterAccuracy: Float
    private val enterHoldMs: Long
    private val breakToleranceMs: Long
    private val targetDurationSec: Float

    private var repIndex = 0
    private var inPose = false
    private var tEnter: Long? = null
    private var tLastGood: Long? = null
    private var accSum = 0f
    private var accN = 0
    private var enterCandidateSince: Long? = null
    private var _last: ExerciseDetector.LastObservation? = null

    override val last: ExerciseDetector.LastObservation?
        get() = _last

    init {
        val detector = spec.detector
        val referenceList = mutableListOf<Reference>()
        detector["reference"]?.jsonArray?.forEach { item ->
            val t = item.jsonObject
            val angleArray = mutableListOf<String>()
            t["angle"]?.jsonArray?.forEach { a ->
                val joint = a.toString().trim('"').trim('\\')
                if (joint.isNotEmpty()) angleArray.add(joint)
            }
            val value = t["value"]?.jsonPrimitive?.floatOrNull ?: 90f
            val tolerance = t["tolerance"]?.jsonPrimitive?.floatOrNull ?: 18f
            val weight = t["weight"]?.jsonPrimitive?.floatOrNull ?: 1f
            referenceList.add(Reference(angleArray, value, tolerance, weight))
        }
        reference = referenceList

        enterAccuracy = detector["enterAccuracy"]?.jsonPrimitive?.floatOrNull ?: 0.70f
        enterHoldMs = detector["enterHoldMs"]?.let {
            it.toString().toLongOrNull() ?: 1500L
        } ?: 1500L
        breakToleranceMs = detector["breakToleranceMs"]?.let {
            it.toString().toLongOrNull() ?: 800L
        } ?: 800L
        targetDurationSec = detector["targetDurationSec"]?.jsonPrimitive?.floatOrNull ?: 20f
    }

    override fun reset() {
        repIndex = 0
        inPose = false
        tEnter = null
        tLastGood = null
        accSum = 0f
        accN = 0
        enterCandidateSince = null
        _last = null
    }

    override fun onFrame(
        world: List<Landmark>?,
        image: List<Landmark>?,
        tMs: Long,
        side: Side,
    ): MovementEvent? {
        val lms = image ?: return null

        // Mirror tolerance: score both sides, keep the better reading.
        val a = accuracy(lms, Side.LEFT)
        val b = accuracy(lms, Side.RIGHT)
        val best = when {
            a == null -> b
            b == null -> a
            a.accuracy >= b.accuracy -> a
            else -> b
        } ?: return null

        _last = ExerciseDetector.LastObservation(
            accuracy = best.accuracy,
            cue = generateCue(best.worst),
        )

        if (!inPose) {
            if (best.accuracy >= enterAccuracy) {
                if (enterCandidateSince == null) {
                    enterCandidateSince = tMs
                }
                if (tMs - enterCandidateSince!! >= enterHoldMs) {
                    inPose = true
                    tEnter = tMs
                    tLastGood = tMs
                    accSum = 0f
                    accN = 0
                }
            } else {
                enterCandidateSince = null
            }
            return null
        }

        if (best.accuracy >= enterAccuracy) {
            tLastGood = tMs
            accSum += best.accuracy
            accN++
        }
        val heldSec = (tMs - tEnter!!) / 1000f
        val lostFor = tMs - (tLastGood ?: tMs)

        if (lostFor >= breakToleranceMs || heldSec >= targetDurationSec) {
            inPose = false
            enterCandidateSince = null
            val held = (tLastGood!! - tEnter!!) / 1000f
            if (held < 1f) return null

            repIndex++
            val accuracy = if (accN > 0) accSum / accN else 0f
            val completed = heldSec >= targetDurationSec
            val formScore = maxOf(0f, minOf(1f, accuracy * minOf(1f, held / targetDurationSec)))
            val reason = when {
                accuracy < 0.8f -> "alignment"
                held < targetDurationSec -> "endurance"
                else -> "none"
            }

            return PoseEvent(
                tStartMs = tEnter!!,
                tEndMs = tMs,
                formScore = formScore,
                signals = mapOf("accuracy" to accuracy),
                accuracy = accuracy,
                heldSec = held,
                worstJoint = best.worst?.joint,
                cue = generateCue(best.worst),
            )
        }
        return null
    }

    private fun accuracy(lms: List<Landmark>, side: Side): AccuracyResult? {
        var wsum = 0f
        var acc = 0f
        var worst: WorstJoint? = null
        var worstDev = -1f

        for (t in reference) {
            if (t.angle.size < 3) continue
            val a = getJointIndex(t.angle[0], side)
            val b = getJointIndex(t.angle[1], side)
            val c = getJointIndex(t.angle[2], side)
            if (a < 0 || b < 0 || c < 0 || a >= lms.size || b >= lms.size || c >= lms.size) {
                continue
            }

            val deg = angle3(lms[a], lms[b], lms[c])
            if (!deg.isFinite()) continue

            val dev = abs(deg - t.value) / t.tolerance
            val w = t.weight
            acc += w * maxOf(0f, 1f - dev)
            wsum += w

            if (dev > worstDev) {
                worstDev = dev
                worst = WorstJoint(joint = t.angle[1], side = side, deg = deg, target = t.value)
            }
        }

        return if (wsum > 0f) {
            AccuracyResult(accuracy = acc / wsum, worst = worst)
        } else null
    }

    private fun generateCue(worst: WorstJoint?): String? {
        if (worst == null) return null

        val readableJoints = mapOf(
            "SHOULDER" to "shoulder",
            "ELBOW" to "arm",
            "WRIST" to "wrist",
            "HIP" to "hip",
            "KNEE" to "leg",
            "ANKLE" to "ankle",
        )

        val part = readableJoints[worst.joint] ?: worst.joint.lowercase()
        val which = if (worst.side == Side.LEFT) "left" else "right"
        return if (worst.deg < worst.target) {
            "Open your $which $part further."
        } else {
            "Ease your $which $part back."
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
