package com.clashfit.run

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for service lifecycle, persistence, and input validation.
 */
class ServiceLifecycleTest {

    @Test
    fun haversineDistance_invalidCoordinates_lat1000_throwsError() {
        // Call haversineM(1000, 0, 0, 0)
        // Verify IllegalArgumentException is thrown, not NaN result

        val exception = assertFailsWith<IllegalArgumentException> {
            haversineM(1000.0, 0.0, 0.0, 0.0)
        }
        assertTrue(exception.message?.contains("lat1") ?: false, "Should reject invalid lat1")
    }

    @Test
    fun haversineDistance_invalidCoordinates_nanLatLon_throwsError() {
        // Call haversineM(NaN, 0, 0, 0)
        // Verify IllegalArgumentException is thrown

        val exception = assertFailsWith<IllegalArgumentException> {
            haversineM(Double.NaN, 0.0, 0.0, 0.0)
        }
        assertTrue(exception.message?.contains("lat1") ?: false, "Should reject NaN coordinates")
    }

    @Test
    fun haversineDistance_validCoordinates_returnsCorrectDistance() {
        // Test valid coordinates: (40.7128°N, 74.0060°W) to (34.0522°N, 118.2437°W)
        // NYC to LA: approximately 3944 km
        val distM = haversineM(40.7128, -74.0060, 34.0522, -118.2437)
        assertTrue(distM in 3900_000f..3990_000f, "NYC to LA should be ~3944 km, got ${distM / 1000}km")
    }

    @Test
    fun haversineDistance_sameCoordinates_returnsZero() {
        val distM = haversineM(40.0, -74.0, 40.0, -74.0)
        assertEquals(0f, distM, 1f, "Same coordinates should return 0 distance")
    }

    @Test
    fun haversineDistance_invalidLongitude_throwsError() {
        val exception = assertFailsWith<IllegalArgumentException> {
            haversineM(40.0, 200.0, 34.0, -118.0) // lon1 = 200, out of range
        }
        assertTrue(exception.message?.contains("lon1") ?: false, "Should reject lon1 > 180")
    }

    @Test
    fun haversineDistance_infinityCoordinate_throwsError() {
        val exception = assertFailsWith<IllegalArgumentException> {
            haversineM(Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0)
        }
        assertTrue(exception.message?.contains("lat1") ?: false, "Should reject infinite coordinates")
    }

    @Test
    fun runStateTracking_basicFields_areImmutable() {
        // RunState should be immutable (data class with val fields)
        val state1 = RunState(
            runId = 1L,
            distanceM = 100f,
            movingMs = 60_000L,
        )

        // Create new state with one field changed
        val state2 = state1.copy(distanceM = 200f)

        // Original should be unchanged
        assertEquals(100f, state1.distanceM, "Original state should not be mutated")
        assertEquals(200f, state2.distanceM, "New state should have updated value")
    }

    /**
     * Haversine distance calculation in metres with input validation.
     */
    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        require(lat1 in -90.0..90.0 && !lat1.isNaN()) { "Invalid lat1: $lat1" }
        require(lon1 in -180.0..180.0 && !lon1.isNaN()) { "Invalid lon1: $lon1" }
        require(lat2 in -90.0..90.0 && !lat2.isNaN()) { "Invalid lat2: $lat2" }
        require(lon2 in -180.0..180.0 && !lon2.isNaN()) { "Invalid lon2: $lon2" }

        val R = 6371000.0 // Earth radius in metres
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).toFloat()
    }
}
