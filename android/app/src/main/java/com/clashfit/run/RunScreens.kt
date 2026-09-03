package com.clashfit.run

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.nav.RunActive
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.RunSummary as RunSummaryRoute
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.LocalReduceMotion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/** Register run routes in the nav graph. */
fun NavGraphBuilder.runRoutes(graph: AppGraph, nav: NavHostController) {
    composable<RunHome> {
        RunHomeScreen(graph, nav)
    }
    composable<RunActive> {
        RunActiveScreen(graph, nav)
    }
    composable<RunSummaryRoute> { backstack ->
        val runId = backstack.toRoute<RunSummaryRoute>().runId
        RunSummaryScreen(graph, nav, runId)
    }
}

@Composable
private fun RunHomeScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val repo = RunRepository(graph.db.runs(), graph.clock)
    val runs by repo.recentRuns(20).collectAsStateWithLifecycle(initialValue = emptyList())

    val hasLocationPerm = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val hasNotificationPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Headline("Runs")
        Spacer(Modifier.height(8.dp))

        // Big Start button
        EmberButton(
            "Start Run",
            modifier = Modifier.fillMaxWidth().height(64.dp),
            enabled = hasLocationPerm && hasNotificationPerm,
            onClick = {
                RunTrackingService.start(context)
                nav.navigate(RunActive)
            },
        )

        if (!hasLocationPerm || !hasNotificationPerm) {
            Text(
                "Grant location and notification permissions to start running",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
        }

        // Recent runs list
        if (runs.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.labelMedium, color = InkFaint)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(runs) { run ->
                    RunListItem(run) {
                        nav.navigate(RunSummaryRoute(run.id))
                    }
                }
            }
        } else {
            Text("No runs yet", color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RunActiveScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val tracker = RunTracker.getInstance()
    val state by tracker.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!state.isRunning) {
            RunTrackingService.start(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Headline("Run")
        Spacer(Modifier.height(16.dp))

        // Big distance display
        val distanceKm = state.distanceM / 1000f
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "%.2f".format(distanceKm),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 72.sp),
                color = Ink,
            )
            Text("km", style = MaterialTheme.typography.labelMedium, color = InkFaint)
        }

        // Metrics row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                formatDuration(state.movingMs),
                "Moving",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                RunSummary.formatPace(state.avgPaceSecPerKm),
                "Pace",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                "%d spm".format(state.cadenceSpm),
                "Cadence",
                modifier = Modifier.weight(1f),
            )
        }

        // Route trace
        RouteTrace(
            points = state.points,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )

        // Elevation if > 0
        if (state.elevationGainM > 0) {
            StatTile(
                "%.0f m".format(state.elevationGainM),
                "Elevation gain",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        // Controls
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isPaused) {
                EmberButton("Resume", modifier = Modifier.weight(1f)) {
                    RunTrackingService.resume(context)
                }
            } else {
                OutlineButton("Pause", modifier = Modifier.weight(1f)) {
                    RunTrackingService.pause(context)
                }
            }
            OutlineButton("Finish", modifier = Modifier.weight(1f)) {
                RunTrackingService.stop(context)
                val finishedRunId = state.runId
                if (finishedRunId != null) {
                    nav.navigate(RunSummaryRoute(finishedRunId))
                    nav.popBackStack(RunActive, inclusive = true)
                } else {
                    nav.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun RunSummaryScreen(graph: AppGraph, nav: NavHostController, runId: Long) {
    val repo = RunRepository(graph.db.runs(), graph.clock)
    var run by remember { mutableStateOf<RunSummary?>(null) }
    var splits by remember { mutableStateOf<List<Float>>(emptyList()) }

    LaunchedEffect(runId) {
        run = repo.getRun(runId)
        if (run != null) {
            // Parse splits from the entity
            val entity = repo.dao.run(runId)
            if (entity != null && entity.splitsJson.isNotEmpty()) {
                splits = entity.splitsJson.split(",").mapNotNull { it.toFloatOrNull() }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Headline("Run Summary")

        if (run != null) {
            val distKm = run!!.distanceM / 1000f
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StatTile(
                        "%.2f km".format(distKm),
                        "Distance",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    StatTile(
                        formatDuration(run!!.movingMs),
                        "Moving time",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    StatTile(
                        run!!.paceStr,
                        "Average pace",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (run!!.elevationGainM > 0) {
                    item {
                        StatTile(
                            "%.0f m".format(run!!.elevationGainM),
                            "Elevation gain",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (splits.isNotEmpty()) {
                    item {
                        Text("Per-kilometre splits", style = MaterialTheme.typography.labelMedium, color = InkFaint)
                    }
                    items(splits.size) { i ->
                        SplitBar(i + 1, splits[i], splits.minOrNull() ?: 0f, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text(
                        "Route stored on this phone only",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                    )
                }
            }
        } else {
            Text("Loading...", color = InkMuted)
            Spacer(Modifier.weight(1f))
        }

        OutlineButton("Back", modifier = Modifier.fillMaxWidth()) {
            nav.popBackStack()
        }
    }
}

@Composable
private fun SplitBar(kmNumber: Int, paceSec: Float, fastestSec: Float, modifier: Modifier = Modifier) {
    val isFastest = (paceSec - fastestSec) < 1f // within 1 second of fastest
    PanelBox(modifier, padding = 12) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("km $kmNumber", style = MaterialTheme.typography.labelMedium, color = Ink)
            Text(
                RunSummary.formatPace(paceSec),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFastest) Ink else InkMuted,
            )
            if (isFastest) {
                Text("⭐", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RunListItem(run: RunSummary, onClick: () -> Unit) {
    val distKm = run.distanceM / 1000f
    PanelBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%.2f km".format(distKm), style = MaterialTheme.typography.titleMedium, color = Ink)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    run.paceStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
                Text(
                    formatDuration(run.movingMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
            }
        }
    }
}

@Composable
private fun RouteTrace(points: List<com.clashfit.data.RunPointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) {
            drawRect(Panel)
            return@Canvas
        }

        drawRect(Panel)

        // Project points to local metres
        val center = points[points.size / 2]
        val metersPerDegLat = 111_320f
        val metersPerDegLon = 111_320f * cos(center.lat * PI / 180.0).toFloat()

        val projected = points.map { p ->
            val x = ((p.lon - center.lon) * metersPerDegLon).toFloat()
            val y = ((p.lat - center.lat) * metersPerDegLat).toFloat()
            Offset(x, y)
        }

        if (projected.size < 2) return@Canvas

        // Find bounds
        val minX = projected.minOf { it.x }
        val maxX = projected.maxOf { it.x }
        val minY = projected.minOf { it.y }
        val maxY = projected.maxOf { it.y }
        val padding = 10f
        val rangeX = maxX - minX + padding * 2
        val rangeY = maxY - minY + padding * 2
        val scaleX = if (rangeX > 0) (size.width - 32) / rangeX else 1f
        val scaleY = if (rangeY > 0) (size.height - 32) / rangeY else 1f
        val scale = kotlin.math.min(scaleX, scaleY)

        // Draw polyline
        for (i in 1 until projected.size) {
            val prev = projected[i - 1]
            val curr = projected[i]
            val px = (prev.x - minX + padding) * scale + 16
            val py = (prev.y - minY + padding) * scale + 16
            val cx = (curr.x - minX + padding) * scale + 16
            val cy = (curr.y - minY + padding) * scale + 16
            drawLine(InkMuted, Offset(px, py), Offset(cx, cy), strokeWidth = 2f)
        }

        // Start ring
        val s = projected[0]
        val sx = (s.x - minX + padding) * scale + 16
        val sy = (s.y - minY + padding) * scale + 16
        drawCircle(Color(0xFF8FD18A), radius = 8f, center = Offset(sx, sy))

        // Current dot
        val c = projected.last()
        val cx = (c.x - minX + padding) * scale + 16
        val cy = (c.y - minY + padding) * scale + 16
        drawCircle(Ink, radius = 6f, center = Offset(cx, cy))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, mins, secs)
        else -> "%d:%02d".format(mins, secs)
    }
}
