package com.clashfit.ui.screens.picker

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.data.Prefs
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.DuelLobby
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.RaidRoom
import com.clashfit.ui.nav.Roster
import com.clashfit.ui.nav.Session
import com.clashfit.ui.screens.library.displayName
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ground2
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft

/**
 * Pick the movement for a mode. Only the families the mode allows are listed; the confirm
 * button sits in a bar at the bottom so it never scrolls away.
 */
/** The exercises the angles were measured for by hand come first in every list, in this order. */
val FEATURED_EXERCISES = listOf("lateral_raise", "bicep_curl", "shoulder_press", "squat")

fun featuredRank(id: String): Int = FEATURED_EXERCISES.indexOf(id).let { if (it < 0) FEATURED_EXERCISES.size else it }

@Composable
fun ExercisePickerScreen(modeStr: String, graph: AppGraph, nav: NavHostController) {
    val mode = GameMode.valueOf(modeStr)
    val exercises by graph.config.exercises.collectAsState()
    val settings by graph.prefs.settings.collectAsState(initial = Prefs.Settings())
    val ghosts by graph.config.ghosts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var ghostId by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(exercises, mode, query) {
        exercises.values
            .filter { it.id in FEATURED_EXERCISES }
            .filter { mode.family == null || it.familyEnum == mode.family }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ it.familyEnum.ordinal }, { featuredRank(it.id) }, { it.difficulty }, { it.name }))
    }
    val grouped = remember(filtered) { filtered.groupBy { it.familyEnum } }

    LaunchedEffect(filtered, settings.preferredExercise) {
        if (selectedId.isEmpty() || filtered.none { it.id == selectedId }) {
            selectedId = filtered.firstOrNull { it.id == settings.preferredExercise }?.id ?: filtered.firstOrNull()?.id ?: ""
        }
    }
    val selected = exercises[selectedId]
    val needsGhost = mode == GameMode.GHOST_RACE

    ScreenScaffold(
        title = mode.title,
        onBack = { nav.navigateUp() },
        bottomBar = {
            if (mode.enabled && selected != null) {
                Box(Modifier.fillMaxWidth().background(Ground2).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val (label, go) = confirmAction(mode, selected.id, ghostId, settings, nav)
                    PrimaryButton(label, enabled = !needsGhost || ghostId != null, onClick = go)
                }
            }
        },
    ) { padding ->
        if (!mode.enabled) {
            EmptyState("Goes online", "Zombie Run happens on a real map and picks no exercise. Start it from Outdoors.", Modifier.padding(padding), icon = AppIcons.Run)
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        ) {
            item(key = "blurb") {
                Text(mode.blurb, style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(bottom = 14.dp))
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search", color = InkFaint) },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true, shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                        focusedBorderColor = Ember, unfocusedBorderColor = Rule,
                        focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ember,
                    ),
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty") { EmptyState("No match", "Try a shorter word.", icon = AppIcons.Search) }
            }
            grouped.forEach { (family, list) ->
                item(key = "head-${family.name}") { SectionTitle(family.displayName(), Modifier.padding(top = 20.dp, bottom = 10.dp)) }
                item(key = "group-${family.name}") {
                    ListGroup {
                        list.forEachIndexed { i, ex ->
                            ExerciseChoice(ex, ex.id == selectedId) { selectedId = ex.id }
                            if (i < list.lastIndex) InnerDivider()
                        }
                    }
                }
            }
            if (needsGhost) {
                item(key = "ghost-head") { SectionTitle("Race against", Modifier.padding(top = 24.dp, bottom = 10.dp)) }
                item(key = "ghost-list") {
                    if (ghosts.isEmpty()) {
                        Text("No ghosts shipped for this build.", style = MaterialTheme.typography.bodySmall, color = InkFaint)
                    } else {
                        ListGroup {
                            ghosts.entries.forEachIndexed { i, (id, g) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { ghostId = id }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RadioDot(ghostId == id)
                                    Column(Modifier.weight(1f)) {
                                        Text(g.meta.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                        Text("${g.meta.reps} reps", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                                    }
                                }
                                if (i < ghosts.size - 1) InnerDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseChoice(ex: ExerciseSpec, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) EmberTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onSelect).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioDot(selected)
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(if (i < ex.difficulty) Ember else RuleSoft))
                }
                Text(
                    when (ex.difficulty) { 1 -> "Easy"; 2 -> "Moderate"; else -> "Hard" },
                    style = MaterialTheme.typography.labelSmall, color = InkMuted, modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (ex.tags.isNotEmpty()) Tag(ex.tags.first())
    }
}

@Composable
private fun RadioDot(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (on) Ember else Panel)
            .then(if (on) Modifier else Modifier.padding(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(AppIcons.Check, contentDescription = "Selected", tint = Ground, modifier = Modifier.size(14.dp))
        else Box(Modifier.size(18.dp).clip(CircleShape).background(Ground2))
    }
}

/** What the big button says and does, by mode. */
private fun confirmAction(mode: GameMode, exerciseId: String, ghostId: String?, settings: Prefs.Settings, nav: NavHostController): Pair<String, () -> Unit> =
    when {
        mode.kind == ModeKind.VERSUS -> "Find a phone" to { nav.navigate(DuelLobby(mode.name, exerciseId)) }
        mode == GameMode.RAID || mode == GameMode.TEAM_VS_TEAM -> "Open the raid" to { nav.navigate(RaidRoom(exerciseId)) }
        mode.kind == ModeKind.GROUP -> "Set the roster" to { nav.navigate(Roster(mode.name, exerciseId)) }
        mode == GameMode.GHOST_RACE -> "Race" to { nav.navigate(Session(mode.name, exerciseId, casual = settings.casual, ghostId = ghostId)) }
        else -> "Start" to { nav.navigate(Session(mode.name, exerciseId, casual = settings.casual)) }
    }

fun NavGraphBuilder.pickerRoutes(graph: AppGraph, nav: NavHostController) {
    composable<ExercisePicker> { entry -> ExercisePickerScreen(entry.toRoute<ExercisePicker>().mode, graph, nav) }
}
