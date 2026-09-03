package com.clashfit.run

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.nav.RunActive
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.RunSummary as RunSummaryRoute
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Success
import kotlin.math.PI
import kotlin.math.cos

/** Register run routes in the nav graph. */
fun NavGraphBuilder.runRoutes(graph: AppGraph, nav: NavHostController) {
    composable<RunHome> { RunHomeScreen(graph, nav) }
    composable<RunActive> { RunActiveScreen(graph, nav) }
    composable<RunSummaryRoute> { backstack -> RunSummaryScreen(graph, nav, backstack.toRoute<RunSummaryRoute>().runId) }
}

private fun hasPermission(context: android.content.Context, p: String) =
    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

@Composable
private fun RunHomeScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val repo = remember(graph) { RunRepository(graph.db.runs(), graph.clock) }
    val runsFlow = remember(repo) { repo.recentRuns(20) }
    val runs by runsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var locationOk by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var notificationsOk by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || locationOk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsOk = result[Manifest.permission.POST_NOTIFICATIONS] == true || notificationsOk
        }
    }
    val ready = locationOk && notificationsOk

    ScreenScaffold(title = "Run tracker", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 18) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconBubble(AppIcons.Run, size = 48)
                        Column {
                            Text("Outdoor run", style = MaterialTheme.typography.titleLarge, color = Ink)
                            Text("Distance, pace, cadence and splits. The route stays on this phone.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (ready) {
                        PrimaryButton("Start run", icon = AppIcons.Run) {
                            RunTrackingService.start(context)
                            nav.navigate(RunActive)
                        }
                    } else {
                        Text(
                            "Runs need your location while running, and a notification to keep tracking with the screen off.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Allow location and notifications") {
                            val wanted = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            ask.launch(wanted.toTypedArray())
                        }
                    }
                }
            }

            SectionGap(24)
            SectionTitle("Recent runs")
            SectionGap(10)
            if (runs.isEmpty()) {
                EmptyState("No runs yet", "Your first run draws its route here.", icon = AppIcons.Run)
            } else {
                ListGroup {
                    runs.forEachIndexed { i, run ->
                        RunRow(run) { nav.navigate(RunSummaryRoute(run.id)) }
                        if (i < runs.lastIndex) InnerDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: RunSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBubble(AppIcons.Run, tint = Success)
        Column(Modifier.weight(1f)) {
            Text("%.2f km".format(run.distanceM / 1000f), style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text("${run.paceStr} · ${formatDuration(run.movingMs)}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
    }
}

/** Full screen while tracking: the number that matters, then the rest. */
@Composable
private fun RunActiveScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val tracker = RunTracker.getInstance()
    val state by tracker.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (!state.isRunning) RunTrackingService.start(context) }

    Column(Modifier.fillMaxSize().background(Ground).safeDrawingPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (state.isPaused) InkFaint else Success))
            Text(if (state.isPaused) "Paused" else "Recording", style = MaterialTheme.typography.labelLarge, color = InkMuted)
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.2f".format(state.distanceM / 1000f), style = MaterialTheme.typography.displayMedium.copy(fontSize = 96.sp), color = Ink)
            Text("KILOMETRES", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        }
        Spacer(Modifier.height(24.dp))
        StatStrip(
            listOf(
                formatDuration(state.movingMs) to "Moving",
                RunSummary.formatPace(state.avgPaceSecPerKm) to "Pace",
                "${state.cadenceSpm}" to "Cadence",
            ),
        )
        Spacer(Modifier.height(20.dp))
        AppCard(Modifier.fillMaxWidth(), padding = 0) {
            RouteTrace(points = state.points, modifier = Modifier.fillMaxWidth().height(220.dp))
        }
        if (state.elevationGainM > 0) {
            Spacer(Modifier.height(12.dp))
            StatTile("%.0f m".format(state.elevationGainM), "Elevation gain", Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isPaused) {
                PrimaryButton("Resume", Modifier.weight(1f)) { RunTrackingService.resume(context) }
            } else {
                SecondaryButton("Pause", Modifier.weight(1f)) { RunTrackingService.pause(context) }
            }
            SecondaryButton("Finish", Modifier.weight(1f)) {
                RunTrackingService.stop(context)
                val finished = state.runId
                if (finished != null) {
                    nav.navigate(RunSummaryRoute(finished))
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
    val repo = remember(graph) { RunRepository(graph.db.runs(), graph.clock) }
    var run by remember { mutableStateOf<RunSummary?>(null) }
    var splits by remember { mutableStateOf<List<Float>>(emptyList()) }

    LaunchedEffect(runId) {
        run = repo.getRun(runId)
        val entity = repo.dao.run(runId)
        if (entity != null && entity.splitsJson.isNotEmpty()) splits = entity.splitsJson.split(",").mapNotNull { it.toFloatOrNull() }
    }

    ScreenScaffold(title = "Run summary", onBack = { nav.popBackStack() }) { padding ->
        val r = run
        if (r == null) {
            EmptyState("Loading", "Reading the run…", Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("%.2f".format(r.distanceM / 1000f), "km", Modifier.weight(1f))
                StatTile(formatDuration(r.movingMs), "Moving", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(r.paceStr, "Avg pace", Modifier.weight(1f))
                StatTile(if (r.elevationGainM > 0) "%.0f m".format(r.elevationGainM) else "—", "Climb", Modifier.weight(1f))
            }
            if (splits.isNotEmpty()) {
                SectionGap(24)
                SectionTitle("Splits")
                SectionGap(10)
                val fastest = splits.minOrNull() ?: 0f
                ListGroup {
                    splits.forEachIndexed { i, pace ->
                        val best = (pace - fastest) < 1f
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("km ${i + 1}", style = MaterialTheme.typography.bodyLarge, color = Ink)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(RunSummary.formatPace(pace), style = MaterialTheme.typography.titleSmall, color = if (best) Success else Ink)
                                if (best) Icon(AppIcons.Bolt, contentDescription = "Fastest", tint = Success, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (i < splits.lastIndex) InnerDivider()
                    }
                }
            }
            SectionGap(20)
            Text("The route is stored on this phone only.", style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
    }
}

@Composable
private fun RouteTrace(points: List<com.clashfit.data.RunPointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(PanelLift)
        if (points.size < 2) return@Canvas
        val centerLat = points.map { it.lat }.average()
        val centerLon = points.map { it.lon }.average()
        val metersPerDegLat = 111_320f
        val projected = points.map { p ->
            val metersPerDegLon = 111_320f * cos(p.lat * PI / 180.0).toFloat()
            Offset(((p.lon - centerLon) * metersPerDegLon).toFloat(), ((p.lat - centerLat) * metersPerDegLat).toFloat())
        }
        val minX = projected.minOf { it.x }; val maxX = projected.maxOf { it.x }
        val minY = projected.minOf { it.y }; val maxY = projected.maxOf { it.y }
        val pad = 10f
        val rangeX = maxX - minX + pad * 2; val rangeY = maxY - minY + pad * 2
        val scale = kotlin.math.min(if (rangeX > 0) (size.width - 32) / rangeX else 1f, if (rangeY > 0) (size.height - 32) / rangeY else 1f)
        fun px(o: Offset) = Offset((o.x - minX + pad) * scale + 16, (o.y - minY + pad) * scale + 16)
        for (i in 1 until projected.size) drawLine(Ember, px(projected[i - 1]), px(projected[i]), strokeWidth = 5f)
        drawCircle(Fresh, radius = 9f, center = px(projected.first()))
        drawCircle(Ink, radius = 7f, center = px(projected.last()))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs) else "%d:%02d".format(mins, secs)
}
