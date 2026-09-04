package com.clashfit.ui.screens.progress

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.data.SessionEntity
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.ProgressRing
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.WeekBars
import com.clashfit.ui.nav.Character
import com.clashfit.ui.nav.Ghosts
import com.clashfit.ui.nav.History
import com.clashfit.ui.nav.Streaks
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import com.clashfit.ui.theme.Shallow
import com.clashfit.ui.theme.Ok
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.screens.character.Domain
import com.clashfit.ui.screens.character.CharacterStats
import com.clashfit.ui.insight.SessionInsights
import com.clashfit.ui.components.TrendLine
import com.clashfit.ui.components.RadarChart
import com.clashfit.ui.components.DonutChart
import com.clashfit.ui.components.ChartLegend
import com.clashfit.ui.components.ChartCard
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable

/** The Progress tab: the week at a glance, the streak, and the way into every record. */
@Composable
fun ProgressScreen(graph: AppGraph, nav: NavHostController) {
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val countFlow = remember(graph) { graph.db.sessions().count() }
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 120) }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessionCount by countFlow.collectAsStateWithLifecycle(initialValue = 0)
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()

    val zone = remember { ZoneId.systemDefault() }
    val week = remember(sessions) { lastSevenDays(sessions, zone) }
    val current = streak?.current ?: 0
    val best = streak?.best ?: 0

    ScreenScaffold(title = "Progress") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 18) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ProgressRing(fraction = (current % 7) / 7f, size = 84, stroke = 8, color = Fresh) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$current", style = MaterialTheme.typography.headlineMedium, color = Ink)
                            Text("DAYS", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                current == 0 -> "No streak yet"
                                current == 1 -> "Day one"
                                else -> "$current-day streak"
                            },
                            style = MaterialTheme.typography.titleLarge, color = Ink,
                        )
                        Text(
                            if (current == 0) "One set today starts it. Rest days are protected."
                            else "Best is $best. One protected rest day a week, and it never shows a number you lost.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            SectionGap(16)
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("This week")
                    Text(
                        "${week.sessions} sessions · ${week.reps} reps · ${week.damage} damage",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
                    WeekBars(week.perDay, labels = week.labels)
                }
            }

            SectionGap(20)
            StatStrip(listOf("$sessionCount" to "Sessions", "$best" to "Best streak", "${week.cleanPct}%" to "Clean this week"))

            if (sessionCount == 0) {
                EmptyState(
                    title = "Nothing on the board yet",
                    body = "Your first set writes the first line here. Every rep after that is measured against it.",
                    icon = AppIcons.Chart,
                )
            }

            // The charts, on the page rather than behind a list. They are the most convincing
            // thing the app has — proof that every rep was actually measured — and they used to
            // take two taps to reach, which meant most people never saw them at all. Each card
            // opens the full screen.
            if (sessionCount > 0) {
                val trend = remember(sessions) { SessionInsights.formTrend(sessions) }
                val verdicts = remember(sessions) { SessionInsights.verdicts(sessions) }
                val sheet = remember(sessions, streak, exercises) {
                    CharacterStats.compute(sessions, streak, exercises)
                }

                SectionGap(22)
                ChartCard(
                    "Form, last ${trend.size} sessions",
                    subtitle = "The dashed line is your average. Tap for every session.",
                    modifier = Modifier.clickable { nav.navigate(History) },
                ) {
                    TrendLine(
                        points = trend,
                        marker = remember(trend) { SessionInsights.average(trend) },
                        height = 120,
                        description = remember(trend) {
                            val avg = SessionInsights.average(trend)
                            if (avg == null) "No sessions yet"
                            else "Form trend, average ${(avg * 100).toInt()} percent"
                        },
                    )
                }

                SectionGap(16)
                ChartCard(
                    "Every rep, graded",
                    subtitle = "Clean, ok or shallow. Tap for the full history.",
                    modifier = Modifier.clickable { nav.navigate(History) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        DonutChart(
                            parts = listOf(Clean to verdicts.clean, Ok to verdicts.ok, Shallow to verdicts.shallow),
                            size = 120,
                            description = "${verdicts.clean} clean, ${verdicts.ok} ok, ${verdicts.shallow} shallow",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${verdicts.total}", style = MaterialTheme.typography.titleLarge, color = Ink)
                                Text("REPS", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            }
                        }
                        ChartLegend(
                            listOf(
                                Triple(Clean, "Clean", "${verdicts.clean}"),
                                Triple(Ok, "Ok", "${verdicts.ok}"),
                                Triple(Shallow, "Shallow", "${verdicts.shallow}"),
                            ),
                        )
                    }
                }

                SectionGap(16)
                ChartCard(
                    "Your shape",
                    subtitle = "Seven domains, earned from measured movement. Tap for the detail.",
                    modifier = Modifier.clickable { nav.navigate(Character) },
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RadarChart(
                            // The full name. "NUT" is not a word for nourishment, and a chart whose
                            // axes need a decoder is not showing anybody anything.
                            stats = Domain.entries.map { it.title to sheet.value(it) / 100f },
                            size = 240,
                            color = Ember,
                        )
                    }
                }
            }

            SectionGap(24)
            SectionTitle("Records")
            SectionGap(10)
            ListGroup {
                NavRow("Streak", { nav.navigate(Streaks) }, icon = AppIcons.Chart, supporting = "The calendar, freezes and rest days")
                InnerDivider()
                NavRow("History", { nav.navigate(History) }, icon = AppIcons.Grid, value = "$sessionCount", supporting = "Every session, newest first")
                InnerDivider()
                NavRow("Character", { nav.navigate(Character) }, icon = AppIcons.Person, supporting = "What the sets have built")
                InnerDivider()
                NavRow("Ghosts", { nav.navigate(Ghosts) }, icon = AppIcons.Bolt, supporting = "Your own best, saved to race against")
            }
        }
    }
}

private data class WeekSummary(
    val perDay: List<Float>,
    val labels: List<String>,
    val sessions: Int,
    val reps: Int,
    val damage: Int,
    val cleanPct: Int,
)

/** Reps per day for the last seven days, today on the right. */
private fun lastSevenDays(sessions: List<SessionEntity>, zone: ZoneId): WeekSummary {
    val today = LocalDate.now(zone)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }
    val inWeek = days.flatMap { byDay[it].orEmpty() }
    val perDay = days.map { d -> byDay[d].orEmpty().sumOf { it.totalReps }.toFloat() }
    val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    val reps = inWeek.sumOf { it.totalReps }
    val cleanPct = if (inWeek.isEmpty()) 0 else (inWeek.map { it.formMean }.average() * 100).toInt()
    return WeekSummary(perDay, labels, inWeek.size, reps, inWeek.sumOf { it.totalDamage }, cleanPct)
}
