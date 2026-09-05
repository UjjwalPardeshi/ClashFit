package com.clashfit.run

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.clashfit.map.RouteMapState
import com.clashfit.map.rememberRouteMapState
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
import com.clashfit.ui.components.MapCaption
import com.clashfit.ui.components.MapControls
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ProgressRing
import com.clashfit.ui.components.RouteTrace
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StreetMapNotice
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
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rival
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.delay
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

    // Said before a single tile is fetched, not after.
    //
    // Streets are on out of the box, which makes *when* this is said the whole of the promise.
    // Every route on this tab draws on the app's own grid, so nothing has touched the network by
    // the time this appears, and `mapTilesAsked` is what the maps themselves wait on. The card
    // this replaced sat below the fold and only appeared once an activity already existed, which
    // meant a new player met the street map before they met the sentence explaining it.
    if (settings.loaded && !settings.mapTilesAsked) {
        StreetMapNotice(
            onShowStreets = { scope.launch { graph.prefs.setMapTiles(true) } },
            onKeepOffline = { scope.launch { graph.prefs.setMapTiles(false) } },
        )
    }
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
                    val (value, unit) = distanceParts(totals.weekDistanceM)
                    Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (fraction >= 1f) "Goal met — ${"%.1f".format(goalM / 1000f)} km"
                    else "${formatDistance((goalM - totals.weekDistanceM).coerceAtLeast(0f))} to your goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fraction >= 1f) Success else InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append(totals.weekActivities)
                        append(if (totals.weekActivities == 1) " activity" else " activities")
                        append(" · ")
                        append(formatDuration(totals.weekMovingMs))
                        append(" moving")
                        if (totals.weekSteps > 0) append(" · ${totals.weekSteps} steps")
                    },
                    style = MaterialTheme.typography.bodySmall, color = InkFaint,
                )
                Text(
                    // "0 m over 0" is arithmetic, not a sentence. A month with nothing in it says so.
                    if (totals.monthActivities == 0) {
                        "This month: nothing yet"
                    } else {
                        "This month: ${formatDistance(totals.monthDistanceM)} over ${totals.monthActivities}"
                    },
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
                Text(formatDistance(run.distanceM), style = MaterialTheme.typography.bodyLarge, color = Ink)
                if (run.kind != com.clashfit.data.ActivityKind.RUN) {
                    Tag(run.title, color = if (run.isWalk) Rival else Gassed)
                }
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
    val mapState = rememberRouteMapState()

    LaunchedEffect(Unit) { if (!state.isRunning) RunTrackingService.start(context, ActivityKind.RUN) }

    // Elapsed time, ticking from the moment the button was pressed.
    //
    // Moving time is the honest number for a pace, but it stays at zero until the phone is sure
    // you are moving, and a screen that shows nothing at all for the first half minute reads as an
    // app that has not started. This is the clock a stopwatch would show.
    var elapsedMs by remember { mutableStateOf(0L) }
    val startedAt = state.startedAtMs
    LaunchedEffect(startedAt, state.isPaused) {
        while (startedAt != null && !state.isPaused) {
            elapsedMs = graph.clock.nowMs() - startedAt
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Ground)) {
        // The map is the screen. Everything else floats on it.
        ActivityMap(
            points = state.points,
            tilesAllowed = settings.mapTiles && settings.mapTilesAsked,
            follow = true,
            interactive = true,
            mapState = mapState,
            modifier = Modifier.fillMaxSize(),
        )

        // A wash at the top and bottom so white text stays legible over whatever the map drew.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Ground.copy(alpha = 0.92f),
                    0.22f to Ground.copy(alpha = 0.10f),
                    0.58f to Color.Transparent,
                    1f to Ground.copy(alpha = 0.94f),
                ),
            ),
        )

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingDot(paused = state.isPaused)
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
                Spacer(Modifier.weight(1f))
                Text(formatDuration(elapsedMs), style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            Spacer(Modifier.weight(1f))
            MapControls(mapState, Modifier.align(Alignment.End))
            Spacer(Modifier.height(10.dp))

            AppCard(Modifier.fillMaxWidth(), padding = 16) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (value, unit) = distanceParts(state.distanceM)
                        Text(
                            value,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp),
                            color = Ink,
                        )
                        Text(
                            unit.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    StatStrip(
                        listOf(
                            formatDuration(state.movingMs) to "Moving",
                            RunSummary.formatPace(state.avgPaceSecPerKm) to "Pace",
                            if (state.isWalk && state.steps > 0) "${state.steps}" to "Steps" else "${state.cadenceSpm}" to "Cadence",
                        ),
                    )
                    if (state.elevationGainM > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "%.0f m of climb".format(state.elevationGainM),
                            style = MaterialTheme.typography.bodySmall, color = InkFaint,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
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
                                // One navigation, not two.
                                //
                                // This used to push the summary and then pop back to the recording
                                // screen inclusively, which takes the summary down with it: every
                                // finished run landed straight back on the feed and the summary was
                                // never seen. It was invisible until now because the run id was
                                // usually still null at this point, so the other branch ran instead.
                                // popUpTo removes the recording screen as part of the same move, so
                                // Back from the summary goes to the feed rather than to a recording
                                // screen for an activity that is already over.
                                nav.navigate(RunSummaryRoute(finished)) {
                                    popUpTo(RunActive) { inclusive = true }
                                }
                            } else {
                                nav.popBackStack()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The recording light: steady while paused, breathing while the activity is being recorded. */
@Composable
private fun RecordingDot(paused: Boolean) {
    val alpha by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = if (paused) 1f else 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "recAlpha",
    )
    Box(
        Modifier.size(10.dp).clip(CircleShape)
            .background((if (paused) InkFaint else Success).copy(alpha = alpha)),
    )
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
    mapState: RouteMapState? = null,
) {
    // One point is enough for a street map: it can show where you are standing while the route is
    // still a single dot. The tile-free trace needs two, because it has nothing else to draw.
    if (tilesAllowed && points.isNotEmpty()) {
        RouteMap(
            points = points,
            modifier = modifier,
            paceColoured = paceColoured,
            follow = follow,
            interactive = interactive,
            state = mapState,
        )
    } else if (points.size >= 2) {
        RouteTrace(points = points, modifier = modifier, paceColoured = paceColoured)
    } else {
        // Nothing to draw yet. Saying so beats an empty grey box that looks like a failure.
        Box(modifier.background(PanelLift), contentAlignment = Alignment.Center) {
            Text(
                if (tilesAllowed) "Finding you…" else "Waiting for the first fix…",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )
        }
    }
}

/**
 * How long the summary waits for the tracking service to write the activity down.
 *
 * Generous, because the alternative to waiting is showing zeroes as though they were the result.
 * In practice the write lands in tens of milliseconds; this only matters when the database is busy.
 */
private const val FINISH_WAIT_MS = 5_000L
private const val FINISH_POLL_MS = 80L

/**
 * The route, owning the whole display.
 *
 * A route is a shape you want to look *into* — which junction, which side of the park — and no map
 * inside a scrolling page can offer that, because it has to give the drag back to the page. So the
 * inline map is a picture and this is the map: full screen, every gesture, zoom and recentre
 * controls, and one obvious way out.
 */
@Composable
private fun FullScreenMap(
    points: List<RunPointEntity>,
    tilesAllowed: Boolean,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val mapState = rememberRouteMapState()
        Box(Modifier.fillMaxSize().background(Ground)) {
            ActivityMap(
                points = points,
                tilesAllowed = tilesAllowed,
                modifier = Modifier.fillMaxSize(),
                interactive = tilesAllowed,
                mapState = mapState,
            )
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SecondaryButton("Close", Modifier.width(120.dp), onClick = onClose)
                }
                Spacer(Modifier.weight(1f))
                if (tilesAllowed) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        // OpenStreetMap asks to be named wherever its tiles are shown large.
                        MapCaption("© OpenStreetMap contributors", Modifier.padding(end = 8.dp))
                        Spacer(Modifier.weight(1f))
                        MapControls(mapState)
                    }
                }
            }
        }
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
    var loaded by remember { mutableStateOf(false) }
    var expandedMap by remember { mutableStateOf(false) }

    LaunchedEffect(runId) {
        // Wait for the tracking service to close the activity before reading it.
        //
        // This screen is opened by the very tap that finishes the run, and the service writes the
        // distance, the moving time and the fastest kilometre from its own coroutine. Reading once,
        // immediately, gets the row as it looked when the run *started* — every field zero — and
        // that is exactly what a finished run used to show. The row is also deleted here when the
        // activity recorded nothing, so a row that vanishes ends the wait too and the screen says
        // so instead of spinning.
        var entity = repo.dao.run(runId)
        var waitedMs = 0L
        while (entity != null && entity.endedAtMs == null && waitedMs < FINISH_WAIT_MS) {
            delay(FINISH_POLL_MS)
            waitedMs += FINISH_POLL_MS
            entity = repo.dao.run(runId)
        }
        run = if (entity == null) null else repo.getRun(runId)
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
        loaded = true
    }

    ScreenScaffold(title = "Activity", onBack = { nav.popBackStack() }) { padding ->
        val r = run
        if (r == null) {
            // Not every id still has an activity behind it. One that recorded nothing is deleted
            // at the finish line, and this screen is opened by the same tap that finished it, so
            // saying so here is the difference between an explanation and a spinner that never
            // resolves.
            if (loaded) {
                EmptyState(
                    "Nothing to save",
                    "That activity did not move far enough to record, so it was discarded.",
                    Modifier.padding(padding),
                )
            } else {
                EmptyState("Loading", "Reading the activity…", Modifier.padding(padding))
            }
            return@ScreenScaffold
        }
        val tilesOn = settings.mapTiles && settings.mapTilesAsked
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dateLabel(r.startedAtMs), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                if (r.kind != com.clashfit.data.ActivityKind.RUN) {
                    Tag(r.title, color = if (r.isWalk) Rival else Gassed)
                }
            }
            SectionGap(12)

            if (points.size >= 2) {
                // Deliberately not interactive here.
                //
                // This map lives inside a column that scrolls, and a map that answers to a drag
                // eats the drag: the page then refuses to scroll past it, which reads as a frozen
                // screen rather than as a map doing its job. So the inline one is a picture, and
                // tapping it opens a real one that owns the whole display and every gesture.
                AppCard(Modifier.fillMaxWidth(), padding = 0) {
                    Box {
                        ActivityMap(
                            points = points,
                            tilesAllowed = tilesOn,
                            // Tall enough to be a map rather than a stamp. Two thirds of a phone
                            // screen is roughly what every other running app gives a finished route.
                            modifier = Modifier.fillMaxWidth().height(380.dp),
                            interactive = false,
                        )
                        // The tap target has to sit *above* the map rather than around it.
                        //
                        // A MapView is an Android View and it swallows every touch that reaches it,
                        // whether or not multi-touch controls are switched on, so a clickable on
                        // the card behind it never fires — the caption said "tap to open" and
                        // nothing happened. A transparent Compose surface drawn after the map wins
                        // the hit test, and forwards the tap to the full-screen map.
                        Box(
                            Modifier.matchParentSize()
                                .clickable(
                                    onClickLabel = "Open the map full screen",
                                    onClick = { expandedMap = true },
                                ),
                        )
                        MapCaption(
                            "Tap to open the map",
                            Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        )
                    }
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
                val (distance, distanceUnit) = distanceParts(r.distanceM)
                StatTile(distance, distanceUnit, Modifier.weight(1f))
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
                // An activity recorded before the step counter existed has neither number to show,
                // and "0 spm" reads as a broken sensor rather than as an absent one.
                val effort = when {
                    r.steps > 0 -> "${r.steps}" to "Steps"
                    r.cadenceSpm > 0 -> "${r.cadenceSpm} spm" to "Cadence"
                    else -> "—" to "Cadence"
                }
                StatTile(effort.first, effort.second, Modifier.weight(1f))
            }

            SectionGap(20)
            PrimaryButton("Share this activity", Modifier.fillMaxWidth()) {
                if (settings.shareNoticeSeen) sharing = true else showShareNotice = true
            }

            val profile = remember(points) { normaliseProfile(elevationProfile(points, buckets = 48)) }
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
                if (settings.mapTiles && settings.mapTilesAsked) {
                    "The route is stored on this phone. Map tiles were fetched from OpenStreetMap to draw the streets."
                } else {
                    "The route never left this phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )
        }
    }

    if (expandedMap) {
        FullScreenMap(
            points = points,
            tilesAllowed = settings.mapTiles && settings.mapTilesAsked,
            onClose = { expandedMap = false },
        )
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
                title = r.title,
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
                effortLabel = if (r.isWalk) "STEADY" else if (r.isZombieRun) "CHASED" else "PUSHED",
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

    val text = "${formatDistance(stats.distanceM)} with ClashFit — every rep graded by the camera. " +
        "clash-fit.vercel.app"

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
