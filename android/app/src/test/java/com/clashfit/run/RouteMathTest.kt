package com.clashfit.run

import com.clashfit.data.RunPointEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The route mathematics the map, the list thumbnail and the shareable image all share.
 *
 * A wrong number here is wrong in three places at once and looks plausible in all of them, which
 * is exactly the sort of thing that survives to a demo.
 */
class RouteMathTest {

    /** A straight eastward path at a steady speed, one fix a second. */
    private fun straightPath(
        n: Int,
        metresPerStep: Double = 5.0,
        speedMps: Float = 5f,
        startLat: Double = 18.5204,
        startLon: Double = 73.8567, // Pune, because that is where this is being run
        altM: Double = 560.0,
    ): List<RunPointEntity> {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(startLat * Math.PI / 180.0)
        return (0 until n).map { i ->
            RunPointEntity(
                id = i.toLong(), runId = 1L, tMs = i * 1000L,
                lat = startLat, lon = startLon + i * metresPerStep / mPerDegLon,
                altM = altM, accuracyM = 5f, speedMps = speedMps,
            )
        }
    }

    // ── projection ──────────────────────────────────────────────────────────

    @Test
    fun `the origin projects to the origin`() {
        val p = metresEastNorth(18.52, 73.85, 18.52, 73.85)
        assertEquals(0f, p.eastM, 0.001f)
        assertEquals(0f, p.northM, 0.001f)
    }

    @Test
    fun `a degree of latitude is about 111 kilometres`() {
        val p = metresEastNorth(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_320f, p.northM, 1f)
    }

    @Test
    fun `longitude shrinks with latitude`() {
        val atEquator = metresEastNorth(0.0, 0.0, 0.0, 1.0).eastM
        val atPune = metresEastNorth(18.52, 73.85, 18.52, 74.85).eastM
        assertTrue(atPune < atEquator, "a degree of longitude did not shrink away from the equator")
        // cos(18.52°) ≈ 0.948
        assertEquals(atEquator * 0.948f, atPune, 200f)
    }

    @Test
    fun `projection agrees with haversine over a short run`() {
        val pts = straightPath(3, metresPerStep = 100.0)
        val local = metresEastNorth(pts[0].lat, pts[0].lon, pts[2].lat, pts[2].lon)
        val greatCircle = haversineM(pts[0].lat, pts[0].lon, pts[2].lat, pts[2].lon)
        assertEquals(greatCircle, abs(local.eastM), 0.5f)
    }

    // ── bounds ──────────────────────────────────────────────────────────────

    @Test
    fun `bounds of an empty route are null rather than a point at zero`() {
        assertNull(RouteBounds.of(emptyList()))
    }

    @Test
    fun `bounds centre on the middle of the route`() {
        val b = RouteBounds.of(straightPath(11, metresPerStep = 10.0))!!
        assertEquals(18.5204, b.centreLat, 1e-9)
        assertTrue(b.spanM in 90f..110f, "span was ${b.spanM}m for a 100 m path")
    }

    // ── simplification ──────────────────────────────────────────────────────

    @Test
    fun `simplifying a straight line keeps only its ends`() {
        val simplified = simplifyRoute(straightPath(50), toleranceM = 1f)
        assertEquals(2, simplified.size)
    }

    @Test
    fun `simplification always keeps the first and last point`() {
        val pts = straightPath(50)
        val simplified = simplifyRoute(pts, toleranceM = 1f)
        assertEquals(pts.first().id, simplified.first().id)
        assertEquals(pts.last().id, simplified.last().id)
    }

    @Test
    fun `simplification preserves a corner`() {
        // East for 100 m, then north for 100 m. The corner must survive any sane tolerance.
        val mPerDegLon = 111_320.0 * kotlin.math.cos(18.52 * Math.PI / 180.0)
        val pts = buildList {
            for (i in 0..10) add(
                RunPointEntity(id = i.toLong(), runId = 1, tMs = i * 1000L,
                    lat = 18.52, lon = 73.85 + i * 10.0 / mPerDegLon,
                    altM = 560.0, accuracyM = 5f, speedMps = 3f),
            )
            for (i in 1..10) add(
                RunPointEntity(id = (10 + i).toLong(), runId = 1, tMs = (10 + i) * 1000L,
                    lat = 18.52 + i * 10.0 / 111_320.0, lon = 73.85 + 100.0 / mPerDegLon,
                    altM = 560.0, accuracyM = 5f, speedMps = 3f),
            )
        }
        val simplified = simplifyRoute(pts, toleranceM = 2f)
        assertEquals(3, simplified.size, "the corner was flattened or over-kept")
    }

    @Test
    fun `a two point route is returned untouched`() {
        val pts = straightPath(2)
        assertEquals(pts, simplifyRoute(pts, toleranceM = 5f))
    }

    @Test
    fun `perpendicular distance handles a degenerate segment`() {
        val a = LocalPoint(0f, 0f)
        val d = perpendicularDistanceM(LocalPoint(3f, 4f), a, a)
        assertEquals(5f, d, 0.001f)
    }

    // ── pace zones ──────────────────────────────────────────────────────────

    @Test
    fun `pace zones band around the median`() {
        assertEquals(2, paceZone(speedMps = 3f, medianSpeedMps = 3f))
        assertEquals(0, paceZone(speedMps = 1f, medianSpeedMps = 3f))
        assertEquals(4, paceZone(speedMps = 6f, medianSpeedMps = 3f))
    }

    @Test
    fun `pace zone survives a zero median instead of dividing by it`() {
        assertEquals(2, paceZone(speedMps = 4f, medianSpeedMps = 0f))
    }

    @Test
    fun `median speed ignores the standing-still samples`() {
        val pts = straightPath(5, speedMps = 4f) + straightPath(5, speedMps = 0f)
        assertEquals(4f, medianSpeedMps(pts), 0.01f)
    }

    // ── elevation ───────────────────────────────────────────────────────────

    @Test
    fun `elevation profile has the requested number of buckets`() {
        val profile = elevationProfile(straightPath(100, metresPerStep = 10.0), buckets = 40)
        assertEquals(40, profile.size)
    }

    @Test
    fun `elevation profile of a route that never moved is empty`() {
        val stuck = straightPath(10, metresPerStep = 0.0)
        assertTrue(elevationProfile(stuck).isEmpty())
    }

    @Test
    fun `a profile in metres above sea level is scaled into the band a chart can draw`() {
        // The bug this pins: the profile is hundreds of metres above sea level, the chart clamps
        // to nought-to-one, so every real altitude used to land on the top edge as a flat line.
        val climbed = normaliseProfile(listOf(560f, 570f, 580f, 590f, 600f))
        assertEquals(0f, climbed.first(), 0.001f)
        assertEquals(1f, climbed.last(), 0.001f)
        assertEquals(0.5f, climbed[2], 0.001f)
        assertTrue(climbed.all { it in 0f..1f }, "nothing may sit outside the band")
    }

    @Test
    fun `a route flat to within a couple of metres is drawn down the middle, not blown up`() {
        // Satellite altitude wanders by a metre or so standing still. Stretching that to full
        // height would draw a mountain range across a flat lap of a car park.
        val noise = normaliseProfile(listOf(560.0f, 560.7f, 559.9f, 560.4f, 560.1f))
        assertTrue(noise.all { it == 0.5f }, "a flat route is a flat line")
    }

    @Test
    fun `an empty profile normalises to an empty profile`() {
        assertTrue(normaliseProfile(emptyList()).isEmpty())
    }

    @Test
    fun `a dead-reckoned stretch written as sea level would ruin the profile`() {
        // Why RunTrackingService carries the last real altitude forward instead of writing zero:
        // one zero among five-hundred-metre readings squashes the entire real range into nothing.
        val withHole = normaliseProfile(listOf(560f, 0f, 570f, 580f))
        assertEquals(0f, withHole[1], 0.001f, "the hole becomes the whole range")
        assertTrue(withHole[3] - withHole[0] < 0.05f, "and the real climb collapses to a hair")
    }

    // ── how a distance is said ──────────────────────────────────────────────

    @Test
    fun `a short activity is said in metres, not in two decimal places of nothing`() {
        assertEquals("35 m", formatDistance(35.1f))
        assertEquals("0 m", formatDistance(0f))
        assertEquals("999 m", formatDistance(999.4f))
    }

    @Test
    fun `a kilometre and beyond is said in kilometres`() {
        assertEquals("1.00 km", formatDistance(1000f))
        assertEquals("5.24 km", formatDistance(5243f))
    }

    @Test
    fun `the number and its unit are split the same way for a stacked layout`() {
        assertEquals("35" to "m", distanceParts(35.1f))
        assertEquals("5.24" to "km", distanceParts(5243f))
    }

    // ── what is worth keeping ───────────────────────────────────────────────

    @Test
    fun `start then finish records nothing and is not kept`() {
        assertTrue(isTooShortToKeep(distanceM = 0f, movingMs = 0L))
    }

    @Test
    fun `a slow first minute is kept, because time passed even if distance did not`() {
        assertFalse(isTooShortToKeep(distanceM = 3f, movingMs = 45_000L))
    }

    @Test
    fun `a short sprint is kept, because distance happened even if time did not`() {
        assertFalse(isTooShortToKeep(distanceM = 60f, movingMs = 8_000L))
    }

    // ── records ─────────────────────────────────────────────────────────────

    @Test
    fun `no fastest kilometre under a kilometre`() {
        assertNull(fastestKmSec(straightPath(50, metresPerStep = 5.0))) // 245 m
    }

    @Test
    fun `fastest kilometre of a steady 5 metre per second run is 200 seconds`() {
        // 300 fixes, 5 m apart, one second each: 1495 m at a constant 5 m/s.
        val km = fastestKmSec(straightPath(300, metresPerStep = 5.0, speedMps = 5f))
        assertTrue(km != null, "a 1.5 km run reported no fastest kilometre")
        assertEquals(200f, km!!, 2f)
    }

    @Test
    fun `fastest kilometre finds a surge that straddles a split boundary`() {
        // Slow for 1200 m, then quick for 1000 m, then slow again. The quick kilometre starts at
        // 1200 m, so no per-kilometre split contains it and only a sliding window can see it.
        val mPerDegLon = 111_320.0 * kotlin.math.cos(18.52 * Math.PI / 180.0)
        var t = 0L
        var metres = 0.0
        val pts = mutableListOf<RunPointEntity>()
        fun leg(distanceM: Double, speed: Double) {
            val steps = (distanceM / 5.0).toInt()
            repeat(steps) {
                metres += 5.0
                t += (5.0 / speed * 1000).toLong()
                pts += RunPointEntity(
                    id = pts.size.toLong(), runId = 1, tMs = t,
                    lat = 18.52, lon = 73.85 + metres / mPerDegLon,
                    altM = 560.0, accuracyM = 5f, speedMps = speed.toFloat(),
                )
            }
        }
        leg(1200.0, 2.5) // slow
        leg(1000.0, 5.0) // the surge: 200 s per km
        leg(500.0, 2.5)  // slow again
        val km = fastestKmSec(pts)
        assertTrue(km != null)
        assertEquals(200f, km!!, 6f)
    }

    @Test
    fun `longest surge stops at a pause and restarts after it`() {
        val fast = straightPath(21, metresPerStep = 5.0, speedMps = 4f)          // 100 m moving
        val stopped = straightPath(5, metresPerStep = 5.0, speedMps = 0f)        // a pause
        val fastAgain = straightPath(11, metresPerStep = 5.0, speedMps = 4f)     // 50 m moving
        val surge = longestSurgeM(fast + stopped + fastAgain)
        // The longest unbroken stretch is the first one, not the sum of both.
        assertTrue(surge in 90f..115f, "longest surge was ${surge}m")
    }

    @Test
    fun `records on an empty route do not throw`() {
        val r = recordsFor(emptyList(), climbM = 0f)
        assertNull(r.fastestKmSec)
        assertEquals(0f, r.distanceM)
    }

    @Test
    fun `a first activity sets no records`() {
        val now = recordsFor(straightPath(300, metresPerStep = 5.0), climbM = 40f)
        assertTrue(newRecordLabels(now, emptyList()).isEmpty())
    }

    @Test
    fun `beating the previous best distance is a record`() {
        val previous = listOf(RunRecords(fastestKmSec = 190f, longestSurgeM = 500f, distanceM = 1000f, climbM = 50f))
        val now = RunRecords(fastestKmSec = 250f, longestSurgeM = 900f, distanceM = 2000f, climbM = 10f)
        assertEquals(listOf("Longest activity"), newRecordLabels(now, previous))
    }

    @Test
    fun `equalling a record does not award it`() {
        val previous = listOf(RunRecords(fastestKmSec = 200f, longestSurgeM = 500f, distanceM = 1000f, climbM = 50f))
        val now = RunRecords(fastestKmSec = 200f, longestSurgeM = 500f, distanceM = 1000f, climbM = 50f)
        assertTrue(newRecordLabels(now, previous).isEmpty())
    }

    @Test
    fun `a quicker kilometre is a record`() {
        val previous = listOf(RunRecords(fastestKmSec = 240f, longestSurgeM = 500f, distanceM = 5000f, climbM = 90f))
        val now = RunRecords(fastestKmSec = 210f, longestSurgeM = 500f, distanceM = 1000f, climbM = 10f)
        assertEquals(listOf("Fastest kilometre"), newRecordLabels(now, previous))
    }

    // ── formatting ──────────────────────────────────────────────────────────

    @Test
    fun `pace formats as minutes and seconds`() {
        assertEquals("5:22", formatPaceSecPerKm(322f))
        assertEquals("—", formatPaceSecPerKm(0f))
        assertEquals("—", formatPaceSecPerKm(Float.NaN))
    }

    @Test
    fun `pace from distance and moving time`() {
        // 2 km in 10 minutes is 5:00 per km.
        assertEquals(300f, paceSecPerKm(2000f, 600_000L), 0.1f)
        assertEquals(0f, paceSecPerKm(0f, 600_000L))
        assertEquals(0f, paceSecPerKm(2000f, 0L))
    }
}
