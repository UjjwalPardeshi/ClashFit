package com.clashfit.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.data.SessionEntity
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.ChartCard
import com.clashfit.ui.components.ChartLegend
import com.clashfit.ui.components.DonutChart
import com.clashfit.ui.components.TrendLine
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.nav.Summary
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Ok
import com.clashfit.ui.theme.Shallow
import com.clashfit.ui.theme.Success
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.clashfit.ui.insight.SessionInsights

/** Every session, newest first, grouped by day. Tap one for its summary. */
@Composable
fun HistoryScreen(graph: AppGraph, nav: NavHostController) {
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 200) }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(sessions) { sessions.groupBy { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() } }

    ScreenScaffold(title = "History", onBack = { nav.navigateUp() }) { padding ->
        if (sessions.isEmpty()) {
            EmptyState("No sessions yet", "Start a fight and it lands here with its fatigue curve and every rep.", Modifier.padding(padding), icon = AppIcons.Grid)
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            item(key = "trend") {
                val recent = remember(sessions) { sessions.take(20).reversed() }
                ChartCard(
                    "Form over your last ${recent.size} sessions",
                    subtitle = "The dashed line is your average.",
                ) {
                    TrendLine(
                        points = recent.map { it.formMean.coerceIn(0f, 1f) },
                        marker = remember(recent) { if (recent.isEmpty()) null else recent.map { it.formMean }.average().toFloat() },
                        description = remember(recent) {
                            if (recent.isEmpty()) "No sessions yet"
                            else "Form trend, latest ${(recent.last().formMean * 100).toInt()} percent, " +
                                "average ${(recent.map { it.formMean }.average() * 100).toInt()} percent"
                        },
                    )
                }
            }
            item(key = "verdicts") {
                val totals = remember(sessions) { SessionInsights.verdicts(sessions) }
                ChartCard("Every rep you have ever done", subtitle = "How the referee graded them") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        DonutChart(
                            parts = listOf(Clean to totals.clean, Ok to totals.ok, Shallow to totals.shallow),
                            description = "${totals.clean} clean, ${totals.ok} ok, ${totals.shallow} shallow",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${totals.total}", style = MaterialTheme.typography.headlineSmall, color = Ink)
                                Text("REPS", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            }
                        }
                        ChartLegend(
                            listOf(
                                Triple(Clean, "Clean", "${totals.clean}"),
                                Triple(Ok, "Ok", "${totals.ok}"),
                                Triple(Shallow, "Shallow", "${totals.shallow}"),
                            ),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            groups.forEach { (day, list) ->
                item(key = "day-$day") { SectionTitle(dayLabel(day, zone), Modifier.padding(top = 16.dp, bottom = 10.dp)) }
                item(key = "group-$day") {
                    ListGroup {
                        list.forEachIndexed { i, s ->
                            SessionRow(s, exercises[s.exerciseId]?.name ?: s.exerciseId.replace('_', ' '), zone) { nav.navigate(Summary(s.id)) }
                            if (i < list.lastIndex) InnerDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionEntity, exerciseName: String, zone: ZoneId, onClick: () -> Unit) {
    val won = s.outcome == "BOSS_DOWN" || s.outcome == "GAME_WON"
    val mode = runCatching { GameMode.valueOf(s.mode).title }.getOrDefault(s.mode.replace('_', ' '))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBubble(if (won) AppIcons.Trophy else AppIcons.Bolt, tint = if (won) Success else Ember)
        Column(Modifier.weight(1f)) {
            Text(exerciseName, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(
                "$mode · ${if (won) "Boss down" else "Set saved"} · ${TIME.withZone(zone).format(Instant.ofEpochMilli(s.startedAtMs))}",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${s.totalReps} reps", style = MaterialTheme.typography.titleSmall, color = Ink)
            Text("${(s.formMean * 100).toInt()}% form", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

private fun dayLabel(day: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DATE.format(day) + if (day.year != today.year) " ${day.year}" else ""
    }
}

fun NavGraphBuilder.historyRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.History> { HistoryScreen(graph, nav) }
}



