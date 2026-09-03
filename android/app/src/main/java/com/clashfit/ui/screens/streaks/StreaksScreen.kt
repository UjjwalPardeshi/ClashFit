package com.clashfit.ui.screens.streaks

import java.time.ZoneId

import java.time.Instant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import java.time.LocalDate
import java.time.YearMonth

/** Current/best streaks, five-week calendar grid with protected rest days and freezes. */
@Composable
fun StreaksScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val streaks by graph.db.streak().observe().collectAsState(initial = null)
    val sessions by graph.db.sessions().recent(limit = 35).collectAsState(initial = emptyList())

    val streak = streaks
    val current = streak?.current ?: 0
    val best = streak?.best ?: 0

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("STREAK")
        SectionGap(24)

        // Current and best stats
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile("$current", "CURRENT", Modifier.weight(1f), color = if (current > 0) Fresh else InkFaint)
            StatTile("$best", "BEST", Modifier.weight(1f), color = Ember)
        }

        SectionGap(28)

        // Five-week calendar
        Kicker("This Month")
        SectionGap(12)

        CalendarGrid(sessions)

        if (streak?.freezes ?: 0 > 0) {
            SectionGap(28)
            Kicker("Protection")
            SectionGap(12)
            RuleRow("Freezes Left", "${streak?.freezes ?: 0}")
            RuleRow("Rest Days This Week", "${streak?.restDaysUsedThisWeek ?: 0} / 1")
        }

        SectionGap(20)
    }
}

@Composable
private fun CalendarGrid(sessions: List<com.clashfit.data.SessionEntity>) {
    val today = LocalDate.now()
    val startOfMonth = today.withDayOfMonth(1)
    val daysInMonth = today.monthValue.let { m ->
        when (m) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (today.year % 4 == 0) 29 else 28
            else -> 30
        }
    }

    // Days of the shown month that have at least one finished session, keyed by day-of-month.
    val zone = ZoneId.systemDefault()
    val sessionsByDate: Map<Int, List<LocalDate>> = sessions
        .map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }
        .filter { it.year == startOfMonth.year && it.month == startOfMonth.month }
        .groupBy { it.dayOfMonth }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Week headers
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(7) { dayOfWeek ->
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                    val dayNames = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
                    Text(dayNames[dayOfWeek], style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
        }

        // Calendar cells
        var cellIndex = 0
        val weekCount = (daysInMonth + startOfMonth.dayOfWeek.value) / 7 + 1
        repeat(weekCount) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(7) {
                    val dayOfMonth = cellIndex - (startOfMonth.dayOfWeek.value - 1) + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val hasSession = sessionsByDate.containsKey(dayOfMonth)
                        CalendarCell(dayOfMonth, hasSession, Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    cellIndex++
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(dayOfMonth: Int, hasSession: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(40.dp)
            .background(if (hasSession) Fresh else Panel)
            .border(1.dp, if (hasSession) Fresh else Rule),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$dayOfMonth",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasSession) Ground else Ink
        )
    }
}

fun NavGraphBuilder.streaksRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Streaks> {
        StreaksScreen(graph)
    }
}
