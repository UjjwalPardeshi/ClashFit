package com.clashfit.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.clashfit.data.RunPointEntity
import com.clashfit.run.RouteBounds
import com.clashfit.run.medianSpeedMps
import com.clashfit.run.paceZone
import com.clashfit.run.simplifyRoute
import com.clashfit.ui.components.paceColour
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Success
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * A handle on a live map, so a screen can put its own controls over one.
 *
 * osmdroid's own zoom buttons are switched off because they are a 2012 Android widget sitting in
 * the middle of a 2026 app. The screen draws its own, and drives them through this.
 */
class RouteMapState {
    internal var view: MapView? = null

    /**
     * True once the player has touched the map.
     *
     * This is the whole reason pinch-to-zoom used to feel broken: the framing code ran on every
     * state update, so a zoom survived exactly as long as it took the next GPS fix to land and
     * then snapped back. Once a hand has moved the map, the map stays where the hand left it
     * until [recentre] is pressed.
     */
    internal var userMoved by mutableStateOf(false)
        private set

    /** Bumped to ask the map to frame itself again, since framing is driven by recomposition. */
    internal var frameRequest by mutableIntStateOf(0)
        private set

    internal fun noteUserTouch() {
        if (!userMoved) userMoved = true
    }

    fun zoomIn() {
        userMoved = true
        view?.controller?.zoomIn()
    }

    fun zoomOut() {
        userMoved = true
        view?.controller?.zoomOut()
    }

    /** Gives control back to the map: it frames the route, or follows the runner, once more. */
    fun recentre() {
        userMoved = false
        frameRequest++
    }

    /** Whether a recentre button is worth showing at all. */
    val canRecentre: Boolean get() = userMoved
}

@Composable
fun rememberRouteMapState(): RouteMapState = remember { RouteMapState() }

/**
 * An OpenStreetMap view with a route drawn on it.
 *
 * The caller decides whether this is allowed to exist: it is only ever composed when the player
 * has said yes to tiles, and the run screens fall back to `RouteTrace` otherwise. That split is
 * why there is no consent check inside here — a component that sometimes silently draws nothing
 * is worse than one that is simply not composed.
 *
 * The route is projected and simplified by the same code the tile-free renderer uses, so the two
 * views cannot disagree about the shape of a run.
 *
 * @param follow keeps the map centred on the newest point, for a run in progress. A player who has
 *   panned away is not dragged back; following resumes when they ask for it.
 * @param interactive allows pan and zoom. Off for a thumbnail, and off inside a scrolling column
 *   where a map that swallows drags makes the page feel broken.
 * @param state the handle a screen uses to drive zoom and recentring from its own controls.
 * @param onMarkerLayout reports where each marker landed on screen, in pixels, so a screen can
 *   draw something better than a circle on top of the map without this file knowing what.
 */
@Composable
fun RouteMap(
    points: List<RunPointEntity>,
    modifier: Modifier = Modifier,
    paceColoured: Boolean = true,
    follow: Boolean = false,
    interactive: Boolean = true,
    extraOverlays: List<MapMarker> = emptyList(),
    state: RouteMapState? = null,
    onMarkerLayout: ((List<PlacedMarker>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    MapTiles.ensureConfigured(context)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setUseDataConnection(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setBackgroundColor(Ground.toArgb())
            // OSM's raster tiles are a light-mode map. Inverting and desaturating them is the only
            // way to put a real street map inside a dark app without it glowing like a torch in a
            // dark room, which on a fight screen is genuinely unusable.
            overlayManager.tilesOverlay.setColorFilter(darkMapFilter())
        }
    }

    DisposableEffect(state) {
        state?.view = mapView
        onDispose { if (state?.view === mapView) state.view = null }
    }

    // osmdroid is a View with its own lifecycle: without this it keeps its tile threads and its
    // download queue running behind a screen the player has left.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Watching the gesture stream on the initial pass and consuming nothing: the map still gets
    // every event, and this learns that a hand arrived. Asking osmdroid instead does not work,
    // because its listeners fire for programmatic moves too and cannot tell the two apart.
    val watcher = if (state != null && interactive) {
        Modifier.pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    state.noteUserTouch()
                }
            }
        }
    } else {
        Modifier
    }

    Box(modifier.then(watcher)) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { mapView },
            update = { map ->
                map.setMultiTouchControls(interactive)
                map.overlays.clear()
                drawRoute(map, points, paceColoured)
                // Where you are standing, right now. Every map app has this dot, and without it a
                // route that has not started yet is an empty street map with no you on it.
                if (follow) points.lastOrNull()?.let { map.overlays.add(PositionOverlay(it.lat, it.lon)) }
                // Markers are drawn as plain circles only when nobody has offered to draw them
                // better. The chase hands its own painter in and gets a figure instead of a dot.
                if (onMarkerLayout == null) extraOverlays.forEach { drawMarker(map, it) }
                state?.frameRequest // read, so a recentre request recomposes this block
                if (state?.userMoved != true) frame(map, points, extraOverlays, follow)
                map.invalidate()
                onMarkerLayout?.invoke(placeMarkers(map, extraOverlays))
            },
        )
    }
}

/**
 * The runner's own position, drawn at a fixed size in pixels.
 *
 * A [Polygon] circle would be stated in metres and so would shrink to nothing when the player
 * zooms out to see where they have been. This is a screen-space thing — it says "you are here",
 * not "you occupy nine metres" — so it is drawn at the same size at every zoom.
 */
private class PositionOverlay(private val lat: Double, private val lon: Double) :
    org.osmdroid.views.overlay.Overlay() {

    override fun draw(canvas: android.graphics.Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val p = map.projection?.toPixels(GeoPoint(lat, lon), null) ?: return
        com.clashfit.ui.components.ChaseGlyphs.runner(canvas, p.x.toFloat(), p.y.toFloat(), 11f, Ember)
    }
}

/** Where a marker ended up on screen, in view pixels, once the map had had its say. */
data class PlacedMarker(val marker: MapMarker, val xPx: Float, val yPx: Float, val radiusPx: Float)

/**
 * Projects markers into screen pixels.
 *
 * The radius is converted through the ground resolution at the current zoom, so a zombie stated as
 * eighteen metres across stays eighteen metres across however far the player zooms in — it grows
 * on screen the way a real thing standing on the road would.
 */
private fun placeMarkers(map: MapView, markers: List<MapMarker>): List<PlacedMarker> {
    if (markers.isEmpty()) return emptyList()
    val projection = map.projection ?: return emptyList()
    val mPerPx = TileSystem.GroundResolution(map.mapCenter.latitude, map.zoomLevelDouble)
    return markers.map { m ->
        val p = projection.toPixels(GeoPoint(m.lat, m.lon), null)
        PlacedMarker(
            marker = m,
            xPx = p.x.toFloat(),
            yPx = p.y.toFloat(),
            radiusPx = if (mPerPx > 0) (m.radiusM / mPerPx).toFloat() else 24f,
        )
    }
}

/** Something standing at a coordinate: a pursuer, a cache, or the player. */
data class MapMarker(
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val fill: Color,
    val outline: Color = fill,
    val filled: Boolean = true,
    /**
     * What to draw here.
     *
     * Carried on the marker rather than decided by the renderer so that the street map and the
     * tile-free trace cannot disagree about what a thing is. A circle is still the default,
     * because most markers are just a place.
     */
    val glyph: Glyph = Glyph.CIRCLE,
    /** 0 far, 1 on top of you. Only a pursuer uses it, to say how frightened to look. */
    val closeness: Float = 0f,
) {
    enum class Glyph { CIRCLE, ZOMBIE, CACHE }
}

private fun drawRoute(map: MapView, points: List<RunPointEntity>, paceColoured: Boolean) {
    if (points.size < 2) return
    // Two metres of tolerance: below the noise floor of the fixes themselves, so nothing visible
    // is lost, and it turns an hour's run from 3600 points into a few hundred.
    val simplified = simplifyRoute(points, toleranceM = 2f)
    if (simplified.size < 2) return

    if (!paceColoured) {
        map.overlays.add(polyline(map, simplified.map { GeoPoint(it.lat, it.lon) }, Ember))
        return
    }

    // One polyline per stretch of constant pace band. Splitting on the band rather than per
    // segment keeps the overlay count to the handful of times the runner actually changed gear.
    val median = medianSpeedMps(simplified)
    var runStart = 0
    var runZone = paceZone(simplified[1].speedMps, median)
    for (i in 1 until simplified.size) {
        val zone = paceZone(simplified[i].speedMps, median)
        if (zone != runZone) {
            map.overlays.add(
                polyline(map, simplified.subList(runStart, i + 1).map { GeoPoint(it.lat, it.lon) }, paceColour(runZone)),
            )
            runStart = i
            runZone = zone
        }
    }
    map.overlays.add(
        polyline(map, simplified.subList(runStart, simplified.size).map { GeoPoint(it.lat, it.lon) }, paceColour(runZone)),
    )

    // Start hollow, finish solid — the same language the tile-free renderer uses.
    map.overlays.add(circle(map, GeoPoint(simplified.first().lat, simplified.first().lon), 9.0, Success, filled = false))
    map.overlays.add(circle(map, GeoPoint(simplified.last().lat, simplified.last().lon), 8.0, Ink, filled = true))
}

private fun polyline(map: MapView, pts: List<GeoPoint>, colour: Color): Polyline =
    Polyline(map).apply {
        setPoints(pts)
        outlinePaint.color = colour.toArgb()
        outlinePaint.strokeWidth = 12f
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        outlinePaint.isAntiAlias = true
        setOnClickListener { _, _, _ -> false }
    }

private fun circle(map: MapView, at: GeoPoint, radiusM: Double, colour: Color, filled: Boolean): Polygon =
    Polygon(map).apply {
        points = Polygon.pointsAsCircle(at, radiusM)
        fillPaint.color = if (filled) colour.toArgb() else android.graphics.Color.TRANSPARENT
        outlinePaint.color = colour.toArgb()
        outlinePaint.strokeWidth = 6f
        outlinePaint.isAntiAlias = true
        setOnClickListener { _, _, _ -> false }
    }

private fun drawMarker(map: MapView, m: MapMarker) {
    map.overlays.add(
        Polygon(map).apply {
            points = Polygon.pointsAsCircle(GeoPoint(m.lat, m.lon), m.radiusM)
            fillPaint.color = if (m.filled) m.fill.toArgb() else android.graphics.Color.TRANSPARENT
            outlinePaint.color = m.outline.toArgb()
            outlinePaint.strokeWidth = 5f
            outlinePaint.isAntiAlias = true
            setOnClickListener { _, _, _ -> false }
        },
    )
}

/**
 * Chooses what the map is looking at.
 *
 * Following pins the newest point at a fixed zoom, which is what a runner wants mid-run. Otherwise
 * the whole route is framed once — `zoomToBoundingBox` with a degenerate box (a route that never
 * left one spot) puts osmdroid at maximum zoom over a car park, so that case is handled explicitly.
 */
private fun frame(map: MapView, points: List<RunPointEntity>, markers: List<MapMarker>, follow: Boolean) {
    if (follow && points.isNotEmpty()) {
        val last = points.last()
        // Set the zoom once and then only pan. Re-applying it on every fix cancelled any zoom the
        // player had reached, and made the map feel like it was resisting them.
        if (map.zoomLevelDouble < 3.0) map.controller.setZoom(17.0)
        map.controller.setCenter(GeoPoint(last.lat, last.lon))
        return
    }

    val bounds = RouteBounds.of(points)
    if (bounds == null) {
        val m = markers.firstOrNull() ?: return
        map.controller.setZoom(16.0)
        map.controller.setCenter(GeoPoint(m.lat, m.lon))
        return
    }
    if (bounds.spanM < 40f) {
        map.controller.setZoom(17.5)
        map.controller.setCenter(GeoPoint(bounds.centreLat, bounds.centreLon))
        return
    }
    // A little slack around the box so the route is not welded to the edge of the frame.
    val padDeg = 0.0004
    map.zoomToBoundingBox(
        BoundingBox(
            bounds.maxLat + padDeg, bounds.maxLon + padDeg,
            bounds.minLat - padDeg, bounds.minLon - padDeg,
        ),
        false,
        24,
    )
}

/**
 * Inverts and desaturates the raster tiles into something that belongs in a dark app.
 *
 * Inversion alone turns vegetation a lurid magenta, so saturation comes down first and the result
 * is a grey-blue basemap that the ember route reads clearly against.
 */
private fun darkMapFilter(): ColorMatrixColorFilter {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val desaturate = ColorMatrix().apply { setSaturation(0.28f) }
    invert.postConcat(desaturate)
    return ColorMatrixColorFilter(invert)
}
