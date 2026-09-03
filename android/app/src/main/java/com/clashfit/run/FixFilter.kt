package com.clashfit.run

/**
 * One GPS sample, stripped of Android types so the filtering rules can be exercised on the JVM.
 *
 * [reportedSpeedMps] is the provider's own (Doppler) speed. It is null when the fix carries none,
 * which matters: at rest the Doppler speed is the only trustworthy "am I moving" signal, because a
 * speed derived from two jittering positions always looks fast.
 */
data class Fix(
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val altM: Double? = null,
    val accuracyM: Float = 0f,
    val reportedSpeedMps: Float? = null,
)

/** Why a fix was dropped, or what it contributed when it was kept. */
sealed interface FixResult {
    /** Kept. [distanceM] and [speedMps] are measured against the previous accepted fix. */
    data class Accepted(
        val fix: Fix,
        val distanceM: Float,
        val speedMps: Float,
        val deltaMs: Long,
    ) : FixResult

    data class Rejected(val reason: Reason) : FixResult

    enum class Reason {
        /** Accuracy circle wider than the gate. */
        ACCURACY,

        /** Inside the settle window after the run started; the receiver has not locked yet. */
        SETTLING,

        /** Outside the WGS84 domain, or NaN. */
        INVALID_COORDS,

        /** Same or older timestamp than the last accepted fix. */
        STALE_TIMESTAMP,

        /** Faster than a human can run: a receiver glitch, not a movement. */
        SPEED_JUMP,

        /** Standing still, or a step smaller than the noise floor. */
        JITTER,
    }
}

/**
 * Gates raw GPS fixes before they are allowed to move the distance total.
 *
 * The rules, in order: accuracy gate, first-fix settle window, coordinate sanity, monotonic
 * timestamps, impossible-speed rejection, and a jitter filter for a runner standing still.
 *
 * A rejected fix never becomes the anchor for the next one. Holding the anchor is what makes the
 * jitter filter safe for slow movement: a genuine walk keeps adding up against a fixed anchor until
 * it clears the noise floor, instead of being nibbled away one sub-threshold step at a time.
 */
class FixFilter(
    private val accuracyGateM: Float = ACCURACY_GATE_M,
    private val settleMs: Long = SETTLE_MS,
    private val maxSpeedMps: Float = MAX_SPEED_MPS,
    private val restSpeedMps: Float = REST_SPEED_MPS,
    private val jitterDistanceM: Float = JITTER_DISTANCE_M,
) {
    private var startedAtMs: Long? = null
    private var lastAccepted: Fix? = null

    /** The last fix that survived the filter, or null before the first one. */
    val anchor: Fix? get() = lastAccepted

    /** Opens the settle window. Called when the run starts. */
    fun start(nowMs: Long) {
        startedAtMs = nowMs
        lastAccepted = null
    }

    /**
     * Forgets the anchor without reopening the settle window. A pause has to do this: resuming a
     * run half a kilometre away would otherwise read as one impossible jump, and since a rejected
     * fix never becomes the anchor, the filter would go on rejecting every fix after it.
     */
    fun clearAnchor() {
        lastAccepted = null
    }

    fun reset() {
        startedAtMs = null
        lastAccepted = null
    }

    fun accept(fix: Fix): FixResult {
        if (!isValidCoordinate(fix.lat, fix.lon) || fix.altM?.isNaN() == true) {
            return FixResult.Rejected(FixResult.Reason.INVALID_COORDS)
        }
        if (fix.accuracyM > accuracyGateM) {
            return FixResult.Rejected(FixResult.Reason.ACCURACY)
        }
        val startedAt = startedAtMs
        if (startedAt != null && fix.tMs - startedAt < settleMs) {
            return FixResult.Rejected(FixResult.Reason.SETTLING)
        }
        val prev = lastAccepted
        if (prev == null) {
            // The first settled fix is the anchor. It contributes no distance of its own.
            lastAccepted = fix
            return FixResult.Accepted(fix, distanceM = 0f, speedMps = 0f, deltaMs = 0L)
        }
        if (fix.tMs <= prev.tMs) {
            return FixResult.Rejected(FixResult.Reason.STALE_TIMESTAMP)
        }

        val deltaMs = fix.tMs - prev.tMs
        val distanceM = haversineM(prev.lat, prev.lon, fix.lat, fix.lon)
        val derivedSpeedMps = distanceM / (deltaMs / 1000f)
        if (derivedSpeedMps > maxSpeedMps) {
            return FixResult.Rejected(FixResult.Reason.SPEED_JUMP)
        }

        // At rest the provider's own speed reads ~0 while the position wanders by metres, so the
        // reported speed decides "standing still" and the derived one is only the fallback.
        val atRest = fix.reportedSpeedMps?.let { it < restSpeedMps } ?: (derivedSpeedMps < restSpeedMps)
        if (atRest || distanceM < jitterDistanceM) {
            return FixResult.Rejected(FixResult.Reason.JITTER)
        }

        lastAccepted = fix
        return FixResult.Accepted(fix, distanceM, derivedSpeedMps, deltaMs)
    }

    companion object {
        /** Fixes with a wider accuracy circle than this are not worth integrating. */
        const val ACCURACY_GATE_M = 25f

        /** The receiver is still settling for the first ten seconds of a run. */
        const val SETTLE_MS = 10_000L

        /** 8 m/s ≈ 28.8 km/h: faster than the run this app tracks, so it is a glitch. */
        const val MAX_SPEED_MPS = 8f

        /** Below this reported speed the runner is standing still. */
        const val REST_SPEED_MPS = 0.1f

        /** Steps shorter than this are inside the noise floor of a consumer receiver. */
        const val JITTER_DISTANCE_M = 2f
    }
}
