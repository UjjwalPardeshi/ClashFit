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
import com.clashfit.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.clashfit.ui.nav.BossPreview
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.AppIcons

/** Every preference, config version and reload, model presence, and the one destructive action. */
@Composable
fun SettingsScreen(graph: AppGraph, nav: NavHostController) {
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val configVersion by graph.config.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val modelInstalled = remember { graph.llmEngine.modelInstalled }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Settings", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Kicker("Display")
            SectionGap(4)
            SwitchRow("Reduce motion", settings.reduceMotion, { v -> scope.launch { graph.prefs.setReduceMotion(v) } },
                supporting = "Swaps shakes and flashes for a border pulse")
            SwitchRow("Debug overlay", settings.debugOverlay, { v -> scope.launch { graph.prefs.setDebugOverlay(v) } },
                supporting = "Landmarks and angles drawn over the camera")
            SwitchRow("3D boss", settings.boss3d, { v -> scope.launch { graph.prefs.setBoss3d(v) } },
                supporting = "Off draws the boss in flat shapes instead. Same fight either way.")
            NavRow("Boss preview", { nav.navigate(BossPreview) }, icon = AppIcons.Bolt,
                supporting = "Every state, without starting a fight")

            SectionGap(28)
            Kicker("Audio and haptics")
            SectionGap(4)
            SwitchRow("Haptics", settings.haptics, { v -> scope.launch { graph.prefs.setHaptics(v) } },
                supporting = "Every counted rep, confirmed in the hand")
            SwitchRow("Speech", settings.speech, { v -> scope.launch { graph.prefs.setSpeech(v) } },
                supporting = "The coach and the boss, out loud")
            SwitchRow("Voice commands", settings.voiceCommands, { v -> scope.launch { graph.prefs.setVoiceCommands(v) } },
                supporting = "Offline. Nothing is sent anywhere.")

            SectionGap(28)
            Kicker("Gameplay")
            SectionGap(4)
            SwitchRow("Casual mode", settings.casual, { v -> scope.launch { graph.prefs.setCasual(v) } },
                supporting = "The boss cannot win. Sets still count.")
            SwitchRow("Arena mode", settings.arenaMode, { v -> scope.launch { graph.prefs.setArenaMode(v) } },
                supporting = "Bigger numerals, for a phone on the floor")

            SectionGap(28)
            Kicker("Configuration")
            SectionGap(4)
            RuleRow("Config version", "$configVersion")
            RuleRow("Coach model", if (modelInstalled) "Installed" else "Not installed · templates speak")
            SectionGap(12)
            OutlineButton("Reload config", Modifier.fillMaxWidth()) { graph.config.reload() }

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
