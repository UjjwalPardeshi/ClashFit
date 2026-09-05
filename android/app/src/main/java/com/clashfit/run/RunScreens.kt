package com.clashfit.run

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.clashfit.data.ActivityKind
import com.clashfit.data.RunPointEntity
import com.clashfit.map.RouteMap
import com.clashfit.meta.ActivityReward
import com.clashfit.meta.OutdoorRules
import com.clashfit.share.ShareCard
import com.clashfit.share.Sharing
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.ChartCard
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.FilterPill
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ProgressRing
import com.clashfit.ui.components.RouteTrace
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.components.Tag
import com.clashfit.ui.components.TrendLine
import com.clashfit.ui.nav.RunActive
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.RunSummary as RunSummaryRoute
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rival
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.clashfit.ui.nav.ZombieRun
import com.clashfit.ui.theme.Gassed

/** Register run routes in the nav graph. */
fun NavGraphBuilder.runRoutes(graph: AppGraph, nav: NavHostController) {
    composable<RunHome> { RunHomeScreen(graph, nav) }
    composable<RunActive> { RunActiveScreen(graph, nav) }
    composable<RunSummaryRoute> { backstack -> RunSummaryScreen(graph, nav, backstack.toRoute<RunSummaryRoute>().runId) }
}

private fun hasPermission(context: android.content.Context, p: String) =
    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

// ── home ────────────────────────────────────────────────────────────────────

@Composable
fun RunHomeScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(graph) { RunRepository(graph.db.runs(), graph.clock) }
    val runsFlow = remember(repo) { repo.recentRuns(20) }
    val runs by runsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = com.clashfit.data.Prefs.Settings())

    var totals by remember { mutableStateOf(ActivityTotals()) }
    var routes by remember { mutableStateOf<Map<Long, List<RunPointEntity>>>(emptyMap()) }
    LaunchedEffect(runs) {
        totals = repo.totals()
        // Thumbnails for the page that is actually on screen, at thumbnail resolution.
        routes = repo.thumbnailRoutes(runs.take(10).map { it.id })
    }

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

    var askingTiles by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Outdoors", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            WeeklyGoalCard(totals, settings.weeklyDistanceGoalM)

            SectionGap(16)
            AppCard(Modifier.fillMaxWidth(), padding = 18) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconBubble(AppIcons.Run, size = 48)
                        Column {
                            Text("Run or walk", style = MaterialTheme.typography.titleLarge, color = Ink)
                            Text(
                                "Distance, pace, cadence and splits. The route stays on this phone unless you share it.",
                                style = MaterialTheme.typography.bodySmall, color = InkMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (ready) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("Start run", Modifier.weight(1f), icon = AppIcons.Run) {
                                RunTrackingService.start(context, ActivityKind.RUN)
                                nav.navigate(RunActive)
                            }
                            SecondaryButton("Start walk", Modifier.weight(1f)) {
                                RunTrackingService.start(context, ActivityKind.WALK)
                                nav.navigate(RunActive)
                            }
                        }
                    } else {
                        Text(
                            "Runs need your location while moving, and a notification to keep tracking with the screen off.",
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

            SectionGap(16)
            AppCard(Modifier.fillMaxWidth(), padding = 18, onClick = { nav.navigate(ZombieRun) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBubble(AppIcons.Bolt, size = 48, tint = Gassed)
                    Column(Modifier.weight(1f)) {
                        Text("Zombie Run", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(
                            "A real chase, on a real map. Sixty seconds head start, then the horde closes when you slow down.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                    }
                    Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
                }
            }

            // Asked once, in words, and never again. Settings still holds the switch.
            if (!settings.mapTilesAsked && runs.isNotEmpty()) {
                SectionGap(16)
                AppCard(Modifier.fillMaxWidth(), padding = 18) {
                    Column {
                        Text("Draw streets under your route?", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A street map is downloaded from OpenStreetMap as you look at it, and asking for " +
                                "the right tiles tells their servers roughly where you were. Your route itself " +
                                "still never leaves this phone. Without this the route draws on our own grid, " +
                                "with the radio off.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("Show streets", Modifier.weight(1f)) {
                                scope.launch { graph.prefs.setMapTiles(true) }
                            }
                            SecondaryButton("Keep it offline", Modifier.weight(1f)) {
                                scope.launch { graph.prefs.setMapTiles(false) }
                            }
                        }
                    }
                }
            }

            SectionGap(24)
            SectionTitle("Recent activity")
            SectionGap(10)
            if (runs.isEmpty()) {
                EmptyState("Nothing out there yet", "Your first run or walk draws its route here.", icon = AppIcons.Run)
            } else {
                ListGroup {
                    runs.forEachIndexed { i, run ->
                        ActivityRow(run, routes[run.id].orEmpty()) { nav.navigate(RunSummaryRoute(run.id)) }
                        if (i < runs.lastIndex) InnerDivider()
                    }
                }
            }
        }
    }

    if (askingTiles) askingTiles = false
}

/**
 * The week's distance against its goal, with the month behind it.
 *
 * A ring rather than a bar because this is the one number on the screen that is meant to be read
 * from across a room and felt rather than parsed.
 */
@Composable
private fun WeeklyGoalCard(totals: ActivityTotals, goalM: Int) {
    val fraction = if (goalM <= 0) 0f else totals.weekDistanceM / goalM
    AppCard(Modifier.fillMaxWidth(), padding = 18) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(fraction = fraction, size = 92, stroke = 9, color = if (fraction >= 1f) Success else Ember) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(totals.weekDistanceM / 1000f), style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text("km", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (fraction >= 1f) "Goal met — ${"%.1f".format(goalM / 1000f)} km"
                    else "${"%.1f".format((goalM - totals.weekDistanceM).coerceAtLeast(0f) / 1000f)} km to your goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fraction >= 1f) Success else InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${totals.weekActivities} activities · ${formatDuration(totals.weekMovingMs)} moving" +
                        if (totals.weekSteps > 0) " · ${totals.weekSteps} steps" else "",
                    style = MaterialTheme.typography.bodySmall, color = InkFaint,
                )
                Text(
                    "This month: %.1f km over %d".format(totals.monthDistanceM / 1000f, totals.monthActivities),
                    style = MaterialTheme.typography.bodySmall, color = InkFaint,
                )
            }
        }
    }
}

/** One activity in the feed: its own route, drawn small, beside the numbers. */
@Composable
private fun ActivityRow(run: RunSummary, points: List<RunPointEntity>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
            if (points.size >= 2) {
                RouteTrace(
                    points = points,
                    modifier = Modifier.fillMaxSize(),
                    showScale = false,
                    showGrid = false,
                    strokeWidth = 4f,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
                    IconBubble(AppIcons.Run, tint = if (run.isWalk) Rival else Success, size = 34)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%.2f km".format(run.distanceM / 1000f), style = MaterialTheme.typography.bodyLarge, color = Ink)
                if (run.isWalk) Tag("Walk", color = Rival)
            }
            Text("${run.paceStr} · ${formatDuration(run.movingMs)}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            Text(dateLabel(run.startedAtMs), style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
    }
}

// ── while moving ────────────────────────────────────────────────────────────

/** Full screen while tracking: the number that matters, then the rest. */
@Composable
private fun RunActiveScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val tracker = RunTracker.getInstance()
    val state by tracker.state.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = com.clashfit.data.Prefs.Settings())

    LaunchedEffect(Unit) { if (!state.isRunning) RunTrackingService.start(context, ActivityKind.RUN) }

    Column(Modifier.fillMaxSize().background(Ground).safeDrawingPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.isPaused) InkFaint else Success))
            Text(
                buildString {
                    append(if (state.isPaused) "Paused" else "Recording")
                    if (state.isWalk) append(" · walk")
                    // Said out loud rather than hidden. A route built from footsteps is an
                    // estimate, and the person looking at it is entitled to know which it is.
                    append(if (state.indoors) " · steps, no GPS" else "")
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state.indoors) Rival else InkMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.2f".format(state.distanceM / 1000f), style = MaterialTheme.typography.displayMedium.copy(fontSize = 88.sp), color = Ink)
            Text("KILOMETRES", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        }
        Spacer(Modifier.height(20.dp))
        StatStrip(
            listOf(
                formatDuration(state.movingMs) to "Moving",
                RunSummary.formatPace(state.avgPaceSecPerKm) to "Pace",
                if (state.isWalk && state.steps > 0) "${state.steps}" to "Steps" else "${state.cadenceSpm}" to "Cadence",
            ),
        )
        Spacer(Modifier.height(18.dp))
        AppCard(Modifier.fillMaxWidth(), padding = 0) {
            ActivityMap(
                points = state.points,
                tilesAllowed = settings.mapTiles,
                follow = true,
                interactive = false,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
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
                val finished = state.runId
                RunTrackingService.stop(context)
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

/**
 * The route, on tiles when the player has allowed them and on our own grid when they have not.
 *
 * The choice is made here rather than inside the map so that with tiles off no `MapView` is ever
 * constructed and nothing can reach the network by accident.
 */
@Composable
private fun ActivityMap(
    points: List<RunPointEntity>,
    tilesAllowed: Boolean,
    modifier: Modifier = Modifier,
    follow: Boolean = false,
    interactive: Boolean = true,
    paceColoured: Boolean = true,
) {
    if (tilesAllowed && points.size >= 2) {
        RouteMap(points = points, modifier = modifier, paceColoured = paceColoured, follow = follow, interactive = interactive)
    } else {
        RouteTrace(points = points, modifier = modifier, paceColoured = paceColoured)
    }
}

// ── summary ─────────────────────────────────────────────────────────────────

@Composable
fun RunSummaryScreen(graph: AppGraph, nav: NavHostController, runId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(graph) { RunRepository(graph.db.runs(), graph.clock) }
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = com.clashfit.data.Prefs.Settings())
    val meta by graph.meta.state.collectAsStateWithLifecycle(initialValue = null)

    var run by remember { mutableStateOf<RunSummary?>(null) }
    var splits by remember { mutableStateOf<List<Float>>(emptyList()) }
    var points by remember { mutableStateOf<List<RunPointEntity>>(emptyList()) }
    var records by remember { mutableStateOf<List<String>>(emptyList()) }
    var reward by remember { mutableStateOf<ActivityReward?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var showShareNotice by remember { mutableStateOf(false) }

    LaunchedEffect(runId) {
        run = repo.getRun(runId)
        val entity = repo.dao.run(runId)
        if (entity != null && entity.splitsJson.isNotEmpty()) splits = parseSplits(entity.splitsJson)
        // The route was drawn the whole way round and then thrown away at the finish line. It is
        // the one thing about a run worth looking at afterwards, and it was already on the phone.
        points = repo.getRunPoints(runId)
        val r = run
        if (r != null && entity != null) {
            records = repo.previousBests(runId).recordsSetBy(r)
            // Banked here rather than in the tracking service, for the same reason a fight banks
            // its XP on its summary screen: this is where the breakdown is shown, and the ledger
            // makes it idempotent, so re-entering the screen never pays twice.
            reward = graph.meta.onActivityFinished(
                OutdoorRules.ActivityFacts(
                    activityId = runId,
                    atMs = r.startedAtMs,
                    isWalk = r.isWalk,
                    distanceM = r.distanceM,
                    movingMs = r.movingMs,
                    elapsedMs = (entity.endedAtMs ?: r.startedAtMs) - r.startedAtMs,
                    climbM = r.elevationGainM,
                    steps = r.steps,
                    recordsSet = records.size,
                ),
            )
        }
    }

    ScreenScaffold(title = "Activity", onBack = { nav.popBackStack() }) { padding ->
        val r = run
        if (r == null) {
            EmptyState("Loading", "Reading the activity…", Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dateLabel(r.startedAtMs), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                if (r.isWalk) Tag("Walk", color = Rival)
            }
            SectionGap(12)

            if (points.size >= 2) {
                AppCard(Modifier.fillMaxWidth(), padding = 0) {
                    ActivityMap(
                        points = points,
                        tilesAllowed = settings.mapTiles,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
                SectionGap(14)
            }

            if (records.isNotEmpty()) {
                AppCard(Modifier.fillMaxWidth(), padding = 16) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(AppIcons.Trophy, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                            Text("Personal best", style = MaterialTheme.typography.titleMedium, color = Ink)
                        }
                        Spacer(Modifier.height(8.dp))
                        records.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = Success) }
                    }
                }
                SectionGap(14)
            }

            val earned = reward
            if (earned != null && earned.xp > 0) {
                AppCard(Modifier.fillMaxWidth(), padding = 16) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Earned", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text("+${earned.xp} XP", style = MaterialTheme.typography.titleMedium, color = Ember)
                        }
                        if (earned.lines.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        earned.lines.forEach { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(line.label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                                Text("+${line.xp}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            }
                        }
                        if (earned.leveledUp) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Level ${earned.after.level} · ${earned.after.title}",
                                style = MaterialTheme.typography.titleSmall, color = Success,
                            )
                        }
                        earned.newAchievements.forEach {
                            Spacer(Modifier.height(6.dp))
                            Text("Badge unlocked · ${it.title}", style = MaterialTheme.typography.bodySmall, color = Success)
                        }
                    }
                }
                SectionGap(14)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("%.2f".format(r.distanceM / 1000f), "km", Modifier.weight(1f))
                StatTile(formatDuration(r.movingMs), "Moving", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(r.paceStr, "Avg pace", Modifier.weight(1f))
                StatTile(if (r.elevationGainM > 0) "%.0f m".format(r.elevationGainM) else "—", "Climb", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(r.fastestKmSec?.let { formatPaceSecPerKm(it) } ?: "—", "Fastest km", Modifier.weight(1f))
                StatTile(if (r.steps > 0) "${r.steps}" else "${r.cadenceSpm} spm", if (r.steps > 0) "Steps" else "Cadence", Modifier.weight(1f))
            }

            SectionGap(20)
            PrimaryButton("Share this activity", Modifier.fillMaxWidth()) {
                if (settings.shareNoticeSeen) sharing = true else showShareNotice = true
            }

            val profile = remember(points) { elevationProfile(points, buckets = 48) }
            if (profile.size >= 8 && r.elevationGainM > 0f) {
                SectionGap(20)
                ChartCard("Elevation", subtitle = "%.0f m of climb".format(r.elevationGainM)) {
                    TrendLine(points = profile, height = 110, description = "Elevation over the route")
                }
            }

            if (splits.isNotEmpty()) {
                SectionGap(20)
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
            Text(
                if (settings.mapTiles) {
                    "The route is stored on this phone. Map tiles were fetched from OpenStreetMap to draw the streets."
                } else {
                    "The route never left this phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )
        }
    }

    if (showShareNotice) {
        ShareNotice(
            onDismiss = { showShareNotice = false },
            onAccept = {
                showShareNotice = false
                scope.launch { graph.prefs.setShareNoticeSeen(true) }
                sharing = true
            },
        )
    }

    val r = run
    if (sharing && r != null) {
        ShareDialog(
            stats = ShareCard.Stats(
                title = if (r.isWalk) "Walk" else "Run",
                dateLabel = dateLabel(r.startedAtMs),
                distanceM = r.distanceM,
                movingMs = r.movingMs,
                paceSecPerKm = r.avgPaceSecPerKm,
                climbM = r.elevationGainM,
                cadenceSpm = r.cadenceSpm,
                points = points,
                levelLabel = meta?.let { "Level ${it.progress.level} · ${it.progress.title}" },
                records = records,
                fastestKmSec = r.fastestKmSec,
                effort = ShareCard.effortFraction(points),
                effortLabel = if (r.isWalk) "STEADY" else "PUSHED",
            ),
            onDismiss = { sharing = false },
        )
    }
}

/**
 * Said once, before the first share, in the words that are actually true.
 *
 * The picture contains the route. That is the whole point of it and also the thing worth knowing
 * before it goes into a group chat, so it is stated plainly here rather than discovered by
 * somebody recognising a street.
 */
@Composable
private fun ShareNotice(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This picture shows where you were") },
        text = {
            Text(
                "The card draws your whole route, including where you started and finished. " +
                    "That is fine for a park and worth thinking about if you started at your front door. " +
                    "Nothing is uploaded by the app — you choose which app receives the picture.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Got it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        containerColor = Panel,
    )
}

/**
 * Pick a look, see it, send it.
 *
 * The card is rendered off the main thread on every change of style, because a 1080×1920 bitmap
 * with a few thousand route segments on it is not free and a share sheet that janks on open is a
 * share sheet nobody uses twice.
 */
@Composable
private fun ShareDialog(stats: ShareCard.Stats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(ShareCard.Style.HERO) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(style, stats.points) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching { ShareCard.render(context, stats, style) }.getOrNull()
        }
    }

    val text = "%.2f km with ClashFit — every rep graded by the camera. clash-fit.vercel.app"
        .format(stats.distanceM / 1000f)

    fun send(action: (android.net.Uri) -> Unit) {
        val bmp = bitmap ?: return
        val uri = Sharing.writeCard(context, bmp)
        action(uri)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCard.Style.entries.forEach { s ->
                        FilterPill(label = s.label, selected = style == s, onClick = { style = s })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(style.blurb, style = MaterialTheme.typography.labelSmall, color = InkFaint)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)).background(Ground),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Preview of the picture that will be shared",
                            modifier = Modifier.aspectRatio(ShareCard.WIDTH.toFloat() / ShareCard.HEIGHT),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("Drawing…", color = InkFaint, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Only offer a direct button for an app that is actually installed; a button that
                // silently does nothing is worse than no button.
                if (Sharing.hasInstagram(context)) {
                    PrimaryButton("Instagram story", Modifier.fillMaxWidth(), enabled = bitmap != null) {
                        send { uri -> if (!Sharing.shareToInstagramStory(context, uri)) Sharing.shareSheet(context, uri, text) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (Sharing.hasWhatsApp(context)) {
                    SecondaryButton("WhatsApp", Modifier.fillMaxWidth(), enabled = bitmap != null) {
                        send { uri -> if (!Sharing.shareToWhatsApp(context, uri, text)) Sharing.shareSheet(context, uri, text) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SecondaryButton("More apps", Modifier.fillMaxWidth(), enabled = bitmap != null) {
                    send { uri -> Sharing.shareSheet(context, uri, text) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = Panel,
    )
}

// ── odds and ends ───────────────────────────────────────────────────────────

/**
 * Per-kilometre splits from the stored column.
 *
 * The column is called splitsJson and holds a bare comma-joined list, not JSON. Anything that ever
 * writes the format the name promises would have had its first and last split silently dropped by
 * a plain split-on-comma, and the remaining ones relabelled — km 2 shown as km 1, and no error
 * anywhere. Tolerating the brackets costs one trim.
 */
internal fun parseSplits(raw: String): List<Float> =
    raw.trim().removeSurrounding("[", "]")
        .split(",")
        .mapNotNull { it.trim().toFloatOrNull() }

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun dateLabel(atMs: Long): String = runCatching {
    DATE_FORMAT.format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

internal fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs) else "%d:%02d".format(mins, secs)
}
