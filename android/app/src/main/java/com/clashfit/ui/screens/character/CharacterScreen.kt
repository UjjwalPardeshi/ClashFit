package com.clashfit.ui.screens.character

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Working

/** Seven bars: POWER/STAMINA/FOCUS/MOBILITY/ENERGY/NOURISHMENT/RESILIENCE from Room data. */
@Composable
fun CharacterScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val sessions by graph.db.sessions().recent(limit = 50).collectAsState(initial = emptyList())
    val streaks by graph.db.streak().observe().collectAsState(initial = null)

    // Compute stats from sessions
    val stats = androidx.compose.runtime.remember(sessions) {
        CharacterStats.compute(sessions, streaks)
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("CHARACTER")
        SectionGap(24)

        // Seven stat bars
        StatBar("POWER", stats.power, 100)
        StatBar("STAMINA", stats.stamina, 100)
        StatBar("FOCUS", stats.focus, 100)
        StatBar("MOBILITY", stats.mobility, 100)
        StatBar("ENERGY", stats.energy, 100)
        StatBar("NOURISHMENT", stats.nourishment, 100)
        StatBar("RESILIENCE", stats.resilience, 100)

        SectionGap(28)
        Kicker("Summary")
        SectionGap(12)
        RuleRow("Total Reps", "${stats.totalReps}")
        RuleRow("Sessions", "${stats.sessionCount}")
        RuleRow("Form Avg", "%.1f%%".format(stats.formAvg * 100))
        SectionGap(20)
    }
}

@Composable
private fun StatBar(label: String, value: Int, max: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Ink)
            Text("$value/$max", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(Rule)) {
            Box(Modifier.fillMaxWidth(value.toFloat() / max).height(8.dp).background(Ember))
        }
    }
}

/** Compute character stats from session records and streaks. */
object CharacterStats {
    data class Stats(
        val power: Int = 0,
        val stamina: Int = 0,
        val focus: Int = 0,
        val mobility: Int = 0,
        val energy: Int = 0,
        val nourishment: Int = 0,
        val resilience: Int = 0,
        val totalReps: Int = 0,
        val sessionCount: Int = 0,
        val formAvg: Float = 0f,
    )

    fun compute(sessions: List<com.clashfit.data.SessionEntity>, streak: com.clashfit.data.StreakEntity?): Stats {
        if (sessions.isEmpty()) return Stats()

        val totalReps = sessions.sumOf { it.totalReps }
        val formAvg = if (sessions.isNotEmpty()) {
            sessions.map { it.formMean }.average().toFloat()
        } else 0f

        // Stats derived from recent sessions
        val recentSessions = sessions.take(10)
        val power = (recentSessions.sumOf { it.totalDamage } / 10).coerceIn(0, 100).toInt()
        val stamina = (totalReps / 10).coerceIn(0, 100).toInt()
        val focus = (formAvg * 100).toInt()
        val mobility = (formAvg * 100).toInt()
        val resilience = (streak?.current?.coerceIn(0, 100) ?: 0)

        return Stats(
            power = power,
            stamina = stamina,
            focus = focus,
            mobility = mobility,
            energy = 0, // Not tracked
            nourishment = 0, // Not tracked
            resilience = resilience,
            totalReps = totalReps,
            sessionCount = sessions.size,
            formAvg = formAvg,
        )
    }
}

fun NavGraphBuilder.characterRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Character> {
        CharacterScreen(graph)
    }
}
