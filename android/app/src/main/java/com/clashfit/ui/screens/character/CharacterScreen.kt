package com.clashfit.ui.screens.character

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working

/** Five stat bars built from what you actually did, and the two that wait on sleep and food. */
@Composable
fun CharacterScreen(graph: AppGraph, nav: NavHostController) {
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 50) }
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val stats = remember(sessions, streak) { CharacterStats.compute(sessions, streak) }

    ScreenScaffold(title = "Character", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                "Every bar is earned from measured reps, never from a plan. Rest days grow resilience.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(bottom = 16.dp),
            )
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatBar("Power", stats.power, Ember, "Damage over your last ten fights")
                    StatBar("Stamina", stats.stamina, Working, "Reps banked, all time")
                    StatBar("Focus", stats.focus, Brass, "How clean the average rep is")
                    StatBar("Mobility", stats.mobility, Fresh, "Depth and range across families")
                    StatBar("Resilience", stats.resilience, Success, "Days in a row, rest included")
                }
            }

            SectionGap(24)
            SectionTitle("Coming with sleep and nutrition")
            Text(
                "Energy and Nourishment read from tracked sleep and meals. Neither is in this build, so neither is shown as a number you have not earned.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 4.dp),
            )

            SectionGap(24)
            SectionTitle("Totals")
            SectionGap(10)
            ListGroup {
                RuleRow("Sessions", "${stats.sessionCount}")
                InnerDivider()
                RuleRow("Reps", "${stats.totalReps}")
                InnerDivider()
                RuleRow("Average form", "${(stats.formAvg * 100).toInt()}%")
                InnerDivider()
                RuleRow("Current streak", "${streak?.current ?: 0} days")
            }
        }
    }
}

@Composable
private fun StatBar(label: String, value: Int, color: Color, hint: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text("$value", style = MaterialTheme.typography.titleSmall, color = color)
        }
        Bar(value / 100f, Modifier.padding(top = 8.dp), color = color, height = 10)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Compute character stats from session records and streaks. All 0–100. */
object CharacterStats {
    data class Stats(
        val power: Int = 0,
        val stamina: Int = 0,
        val focus: Int = 0,
        val mobility: Int = 0,
        val resilience: Int = 0,
        val totalReps: Int = 0,
        val sessionCount: Int = 0,
        val formAvg: Float = 0f,
    )

    fun compute(sessions: List<SessionEntity>, streak: StreakEntity?): Stats {
        if (sessions.isEmpty()) return Stats(resilience = streak?.current?.coerceIn(0, 100) ?: 0)
        val totalReps = sessions.sumOf { it.totalReps }
        val formAvg = sessions.map { it.formMean }.average().toFloat()
        val recent = sessions.take(10)
        return Stats(
            power = (recent.sumOf { it.totalDamage } / 100).coerceIn(0, 100),
            stamina = (totalReps / 10).coerceIn(0, 100),
            focus = (formAvg * 100).toInt().coerceIn(0, 100),
            mobility = (sessions.map { it.exerciseId }.distinct().size * 8).coerceIn(0, 100),
            resilience = (streak?.current ?: 0).coerceIn(0, 100),
            totalReps = totalReps,
            sessionCount = sessions.size,
            formAvg = formAvg,
        )
    }
}

fun NavGraphBuilder.characterRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Character> { CharacterScreen(graph, nav) }
}
