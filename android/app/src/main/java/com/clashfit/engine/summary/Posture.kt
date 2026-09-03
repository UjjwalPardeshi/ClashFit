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

    /** Angle of ear→shoulder vector from vertical (y-axis). 0° is upright, positive is forward. */
    private fun neckFlexionDeg(ear: Landmark, shoulder: Landmark): Float {
        val dy = shoulder.y - ear.y
        val dx = shoulder.x - ear.x
        val angleRad = atan2(dx, dy)
        return Math.toDegrees(angleRad.toDouble()).toFloat()
    }

    /** Vertical distance from ear to shoulder, normalized by torso length (shoulder→hip distance). */
    private fun shoulderElevation(ear: Landmark, shoulder: Landmark, hip: Landmark): Float {
        val verticalDist = abs(shoulder.y - ear.y)
        val torsoLen = sqrt((shoulder.x - hip.x) * (shoulder.x - hip.x) + (shoulder.y - hip.y) * (shoulder.y - hip.y))
        return if (torsoLen > 0f) verticalDist / torsoLen else 0f
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
