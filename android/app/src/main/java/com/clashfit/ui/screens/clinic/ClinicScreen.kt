package com.clashfit.ui.screens.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.R
import com.clashfit.core.model.GameMode
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** 30-second sit-to-stand protocol: text, not-a-medical-device, personal trend from SessionDao. */
@Composable
fun ClinicScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val clinic by graph.config.clinic.collectAsState(initial = emptyMap())
    val recentSessions by graph.db.sessions().recent(limit = 50).collectAsState(initial = emptyList())
    val sessions = remember(recentSessions) { recentSessions.filter { it.mode == "CLINIC_STS" }.take(10) }

    val protocol = clinic["clinic_sts"]

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("CLINIC")
        SectionGap(24)

        if (protocol != null) {
            // Protocol description
            Kicker("30-Second Sit-to-Stand")
            SectionGap(12)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .border(1.dp, Rule)
                    .padding(12.dp)
            ) {
                Text(
                    protocol.protocol,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }

            SectionGap(12)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .border(1.dp, Rule)
                    .padding(12.dp)
            ) {
                Text(
                    stringResource(R.string.not_medical),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )
            }

            SectionGap(28)

            // Personal trend
            if (sessions.isNotEmpty()) {
                Kicker("Personal Trend")
                SectionGap(12)
                val bestSession = sessions.maxByOrNull { it.totalReps }
                val recentSession = sessions.firstOrNull()

                if (bestSession != null) {
                    StatTile("${bestSession.totalReps}", "PERSONAL BEST", Modifier.fillMaxWidth(), color = Ember)
                }
                if (recentSession != null) {
                    SectionGap(12)
                    StatTile("${recentSession.totalReps}", "LATEST", Modifier.fillMaxWidth(), color = Ink)
                }
            } else {
                Kicker("No Results Yet")
                SectionGap(12)
                Text(
                    "Start a sit-to-stand session to begin tracking your personal trend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkFaint,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            SectionGap(28)

            // Start button
            EmberButton("START TEST") {
                nav.navigate(Session(GameMode.CLINIC_STS.name, "chair_squat"))
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Protocol not found", style = MaterialTheme.typography.bodyLarge, color = InkFaint)
            }
        }

        SectionGap(20)
    }
}

fun NavGraphBuilder.clinicRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Clinic> {
        ClinicScreen(graph, nav)
    }
}
