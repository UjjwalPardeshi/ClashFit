package com.clashfit.engine.detect

import com.clashfit.core.config.ExerciseSpec

/**
 * Creates an ExerciseDetector for an exercise spec. One interface, five movement families —
 * which is the mechanism behind "five detectors, sixty-one exercises, one fatigue model".
 * Adding an exercise is a config record; adding a family is one class.
 * docs/09-MODULE-CONTRACTS.md §3b
 */
object DetectorFactory {
    /**
     * Create a detector for the given exercise spec, or null if the family is REP_CYCLE
     * (which is handled directly by SessionEngine).
     */
    fun create(spec: ExerciseSpec): ExerciseDetector? = when (spec.family) {
        "ISOMETRIC_HOLD" -> IsometricHoldDetector(spec)
        "CADENCE" -> CadenceDetector(spec)
        "BALLISTIC" -> BallisticDetector(spec)
        "POSE_MATCH" -> PoseMatchDetector(spec)
        "REP_CYCLE" -> null // Handled by SessionEngine
        else -> null
    }
}
