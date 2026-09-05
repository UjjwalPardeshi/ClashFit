package com.clashfit.ui.screens.outbreak

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.GameOutcome
import com.clashfit.core.model.OutbreakPhase
import com.clashfit.data.ActivityKind
import com.clashfit.engine.games.OutbreakGame
import com.clashfit.map.MapMarker
import com.clashfit.map.RouteMap
import com.clashfit.run.RunTracker
import com.clashfit.run.RunTrackingService
import com.clashfit.run.metresEastNorth
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.RouteTrace
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * OUTBREAK — the outdoor chase, on a real map. `docs/33-FEATURE-OUTBREAK.md`.
 *
 * The mode is a layer over the ordinary run tracker rather than a second location pipeline: the
 * service is already the one thing allowed to hold a GPS subscription, it already filters bad
 * fixes, and running the chase on top of it means an Outbreak also *is* a recorded activity — it
 * shows up in the feed, it earns its distance, and its route is stored exactly like any other.
 *
 * Two rules from the design document are enforced here rather than promised:
 *
 * - **The camera never opens.** Nothing on this screen touches it, so the video promise holds
 *   without qualification even in the one mode that goes online.
 * - **The mode announces itself** before the head start, in words, every time. Not a permission
 *   dialog, and not once-ever-then-forgotten: this is the one mode that downloads map tiles and
 *   the player is told each time they choose it.
 */
@Composable
fun OutbreakScreen(graph: AppGraph, nav: NavHostController) {
    val context = LocalContext.current
    val tracker = RunTracker.getInstance()
    val runState by tracker.state.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = com.clashfit.data.Prefs.Settings())

    val cfg = graph.config.combat.value.modes.outbreak
    val game = remember {
        OutbreakGame(
            OutbreakGame.OutbreakConfig(
                headStartSec = cfg.headStartSec,
                spawnRadiusM = cfg.spawnRadiusM,
                captureRadiusM = cfg.captureRadiusM,
                ammoPickups = cfg.ammoPickups,
            ),
        )
    }

    var started by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var state by remember { mutableStateOf(game.state(0L)) }
    // The last position we were sure of, held here rather than read from the tracker each tick.
    // The service flushes its point buffer to the database every ten fixes and starts a new one,
    // so `state.points` empties roughly once a second — reading the position straight out of it
    // would stall the chase for a tick every time that happened, and a pack that freezes whenever
    // the database is written is a pack you can escape by waiting.
    var lastEastM by remember { mutableStateOf(0f) }
    var lastNorthM by remember { mutableStateOf(0f) }

    val locationOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    // The chase runs on its own clock at 10 Hz. GPS delivers about one fix a second, which is far
    // too coarse for a pursuer to look alive; between fixes the pack keeps closing on the last
    // known position, which is also what it would really do.
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (true) {
            val here = runState.points.lastOrNull()
            if (here != null) {
                if (origin == null) origin = here.lat to here.lon
                origin?.let { o ->
                    val local = metresEastNorth(o.first, o.second, here.lat, here.lon)
                    lastEastM = local.eastM
                    lastNorthM = local.northM
                }
            }
            // Tick every time, fix or no fix. Losing the signal must not pause the chase.
            state = game.onTick(graph.clock.nowMs(), lastEastM, lastNorthM, runState.cadenceSpm)
            if (state.outcome != GameOutcome.RUNNING) break
            delay(100)
        }
    }

    ScreenScaffold(title = "Outbreak", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Ground)
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
        ) {
            if (!started) {
                Briefing(
                    cfg = cfg,
                    locationOk = locationOk,
                    onStart = {
                        RunTrackingService.start(context, ActivityKind.RUN)
                        // Seeded from the wall clock so two players never get the same layout, and
                        // so a chase could be replayed from its seed later.
                        game.start(graph.clock.nowMs(), graph.clock.nowMs())
                        started = true
                    },
                )
                return@Column
            }

            ChaseHud(state)
            SectionGap(12)

            val o = origin
            val markers = if (o == null) emptyList() else buildList {
                state.caches.filter { !it.taken }.forEach {
                    add(marker(o, it.eastM, it.northM, radiusM = 14.0, colour = Success, filled = false))
                }
                state.pursuers.filter { it.alive }.forEach {
                    add(marker(o, it.eastM, it.northM, radiusM = 18.0, colour = Gassed, filled = true))
                }
            }

            AppCard(Modifier.fillMaxWidth(), padding = 0) {
                if (settings.mapTiles && runState.points.isNotEmpty()) {
                    RouteMap(
                        points = runState.points,
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        paceColoured = false,
                        follow = true,
                        interactive = false,
                        extraOverlays = markers,
                    )
                } else {
                    // No tiles: the route still draws, and the chase is still legible from the
                    // numbers above it. A map is an aid here, not a requirement.
                    RouteTrace(
                        points = runState.points,
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        paceColoured = false,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Outcome(state, onFinish = {
                RunTrackingService.stop(context)
                nav.navigateUp()
            })
        }
    }
}

/** Converts a chase coordinate back to latitude and longitude for the map. */
private fun marker(
    origin: Pair<Double, Double>,
    eastM: Float,
    northM: Float,
    radiusM: Double,
    colour: androidx.compose.ui.graphics.Color,
    filled: Boolean,
): MapMarker {
    val mPerDegLat = 111_320.0
    val mPerDegLon = mPerDegLat * cos(origin.first * PI / 180.0)
    return MapMarker(
        lat = origin.first + northM / mPerDegLat,
        lon = origin.second + eastM / mPerDegLon,
        radiusM = radiusM,
        fill = colour.copy(alpha = if (filled) 0.55f else 0f),
        outline = colour,
        filled = filled,
    )
}

@Composable
private fun Briefing(
    cfg: com.clashfit.core.config.CombatConfig.ModesSpec.OutbreakSpec,
    locationOk: Boolean,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().safeDrawingPadding()) {
        SectionGap(8)
        Text("A real chase, on a real map", style = MaterialTheme.typography.headlineSmall, color = Ink)
        SectionGap(10)
        Text(
            "You get ${cfg.headStartSec} seconds. Then pursuers spawn within ${cfg.spawnRadiusM} metres and " +
                "start closing. They move faster when your cadence drops, so stopping is what gets you " +
                "caught — not standing in the wrong place. ${cfg.ammoPickups} caches are scattered around " +
                "you; reaching one arms you, and a pursuer that gets within ${cfg.captureRadiusM} metres is " +
                "spent against a round if you have one.",
            style = MaterialTheme.typography.bodyMedium, color = InkMuted,
        )
        SectionGap(18)
        AppCard(Modifier.fillMaxWidth(), padding = 16) {
            Column {
                Text("This is the one mode that goes online", style = MaterialTheme.typography.titleSmall, color = Ember)
                Spacer(Modifier.height(6.dp))
                Text(
                    "It uses your location and downloads map tiles. The camera never opens, so no frame " +
                        "and no landmark exists to leave the phone, and your route is stored here like any " +
                        "other run. Every other mode still works with the radio off.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }
        }
        SectionGap(18)
        if (locationOk) {
            PrimaryButton("Start the head start", Modifier.fillMaxWidth(), onClick = onStart)
        } else {
            Text(
                "Outbreak needs location. Grant it from the run tracker, then come back.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint,
            )
        }
    }
}

@Composable
private fun ChaseHud(state: com.clashfit.core.model.OutbreakState) {
    when (state.phase) {
        OutbreakPhase.HEAD_START -> {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HEAD START", style = MaterialTheme.typography.labelLarge, color = Ember)
                Text(
                    "${state.headStartLeftSec.roundToInt()}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp), color = Ink,
                )
                Text("Move. They are already here.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        OutbreakPhase.CHASE -> {
            val nearest = state.nearestPursuerM
            val danger = nearest != null && nearest < 60f
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (nearest == null) "CLEAR" else "NEAREST",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (danger) Gassed else InkMuted,
                )
                Text(
                    nearest?.let { "${it.roundToInt()} m" } ?: "—",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp),
                    color = if (danger) Gassed else Ink,
                )
            }
            SectionGap(8)
            StatStrip(
                listOf(
                    "${state.ammo}" to "Ammo",
                    "${state.pursuersLeft}" to "Chasing",
                    "${(state.secondsLeft / 60).roundToInt()}m" to "Left",
                ),
            )
        }
        OutbreakPhase.ESCAPED, OutbreakPhase.CAUGHT -> {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.phase == OutbreakPhase.ESCAPED) "ESCAPED" else "CAUGHT",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (state.phase == OutbreakPhase.ESCAPED) Success else Gassed,
                )
                Text(
                    "%.2f km · %d caches · %d down".format(
                        state.distanceM / 1000f, state.cachesCollected, state.neutralised,
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Outcome(state: com.clashfit.core.model.OutbreakState, onFinish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.outcome == GameOutcome.RUNNING) {
            SecondaryButton("Give up", Modifier.fillMaxWidth(), onClick = onFinish)
        } else {
            PrimaryButton("Finish", Modifier.fillMaxWidth(), onClick = onFinish)
        }
    }
}
