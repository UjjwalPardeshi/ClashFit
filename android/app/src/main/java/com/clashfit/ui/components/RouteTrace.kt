package com.clashfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.clashfit.data.RunPointEntity
import com.clashfit.map.MapMarker
import com.clashfit.run.LocalPoint
import com.clashfit.run.medianSpeedMps
import com.clashfit.run.metresEastNorth
import com.clashfit.run.paceZone
import com.clashfit.run.simplifyRoute
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The route, drawn on the app's own grid with no tiles and no network.
 *
 * This is both a fallback and a first-class view. It is what shows before the player has said yes
 * to map tiles, what shows in a list thumbnail where streets would be illegible anyway, and what
 * shows on the shareable image when somebody would rather not publish their street layout. The
 * map in `com.clashfit.map` draws exactly the same polyline over exactly the same projection, so
 * the two never disagree about the shape of a run.
 *
 * @param paceColoured colours each segment by how it compares with the run's own median speed.
 * @param showScale draws a scale bar, which is the only thing that makes a gridless route legible.
 */
@Composable
fun RouteTrace(
    points: List<RunPointEntity>,
    modifier: Modifier = Modifier,
    paceColoured: Boolean = false,
    showScale: Boolean = true,
    showGrid: Boolean = true,
    strokeWidth: Float = 6f,
    background: Color = PanelLift,
    /**
     * Things standing on the route: pursuers and caches.
     *
     * They are drawn here, by the same painter the street map uses, because a chase whose zombies
     * are only visible when map tiles are switched on is a chase most players never see. They also
     * widen the frame: a pursuer outside the route's own box still has to be on screen, or the
     * player is being chased by something they cannot look at.
     */
    markers: List<MapMarker> = emptyList(),
    /** A 0..1 phase, advanced by the caller, that makes the whole pack breathe together. */
    markerPulse: Float = 0f,
) {
    Canvas(modifier) {
        drawRect(background)
        if (showGrid) drawGrid()
        if (points.size < 2) return@Canvas

        // Simplify to the size actually being drawn: the tolerance is a fraction of the span, so a
        // 120 dp thumbnail sheds far more points than a full-screen summary and both stay honest.
        val projected = project(points, size, strokeWidth, markers)
        if (projected == null) return@Canvas

        val median = if (paceColoured) medianSpeedMps(projected.simplified) else 0f
        for (i in 1 until projected.screen.size) {
            val colour = if (paceColoured) {
                paceColour(paceZone(projected.simplified[i].speedMps, median))
            } else {
                Ember
            }
            drawLine(
                color = colour,
                start = projected.screen[i - 1],
                end = projected.screen[i],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // Start and finish. Hollow for the start, solid for the finish: on a loop they land on top
        // of each other, and two identical dots there would say nothing about which was which.
        val start = projected.screen.first()
        val finish = projected.screen.last()
        drawCircle(background, radius = strokeWidth * 1.9f, center = start)
        drawCircle(Success, radius = strokeWidth * 1.5f, center = start, style = Stroke(width = strokeWidth * 0.8f))
        drawCircle(background, radius = strokeWidth * 1.9f, center = finish)
        drawCircle(Ink, radius = strokeWidth * 1.4f, center = finish)

        if (showScale) drawScaleBar(projected.metresPerPixel)

        if (markers.isNotEmpty()) {
            val mPerPx = projected.metresPerPixel
            drawIntoCanvas { canvas ->
                markers.forEach { m ->
                    val at = projected.toScreen(m.lat, m.lon)
                    val radiusPx = if (mPerPx > 0f) (m.radiusM / mPerPx).toFloat() else 20f
                    when (m.glyph) {
                        MapMarker.Glyph.ZOMBIE ->
                            ChaseGlyphs.zombie(canvas.nativeCanvas, at.x, at.y, radiusPx, m.closeness, markerPulse)
                        MapMarker.Glyph.CIRCLE -> drawCircle(
                            color = m.outline,
                            radius = radiusPx.coerceAtLeast(6f),
                            center = at,
                            style = Stroke(width = strokeWidth * 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/** A faint grid, so a route without streets still has something to be a shape against. */
private fun DrawScope.drawGrid(step: Float = 42f) {
    var x = step
    while (x < size.width) {
        drawLine(RuleSoft, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(RuleSoft, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/**
 * A scale bar of a round number of metres.
 *
 * Without it the drawing has no size at all: the same picture serves a lap of a car park and a
 * half marathon, and a viewer has no way to tell which they are looking at.
 */
private fun DrawScope.drawScaleBar(metresPerPixel: Float) {
    if (metresPerPixel <= 0f || metresPerPixel.isNaN()) return
    val targetPx = size.width * 0.28f
    val rawM = targetPx * metresPerPixel
    val niceM = niceRoundMetres(rawM)
    val barPx = niceM / metresPerPixel
    if (barPx < 16f || barPx > size.width * 0.8f) return

    val y = size.height - 14f
    val x0 = 14f
    val x1 = x0 + barPx
    drawLine(InkFaint, Offset(x0, y), Offset(x1, y), strokeWidth = 2f)
    drawLine(InkFaint, Offset(x0, y - 4f), Offset(x0, y + 4f), strokeWidth = 2f)
    drawLine(InkFaint, Offset(x1, y - 4f), Offset(x1, y + 4f), strokeWidth = 2f)
}

/** 1, 2 or 5 times a power of ten — the numbers a person reads off a scale bar without thinking. */
internal fun niceRoundMetres(raw: Float): Float {
    if (raw <= 0f) return 1f
    val magnitude = 10f.pow(kotlin.math.floor(kotlin.math.log10(raw.toDouble())).toFloat())
    val normalised = raw / magnitude
    val step = when {
        normalised < 1.5f -> 1f
        normalised < 3.5f -> 2f
        normalised < 7.5f -> 5f
        else -> 10f
    }
    return step * magnitude
}

/** Five bands, slow to quick, reusing the fatigue ramp so one colour means one thing app-wide. */
internal fun paceColour(zone: Int): Color = when (zone) {
    0 -> Gassed
    1 -> Heavy
    2 -> Working
    3 -> Fresh
    else -> Success
}

private class Projected(
    val screen: List<Offset>,
    val simplified: List<RunPointEntity>,
    val metresPerPixel: Float,
    /** Puts any coordinate into the same frame the route was drawn in. */
    val toScreen: (Double, Double) -> Offset,
)

/**
 * Projects a route into the drawing area, preserving aspect ratio.
 *
 * The scale is the same on both axes on purpose. Stretching to fill would make every run look
 * like a rectangle and would quietly lie about the shape of the route, which is the only thing
 * this picture is for.
 */
private fun project(
    points: List<RunPointEntity>,
    size: Size,
    strokeWidth: Float,
    markers: List<MapMarker> = emptyList(),
): Projected? {
    val origin = points.first()
    val localAll = points.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) } +
        markers.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
    val spanM = max(
        localAll.maxOf { it.eastM } - localAll.minOf { it.eastM },
        localAll.maxOf { it.northM } - localAll.minOf { it.northM },
    )
    if (spanM <= 0f) return null

    // Half a pixel of tolerance at the size being drawn: invisible, and it sheds most of the points.
    val drawSpanPx = max(1f, min(size.width, size.height))
    val simplified = simplifyRoute(points, toleranceM = spanM / drawSpanPx * 0.5f)
    if (simplified.size < 2) return null

    val local = simplified.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
    // The frame has to hold the markers too, or a zombie thirty metres off the route is chasing
    // the player from outside the picture.
    val framed = local + markers.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
    val minE = framed.minOf { it.eastM }
    val maxE = framed.maxOf { it.eastM }
    val minN = framed.minOf { it.northM }
    val maxN = framed.maxOf { it.northM }

    val pad = strokeWidth * 2.5f + 8f
    val usableW = max(1f, size.width - pad * 2)
    val usableH = max(1f, size.height - pad * 2)
    val scale = min(
        if (maxE - minE > 0f) usableW / (maxE - minE) else Float.MAX_VALUE,
        if (maxN - minN > 0f) usableH / (maxN - minN) else Float.MAX_VALUE,
    ).let { if (it == Float.MAX_VALUE) 1f else it }

    // Centre whatever is left over, so a north-south run is not glued to the left edge.
    val offsetX = pad + (usableW - (maxE - minE) * scale) / 2f
    val offsetY = pad + (usableH - (maxN - minN) * scale) / 2f

    fun toScreen(p: LocalPoint) = Offset(
        x = offsetX + (p.eastM - minE) * scale,
        // North is up on a map and down in screen coordinates. Every route drawn without this
        // flip is a correct shape upside down, which nobody notices until they compare it.
        y = offsetY + (maxN - p.northM) * scale,
    )

    return Projected(
        screen = local.map(::toScreen),
        simplified = simplified,
        metresPerPixel = if (scale > 0f) 1f / scale else 0f,
        toScreen = { lat, lon -> toScreen(metresEastNorth(origin.lat, origin.lon, lat, lon)) },
    )
}

/** How many metres a route covers end to end, for a caption. */
fun routeSpanLabel(points: List<RunPointEntity>): String {
    if (points.size < 2) return ""
    val origin = points.first()
    val local = points.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
    val span = max(
        local.maxOf { it.eastM } - local.minOf { it.eastM },
        local.maxOf { it.northM } - local.minOf { it.northM },
    )
    return if (span >= 1000f) "%.1f km across".format(span / 1000f) else "${span.roundToInt()} m across"
}
