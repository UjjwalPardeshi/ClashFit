package com.clashfit.perception.gesture

import kotlinx.coroutines.flow.Flow

/**
 * A pose source that can also read the player's hand.
 *
 * Kept separate from [com.clashfit.core.pose.PoseSource] so the recorded-trace and synthetic
 * sources need not pretend to see hands, and so the fight can ask `deps.pose as? GestureSource`
 * and simply carry on without gestures when the answer is no.
 */
interface GestureSource {
    /** Raw recogniser readings, roughly ten a second while enabled. Latest wins. */
    val gestures: Flow<GestureReading>

    /**
     * Whether to spend frames on the hand at all. Off during calibration and rest, when a raised
     * palm means nothing, and off entirely if the player turned the setting off.
     */
    fun setGesturesEnabled(enabled: Boolean)
}
