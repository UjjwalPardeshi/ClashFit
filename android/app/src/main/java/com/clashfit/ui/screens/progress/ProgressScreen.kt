package com.clashfit.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.nav.Character
import com.clashfit.ui.nav.Ghosts
import com.clashfit.ui.nav.History
import com.clashfit.ui.nav.Streaks
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.InkFaint

/** The Progress tab: the numbers that carry across sessions, and the way into each record. */
@Composable
fun ProgressScreen(graph: AppGraph, nav: NavHostController) {
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val countFlow = remember(graph) { graph.db.sessions().count() }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessionCount by countFlow.collectAsStateWithLifecycle(initialValue = 0)
    val current = streak?.current ?: 0
    val best = streak?.best ?: 0

    ScreenScaffold(title = "Progress") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$current", "Day streak", Modifier.weight(1f), color = if (current > 0) Fresh else InkFaint)
                StatTile("$best", "Best", Modifier.weight(1f), color = Ember)
                StatTile("$sessionCount", "Sessions", Modifier.weight(1f))
            }

            if (sessionCount == 0) {
                EmptyState(
                    title = "Nothing yet",
                    body = "Your first set writes the first line here. Every rep after that is measured against it.",
                    icon = AppIcons.Chart,
                )
            }

            SectionGap(24)
            Kicker("Records")
            SectionGap(4)
            NavRow("Streak", { nav.navigate(Streaks) }, supporting = "Rest days count. They are training.")
            NavRow("History", { nav.navigate(History) }, value = "$sessionCount")
            NavRow("Character", { nav.navigate(Character) }, supporting = "What the sets have built")
            NavRow("Ghosts", { nav.navigate(Ghosts) }, supporting = "Your own best, saved to race against")
            SectionGap(32)
        }
    }
}
