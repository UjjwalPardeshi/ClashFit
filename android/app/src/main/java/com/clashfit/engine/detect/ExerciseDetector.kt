package com.clashfit.engine.detect

import com.clashfit.core.model.Side

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.MovementEvent

/**
 * Detects discrete movements from landmark streams. Each detector family processes a different
 * kind of movement and emits its own MovementEvent type. The detector is stateful: onFrame
 * accumulates data and returns null most frames, then emits an event when a complete movement
 * is detected. Reset the detector at the start of each exercise attempt.
 */
interface ExerciseDetector {
    /** The movement family this detector handles (ISOMETRIC_HOLD, CADENCE, BALLISTIC, POSE_MATCH). */
    val family: String

    /**
     * Process one video frame. Exactly one of world or image is supplied per detector family,
     * as per the ExerciseSpec detector config.
     *
     * @param world World-space landmarks (metric, hip-centred) for ballistic.
     * @param image Image-space landmarks (normalised, viewport-centred) for all families.
     * @param tMs Timestamp of this frame in milliseconds since the start of tracking.
     * @param side LEFT or RIGHT, from which side the gesture is best seen.
     * @return A MovementEvent if a complete movement was detected in this frame, else null.
     */
    fun onFrame(
        world: List<Landmark>?,
        image: List<Landmark>?,
        tMs: Long,
        side: Side,
    ): MovementEvent?

    /** Reset the detector state for a new attempt. */
    fun reset()

    /** The last partial observation, updated every frame for diagnostics (cues, partial quality). */
    val last: LastObservation?

    /** Last partial observation, never a full event. Different fields are populated per family. */
    data class LastObservation(
        val quality: Float? = null,      // isometric, pose match
        val accuracy: Float? = null,     // pose match
        val tremor: Float? = null,       // isometric
        val cue: String? = null,         // pose match
        val holdSec: Float? = null,      // isometric
    )
}

