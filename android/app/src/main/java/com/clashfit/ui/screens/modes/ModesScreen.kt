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
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** Every mode, grouped by kind: SOLO, VERSUS, GROUP, FAMILY, CLINIC. */
@Composable
fun ModesScreen(nav: NavHostController, modifier: Modifier = Modifier) {
    ScreenScaffold(title = "Choose your fight", onBack = { nav.navigateUp() }) { padding ->
        Column(modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            // Every mode of each kind, not just the sixteen tiles on the site's fixed home grid
            // (GameMode.grid) — this screen is the full catalogue, e.g. Versus also includes
            // TUG_OF_WAR and MIRROR_MATCH, and Group also includes RAID and TEAM_VS_TEAM.
            ModeSection("Solo", GameMode.entries.filter { it.kind == ModeKind.SOLO }, nav)
            SectionGap()
            ModeSection("Versus", GameMode.entries.filter { it.kind == ModeKind.VERSUS }, nav)
            SectionGap()
            ModeSection("Group", GameMode.entries.filter { it.kind == ModeKind.GROUP }, nav)
            SectionGap()
            ModeSection("Family", GameMode.entries.filter { it.kind == ModeKind.FAMILY }, nav)
            SectionGap()
            ModeSection("Clinic", GameMode.entries.filter { it.kind == ModeKind.CLINIC }, nav)
            SectionGap()
        }
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
            .background(if (mode.enabled) Panel else InkFaint.copy(alpha = 0.1f))
            .border(1.dp, if (mode.enabled) Rule else InkMuted.copy(alpha = 0.3f))
            .clickable(enabled = mode.enabled) { nav.navigate(ExercisePicker(mode.name)) }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(mode.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (mode.enabled) Ink else InkMuted)
                Text("→", style = MaterialTheme.typography.titleMedium, color = if (mode.enabled) Ink else InkMuted)
            }
            Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = if (mode.enabled) InkFaint else InkMuted.copy(alpha = 0.7f), maxLines = 2)
            if (!mode.enabled) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Tag("GOES ONLINE · NOT IN THIS BUILD", color = InkMuted.copy(alpha = 0.7f))
                }
            }
        }
    }
}

fun NavGraphBuilder.modesRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Modes> {
        ModesScreen(nav)
    }
}
