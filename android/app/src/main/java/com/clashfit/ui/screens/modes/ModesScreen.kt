package com.clashfit.ui.screens.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** Sixteen tiles grouped by kind: SOLO, VERSUS, GROUP, FAMILY, CLINIC. */
@Composable
fun ModesScreen(nav: NavHostController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("CHOOSE", accent = "YOUR FIGHT")
        SectionGap(24)

        ModeSection("Solo", GameMode.grid.filter { it.kind == ModeKind.SOLO }, nav)
        SectionGap()
        ModeSection("Versus", GameMode.grid.filter { it.kind == ModeKind.VERSUS }, nav)
        SectionGap()
        ModeSection("Group", GameMode.grid.filter { it.kind == ModeKind.GROUP }, nav)
        SectionGap()
        ModeSection("Family", GameMode.grid.filter { it.kind == ModeKind.FAMILY }, nav)
        SectionGap()
        ModeSection("Clinic", GameMode.grid.filter { it.kind == ModeKind.CLINIC }, nav)
        SectionGap()
    }
}

@Composable
private fun ModeSection(label: String, modes: List<GameMode>, nav: NavHostController) {
    Column(Modifier.fillMaxWidth()) {
        Kicker(label)
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(modes) { mode ->
                ModeTile(mode, nav)
            }
        }
    }
}

@Composable
private fun ModeTile(mode: GameMode, nav: NavHostController, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .clickable { nav.navigate(ExercisePicker(mode.name)) }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(mode.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Ink)
                Text("→", style = MaterialTheme.typography.titleMedium, color = Ink)
            }
            Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = InkFaint, maxLines = 2)
        }
    }
}

fun NavGraphBuilder.modesRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Modes> {
        ModesScreen(nav)
    }
}
