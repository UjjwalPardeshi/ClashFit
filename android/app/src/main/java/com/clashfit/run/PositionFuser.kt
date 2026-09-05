package com.clashfit.run

import kotlin.math.hypot

/**
 * One position, from whichever sensor can currently be believed.
 *
 * Outdoors that is GPS. Indoors GPS reports an accuracy circle wider than a building and
 * [FixFilter] throws every fix away, so the honest answer comes from the step counter and the
 * compass instead. This class owns the decision between them, and the handover, which is the part
 * that is easy to get visibly wrong.
 *
 * ### The handover
 *
 * Dead reckoning drifts. Walk a lap of a lab and it will place you a few metres from where you
 * started; walk five minutes of corridors and it may be tens of metres out. So when a good fix
 * returns, the fused position has to move to it — and *snapping* would teleport the runner across
 * the map in one frame, in full view of whoever is watching. Instead the estimate is pulled toward
 * each good fix by [SNAP_FRACTION], so it converges over a few seconds and the line on the map
 * stays a line. A genuinely large disagreement snaps anyway: past [SNAP_HARD_M] the estimate is
 * not drifting, it is wrong, and crawling toward the truth would draw a long false corridor.
 *
 * ### Why not always fuse both
 *
 * Because a fix that [FixFilter] rejected carries no information worth blending. The choice here
 * is not between two noisy estimates of the same quality; it is between a measurement and a guess,
 * and the guess is only used when there is no measurement.
 */
class PositionFuser(
    private val gpsStaleMs: Long = GPS_STALE_MS,
    private val snapFraction: Float = SNAP_FRACTION,
    private val snapHardM: Float = SNAP_HARD_M,
) {
    /** Which sensor the current position came from. Shown to the athlete, never hidden. */
    enum class Source {
        /** No position yet. */
        NONE,

        /** A GPS fix that cleared every quality gate. */
        GPS,

        /** Steps and heading, because GPS is unusable here. */
        STEPS,
    }

    var eastM: Float = 0f
        private set
    var northM: Float = 0f
        private set
    var source: Source = Source.NONE
        private set

    /** Distance credited so far, in metres, from whichever source was in charge at the time. */
    var distanceM: Float = 0f
        private set

    /** Steps taken since the last good fix, held so the stride can be learned from the pair. */
    private var stepsSinceFix: Int = 0
    private var lastGoodFixMs: Long? = null
    private var everHadFix = false

    /** True when the last update came from the step counter rather than a satellite. */
    val indoors: Boolean get() = source == Source.STEPS

    /** True when a fix has been accepted at some point, so the origin is a real coordinate. */
    val hasEverFixed: Boolean get() = everHadFix

    /**
     * A GPS fix that [FixFilter] accepted, in metres from the activity's origin.
     *
     * [gpsDistanceM] is what the filter measured against the previous accepted fix, and is
     * credited as distance directly: a satellite measurement beats an estimate built from it.
     */
    fun onGpsFix(
        eastM: Float,
        northM: Float,
        gpsDistanceM: Float,
        tMs: Long,
        deadReckoning: DeadReckoning? = null,
    ) {
        // Everything walked since the last fix, measured properly: that is what teaches the stride.
        if (stepsSinceFix > 0 && gpsDistanceM > 0f) {
            deadReckoning?.learnStride(gpsDistanceM, stepsSinceFix)
        }
        stepsSinceFix = 0
        lastGoodFixMs = tMs

        if (source == Source.NONE) {
            this.eastM = eastM
            this.northM = northM
        } else {
            val gap = hypot(eastM - this.eastM, northM - this.northM)
            if (gap > snapHardM) {
                this.eastM = eastM
                this.northM = northM
            } else {
                this.eastM += (eastM - this.eastM) * snapFraction
                this.northM += (northM - this.northM) * snapFraction
            }
        }
        source = Source.GPS
        everHadFix = true
        distanceM += gpsDistanceM
    }

    /**
     * Steps taken, used only while GPS is stale.
     *
     * Returns true when the step actually moved the fused position, so the caller knows whether a
     * route point is worth recording. While GPS is healthy the steps are still counted — they are
     * what the stride is learned from — but they do not move anything.
     */
    fun onSteps(displacement: DeadReckoning.Displacement, tMs: Long): Boolean {
        if (!displacement.moved) return false
        stepsSinceFix += displacement.steps

        val lastFix = lastGoodFixMs
        val gpsUsable = lastFix != null && tMs - lastFix < gpsStaleMs
        if (gpsUsable) return false

        eastM += displacement.eastM
        northM += displacement.northM
        distanceM += displacement.metres
        source = Source.STEPS
        return true
    }

    /** Seeds the origin when the activity begins somewhere with no usable fix at all. */
    fun seedOrigin() {
        if (source == Source.NONE) {
            eastM = 0f
            northM = 0f
            source = Source.STEPS
        }
    }

    /**
     * Forgets where we were without forgetting how far we have come.
     *
     * A pause does this: resuming somewhere else must not draw a straight line across the gap,
     * and must not credit the distance either.
     */
    fun clearAnchor() {
        lastGoodFixMs = null
        stepsSinceFix = 0
    }

    fun reset() {
        eastM = 0f
        northM = 0f
        distanceM = 0f
        source = Source.NONE
        stepsSinceFix = 0
        lastGoodFixMs = null
        everHadFix = false
    }

    companion object {
        /**
         * How long a fix stays authoritative.
         *
         * The service asks for one fix a second, so six seconds of silence means the receiver has
         * genuinely lost the sky rather than skipped a beat — which is what walking through a door
         * looks like.
         */
        const val GPS_STALE_MS = 6_000L

        /** How far the estimate moves toward each good fix. Converges in a few seconds. */
        const val SNAP_FRACTION = 0.45f

        /** Past this the estimate is wrong rather than drifting, so it snaps outright. */
        const val SNAP_HARD_M = 35f
    }
}
