package com.clashfit.run

/**
 * Splits a run into moving and paused time.
 *
 * Two separate decisions, deliberately kept apart:
 *
 * - **Moving time** only accrues for intervals actually covered at [movingSpeedMps] or better. The
 *   pause hysteresis must not be paid for in seconds: waiting three seconds to *notice* a stop is
 *   not the same as having been running for those three seconds.
 * - **The moving/paused state** the UI shows uses a lower threshold held for [pauseDwellMs], so a
 *   runner drifting around the threshold at a traffic light does not make the banner flicker.
 */
class MovingTimeTracker(
    private val movingSpeedMps: Float = MOVING_SPEED_MPS,
    private val pauseSpeedMps: Float = PAUSE_SPEED_MPS,
    private val pauseDwellMs: Long = PAUSE_DWELL_MS,
) {
    var movingMs: Long = 0L
        private set

    var isMoving: Boolean = false
        private set

    /** How many times the moving/paused state has flipped. Cheap flicker check. */
    var toggleCount: Int = 0
        private set

    private var belowSinceMs: Long? = null

    /** Feeds one accepted fix: [deltaMs] is the interval it covered at [speedMps]. */
    fun onFix(nowMs: Long, speedMps: Float, deltaMs: Long) {
        if (speedMps >= movingSpeedMps && deltaMs > 0L) {
            movingMs += deltaMs
        }

        if (speedMps >= pauseSpeedMps) {
            belowSinceMs = null
            if (!isMoving) {
                isMoving = true
                toggleCount++
            }
            return
        }

        val since = belowSinceMs ?: nowMs.also { belowSinceMs = it }
        if (isMoving && nowMs - since >= pauseDwellMs) {
            isMoving = false
            toggleCount++
        }
    }

    /** Starts the run already moving, for a resume that should not re-toggle. */
    fun markMoving() {
        isMoving = true
        belowSinceMs = null
    }

    fun reset() {
        movingMs = 0L
        isMoving = false
        toggleCount = 0
        belowSinceMs = null
    }

    companion object {
        /** Below 0.5 m/s (1.8 km/h) the clock stops: that is not running, and barely walking. */
        const val MOVING_SPEED_MPS = 0.5f

        /** The state only flips to paused below this, giving a band the runner can drift inside. */
        const val PAUSE_SPEED_MPS = 0.3f

        /** …and only after it has been held that long. */
        const val PAUSE_DWELL_MS = 3_000L
    }
}
