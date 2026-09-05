package com.clashfit.perception.gesture

/**
 * Turns a stream of noisy per-frame readings into deliberate commands.
 *
 * A recogniser at ten frames a second sees an open palm for a fraction of a second every time a
 * hand passes the camera on the way up from a squat. If that fired "pause", the fight would pause
 * itself. So a shape has to be *held*: the same gesture, above a confidence floor, for [holdMs]
 * without interruption. Then it fires exactly once, and the same gesture cannot fire again until
 * [cooldownMs] has passed and the hand has been seen doing something else — otherwise a palm held
 * for two seconds would pause and immediately resume.
 *
 * Pure Kotlin on purpose: it is the part that decides whether the phone obeys, and it is tested
 * against a clock rather than a camera.
 */
class GestureIntent(
    private val holdMs: Long = 600L,
    private val cooldownMs: Long = 1500L,
    private val minScore: Float = 0.60f,
) {
    /** How far the current hold has progressed, 0..1, for the ring the HUD draws while you hold. */
    var progress: Float = 0f
        private set

    /** The gesture currently being held, if any — what the HUD names while the ring fills. */
    var holding: HandGesture? = null
        private set

    private var holdStartMs: Long = -1L
    private var lastFiredGesture: HandGesture? = null
    private var lastFiredAtMs: Long = Long.MIN_VALUE
    private var releasedSinceFire: Boolean = true

    /**
     * Feed one reading. Returns the gesture that just fired, or null.
     *
     * A reading below the confidence floor, or NONE, counts as the hand doing something else: it
     * resets the hold and clears the way for the last-fired gesture to fire again after cooldown.
     */
    fun offer(reading: GestureReading): HandGesture? {
        val g = reading.gesture
        val confident = g != HandGesture.NONE && reading.score >= minScore

        if (!confident) {
            reset()
            releasedSinceFire = true
            return null
        }

        if (holding != g) {
            // A different shape: start the hold over.
            holding = g
            holdStartMs = reading.tMs
            progress = 0f
            if (g != lastFiredGesture) releasedSinceFire = true
        }

        val held = reading.tMs - holdStartMs
        progress = (held.toFloat() / holdMs).coerceIn(0f, 1f)
        if (held < holdMs) return null

        // Held long enough. Fire only if this is not the same gesture repeating inside its cooldown
        // without the hand ever having been lowered.
        val sameAsLast = g == lastFiredGesture
        val inCooldown = reading.tMs - lastFiredAtMs < cooldownMs
        if (sameAsLast && (inCooldown || !releasedSinceFire)) {
            progress = 1f
            return null
        }

        lastFiredGesture = g
        lastFiredAtMs = reading.tMs
        releasedSinceFire = false
        // The hold is spent; a continued hold does not fire again until released.
        holdStartMs = reading.tMs
        progress = 1f
        return g
    }

    /** Forget any hold in progress. Called when the hand disappears or the set changes phase. */
    fun reset() {
        holding = null
        holdStartMs = -1L
        progress = 0f
    }
}
