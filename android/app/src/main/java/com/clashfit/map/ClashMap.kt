package com.clashfit.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

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
 * @param follow keeps the map centred on the newest point, for a run in progress.
 * @param interactive allows pan and zoom. Off for a thumbnail, and off inside a scrolling column
 *   where a map that swallows drags makes the page feel broken.
 */
@Composable
fun RouteMap(
    points: List<RunPointEntity>,
    modifier: Modifier = Modifier,
    paceColoured: Boolean = true,
    follow: Boolean = false,
    interactive: Boolean = true,
    extraOverlays: List<MapMarker> = emptyList(),
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

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.setMultiTouchControls(interactive)
            map.overlays.clear()
            drawRoute(map, points, paceColoured)
            extraOverlays.forEach { drawMarker(map, it) }
            frame(map, points, extraOverlays, follow)
            map.invalidate()
        },
    )
}

/** A circle drawn at a coordinate: a pursuer, a cache, or the player. */
data class MapMarker(
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val fill: Color,
    val outline: Color = fill,
    val filled: Boolean = true,
)

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
        map.controller.setZoom(17.0)
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
