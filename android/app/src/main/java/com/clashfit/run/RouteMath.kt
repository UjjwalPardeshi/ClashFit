package com.clashfit.run

import com.clashfit.data.RunPointEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Route mathematics: projection, simplification, pace banding and personal records.
 *
 * Everything here is pure and Android-free, so the map, the list thumbnail and the shareable
 * image all draw the same route from the same numbers, and every one of those numbers can be
 * checked on the JVM. Nothing in this file touches a tile server or a Canvas.
 */

/** Metres per degree of latitude. Constant enough for a run; the error is centimetres. */
private const val M_PER_DEG_LAT = 111_320.0

/** A point projected into a local flat frame, in metres east and north of an origin. */
data class LocalPoint(val eastM: Float, val northM: Float)

/**
 * Projects a WGS84 coordinate into metres east and north of an origin.
 *
 * Equirectangular, which is wrong for a continent and exact enough for a run: over the few
 * kilometres a route covers, the error against a proper projection is well under a metre, and the
 * alternative is trigonometry nobody can check by eye. The longitude scale is taken at the
 * *origin's* latitude rather than each point's, so the frame stays linear — using each point's own
 * latitude would bend straight roads.
 */
fun metresEastNorth(originLat: Double, originLon: Double, lat: Double, lon: Double): LocalPoint {
    val mPerDegLon = M_PER_DEG_LAT * cos(originLat * PI / 180.0)
    return LocalPoint(
        eastM = ((lon - originLon) * mPerDegLon).toFloat(),
        northM = ((lat - originLat) * M_PER_DEG_LAT).toFloat(),
    )
}

/** The rectangle a route occupies, plus the centre a map should open on. */
data class RouteBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    val centreLat: Double get() = (minLat + maxLat) / 2
    val centreLon: Double get() = (minLon + maxLon) / 2

    /** The longer side of the route's box, in metres. Drives the opening zoom. */
    val spanM: Float
        get() {
            val north = haversineM(minLat, centreLon, maxLat, centreLon)
            val east = haversineM(centreLat, minLon, centreLat, maxLon)
            return max(north, east)
        }

    companion object {
        /** Null for an empty route: there is no box around no points, and no map to centre. */
        fun of(points: List<RunPointEntity>): RouteBounds? {
            if (points.isEmpty()) return null
            return RouteBounds(
                minLat = points.minOf { it.lat },
                maxLat = points.maxOf { it.lat },
                minLon = points.minOf { it.lon },
                maxLon = points.maxOf { it.lon },
            )
        }
    }
}

/**
 * Ramer-Douglas-Peucker simplification, in the local metric frame.
 *
 * An hour's run at one fix a second is three and a half thousand points. Drawing every one of them
 * into a 120 dp list thumbnail costs a lot to say nothing: at that size most of them land on the
 * same pixel. [toleranceM] is the greatest distance a dropped point may sit from the line that
 * replaces it, so the shape is preserved to a stated accuracy rather than by luck.
 *
 * The first and last points are always kept. A route that has lost its start and finish is a
 * different route.
 */
fun simplifyRoute(points: List<RunPointEntity>, toleranceM: Float): List<RunPointEntity> {
    if (points.size <= 2 || toleranceM <= 0f) return points
    val origin = points.first()
    val local = points.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    rdp(local, 0, points.size - 1, toleranceM, keep)
    return points.filterIndexed { i, _ -> keep[i] }
}

private fun rdp(pts: List<LocalPoint>, first: Int, last: Int, toleranceM: Float, keep: BooleanArray) {
    if (last <= first + 1) return
    var worst = first
    var worstDist = 0f
    for (i in first + 1 until last) {
        val d = perpendicularDistanceM(pts[i], pts[first], pts[last])
        if (d > worstDist) {
            worstDist = d
            worst = i
        }
    }
    if (worstDist <= toleranceM) return
    keep[worst] = true
    rdp(pts, first, worst, toleranceM, keep)
    rdp(pts, worst, last, toleranceM, keep)
}

/** Distance from [p] to the segment [a]-[b], in metres. Degenerate segments fall back to a point. */
internal fun perpendicularDistanceM(p: LocalPoint, a: LocalPoint, b: LocalPoint): Float {
    val dx = b.eastM - a.eastM
    val dy = b.northM - a.northM
    if (dx == 0f && dy == 0f) return hypot(p.eastM - a.eastM, p.northM - a.northM)
    val t = (((p.eastM - a.eastM) * dx + (p.northM - a.northM) * dy) / (dx * dx + dy * dy))
        .coerceIn(0f, 1f)
    return hypot(p.eastM - (a.eastM + t * dx), p.northM - (a.northM + t * dy))
}

/**
 * Which of five pace bands a speed falls in, fastest last.
 *
 * Bands are relative to the run's own median speed rather than absolute, because an absolute scale
 * paints a walker's whole route the same colour and says nothing. Relative banding shows where
 * *this* runner pushed and where they eased, which is the only thing a colour on a line can
 * usefully mean.
 */
fun paceZone(speedMps: Float, medianSpeedMps: Float): Int {
    if (medianSpeedMps <= 0f) return 2
    return when (val ratio = speedMps / medianSpeedMps) {
        in 0f..0.75f -> 0
        in 0.75f..0.92f -> 1
        in 0.92f..1.08f -> 2
        in 1.08f..1.25f -> 3
        else -> if (ratio.isNaN()) 2 else 4
    }
}

/** The median moving speed of a route, used as the reference for [paceZone]. */
fun medianSpeedMps(points: List<RunPointEntity>): Float {
    val moving = points.map { it.speedMps }.filter { it > 0.3f }.sorted()
    if (moving.isEmpty()) return 0f
    return moving[moving.size / 2]
}

/**
 * Altitude resampled to [buckets] evenly spaced along the route's distance.
 *
 * Sampled by distance rather than by index so a stretch where the runner stopped does not stretch
 * out across the chart. Returns an empty list when there is nothing to draw.
 */
fun elevationProfile(points: List<RunPointEntity>, buckets: Int = 60): List<Float> {
    if (points.size < 2 || buckets < 2) return emptyList()
    val cumulative = cumulativeDistanceM(points)
    val total = cumulative.last()
    if (total <= 0f) return emptyList()
    val out = ArrayList<Float>(buckets)
    var cursor = 0
    for (b in 0 until buckets) {
        val target = total * b / (buckets - 1f)
        while (cursor < cumulative.size - 1 && cumulative[cursor + 1] < target) cursor++
        out += points[cursor].altM.toFloat()
    }
    return out
}

/**
 * Whether an activity recorded so little that keeping it would be noise.
 *
 * A tap on Start followed by a tap on Finish is not a run, but it used to become one: a row in the
 * feed reading "0.00 km · 0:00", counted in "2 activities", dragging the month's average down and
 * making a working tracker look broken. Both conditions must hold, so a genuine slow first minute
 * is never thrown away — only the activity where nothing at all happened.
 */
fun isTooShortToKeep(distanceM: Float, movingMs: Long): Boolean =
    distanceM < MIN_KEEPABLE_M && movingMs < MIN_KEEPABLE_MOVING_MS

/** Ten metres: shorter than the width of the room this was written in. */
const val MIN_KEEPABLE_M = 10f

/** Ten seconds of actual movement. Below this there is no pace worth computing. */
const val MIN_KEEPABLE_MOVING_MS = 10_000L

/**
 * A distance as a person would say it out loud.
 *
 * Under a kilometre it is said in metres. Thirty-five metres of walking a lab is a real activity,
 * and rendering it as "0.00 km" made a working tracker look broken on its own summary screen. From
 * a kilometre up it is said in kilometres to two places, the unit every running app agrees on.
 */
fun formatDistance(metres: Float): String =
    if (metres < 1000f) "%.0f m".format(metres) else "%.2f km".format(metres / 1000f)

/**
 * The same distance split into the number and its unit, for the places that stack one above the
 * other — a progress ring, a stat tile — and so cannot use a single string.
 */
fun distanceParts(metres: Float): Pair<String, String> =
    if (metres < 1000f) "%.0f".format(metres) to "m" else "%.2f".format(metres / 1000f) to "km"

/**
 * The same profile scaled into the nought-to-one band a trend chart draws in.
 *
 * [elevationProfile] returns metres above sea level, which for Pune is around five hundred and
 * fifty. A chart that clamps its input to nought-to-one would pin every one of those to the top of
 * the canvas and draw a dead flat line, which is exactly what it did until this existed. The
 * scaling has to happen before the numbers reach a canvas, and it belongs here where it can be
 * tested rather than inside a composable.
 *
 * A route whose highest and lowest points are within [flatBandM] of each other has no shape worth
 * stretching. Blowing a metre of satellite noise up to full height would draw a mountain range
 * across a flat lap of a car park, so a profile that flat is drawn as a line down the middle.
 */
fun normaliseProfile(profile: List<Float>, flatBandM: Float = 2f): List<Float> {
    if (profile.isEmpty()) return emptyList()
    val lo = profile.min()
    val hi = profile.max()
    val span = hi - lo
    if (span < flatBandM) return List(profile.size) { 0.5f }
    return profile.map { ((it - lo) / span).coerceIn(0f, 1f) }
}

/** Running total of distance along a route, one entry per point, starting at zero. */
fun cumulativeDistanceM(points: List<RunPointEntity>): FloatArray {
    val out = FloatArray(points.size)
    for (i in 1 until points.size) {
        val d = haversineM(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        out[i] = out[i - 1] + d
    }
    return out
}

/**
 * The personal records one activity can set. Strava's most habit-forming idea, and the cheapest
 * to compute honestly: every one of these comes from points already on the phone.
 */
data class RunRecords(
    /** Quickest continuous kilometre anywhere in the run, in seconds. Null under a kilometre. */
    val fastestKmSec: Float?,
    /** Longest continuous stretch above the moving threshold, in metres. */
    val longestSurgeM: Float,
    /** Total distance, in metres. Repeated here so a record card needs one object. */
    val distanceM: Float,
    /** Total ascent, in metres. */
    val climbM: Float,
)

/**
 * The fastest continuous kilometre in a run, in seconds.
 *
 * A sliding window over cumulative distance, not a per-split minimum. The distinction matters: a
 * runner whose quickest kilometre straddles the 3 km marker has a fast kilometre that the split
 * table cannot see, and reporting the best *split* as the best *kilometre* would be quietly wrong
 * every time that happened.
 *
 * The window is interpolated at both ends so the answer is a kilometre rather than "a kilometre
 * and a bit, whatever the fix spacing happened to give us".
 */
fun fastestKmSec(points: List<RunPointEntity>): Float? {
    if (points.size < 2) return null
    val cumulative = cumulativeDistanceM(points)
    if (cumulative.last() < 1000f) return null

    var best = Float.MAX_VALUE
    var start = 0
    for (end in 1 until points.size) {
        while (start < end && cumulative[end] - cumulative[start] >= 1000f) {
            // Interpolate the entry point so the window measures exactly one kilometre.
            val overshoot = (cumulative[end] - cumulative[start]) - 1000f
            val legM = cumulative[start + 1] - cumulative[start]
            val legMs = (points[start + 1].tMs - points[start].tMs).toFloat()
            val fraction = if (legM > 0f) (overshoot / legM).coerceIn(0f, 1f) else 0f
            val elapsedMs = (points[end].tMs - points[start].tMs) - legMs * fraction
            if (elapsedMs > 0f) best = min(best, elapsedMs / 1000f)
            start++
        }
    }
    return if (best == Float.MAX_VALUE) null else best
}

/** The longest unbroken stretch spent above the moving threshold, in metres. */
fun longestSurgeM(points: List<RunPointEntity>, movingSpeedMps: Float = MovingTimeTracker.MOVING_SPEED_MPS): Float {
    var best = 0f
    var run = 0f
    for (i in 1 until points.size) {
        if (points[i].speedMps >= movingSpeedMps) {
            run += haversineM(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
            best = max(best, run)
        } else {
            run = 0f
        }
    }
    return best
}

/** Every record for one activity, from its stored points. */
fun recordsFor(points: List<RunPointEntity>, climbM: Float): RunRecords {
    val cumulative = cumulativeDistanceM(points)
    return RunRecords(
        fastestKmSec = fastestKmSec(points),
        longestSurgeM = longestSurgeM(points),
        distanceM = if (cumulative.isEmpty()) 0f else cumulative.last(),
        climbM = climbM,
    )
}

/**
 * Which records in [now] beat everything in [previous]. Returned as display labels.
 *
 * Ties do not count. "Equalled your best" is a fine thing to feel and a bad thing to award, and a
 * float comparison that treats them as a win fires on rounding noise.
 */
fun newRecordLabels(now: RunRecords, previous: List<RunRecords>): List<String> {
    val out = mutableListOf<String>()
    if (previous.isEmpty()) return out

    val bestKm = previous.mapNotNull { it.fastestKmSec }.minOrNull()
    val nowKm = now.fastestKmSec
    if (nowKm != null && (bestKm == null || nowKm < bestKm - 0.5f)) out += "Fastest kilometre"

    val bestDistance = previous.maxOf { it.distanceM }
    if (now.distanceM > bestDistance + 1f) out += "Longest activity"

    val bestClimb = previous.maxOf { it.climbM }
    if (now.climbM > bestClimb + 1f && now.climbM > 0f) out += "Biggest climb"

    return out
}

/** Formats a pace in seconds per kilometre as m:ss, the way every run app writes it. */
fun formatPaceSecPerKm(secPerKm: Float): String {
    if (secPerKm <= 0f || secPerKm.isNaN() || secPerKm.isInfinite()) return "—"
    val total = secPerKm.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** Average pace in seconds per kilometre from a distance and a moving time. */
fun paceSecPerKm(distanceM: Float, movingMs: Long): Float {
    if (distanceM <= 0f || movingMs <= 0L) return 0f
    return (movingMs / 1000f) / (distanceM / 1000f)
}

/** True when two coordinates are close enough to be treated as the same place. */
internal fun sameSpot(a: RunPointEntity, b: RunPointEntity, toleranceM: Float = 1f): Boolean =
    abs(haversineM(a.lat, a.lon, b.lat, b.lon)) < toleranceM
