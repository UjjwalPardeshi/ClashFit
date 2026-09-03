package com.clashfit.engine.core

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Side

import kotlin.math.sqrt
import kotlin.math.acos
import kotlin.math.PI

/** MediaPipe Pose landmark indices. */
object LM {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32
}

/** Joint name → [leftIndex, rightIndex]. Used in exercise detector configs. */
object Geometry {
    private val JOINT_MAP = mapOf(
        "SHOULDER" to (LM.LEFT_SHOULDER to LM.RIGHT_SHOULDER),
        "ELBOW" to (LM.LEFT_ELBOW to LM.RIGHT_ELBOW),
        "WRIST" to (LM.LEFT_WRIST to LM.RIGHT_WRIST),
        "HIP" to (LM.LEFT_HIP to LM.RIGHT_HIP),
        "KNEE" to (LM.LEFT_KNEE to LM.RIGHT_KNEE),
        "ANKLE" to (LM.LEFT_ANKLE to LM.RIGHT_ANKLE),
        "HEEL" to (LM.LEFT_HEEL to LM.RIGHT_HEEL),
        "FOOT_INDEX" to (LM.LEFT_FOOT_INDEX to LM.RIGHT_FOOT_INDEX),
    )

    /** Get landmark index for a joint name and side. */
    fun idx(joint: String, side: Side): Int =
        JOINT_MAP[joint]?.let { if (side == Side.LEFT) it.first else it.second } ?: -1

    /** Angle at vertex b, computed from three 3D points, in degrees. NaN if degenerate. */
    fun angle3(a: Landmark, b: Landmark, c: Landmark): Float {
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
        return (acos(cos.toDouble()) * 180 / PI).toFloat()
    }

    /** Mean visibility of required joints on one side. */
    fun sideVisibility(landmarks: List<Landmark>, jointNames: List<String>, side: Side): Float {
        var sum = 0f
        for (j in jointNames) {
            val i = idx(j, side)
            if (i >= 0 && i < landmarks.size) sum += landmarks[i].visibility
        }
        return sum / jointNames.size
    }

    /** Which side to score from — chooses the better-seen side, averages if both are good. */
    fun chooseSide(
        landmarks: List<Landmark>,
        jointNames: List<String>,
        threshold: Float = 0.6f,
    ): SideSelection {
        val l = sideVisibility(landmarks, jointNames, Side.LEFT)
        val r = sideVisibility(landmarks, jointNames, Side.RIGHT)
        val bothOk = l >= threshold && r >= threshold
        val best = if (l >= r) Side.LEFT else Side.RIGHT
        val bestVis = maxOf(l, r)
        return SideSelection(best, bothOk, bestVis, bestVis >= threshold)
    }

    /** Primary angle for an exercise in degrees. NaN if the frame is unusable. */
    fun primaryAngle(
        landmarks: List<Landmark>,
        aName: String, bName: String, cName: String,
        jointNames: List<String>,
        threshold: Float = 0.6f,
    ): PrimaryAngleResult {
        val sel = chooseSide(landmarks, jointNames, threshold)
        fun one(side: Side): Float {
            val ai = idx(aName, side)
            val bi = idx(bName, side)
            val ci = idx(cName, side)
            if (ai < 0 || bi < 0 || ci < 0) return Float.NaN
            return angle3(landmarks[ai], landmarks[bi], landmarks[ci])
        }
        val left = one(Side.LEFT)
        val right = one(Side.RIGHT)
        val deg = if (sel.both) {
            if (left.isNaN() || right.isNaN()) Float.NaN else (left + right) / 2f
        } else {
            one(sel.side)
        }
        return PrimaryAngleResult(
            angle = deg,
            left = left,
            right = right,
            side = sel.side,
            both = sel.both,
            visibility = sel.visibility,
            valid = sel.valid,
        )
    }

    /** Distance in metres between the outer two joints of the primary angle. */
    fun spanMetres(landmarks: List<Landmark>, aName: String, cName: String, side: Side): Float {
        val ai = idx(aName, side)
        val ci = idx(cName, side)
        if (ai < 0 || ci < 0 || ai >= landmarks.size || ci >= landmarks.size) return Float.NaN
        val a = landmarks[ai]
        val c = landmarks[ci]
        val dx = a.x - c.x
        val dy = a.y - c.y
        val dz = a.z - c.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Horizontal knee-to-ankle offset, normalised by shin length. Squat alignment metric. */
    fun kneeTracking(landmarks: List<Landmark>, side: Side): Float {
        val ki = idx("KNEE", side)
        val ai = idx("ANKLE", side)
        if (ki < 0 || ai < 0 || ki >= landmarks.size || ai >= landmarks.size) return Float.NaN
        val knee = landmarks[ki]
        val ankle = landmarks[ai]
        val dx = knee.x - ankle.x
        val dy = knee.y - ankle.y
        val dz = knee.z - ankle.z
        val shin = sqrt(dx * dx + dy * dy + dz * dz)
        if (shin < 1e-6f) return Float.NaN
        return kotlin.math.abs(dx) / shin
    }

    /** Torso line angle shoulder-hip-ankle. Push-up sag metric. */
    fun torsoLine(landmarks: List<Landmark>, side: Side): Float {
        val si = idx("SHOULDER", side)
        val hi = idx("HIP", side)
        val ai = idx("ANKLE", side)
        if (si < 0 || hi < 0 || ai < 0) return Float.NaN
        if (si >= landmarks.size || hi >= landmarks.size || ai >= landmarks.size) return Float.NaN
        return angle3(landmarks[si], landmarks[hi], landmarks[ai])
    }

    /** Which joints are not visible enough, by name. Readable for UI cueing. */
    fun missingJoints(landmarks: List<Landmark>, jointNames: List<String>, threshold: Float, side: Side): List<String> {
        val out = mutableListOf<String>()
        for (j in jointNames) {
            val i = idx(j, side)
            val v = if (i >= 0 && i < landmarks.size) landmarks[i].visibility else 0f
            if (v < threshold) out += j
        }
        return out
    }

    /** Human-readable joint names for the UI. */
    fun jointPhrase(names: List<String>): String {
        val readable = mapOf(
            "SHOULDER" to "shoulders", "ELBOW" to "elbows", "WRIST" to "wrists",
            "HIP" to "hips", "KNEE" to "knees", "ANKLE" to "ankles",
            "HEEL" to "heels", "FOOT_INDEX" to "feet",
        )
        val words = names.map { readable[it] ?: it.lowercase() }
        return when (words.size) {
            0 -> ""
            1 -> words[0]
            2 -> "${words[0]} or ${words[1]}"
            else -> "${words.dropLast(1).joinToString(", ")} or ${words.last()}"
        }
    }

    /** Normalised height of the landmark bounding box. Used for framing hint. */
    fun boxHeight(landmarks: List<Landmark>): Float {
        if (landmarks.isEmpty()) return 0f
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (p in landmarks) {
            if (p.y < min) min = p.y
            if (p.y > max) max = p.y
        }
        return if (min == Float.POSITIVE_INFINITY) 0f else max - min
    }
}

data class SideSelection(
    val side: Side,
    val both: Boolean,
    val visibility: Float,
    val valid: Boolean,
)

/** Result of primaryAngle computation, including bilateral breakdown. */
data class PrimaryAngleResult(
    val angle: Float,
    val left: Float,
    val right: Float,
    val side: Side,
    val both: Boolean,
    val visibility: Float,
    val valid: Boolean,
)

