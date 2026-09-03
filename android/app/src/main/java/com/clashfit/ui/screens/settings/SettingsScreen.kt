package com.clashfit.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import java.io.File

/** Every Prefs toggle, config version + reload, model file presence, clear data. */
@Composable
fun SettingsScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val settings by graph.prefs.settings.collectAsState(initial = com.clashfit.data.Prefs.Settings())
    val configVersion by graph.config.version.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    val modelFile = File(graph.app.filesDir, "models/gemma-3n-e2b-int4.task")

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("SETTINGS")
        SectionGap(24)

        // Display toggles
        Kicker("Display")
        SectionGap(12)
        ToggleRow("Reduce Motion", settings.reduceMotion) {
            scope.launch { graph.prefs.setReduceMotion(it) }
        }
        ToggleRow("Debug Overlay", settings.debugOverlay) {
            scope.launch { graph.prefs.setDebugOverlay(it) }
        }

        SectionGap(28)

        // Sound and haptics
        Kicker("Audio & Haptics")
        SectionGap(12)
        ToggleRow("Haptics", settings.haptics) {
            scope.launch { graph.prefs.setHaptics(it) }
        }
        ToggleRow("Speech", settings.speech) {
            scope.launch { graph.prefs.setSpeech(it) }
        }
        ToggleRow("Voice Commands", settings.voiceCommands) {
            scope.launch { graph.prefs.setVoiceCommands(it) }
        }

        SectionGap(28)

        // Game settings
        Kicker("Gameplay")
        SectionGap(12)
        ToggleRow("Casual Mode", settings.casual) {
            scope.launch { graph.prefs.setCasual(it) }
        }
        ToggleRow("Arena Mode", settings.arenaMode) {
            scope.launch { graph.prefs.setArenaMode(it) }
        }

        SectionGap(28)

        // Config
        Kicker("Configuration")
        SectionGap(12)
        RuleRow("Config Version", "$configVersion")
        OutlineButton("RELOAD CONFIG") {
            graph.config.reload()
        }

        SectionGap(12)
        RuleRow("Model File", if (modelFile.exists()) "INSTALLED" else "MISSING")

        SectionGap(28)

        // Danger zone
        Kicker("Danger Zone")
        SectionGap(12)
        EmberButton("CLEAR ALL DATA") {
            scope.launch {
                // Would clear database and prefs
                // graph.db.clearAllTables()
            }
        }

        SectionGap(20)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .clickable { onToggle(!value) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
        Box(
            Modifier
                .height(24.dp)
                .padding(2.dp)
                .background(if (value) Fresh else Rule)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (value) {
                Text("ON", style = MaterialTheme.typography.labelSmall, color = Ink)
            }
        }
    }
}

fun NavGraphBuilder.settingsRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Settings> {
        SettingsScreen(graph)
    }
}
