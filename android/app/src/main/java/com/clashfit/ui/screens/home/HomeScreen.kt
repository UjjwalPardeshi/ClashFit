package com.clashfit.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/**
 * The Train tab. Get into a fight in one tap: FIGHT is the largest thing on screen and the
 * sixteen modes sit under it, grouped by how many people they need. Everything that is not a
 * fight lives on the other three tabs now, not in a scrolling row of chips. docs/02-APP-FLOW.md §2
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = com.clashfit.data.Prefs.Settings())
    val preferred = exercises[settings.preferredExercise]?.name ?: settings.preferredExercise.replace('_', ' ')

    ScreenScaffold(title = "ClashFit") { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text("YOUR BODY IS\nTHE CONTROLLER.", style = MaterialTheme.typography.headlineLarge, color = Ink)
                    Text("YOUR CAMERA IS THE REFEREE.", style = MaterialTheme.typography.headlineLarge, color = Ember)
                    Spacer(Modifier.height(18.dp))
                    FightCard(preferred) {
                        nav.navigate(Session(mode = GameMode.BOSS_FIGHT.name, exerciseId = settings.preferredExercise))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${GameMode.grid.size} ways in · ${exercises.size} exercises".uppercase(),
                        style = MaterialTheme.typography.labelMedium, color = InkMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            for (kind in listOf(ModeKind.SOLO, ModeKind.VERSUS, ModeKind.GROUP, ModeKind.FAMILY, ModeKind.CLINIC)) {
                val modes = GameMode.grid.filter { it.kind == kind }
                if (modes.isEmpty()) continue
                item(key = "kind-${kind.name}", span = { GridItemSpan(2) }) {
                    Kicker(kind.label(), Modifier.padding(top = 14.dp, bottom = 2.dp))
                }
                items(modes, key = { it.name }) { mode ->
                    ModeCard(mode) { nav.navigate(ExercisePicker(mode.name)) }
                }
            }
        }
    }
}

private fun ModeKind.label(): String = when (this) {
    ModeKind.SOLO -> "Alone"
    ModeKind.VERSUS -> "Head to head"
    ModeKind.GROUP -> "A room"
    ModeKind.FAMILY -> "By movement"
    ModeKind.CLINIC -> "Clinic"
}

@Composable
private fun FightCard(exerciseName: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ember).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("FIGHT", style = MaterialTheme.typography.headlineLarge, color = Ground)
            Text("${exerciseName.uppercase()} · BOSS FIGHT", style = MaterialTheme.typography.labelLarge, color = Ground)
        }
        Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun ModeCard(mode: GameMode, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 132.dp).background(Panel).border(1.dp, Rule)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(mode.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(
            mode.blurb, style = MaterialTheme.typography.bodySmall, color = InkMuted,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}
