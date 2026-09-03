package com.clashfit.ui.screens.ghosts

import androidx.compose.foundation.background
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
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.Ghosts
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Kicker("RACE THE PAST")
        SectionGap(10)
        Headline("THE", accent = "GHOSTS")
        SectionGap(24)

        Kicker("YOUR GHOSTS")
        SectionGap(12)
        if (saved.isEmpty()) {
            EmptyState("Nothing saved", "Finish a fight and it becomes a ghost.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                saved.forEach { ghost ->
                    SavedGhostRow(
                        ghost = ghost,
                        exerciseTitle = exercises.title(ghost.exerciseId),
                        onRace = { nav.navigate(raceRoute(ghost.exerciseId, ghost.id)) },
                        onDelete = { scope.launch { dao.delete(ghost) } },
                    )
                }
            }
        }

        SectionGap()

        Kicker("SHIPPED")
        SectionGap(12)
        if (shipped.isEmpty()) {
            Text(
                "No pacers bundled with this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint,
            )
        } else {
            Column {
                shipped.forEach { (id, data) ->
                    ShippedGhostRow(
                        data = data,
                        exerciseTitle = exercises.title(data.meta.exercise),
                        onRace = { nav.navigate(raceRoute(data.meta.exercise, id)) },
                    )
                }
            }
        }

        SectionGap(20)
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

    PanelBox(Modifier.fillMaxWidth(), padding = 12) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ghost.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    Text(
                        exerciseTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ghost.reps.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ember,
                    )
                    Text("REPS", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("${ghost.totalDamage} DMG")
                Tag(formatDay(ghost.createdAtMs))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmberButton("RACE", Modifier.weight(1f), onClick = onRace)
                Text(
                    if (confirming) "SURE?" else "DELETE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (confirming) Ember else InkFaint,
                    modifier = Modifier
                        .clickable { if (confirming) onDelete() else confirming = true }
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** A bundled pacer. Read-only: you race it, you never delete it. */
@Composable
private fun ShippedGhostRow(data: GhostData, exerciseTitle: String, onRace: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onRace)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(data.meta.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(
                    exerciseTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${data.meta.reps} REPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember,
                )
                Text("→", style = MaterialTheme.typography.titleMedium, color = Ink)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
    }
}

/** Ghost races always start the same way; the session resolves the id, not this screen. */
private fun raceRoute(exerciseId: String, ghostId: String): Session =
    Session(mode = GameMode.GHOST_RACE.name, exerciseId = exerciseId, ghostId = ghostId)

/** The exercise index carries the display name; fall back to the raw id rather than blank. */
internal fun Map<String, ExerciseSpec>.title(exerciseId: String): String =
    this[exerciseId]?.name ?: exerciseId

private val DayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

private fun formatDay(atMs: Long): String =
    runCatching { DayFormat.format(java.time.Instant.ofEpochMilli(atMs)) }.getOrDefault("")

fun NavGraphBuilder.ghostsRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Ghosts> { GhostsScreen(graph, nav) }
}
