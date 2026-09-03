package com.clashfit.run

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius in metres. */
private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Great-circle distance between two WGS84 points, in metres.
 *
 * Coordinates are validated rather than trusted: a malformed fix must fail loudly instead of
 * poisoning the run total with a NaN that then propagates into every derived statistic.
 */
fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    // NaN and the infinities all fall outside these ranges, so one check covers them.
    require(lat1 in -90.0..90.0) { "Invalid lat1: $lat1" }
    require(lon1 in -180.0..180.0) { "Invalid lon1: $lon1" }
    require(lat2 in -90.0..90.0) { "Invalid lat2: $lat2" }
    require(lon2 in -180.0..180.0) { "Invalid lon2: $lon2" }

    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (EARTH_RADIUS_M * c).toFloat()
}

/** True when a pair of coordinates is inside the WGS84 domain and free of NaN. */
fun isValidCoordinate(lat: Double, lon: Double): Boolean =
    lat in -90.0..90.0 && lon in -180.0..180.0
