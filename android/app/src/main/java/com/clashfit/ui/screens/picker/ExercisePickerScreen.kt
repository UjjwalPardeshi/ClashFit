package com.clashfit.ui.screens.picker

import com.clashfit.ui.nav.Roster

import com.clashfit.ui.nav.RaidRoom

import com.clashfit.ui.nav.DuelLobby

import com.clashfit.core.model.ModeKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import com.clashfit.data.Prefs
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch

/** Filter exercises by mode's family, grouped by family with counts, difficulty dots, tags, search. */
@Composable
fun ExercisePickerScreen(
    modeStr: String,
    graph: AppGraph,
    nav: NavHostController,
    modifier: Modifier = Modifier
) {
    val mode = GameMode.valueOf(modeStr)
    val exercises by graph.config.exercises.collectAsState()
    val settings by graph.prefs.settings.collectAsState(initial = Prefs.Settings())
    var searchQuery by remember { mutableStateOf("") }
    var selectedExerciseId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val filtered = remember(exercises, mode, searchQuery) {
        exercises.values
            .filter { mode.family == null || it.familyEnum == mode.family }
            .filter { searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.familyEnum }
    }

    LaunchedEffect(filtered) {
        if (filtered.isNotEmpty() && selectedExerciseId.isEmpty()) {
            selectedExerciseId = filtered.first().id
        }
    }

    ScreenScaffold(title = mode.title, onBack = { nav.navigateUp() }) { padding ->
        Column(modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (!mode.enabled) {
                EmptyState("Goes online", "This mode is not in this build. Outbreak needs a network, and this build has none.")
            } else {
                Kicker("Pick your move")
                SectionGap(14)

                SearchBox(searchQuery) { searchQuery = it }
                SectionGap(20)

                if (filtered.isEmpty()) {
                    Text("No exercises found", style = MaterialTheme.typography.bodyLarge, color = InkFaint)
                } else {
                    ExercisesList(filtered, selectedExerciseId) { selectedExerciseId = it }
                    SectionGap(20)

                    if (selectedExerciseId.isNotEmpty()) {
                        val selected = exercises[selectedExerciseId]
                        if (selected != null) {
                            ExercisePreview(selected)
                            SectionGap(20)

                            ConfirmButton(mode, selectedExerciseId, settings, graph, nav, scope)
                        }
                    }
                }
            }
            SectionGap(20)
        }
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .padding(12.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text("Search exercises", style = MaterialTheme.typography.bodyLarge, color = InkFaint)
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun ExercisesList(
    exercises: List<ExerciseSpec>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    val grouped = exercises.groupBy { it.familyEnum }
    Column {
        grouped.forEach { (family, exs) ->
            FamilyGroup(family.name, exs, selectedId, onSelect)
            SectionGap(12)
        }
    }
}

@Composable
private fun FamilyGroup(
    familyName: String,
    exercises: List<ExerciseSpec>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column {
        Kicker("$familyName · ${exercises.size}")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercises.forEach { ex ->
                ExerciseRow(ex, ex.id == selectedId, onSelect)
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseSpec,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Ember else Panel)
            .border(1.dp, if (selected) Ember else Rule)
            .clickable { onSelect(exercise.id) }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.labelLarge, color = if (selected) Ground else Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(exercise.difficulty) {
                    Box(Modifier.background(if (selected) Ground else Ember).padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ExercisePreview(exercise: ExerciseSpec) {
    Column(Modifier.fillMaxWidth()) {
        Kicker("Details")
        SectionGap(12)
        RuleRow("Name", exercise.name)
        RuleRow("Difficulty", "★".repeat(exercise.difficulty))
        RuleRow("Family", exercise.family)
        if (exercise.tags.isNotEmpty()) {
            RuleRow("Tags", "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                exercise.tags.forEach { tag ->
                    Tag(tag)
                }
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    mode: GameMode,
    exerciseId: String,
    settings: Prefs.Settings,
    graph: AppGraph,
    nav: NavHostController,
    scope: kotlinx.coroutines.CoroutineScope
) {
    when {
        mode.kind == ModeKind.VERSUS -> EmberButton("FIND A PHONE") { nav.navigate(DuelLobby(mode.name, exerciseId)) }
        mode == GameMode.RAID || mode == GameMode.TEAM_VS_TEAM -> EmberButton("OPEN THE RAID") { nav.navigate(RaidRoom(exerciseId)) }
        mode.kind == ModeKind.GROUP -> EmberButton("SET THE ROSTER") { nav.navigate(Roster(mode.name, exerciseId)) }
        mode == GameMode.GHOST_RACE -> GhostSelector(mode, exerciseId, settings, graph, nav)
        else -> EmberButton("START FIGHT") { nav.navigate(Session(mode.name, exerciseId, casual = settings.casual)) }
    }
}


@Composable
private fun GhostSelector(
    mode: GameMode,
    exerciseId: String,
    settings: Prefs.Settings,
    graph: AppGraph,
    nav: NavHostController
) {
    val ghosts by graph.config.ghosts.collectAsState()
    Column {
        Kicker("Choose opponent")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ghosts.entries.forEach { (ghostId, ghost) ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel)
                        .border(1.dp, Rule)
                        .clickable { nav.navigate(Session(mode.name, exerciseId, casual = settings.casual, ghostId = ghostId)) }
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ghost.meta.name, style = MaterialTheme.typography.labelLarge, color = Ink)
                        Text("${ghost.meta.reps} reps", style = MaterialTheme.typography.bodySmall, color = InkFaint)
                    }
                }
            }
        }
    }
}

fun NavGraphBuilder.pickerRoutes(graph: AppGraph, nav: NavHostController) {
    composable<ExercisePicker> { backStackEntry ->
        val pickerRoute = backStackEntry.toRoute<ExercisePicker>()
        ExercisePickerScreen(pickerRoute.mode, graph, nav)
    }
}
