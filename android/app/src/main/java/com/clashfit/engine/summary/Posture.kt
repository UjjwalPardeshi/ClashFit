package com.clashfit.engine.summary

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** One posture frame: score 0-100, metrics, and a description. */
data class PostureSample(
    val score: Int,
    val neckFlexionDeg: Float,
    val shoulderElevation: Float,
    val description: String,
)

/**
 * Scores a single frame of pose landmarks for postural health: neck flexion and shoulder elevation.
 * Frames are sampled, not stored — only the score and metrics are persisted.
 *
 * Indices from MediaPipe Pose (33-point model):
 * - Ears: 7 (left), 8 (right)
 * - Shoulders: 11 (left), 12 (right)
 * - Hips: 23 (left), 24 (right)
 *
 * Neck flexion: angle between the ear→shoulder segment and vertical (0° = upright, 45° = max forward head).
 * Shoulder elevation: vertical distance from ear to shoulder, normalized by torso length.
 * Score is 0–100, where 100 is upright (≤10° flexion), 0 is hunched (≥45°), with an elevation penalty applied.
 *
 * Returns null if visibility is poor (fewer than 4 of the 6 required points visible).
 */
object PostureScorer {
    private const val VISIBILITY_THRESHOLD = 0.4f
    /** Ear-to-shoulder gap over torso length for a relaxed, upright figure. */
    private const val NEUTRAL_GAP_RATIO = 0.38f

    fun score(world: Landmarks, image: Landmarks?): PostureSample? {
        // Six required points: ears, shoulders, hips.
        val earL = world.getOrNull(7) ?: return null
        val earR = world.getOrNull(8) ?: return null
        val shoulderL = world.getOrNull(11) ?: return null
        val shoulderR = world.getOrNull(12) ?: return null
        val hipL = world.getOrNull(23) ?: return null
        val hipR = world.getOrNull(24) ?: return null

        val visibleCount = listOfNotNull(
            earL.takeIf { it.visibility > VISIBILITY_THRESHOLD },
            earR.takeIf { it.visibility > VISIBILITY_THRESHOLD },
            shoulderL.takeIf { it.visibility > VISIBILITY_THRESHOLD },
            shoulderR.takeIf { it.visibility > VISIBILITY_THRESHOLD },
            hipL.takeIf { it.visibility > VISIBILITY_THRESHOLD },
            hipR.takeIf { it.visibility > VISIBILITY_THRESHOLD },
        ).size

        if (visibleCount < 4) return null

        // Neck flexion: angle of ear→shoulder segment from vertical (y-axis).
        val flexL = neckFlexionDeg(earL, shoulderL)
        val flexR = neckFlexionDeg(earR, shoulderR)
        val flexBoth = if (visibleCount >= 4) listOfNotNull(
            flexL.takeIf { earL.visibility > VISIBILITY_THRESHOLD && shoulderL.visibility > VISIBILITY_THRESHOLD },
            flexR.takeIf { earR.visibility > VISIBILITY_THRESHOLD && shoulderR.visibility > VISIBILITY_THRESHOLD },
        ).let { if (it.isEmpty()) 0f else it.average().toFloat() } else (flexL + flexR) / 2f

        // Shoulder elevation: vertical distance ear→shoulder, normalized by torso length.
        val elevL = shoulderElevation(earL, shoulderL, hipL)
        val elevR = shoulderElevation(earR, shoulderR, hipR)
        val elevBoth = if (visibleCount >= 4) listOfNotNull(
            elevL.takeIf { earL.visibility > VISIBILITY_THRESHOLD && shoulderL.visibility > VISIBILITY_THRESHOLD && hipL.visibility > VISIBILITY_THRESHOLD },
            elevR.takeIf { earR.visibility > VISIBILITY_THRESHOLD && shoulderR.visibility > VISIBILITY_THRESHOLD && hipR.visibility > VISIBILITY_THRESHOLD },
        ).let { if (it.isEmpty()) 0f else it.average().toFloat() } else (elevL + elevR) / 2f

        // Score: 100 at ≤10° flexion, 0 at ≥45°, linear interpolation.
        // Elevation penalty: reduce by up to 20 points if shoulders are raised.
        val flexScore = (45f - flexBoth.coerceIn(10f, 45f)) / (45f - 10f) * 100f
        val elevPenalty = elevBoth.coerceIn(0f, 1f) * 20f
        val finalScore = (flexScore - elevPenalty).toInt().coerceIn(0, 100)

        val description = postureDescription(flexBoth.toInt(), elevBoth)
        return PostureSample(
            score = finalScore,
            neckFlexionDeg = flexBoth,
            shoulderElevation = elevBoth,
            description = description,
        )
    }

    /**
     * Forward head: the angle of the ear→shoulder segment from vertical in the sagittal plane. With
     * the camera facing the user, "forward" is depth (z); lateral offset (x) is a head tilt, not
     * flexion, and is deliberately ignored. 0° is upright.
     */
    private fun neckFlexionDeg(ear: Landmark, shoulder: Landmark): Float {
        val forward = abs(ear.z - shoulder.z)
        val up = abs(ear.y - shoulder.y)
        return Math.toDegrees(atan2(forward, up).toDouble()).toFloat()
    }

    /**
     * Shoulder elevation, 0 relaxed → 1 fully shrugged. A shrug closes the ear-to-shoulder gap, so
     * the signal is how far that gap (normalised by torso length) has shrunk below neutral.
     */
    private fun shoulderElevation(ear: Landmark, shoulder: Landmark, hip: Landmark): Float {
        val gap = abs(ear.y - shoulder.y)
        val torsoLen = sqrt((shoulder.x - hip.x) * (shoulder.x - hip.x) + (shoulder.y - hip.y) * (shoulder.y - hip.y))
        if (torsoLen <= 0f) return 0f
        val ratio = gap / torsoLen
        return (1f - (ratio / NEUTRAL_GAP_RATIO)).coerceIn(0f, 1f)
    }

    private fun postureDescription(flexDeg: Int, elevation: Float): String {
        return when {
            flexDeg <= 15 && elevation <= 0.3f -> "Excellent posture — shoulders back, head upright."
            flexDeg <= 25 && elevation <= 0.4f -> "Good posture — slight forward lean."
            flexDeg <= 35 && elevation <= 0.5f -> "Moderate — some neck flexion and shoulder elevation."
            elevation > 0.5f -> "Shoulders raised — consider lowering your shoulders."
            else -> "Forward head position — try moving your head back."
        }
    }
}
