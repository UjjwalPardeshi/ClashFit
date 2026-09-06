package com.clashfit.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.data.Prefs
import com.clashfit.ui.components.EmberButton
import com.clashfit.core.model.GameMode
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SwitchRow
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.map.MapTiles
import com.clashfit.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.clashfit.ui.nav.BossPreview
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.AppIcons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect

/** Every preference, config version and reload, model presence, and the one destructive action. */
@Composable
fun SettingsScreen(graph: AppGraph, nav: NavHostController) {
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val configVersion by graph.config.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val modelInstalled = remember { graph.llmEngine.modelInstalled }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Read off the main thread: walking the tile cache touches the filesystem, and this screen is
    // opened mid-event on a phone that may be busy.
    var tileCacheBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(settings.mapTiles) {
        tileCacheBytes = withContext(Dispatchers.IO) { MapTiles.cacheBytes(context) }
    }

    ScreenScaffold(title = "Settings", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Kicker("Display")
            SectionGap(4)
            SwitchRow("Reduce motion", settings.reduceMotion, { v -> scope.launch { graph.prefs.setReduceMotion(v) } },
                supporting = "Swaps shakes and flashes for a border pulse")
            SwitchRow("Tracking overlay", settings.debugOverlay, { v -> scope.launch { graph.prefs.setDebugOverlay(v) } },
                supporting = "Draws the points and angles the camera is measuring, live over your body")
            SwitchRow("3D boss", settings.boss3d, { v -> scope.launch { graph.prefs.setBoss3d(v) } },
                supporting = "Off draws the boss in flat shapes instead. Same fight either way.")
            NavRow("Meet the boss", { nav.navigate(BossPreview) }, icon = AppIcons.Bolt,
                supporting = "The Pacemaker, in every state it fights you in")

            SectionGap(28)
            Kicker("Audio and haptics")
            SectionGap(4)
            SwitchRow("Haptics", settings.haptics, { v -> scope.launch { graph.prefs.setHaptics(v) } },
                supporting = "Every counted rep, confirmed in the hand")
            SwitchRow("Voice commands", settings.voiceCommands, { v -> scope.launch { graph.prefs.setVoiceCommands(v) } },
                supporting = "Offline. Nothing is sent anywhere.")

            SectionGap(28)
            Kicker("Gameplay")
            SectionGap(4)
            SwitchRow("Casual mode", settings.casual, { v -> scope.launch { graph.prefs.setCasual(v) } },
                supporting = "The boss cannot win. Sets still count.")
            SwitchRow("Arena mode", settings.arenaMode, { v -> scope.launch { graph.prefs.setArenaMode(v) } },
                supporting = "Bigger numerals, for a phone on the floor")
            SwitchRow("Hand gestures", settings.gestures, { v -> scope.launch { graph.prefs.setGestures(v) } },
                supporting = "Open palm pauses. Thumb up ends the set. Fist skips the rest. Read from the camera, on the phone.")

            SectionGap(28)
            Kicker("Outdoors")
            SectionGap(4)
            SwitchRow(
                "Street maps",
                settings.mapTiles,
                { v -> scope.launch { graph.prefs.setMapTiles(v) } },
                supporting = "Downloads OpenStreetMap tiles under your route. Asking for tiles tells " +
                    "their servers roughly where you were. Off draws the route on our own grid, with " +
                    "the radio off. Your route never leaves this phone either way.",
            )
            if (settings.mapTiles) {
                RuleRow("Cached map tiles", formatBytes(tileCacheBytes))
                NavRow(
                    "Clear map tile cache",
                    {
                        scope.launch {
                            withContext(Dispatchers.IO) { MapTiles.clearCache(context) }
                            tileCacheBytes = withContext(Dispatchers.IO) { MapTiles.cacheBytes(context) }
                        }
                    },
                    supporting = "The map still works; it fetches again as you look at it.",
                )
            }

            SectionGap(28)
            Kicker("Configuration")
            SectionGap(4)
            RuleRow("Coach", if (modelInstalled) "Runs on this phone" else "Built-in coaching")
            SwitchRow("Cloud coach", settings.cloudCoach, { v -> scope.launch { graph.prefs.setCloudCoach(v) } },
                supporting = "When the on-device coach is not installed, send the set's numbers to a cloud model for its lines. Never a frame, never a landmark. Off keeps everything on the phone.")

            SectionGap(28)
            Kicker("Advanced")
            SectionGap(4)
            NavRow(
                "Replay a recorded set",
                {
                    nav.navigate(
                        com.clashfit.ui.nav.Session(
                            mode = GameMode.BOSS_FIGHT.name,
                            exerciseId = "squat",
                            replay = true,
                        ),
                    )
                },
                icon = AppIcons.Bolt,
                supporting = "A real squat set, recorded and played back through the same scoring. " +
                    "Labelled REPLAY throughout. Use it when the light beats the camera.",
            )
            SectionGap(12)
            OutlineButton("Reload exercise data", Modifier.fillMaxWidth()) { graph.config.reload() }

            SectionGap(36)
            Kicker("Danger zone", color = Gassed)
            SectionGap(8)
            Text(
                "Removes every session, streak, run and alarm on this phone. Nothing is backed up anywhere, so this cannot be undone.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
            SectionGap(12)
            EmberButton("Clear all data", Modifier.fillMaxWidth()) { confirmClear = true }
            SectionGap(32)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = InkMuted,
            title = { Text("CLEAR ALL DATA?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Every session, streak, run and alarm on this phone will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    // App-scoped, so leaving the screen mid-wipe cannot cancel it half way.
                    graph.scope.launch { withContext(Dispatchers.IO) { graph.db.clearAllTables() } }
                }) { Text("DELETE", color = Gassed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("KEEP", color = Ember) }
            },
        )
    }
}

fun NavGraphBuilder.settingsRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Settings> { SettingsScreen(graph, nav) }
}

/** Bytes as a human reads them. Only the map cache needs this, so it lives here. */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "empty"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}
