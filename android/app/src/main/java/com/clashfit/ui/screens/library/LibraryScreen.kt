package com.clashfit.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.ui.components.AppDivider
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.ExerciseDetail
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted

/** The Library tab: five families as sections, every exercise a row with its difficulty. */
@Composable
fun LibraryScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsState()
    val byFamily = remember(exercises) {
        Family.entries.associateWith { family ->
            exercises.values.filter { it.familyEnum == family }.sortedBy { it.name }
        }
    }

    ScreenScaffold(title = "Library") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "${exercises.size} exercises · ${Family.entries.size} kinds of movement".uppercase(),
                style = MaterialTheme.typography.labelMedium, color = InkMuted,
            )
            SectionGap(20)
            Family.entries.forEach { family ->
                val inFamily = byFamily[family].orEmpty()
                if (inFamily.isNotEmpty()) {
                    Kicker(family.displayName())
                    SectionGap(4)
                    inFamily.forEach { ExerciseRow(it) { nav.navigate(ExerciseDetail(it.id)) } }
                    SectionGap(26)
                }
            }
            SectionGap(12)
        }
    }
}

private fun Family.displayName(): String = when (this) {
    Family.REP_CYCLE -> "Strength reps"
    Family.ISOMETRIC_HOLD -> "Holds"
    Family.POSE_MATCH -> "Yoga"
    Family.CADENCE -> "Cardio"
    Family.BALLISTIC -> "Jumps"
}

@Composable
private fun ExerciseRow(exercise: ExerciseSpec, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(width = 14.dp, height = 3.dp).background(if (i < exercise.difficulty) Ink else InkFaint))
                    }
                }
            }
            Icon(AppIcons.Chevron, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
        }
        AppDivider()
    }
}

@Composable
fun ExerciseDetailScreen(exerciseId: String, graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsState()
    val exercise = exercises[exerciseId]

    ScreenScaffold(title = exercise?.name ?: "Exercise", onBack = { nav.navigateUp() }) { padding ->
        if (exercise == null) {
            EmptyState("Not found", "That exercise is not in this build's library.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            RuleRow("Family", exercise.familyEnum.displayName())
            RuleRow("Framing", exercise.framing)
            RuleRow("Difficulty", "●".repeat(exercise.difficulty) + "○".repeat((3 - exercise.difficulty).coerceAtLeast(0)))

            if (exercise.games.isNotEmpty()) {
                SectionGap(24)
                Kicker("Games")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    exercise.games.forEach { Tag(it) }
                }
            }
            if (exercise.tags.isNotEmpty()) {
                SectionGap(12)
                Kicker("Tags")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    exercise.tags.forEach { Tag(it) }
                }
            }
            if (exercise.cues.isNotEmpty()) {
                SectionGap(12)
                Kicker("Cues")
                SectionGap(10)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercise.cues.forEach { (key, cue) ->
                        PanelBox(Modifier.fillMaxWidth(), padding = 12) {
                            Column {
                                Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
                                Text(cue, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            SectionGap(32)
        }
    }
}

fun NavGraphBuilder.libraryRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Library> { LibraryScreen(graph, nav) }
    composable<ExerciseDetail> { entry ->
        ExerciseDetailScreen(entry.toRoute<ExerciseDetail>().exerciseId, graph, nav)
    }
}
