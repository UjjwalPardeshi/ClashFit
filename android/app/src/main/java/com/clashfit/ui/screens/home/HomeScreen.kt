package com.clashfit.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.ui.nav.Alarms
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.nav.Challenge
import com.clashfit.ui.nav.Character
import com.clashfit.ui.nav.Clinic
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.Ghosts
import com.clashfit.ui.nav.History
import com.clashfit.ui.nav.Library
import com.clashfit.ui.nav.Preflight
import com.clashfit.ui.nav.Privacy
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.Session
import com.clashfit.ui.nav.Settings
import com.clashfit.ui.nav.Streaks
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** Everything that is not a fight, one tap from Home. Order is the order of use. */
private val SHELL: List<Pair<String, Route>> = listOf(
    "RUN" to RunHome, "ALARMS" to Alarms, "LIBRARY" to Library, "STREAKS" to Streaks, "HISTORY" to History,
    "CHARACTER" to Character, "GHOSTS" to Ghosts, "CHALLENGE" to Challenge, "BREATHE" to Breathing,
    "CLINIC" to Clinic, "PREFLIGHT" to Preflight, "PRIVACY" to Privacy, "SETTINGS" to Settings,
)

/**
 * Get into a fight in one tap. FIGHT is the largest thing on screen; the sixteen modes sit
 * under it, all visible, nothing behind a menu. docs/02-APP-FLOW.md §2
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("YOUR BODY IS\nTHE CONTROLLER.", style = MaterialTheme.typography.displaySmall)
        Text("YOUR CAMERA IS THE REFEREE.", style = MaterialTheme.typography.displaySmall, color = Ember)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().background(Ember)
                .clickable { nav.navigate(Session(mode = GameMode.BOSS_FIGHT.name, exerciseId = "squat")) }
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FIGHT", style = MaterialTheme.typography.headlineLarge, color = Ground)
            Text("SQUAT · BOSS FIGHT", style = MaterialTheme.typography.labelLarge, color = Ground)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems(SHELL) { (label, route) ->
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = Ink,
                    modifier = Modifier.border(1.dp, Rule).clickable { nav.navigate(route) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("${GameMode.grid.size} WAYS IN · ${exercises.size} EXERCISES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(GameMode.grid) { mode ->
                Column(
                    Modifier.background(Panel).border(1.dp, Rule)
                        .clickable { nav.navigate(ExercisePicker(mode.name)) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(mode.kind.name, style = MaterialTheme.typography.labelSmall, color = Ember)
                    Text(mode.title.uppercase(), style = MaterialTheme.typography.headlineSmall)
                    Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}
