package com.clashfit.alarm

import kotlinx.coroutines.flow.Flow

/** Event emitted when the rep gate observes a rep. */
data class RepGateEvent(
    val counted: Int,
    val target: Int,
    val lastVerdict: String?,
    val cue: String?,
)

/** Observes verified reps from pose analysis for a specific exercise. */
interface RepGate {
    /** Emits RepGateEvent updates for the given exercise. */
    fun observe(exerciseId: String): Flow<RepGateEvent>

    /** Reset the rep counter. */
    fun reset()
}
