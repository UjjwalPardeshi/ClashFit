package com.clashfit.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.ExerciseDetail
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.components.FilterPill

fun Family.displayName(): String = when (this) {
    Family.REP_CYCLE -> "Strength"
    Family.ISOMETRIC_HOLD -> "Holds"
    Family.POSE_MATCH -> "Yoga"
    Family.CADENCE -> "Cardio"
    Family.BALLISTIC -> "Jumps"
}

/** The Library tab: search, a family filter, and every exercise as a row with its difficulty. */
@Composable
fun LibraryScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var family by rememberSaveable { mutableStateOf<Family?>(null) }

    val shown = remember(exercises, query, family) {
        exercises.values
            .filter { family == null || it.familyEnum == family }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ it.familyEnum.ordinal }, { it.difficulty }, { it.name }))
            .groupBy { it.familyEnum }
    }

    ScreenScaffold(title = "Library") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search ${exercises.size} exercises", color = InkFaint) },
                    leadingIcon = { Icon(AppIcons.Grid, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                        focusedBorderColor = Ember, unfocusedBorderColor = Rule,
                        focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ember,
                    ),
                )
            }
            item(key = "chips") {
                LazyRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterPill("All", family == null, onClick = { family = null }) }
                    items(Family.entries, key = { it.name }) { f ->
                        FilterPill(f.displayName(), family == f, onClick = { family = if (family == f) null else f })
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyState("No match", "Try a shorter word, or clear the filter.", icon = AppIcons.Grid)
                }
            }
            shown.forEach { (fam, list) ->
                item(key = "head-${fam.name}") {
                    SectionTitle(fam.displayName(), Modifier.padding(top = 22.dp, bottom = 10.dp))
                }
                item(key = "group-${fam.name}") {
                    ListGroup {
                        list.forEachIndexed { i, ex ->
                            ExerciseRow(ex) { nav.navigate(ExerciseDetail(ex.id)) }
                            if (i < list.lastIndex) InnerDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseSpec, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(width = 16.dp, height = 4.dp).clip(CircleShape).background(if (i < exercise.difficulty) Ember else RuleSoft))
                }
                Text(
                    when (exercise.difficulty) { 1 -> "Easy"; 2 -> "Moderate"; else -> "Hard" },
                    style = MaterialTheme.typography.labelSmall, color = InkMuted, modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
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
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(exercise.familyEnum.displayName(), color = Ember)
                Tag(when (exercise.difficulty) { 1 -> "Easy"; 2 -> "Moderate"; else -> "Hard" })
                exercise.games.take(2).forEach { Tag(it) }
            }
            SectionGap(16)
            ListGroup {
                RuleRow("Framing", exercise.framing)
                InnerDivider()
                RuleRow("Family", exercise.familyEnum.displayName())
                InnerDivider()
                RuleRow("Difficulty", "●".repeat(exercise.difficulty) + "○".repeat((3 - exercise.difficulty).coerceAtLeast(0)))
            }
            if (exercise.cues.isNotEmpty()) {
                SectionGap(24)
                SectionTitle("Cues")
                SectionGap(10)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercise.cues.forEach { (key, cue) ->
                        AppCard(Modifier.fillMaxWidth(), padding = 14, container = PanelLift) {
                            Column {
                                Kicker(key)
                                Text(cue, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            if (exercise.tags.isNotEmpty()) {
                SectionGap(24)
                SectionTitle("Tags")
                SectionGap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { exercise.tags.forEach { Tag(it) } }
            }
        }
    }
}

fun NavGraphBuilder.libraryRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Library> { LibraryScreen(graph, nav) }
    composable<ExerciseDetail> { entry -> ExerciseDetailScreen(entry.toRoute<ExerciseDetail>().exerciseId, graph, nav) }
}
