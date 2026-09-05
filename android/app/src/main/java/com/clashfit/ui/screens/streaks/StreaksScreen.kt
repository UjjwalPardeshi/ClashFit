package com.clashfit.ui.screens.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.clashfit.data.SessionEntity
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.ChartCard
import com.clashfit.ui.components.Heatmap
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.ScreenEmptyState
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.nav.Modes
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Current and best streaks, this month as a grid with the days you trained lit. */
@Composable
fun StreaksScreen(graph: AppGraph, nav: NavHostController) {
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 62) }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val current = streak?.current ?: 0
    val best = streak?.best ?: 0

    ScreenScaffold(title = "Streak", onBack = { nav.navigateUp() }) { padding ->
        if (sessions.isEmpty()) {
            // Two zeroes over twelve weeks of unlit squares says one thing, three times over.
            ScreenEmptyState(
                title = "No streak yet",
                body = "One set today starts it. Miss a day and a protected rest day covers you, so a " +
                    "streak here is never lost to a life that happened.",
                modifier = Modifier.padding(padding),
                icon = AppIcons.Bolt,
                action = { PrimaryButton("Start training") { nav.navigate(Modes) } },
            )
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$current", "Current", Modifier.weight(1f), color = if (current > 0) Fresh else InkFaint)
                StatTile("$best", "Best", Modifier.weight(1f), color = Ember)
            }

            SectionGap(20)
            ChartCard("Last twelve weeks", subtitle = "One square a day. Brighter is more reps.") {
                Heatmap(
                    intensity = remember(sessions) { dailyIntensity(sessions, ZoneId.systemDefault(), weeks = 12) },
                    weeks = 12,
                    description = "Training heatmap for the last twelve weeks",
                )
            }

            SectionGap(20)
            ChartCard(
                YearMonth.now().month.name.lowercase().replaceFirstChar { it.uppercase() },
                subtitle = "The days you trained this month",
            ) {
                MonthGrid(YearMonth.now(), sessions)
            }

            val freezes = streak?.freezes ?: 0
            if (freezes > 0) {
                SectionGap(28)
                Kicker("Protection")
                SectionGap(4)
                RuleRow("Freezes left", "$freezes")
                RuleRow("Rest days this week", "${streak?.restDaysUsedThisWeek ?: 0} / 1")
            }
            SectionGap(32)
        }
    }
}

/**
 * A month as rows of seven, Sunday first. The row count is derived from where the month
 * starts and how long it is, so February beginning on a Monday gets exactly the rows it needs.
 * Month length comes from YearMonth, which knows that 2100 is not a leap year.
 */
@Composable
private fun MonthGrid(month: YearMonth, sessions: List<SessionEntity>) {
    val daysInMonth = month.lengthOfMonth()
    // Sunday-first column index of the 1st: Sunday=0 … Saturday=6.
    val leading = month.atDay(1).dayOfWeek.value % 7
    val rows = (leading + daysInMonth + 6) / 7

    val zone = ZoneId.systemDefault()
    val trainedDays: Set<Int> = remember(sessions, month) {
        sessions.asSequence()
            .map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }
            .filter { YearMonth.from(it) == month }
            .map { it.dayOfMonth }
            .toSet()
    }
    val today = LocalDate.now(zone)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val names = DayOfWeek.entries.let { listOf(it[6]) + it.take(6) } // Sunday first
            names.forEach { d ->
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                    Text(d.name.take(3), style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
        }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { c ->
                    val day = r * 7 + c - leading + 1
                    if (day in 1..daysInMonth) {
                        DayCell(
                            day = day,
                            trained = day in trainedDays,
                            isToday = today.year == month.year && today.month == month.month && today.dayOfMonth == day,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, trained: Boolean, isToday: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.height(48.dp)
            .background(if (trained) Fresh else Panel)
            .border(1.dp, if (trained) Fresh else if (isToday) Ember else Rule),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.labelMedium,
            color = if (trained) Ground else if (isToday) Ember else Ink,
        )
    }
}

fun NavGraphBuilder.streaksRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Streaks> { StreaksScreen(graph, nav) }
}

/**
 * Reps per day for the last N weeks, oldest first, scaled 0..1 against the best day in the window.
 * A day with a single set still reads, because showing up is the thing being rewarded.
 */
private fun dailyIntensity(sessions: List<SessionEntity>, zone: ZoneId, weeks: Int): List<Float> {
    val days = weeks * 7
    val today = LocalDate.now(zone)
    val repsByDay = sessions
        .groupBy { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }
        .mapValues { (_, v) -> v.sumOf { it.totalReps } }
    val window = (days - 1 downTo 0).map { back -> repsByDay[today.minusDays(back.toLong())] ?: 0 }
    val best = (window.maxOrNull() ?: 0).coerceAtLeast(1)
    return window.map { if (it == 0) 0f else (0.25f + 0.75f * it / best) }
}
