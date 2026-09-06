package com.clashfit.ui.screens.modes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.ui.components.ModeCard
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.label
import com.clashfit.ui.nav.DuelLobby
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.ZombieRun

/**
 * The full catalogue: every mode of every kind, including the ones the home carousel does not
 * show (Tug of War, Mirror Match, Raid, Team vs Team, and the online-only Zombie Run, disabled).
 * One lazy grid with full-span headers; a lazy grid inside a scrolling column is not allowed.
 */
@Composable
fun ModesScreen(nav: NavHostController) {
    ScreenScaffold(title = "All modes", onBack = { nav.navigateUp() }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (kind in ModeKind.entries) {
                val modes = GameMode.entries.filter { it.kind == kind }
                if (modes.isEmpty()) continue
                item(key = "kind-${kind.name}", span = { GridItemSpan(2) }) {
                    SectionTitle(kind.label(), Modifier.padding(top = 12.dp, bottom = 2.dp))
                }
                items(modes, key = { it.name }) { mode ->
                    ModeCard(
                        mode,
                        onClick = { nav.navigate(modeDestination(mode)) },
                    )
                }
            }
        }
    }
}

/**
 * Where a mode tile goes.
 *
 * Zombie Run picks no exercise: it is a chase, and the picker would ask a question the mode has
 * no answer to. A versus mode does not pick one here either — the phones have to agree on the
 * movement, and only the host gets to say, so the choice happens inside the lobby once a phone
 * has claimed that role.
 */
fun modeDestination(mode: GameMode): Route = when {
    mode == GameMode.OUTBREAK -> ZombieRun
    mode.kind == ModeKind.VERSUS -> DuelLobby(mode.name)
    else -> ExercisePicker(mode.name)
}

fun NavGraphBuilder.modesRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Modes> { ModesScreen(nav) }
}
