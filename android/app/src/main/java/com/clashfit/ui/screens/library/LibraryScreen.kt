package com.clashfit.ui.screens.library

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.ExerciseDetail
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** Five families as rows, each exercise with framing, difficulty, games, cues, thresholds. */
@Composable
fun LibraryScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val exercises by graph.config.exercises.collectAsState()

    val byFamily = remember(exercises) {
        Family.values().associateWith { family ->
            exercises.values.filter { it.familyEnum == family }.sortedBy { it.name }
        }
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("THE", accent = "LIBRARY")
        SectionGap(24)

        Family.values().forEach { family ->
            val exsInFamily = byFamily[family] ?: emptyList()
            if (exsInFamily.isNotEmpty()) {
                FamilySection(family, exsInFamily, nav)
                SectionGap()
            }
        }
        SectionGap(20)
    }
}

@Composable
private fun FamilySection(family: Family, exercises: List<ExerciseSpec>, nav: NavHostController) {
    Column(Modifier.fillMaxWidth()) {
        Kicker(family.name)
        Column {
            exercises.forEach { ex ->
                ExerciseLibraryRow(ex, nav)
            }
        }
    }
}

@Composable
private fun ExerciseLibraryRow(exercise: ExerciseSpec, nav: NavHostController) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { nav.navigate(ExerciseDetail(exercise.id)) }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    repeat(exercise.difficulty) {
                        Box(Modifier.background(Ink).padding(2.dp))
                    }
                }
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
    }
}

@Composable
fun ExerciseDetailScreen(exerciseId: String, graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val exercises by graph.config.exercises.collectAsState()
    val exercise = exercises[exerciseId]

    if (exercise == null) {
        Column(modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Exercise not found", style = MaterialTheme.typography.bodyLarge, color = InkFaint)
        }
    } else {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("← BACK", style = MaterialTheme.typography.labelSmall, color = Ink, modifier = Modifier.clickable { nav.navigateUp() })
            SectionGap(20)

            Headline(exercise.name)
            SectionGap(16)

            // Family and Framing
            RuleRow("Family", exercise.family)
            RuleRow("Framing", exercise.framing)
            RuleRow("Difficulty", "★".repeat(exercise.difficulty))

            // Games
            if (exercise.games.isNotEmpty()) {
                SectionGap(12)
                Kicker("Games")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    exercise.games.forEach { game ->
                        Tag(game)
                    }
                }
            }

            // Tags
            if (exercise.tags.isNotEmpty()) {
                SectionGap(12)
                Kicker("Tags")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    exercise.tags.forEach { tag ->
                        Tag(tag)
                    }
                }
            }

            // Cues
            if (exercise.cues.isNotEmpty()) {
                SectionGap(12)
                Kicker("Cues")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercise.cues.forEach { (key, cue) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Panel)
                                .border(1.dp, Rule)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
                                Text(cue, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            SectionGap(20)
        }
    }
}

fun NavGraphBuilder.libraryRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Library> {
        LibraryScreen(graph, nav)
    }
    composable<ExerciseDetail> { backStackEntry ->
        val args = backStackEntry.arguments?.getString("exerciseId") ?: ""
        ExerciseDetailScreen(args, graph, nav)
    }
}
