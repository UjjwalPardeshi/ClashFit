package com.clashfit.ui.screens.clinic

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.R
import com.clashfit.core.model.GameMode
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 30-second sit-to-stand protocol: text, not-a-medical-device, personal trend from SessionDao. */
@Composable
fun ClinicScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val clinic by graph.config.clinic.collectAsState(initial = emptyMap())
    val recentSessions by graph.db.sessions().recent(limit = 50).collectAsState(initial = emptyList())
    val sessions = remember(recentSessions) { recentSessions.filter { it.mode == "CLINIC_STS" }.take(10) }

    // Fix protocol lookup: try both keys, then the first protocol in the map, then null
    val protocol = remember(clinic) {
        clinic["clinic_sts"] ?: clinic["sit_to_stand_30s"] ?: clinic.values.firstOrNull()
    }

    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    ScreenScaffold(title = "Clinic", onBack = { nav.navigateUp() }) { padding ->
        Column(modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (protocol != null) {
                // Protocol description in AppCard
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            protocol.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            protocol.protocol,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            stringResource(R.string.not_medical),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                }

                SectionGap(28)

                // Personal trend and past attempts
                if (sessions.isNotEmpty()) {
                    SectionTitle("Attempts")
                    SectionGap(10)
                    ListGroup {
                        for ((idx, session) in sessions.withIndex()) {
                            RuleRow(
                                label = "${session.totalReps} reps",
                                value = Instant.ofEpochMilli(session.startedAtMs)
                                    .atZone(zone).toLocalDate()
                                    .format(dateFormatter),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (idx < sessions.size - 1) InnerDivider()
                        }
                    }
                } else {
                    SectionGap(4)
                }

                SectionGap(28)

                // Start button
                PrimaryButton(
                    "Start test",
                    onClick = {
                        nav.navigate(Session(GameMode.CLINIC_STS.name, "chair_squat"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SectionGap(8)
                EmptyState(
                    title = "Protocol not found",
                    body = "The sit-to-stand protocol is not configured. Check that the config is loaded.",
                    icon = AppIcons.Heart
                )
            }

            SectionGap(20)
        }
    }
}

fun NavGraphBuilder.clinicRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Clinic> {
        ClinicScreen(graph, nav)
    }
}
