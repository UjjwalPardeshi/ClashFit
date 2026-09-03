package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.HoldEvent
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * F2 · ISOMETRIC_HOLD — angle-in-range timer.
 * An isometric has no velocity, so fatigue shows up as tremor: rising positional variance of
 * the tracked joints. That is what actually happens to a human holding a plank, and it is why
 * the fatigue framework needed to be signal-driven rather than velocity-driven.
 */
class IsometricHoldDetector(
    private val spec: ExerciseSpec,
) : ExerciseDetector {

    override val family = "ISOMETRIC_HOLD"

    private data class Target(
        val angle: List<String>,
        val value: Float,
        val tolerance: Float,
        val weight: Float,
    )

    private data class EvaluationResult(
        val quality: Float,
        val inRange: Boolean,
        val worstJoint: String,
        val worstDev: Float,
    )

    private val targets: List<Target>
    private val breakToleranceMs: Long
    private val targetDurationSec: Float

    private var repIndex = 0
    private var holding = false
    private var tStart: Long? = null
    private var tLastInRange: Long? = null
    private var qualitySum = 0f
    private var qualityN = 0
    private var window = mutableListOf<Point>()
    private var baselineTremor: Float? = null
    private var _last: ExerciseDetector.LastObservation? = null

    override val last: ExerciseDetector.LastObservation?
        get() = _last

    init {
        val detector = spec.detector
        val targetsList = mutableListOf<Target>()
        detector["targets"]?.jsonArray?.forEach { item ->
            val t = item.jsonObject
            val angleArray = mutableListOf<String>()
            t["angle"]?.jsonArray?.forEach { a ->
                val joint = a.toString().trim('"').trim('\\')
                if (joint.isNotEmpty()) angleArray.add(joint)
            }
            val value = t["value"]?.jsonPrimitive?.floatOrNull ?: 90f
            val tolerance = t["tolerance"]?.jsonPrimitive?.floatOrNull ?: 12f
            val weight = t["weight"]?.jsonPrimitive?.floatOrNull ?: 1f
            targetsList.add(Target(angleArray, value, tolerance, weight))
        }
        targets = targetsList

        breakToleranceMs = detector["breakToleranceMs"]?.let {
            it.toString().toIntOrNull()?.toLong() ?: 500L
        } ?: 500L

        targetDurationSec = detector["targetDurationSec"]?.jsonPrimitive?.floatOrNull ?: 45f
    }

    override fun reset() {
        repIndex = 0
        holding = false
        tStart = null
        tLastInRange = null
        qualitySum = 0f
        qualityN = 0
        window.clear()
        baselineTremor = null
        _last = null
    }

    override fun onFrame(
        world: List<Landmark>?,
        image: List<Landmark>?,
        tMs: Long,
        side: Side,
    ): MovementEvent? {
        // Angles come from the metric world landmarks, as in the JS; image space only as a fallback.
        val lms = world ?: image ?: return null
        val e = evaluate(lms, side) ?: return null
        val tremor = calculateTremor(lms, side, tMs)

        if (!holding) {
            if (e.inRange) {
                holding = true
                tStart = tMs
                tLastInRange = tMs
                qualitySum = 0f
                qualityN = 0
                baselineTremor = null
            }
            _last = ExerciseDetector.LastObservation(quality = e.quality, tremor = tremor)
            return null
        }

        if (e.inRange) {
            tLastInRange = tMs
            qualitySum += e.quality
            qualityN++
        }
        val holdSec = (tMs - tStart!!) / 1000f
        if (baselineTremor == null && holdSec > 3) {
            baselineTremor = tremor
        }
        _last = ExerciseDetector.LastObservation(quality = e.quality, tremor = tremor, holdSec = holdSec)

        val brokenFor = tMs - (tLastInRange ?: tMs)
        val done = holdSec >= targetDurationSec
        if (brokenFor >= breakToleranceMs || done) {
            return complete(tMs, done, tremor)
        }
        return null
    }

    private fun evaluate(lms: List<Landmark>, side: Side): EvaluationResult? {
        var wsum = 0f
        var acc = 0f
        var worst: String? = null
        var worstDev = -1f

        for (t in targets) {
            if (t.angle.size < 3) continue
            val a = getJointIndex(t.angle[0], side)
            val b = getJointIndex(t.angle[1], side)
            val c = getJointIndex(t.angle[2], side)
            if (a < 0 || b < 0 || c < 0 || a >= lms.size || b >= lms.size || c >= lms.size) continue

            val deg = angle3(lms[a], lms[b], lms[c])
            if (!deg.isFinite()) return null

            val dev = abs(deg - t.value) / t.tolerance
            val w = t.weight
            acc += w * maxOf(0f, 1f - dev)
            wsum += w
            if (dev > worstDev) {
                worstDev = dev
                worst = t.angle[1]
            }
        }
        return if (wsum > 0f) {
            EvaluationResult(
                quality = acc / wsum,
                inRange = worstDev <= 1f,
                worstJoint = worst ?: "UNKNOWN",
                worstDev = worstDev,
            )
        } else null
    }

    private fun calculateTremor(lms: List<Landmark>, side: Side, tMs: Long): Float {
        val pts = mutableListOf<Point>()
        for (t in targets) {
            if (t.angle.size < 2) continue
            val idx = getJointIndex(t.angle[1], side)
            if (idx >= 0 && idx < lms.size) {
                val lm = lms[idx]
                pts.add(Point(lm.x, lm.y))
            }
        }
        if (pts.isEmpty()) return 0f

        val centroid = Point(
            x = pts.map { it.x }.average().toFloat(),
            y = pts.map { it.y }.average().toFloat(),
        )
        window.add(Point(x = centroid.x, y = centroid.y, t = tMs))

        while (window.isNotEmpty() && tMs - window.first().t > 1000) {
            window.removeAt(0)
        }
        if (window.size < 8) return 0f

        val mx = window.map { it.x }.average().toFloat()
        val my = window.map { it.y }.average().toFloat()
        val variance = window.map { (it.x - mx) * (it.x - mx) + (it.y - my) * (it.y - my) }
            .average().toFloat()
        return sqrt(variance) * 1000f // millimetres of wobble
    }

    private fun complete(tMs: Long, completed: Boolean, tremor: Float): MovementEvent? {
        val holdSec = (tLastInRange!! - tStart!!) / 1000f
        holding = false
        if (holdSec < 1f) return null

        repIndex++
        val quality = if (qualityN > 0) qualitySum / qualityN else 0f
        val formScore = maxOf(0f, minOf(1f, quality * minOf(1f, holdSec / targetDurationSec)))
        val reason = when {
            quality < 0.7f -> "alignment"
            holdSec < targetDurationSec -> "endurance"
            else -> "none"
        }

        return HoldEvent(
            tStartMs = tStart!!,
            tEndMs = tMs,
            formScore = formScore,
            signals = mapOf("tremor" to tremor, "quality" to quality),
            holdSec = holdSec,
            quality = quality,
            completed = completed,
            tremor = tremor,
        )
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

    private data class Point(val x: Float, val y: Float, val t: Long = 0L)
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
