package com.clashfit.ui.screens.history

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
import com.clashfit.data.SessionEntity
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.nav.Summary
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sessions list with clickable rows leading to Summary(sessionId). */
@Composable
fun HistoryScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val sessions by graph.db.sessions().recent(limit = 50).collectAsState(initial = emptyList())

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("HISTORY")
        SectionGap(24)

        if (sessions.isEmpty()) {
            EmptyState("No sessions", "Start a fight to build your history")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { session ->
                    SessionRow(session, nav)
                }
            }
        }
        SectionGap(20)
    }
}

@Composable
private fun SessionRow(session: SessionEntity, nav: NavHostController) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .clickable { nav.navigate(Summary(session.id)) }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        session.mode.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink
                    )
                    Text(
                        session.exerciseId,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${session.totalReps} reps",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ember
                    )
                    Text(
                        "%.0f%% form".format(session.formMean * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint
                    )
                }
            }
            Text(
                formatDate(session.startedAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
    }
}

private fun formatDate(timeMs: Long): String {
    val instant = Instant.ofEpochMilli(timeMs)
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(zone)
    return formatter.format(instant)
}

fun NavGraphBuilder.historyRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.History> {
        HistoryScreen(graph, nav)
    }
}
