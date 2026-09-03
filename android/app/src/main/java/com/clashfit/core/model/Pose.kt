package com.clashfit.core.model

/** One landmark. World landmarks are metric and hip-centred; image landmarks are normalised 0..1. */
data class Landmark(val x: Float, val y: Float, val z: Float = 0f, val visibility: Float = 1f)

typealias Landmarks = List<Landmark>

/**
 * One frame out of the pose detector. `world` drives every angle; `image` drives only the
 * overlay and the framing check. Either may be null when nothing was detected.
 */
data class PoseFrame(val world: Landmarks?, val image: Landmarks?, val tMs: Long)

data class FrameQuality(
    val poseDetected: Boolean,
    val requiredJointsVisible: Boolean,
    val framing: Framing,
    val missingJoints: List<String>,
    val fps: Float,
) {
    companion object {
        val NONE = FrameQuality(false, false, Framing.NONE, emptyList(), 0f)
    }
}

data class CalibrationResult(val topRefDeg: Float, val romBaselineDeg: Float)
