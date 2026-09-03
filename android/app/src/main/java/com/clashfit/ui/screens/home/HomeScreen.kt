package com.clashfit.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.data.Prefs
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.ModeCard
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.label
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.nav.Modes
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Train tab. One tap into a fight, then every way to play as carousels grouped by how many
 * people they need. Everything that is not a fight lives on the other tabs. docs/02-APP-FLOW.md §2
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 60) }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val preferredId = settings.preferredExercise
    val preferredName = exercises[preferredId]?.name ?: preferredId.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val zone = remember { ZoneId.systemDefault() }
    val thisWeek = remember(sessions) {
        val weekAgo = LocalDate.now(zone).minusDays(6)
        sessions.count { !Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate().isBefore(weekAgo) }
    }

    ScreenScaffold(title = "ClashFit") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
            HeroCard(
                exerciseName = preferredName,
                streak = streak?.current ?: 0,
                onFight = { nav.navigate(Session(mode = GameMode.BOSS_FIGHT.name, exerciseId = preferredId)) },
                onChange = { nav.navigate(ExercisePicker(GameMode.BOSS_FIGHT.name)) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            SectionGap(20)
            StatStrip(
                listOf(
                    "${streak?.current ?: 0}" to "Day streak",
                    "$thisWeek" to "This week",
                    "${streak?.best ?: 0}" to "Best streak",
                ),
                Modifier.padding(horizontal = 20.dp),
            )

            SectionGap(28)
            SectionTitle("Ways to play", Modifier.padding(horizontal = 20.dp), action = "All modes") { nav.navigate(Modes) }

            for (kind in listOf(ModeKind.SOLO, ModeKind.VERSUS, ModeKind.GROUP, ModeKind.FAMILY, ModeKind.CLINIC)) {
                val modes = GameMode.grid.filter { it.kind == kind }
                if (modes.isEmpty()) continue
                Text(
                    kind.label(), style = MaterialTheme.typography.titleSmall, color = InkMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(modes, key = { it.name }) { mode ->
                        ModeCard(mode, onClick = { nav.navigate(ExercisePicker(mode.name)) }, width = 210)
                    }
                }
            }
        }
    }
}

/** The one thing on the tab that asks for a tap. */
@Composable
private fun HeroCard(exerciseName: String, streak: Int, onFight: () -> Unit, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Ember, EmberDeep)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Ground.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(16.dp))
            }
            Text(
                if (streak > 0) "Day $streak. Keep it." else "Your body is the controller.",
                style = MaterialTheme.typography.labelLarge, color = Ground,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("READY\nTO FIGHT?", style = MaterialTheme.typography.headlineLarge, color = Ground)
        Text(
            "$exerciseName · Boss Fight · your camera is the referee.",
            style = MaterialTheme.typography.bodyMedium, color = Ground.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onFight,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Ground, contentColor = Ink),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Start a fight", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.size(8.dp))
            LinkButton("Change", color = Ground, onClick = onChange)
        }
    }
}
