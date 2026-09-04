package com.clashfit.ui.screens.ghosts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.GhostData
import com.clashfit.core.model.GameMode
import com.clashfit.data.GhostEntity
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.nav.Ghosts
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import kotlinx.coroutines.launch

/**
 * Every ghost you can race: the ones you recorded, and the pacers that ship in the APK.
 * A ghost is a rep timeline, nothing more — racing one is a normal Ghost Race session
 * that resolves `ghostId` against the database first and `config/ghosts` second.
 */
@Composable
fun GhostsScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val dao = remember(graph) { graph.db.ghosts() }
    val saved by remember(dao) { dao.all() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val shipped by graph.config.ghosts.collectAsStateWithLifecycle()
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Ghosts", onBack = { nav.navigateUp() }) { padding ->
        Column(
            modifier
                .fillMaxWidth().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Kicker("Your ghosts")
            SectionGap(12)
            if (saved.isEmpty()) {
                EmptyState("Nothing saved", "Finish a fight and it becomes a ghost.")
            } else {
                ListGroup {
                    saved.forEachIndexed { index, ghost ->
                        SavedGhostRow(
                            ghost = ghost,
                            exerciseTitle = exercises.title(ghost.exerciseId),
                            onRace = { nav.navigate(raceRoute(ghost.exerciseId, ghost.id)) },
                            onDelete = { scope.launch { dao.delete(ghost) } },
                        )
                        if (index < saved.size - 1) InnerDivider()
                    }
                }
            }

            SectionGap(26)

            Kicker("Shipped pacers")
            SectionGap(12)
            if (shipped.isEmpty()) {
                EmptyState("No pacers bundled", "Add more challenge codes to unlock pacers.")
            } else {
                // Easiest first. A config map has no order of its own, so the bronze/silver/gold
                // ladder arrived shuffled and read as if the tiers meant nothing.
                val ladder = shipped.entries.sortedBy { it.value.meta.reps }
                ListGroup {
                    ladder.forEachIndexed { index, (id, data) ->
                        ShippedGhostRow(
                            data = data,
                            exerciseTitle = exercises.title(data.meta.exercise),
                            onRace = { nav.navigate(raceRoute(data.meta.exercise, id)) },
                        )
                        if (index < ladder.size - 1) InnerDivider()
                    }
                }
            }

            SectionGap(20)
        }
    }
}

/** One saved ghost: what it did, and the two things you can do to it. */
@Composable
private fun SavedGhostRow(
    ghost: GhostEntity,
    exerciseTitle: String,
    onRace: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirming by remember(ghost.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onRace)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(AppIcons.Bolt)
        Column(Modifier.weight(1f)) {
            Text(ghost.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(
                "$exerciseTitle · ${ghost.reps} reps",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (confirming) "Sure?" else "Delete",
                style = MaterialTheme.typography.labelMedium,
                color = if (confirming) Ember else InkMuted,
                modifier = Modifier
                    .clickable(enabled = true) { if (confirming) onDelete() else confirming = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** A bundled pacer. Read-only: you race it, you never delete it. */
@Composable
private fun ShippedGhostRow(data: GhostData, exerciseTitle: String, onRace: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onRace)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(AppIcons.Bolt)
        Column(Modifier.weight(1f)) {
            Text(data.meta.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(
                "$exerciseTitle · ${data.meta.reps} reps",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            "Race",
            style = MaterialTheme.typography.labelMedium,
            color = Ember,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Ghost races always start the same way; the session resolves the id, not this screen. */
private fun raceRoute(exerciseId: String, ghostId: String): Session =
    Session(mode = GameMode.GHOST_RACE.name, exerciseId = exerciseId, ghostId = ghostId)

/** The exercise index carries the display name; fall back to the raw id rather than blank. */
internal fun Map<String, ExerciseSpec>.title(exerciseId: String): String =
    this[exerciseId]?.name ?: exerciseId

fun NavGraphBuilder.ghostsRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Ghosts> { GhostsScreen(graph, nav) }
}
