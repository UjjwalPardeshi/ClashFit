package com.clashfit.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.clashfit.AppGraph
import com.clashfit.meta.Metric
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.LoadingState
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ProgressRing
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** This week's target, the same for every player, and how far you are from it. */
@Composable
fun WeeklyScreen(graph: AppGraph, onBack: () -> Unit, onFight: () -> Unit, onLeaderboard: () -> Unit) {
    val stateFlow = remember(graph) { graph.meta.state }
    val meta by stateFlow.collectAsStateWithLifecycle(initialValue = null)
    val daysLeft = remember { daysLeftInWeek() }

    ScreenScaffold(title = "Weekly challenge", onBack = onBack) { padding ->
        val w = meta?.weekly
        if (w == null) { LoadingState("Loading", Modifier.padding(padding)); return@ScreenScaffold }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 20) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    ProgressRing(w.fraction, size = 150, stroke = 12, color = if (w.done) Success else MaterialTheme.colorScheme.primary) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${w.value}", style = MaterialTheme.typography.headlineLarge, color = Ink)
                            Text("of ${w.challenge.target}", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(w.challenge.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text(w.challenge.description, style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(if (w.done) AppIcons.Check else AppIcons.Trophy, size = 28, tint = if (w.done) Success else MaterialTheme.colorScheme.primary)
                        Text(
                            if (w.done) "Done. +100 XP banked." else "$daysLeft ${if (daysLeft == 1) "day" else "days"} left · +100 XP on completion",
                            style = MaterialTheme.typography.labelLarge, color = if (w.done) Success else Ink,
                        )
                    }
                }
            }

            SectionGap(16)
            if (!w.done) PrimaryButton("Start a fight", icon = AppIcons.Bolt, onClick = onFight)

            SectionGap(24)
            SectionTitle("This week")
            SectionGap(10)
            ListGroup {
                RuleRow("Week", w.challenge.weekKey)
                InnerDivider()
                RuleRow("Counts", metricLabel(w.challenge.metric))
                InnerDivider()
                RuleRow("Target", "${w.challenge.target}")
                InnerDivider()
                NavRow("Leaderboard", onLeaderboard, icon = AppIcons.Trophy, supporting = "See who is ahead this week")
            }

            SectionGap(24)
            SectionTitle("How it works")
            Text(
                "Every player gets the same challenge each week, from Monday. Casual sessions count toward reps and sessions, not toward damage. Rest days never break it.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun metricLabel(m: Metric): String = when (m) {
    Metric.DAMAGE -> "Damage dealt"
    Metric.CLEAN_REPS -> "Clean reps"
    Metric.SESSIONS -> "Sessions finished"
    Metric.STREAK_DAYS -> "Streak days"
}

private fun daysLeftInWeek(): Int {
    val today = LocalDate.now()
    val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return (sunday.toEpochDay() - today.toEpochDay()).toInt() + 1
}
