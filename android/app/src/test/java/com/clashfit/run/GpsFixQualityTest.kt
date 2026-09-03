package com.clashfit.run

import com.clashfit.core.util.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for GPS fix quality, jitter filtering, and first-fix handling.
 *
 * These drive [FixFilter], the class the tracking service delegates its gating to, so the rules
 * are exercised as shipped rather than re-typed into the test.
 */
class GpsFixQualityTest {

    private val clock = FakeClock()

    /** A fix from a receiver that has locked: tight accuracy circle, Doppler speed present. */
    private fun fix(lat: Double, lon: Double, speedMps: Float, altM: Double? = null, accuracyM: Float = 8f) =
        Fix(
            tMs = clock.nowMs(),
            lat = lat,
            lon = lon,
            altM = altM,
            accuracyM = accuracyM,
            reportedSpeedMps = speedMps,
        )

    private fun accepted(result: FixResult, message: String) =
        assertNotNull(result as? FixResult.Accepted, "$message (got $result)")

    private fun rejected(result: FixResult, message: String) =
        assertNotNull(result as? FixResult.Rejected, "$message (got $result)")

    @Test
    fun jitterAtRest_standingStill_distanceRemainsNear0() {
        // Standing at (0°, 0°) with ±5m GPS noise for 20 seconds at 1 Hz.
        // Expected: the run total does not move, because the receiver reports no speed. A speed
        // derived from two jittering positions always looks fast, so it cannot be the rest signal.
        val filter = FixFilter()
        filter.start(clock.nowMs())
        clock.advance(10_000) // past the settle window

        // Fixed deterministic jitter offsets, ≈ ±2m and ±4.5m at the equator.
        val jitterOffsets = listOf(-0.00004, -0.00002, 0.00002, 0.00004)
        var totalDistance = 0f
        var jitterRejections = 0

        for (i in 0..20) {
            clock.advance(1_000)
            val result = filter.accept(
                fix(
                    lat = jitterOffsets[i % jitterOffsets.size],
                    lon = jitterOffsets[(i + 1) % jitterOffsets.size],
                    speedMps = 0.0f, // standing still
                ),
            )
            when (result) {
                is FixResult.Accepted -> totalDistance += result.distanceM
                is FixResult.Rejected -> if (result.reason == FixResult.Reason.JITTER) jitterRejections++
            }
        }

        assertTrue(totalDistance < 20f, "Jitter caused ${totalDistance}m spurious distance (expected <20m)")
        assertEquals(0f, totalDistance, 0.01f, "Standing still should add no distance at all")
        assertTrue(jitterRejections >= 19, "Wandering fixes should be rejected as jitter, got $jitterRejections")

        // …and the filter is not simply rejecting everything: real movement still lands.
        clock.advance(5_000)
        val moved = accepted(
            filter.accept(fix(lat = 0.00018, lon = 0.0, speedMps = 2.5f)), // ≈20m north
            "A real 20m move must be accepted",
        )
        assertTrue(moved.distanceM > 15f, "Real movement should be measured, got ${moved.distanceM}m")
    }

    @Test
    fun firstFixAccuracy_setArtificiallyHighInitially_elevationBaselineSettled() {
        // Poor fixes at t=0.1s and t=5s, a good one still inside the settle window at t=5.5s,
        // then a good fix at t=12.5s. Only the last one may touch elevation, and only as a
        // baseline: a starting altitude of 100m is where the run begins, not 100m of climb.
        val filter = FixFilter()
        val elevation = ElevationTracker()
        filter.start(clock.nowMs())

        clock.advance(100)
        val early = rejected(
            filter.accept(fix(40.0, -74.0, speedMps = 0f, altM = 100.0, accuracyM = 60f)),
            "A 60m accuracy circle is not a fix",
        )
        clock.advance(4_900)
        rejected(
            filter.accept(fix(40.0, -74.0, speedMps = 0f, altM = 105.0, accuracyM = 40f)),
            "A 40m accuracy circle is not a fix either",
        )
        clock.advance(500)
        val goodButEarly = rejected(
            filter.accept(fix(40.0, -74.0, speedMps = 0f, altM = 130.0, accuracyM = 8f)),
            "Even a tight fix inside the settle window is not trusted",
        )

        assertEquals(FixResult.Reason.ACCURACY, early.reason)
        assertEquals(FixResult.Reason.SETTLING, goodButEarly.reason)

        clock.advance(7_000) // t = 12.5s
        val settled = accepted(
            filter.accept(fix(40.0, -74.0, speedMps = 0f, altM = 100.0, accuracyM = 8f)),
            "The first settled fix should be accepted",
        )
        val altM = assertNotNull(settled.fix.altM, "The settled fix carries an altitude")
        val elevationGainM = elevation.onAltitude(altM)

        assertEquals(0f, elevationGainM, 0.1f, "Elevation should not include pre-settlement fixes")
    }

    @Test
    fun impossibleSpeedJump_1kmIn1sec_rejected() {
        // (0°, 0°) then (0.009°, 0°) one second later: ≈1km, so ≈1000 m/s. A receiver glitch.
        val filter = FixFilter()
        filter.start(clock.nowMs())
        clock.advance(10_000)

        accepted(filter.accept(fix(0.0, 0.0, speedMps = 3.0f)), "The first settled fix anchors the run")

        clock.advance(1_000)
        val jump = rejected(filter.accept(fix(0.009, 0.0, speedMps = 3.0f)), "Impossible speed jump should be rejected")

        assertEquals(FixResult.Reason.SPEED_JUMP, jump.reason, "…and for that reason")
        assertTrue(filter.anchor?.lat == 0.0, "A rejected fix must not become the anchor")
    }

    @Test
    fun duplicateTimestamps_twoPointsAtSameMs_oneIgnored() {
        // Two fixes carrying the same millisecond, then one a second later.
        // The repeat is dropped rather than dividing a distance by a zero interval.
        val filter = FixFilter()
        filter.start(clock.nowMs())
        clock.advance(10_000)

        accepted(filter.accept(fix(0.0, 0.0, speedMps = 3.0f)), "anchor")
        val sameMs = rejected(
            filter.accept(fix(0.00003, 0.0, speedMps = 3.0f)),
            "Duplicate timestamps should be ignored",
        )
        assertEquals(FixResult.Reason.STALE_TIMESTAMP, sameMs.reason)

        clock.advance(1_000)
        val later = accepted(
            filter.accept(fix(0.00003, 0.0, speedMps = 3.0f)),
            "A fix with a newer timestamp is fine",
        )
        assertTrue(later.deltaMs > 0, "Accepted fixes must span a non-zero interval")
    }
}
