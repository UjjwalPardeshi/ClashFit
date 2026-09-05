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
import androidx.compose.foundation.layout.Arrangement
import com.clashfit.ui.theme.InkMuted

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

                SectionGap(28)

                // The clinic is designed as five protocols; one is measured well enough to ship.
                // Naming the other four is more useful than pretending the set is complete, and
                // more honest than a greyed-out button that does nothing.
                SectionTitle("Coming next")
                SectionGap(10)
                AppCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PLANNED.forEach { (name, what) ->
                            Column {
                                Text(name, style = MaterialTheme.typography.titleSmall, color = InkMuted)
                                Text(
                                    what,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkFaint,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Text(
                            "Each one needs its own validated scoring before it can be trusted. A test measured roughly is " +
                                "worth less than no test at all.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkFaint,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                SectionGap(8)
                EmptyState(
                    title = "Protocol not found",
                    body = "This test could not be loaded. Close ClashFit and open it again.",
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

/** The other four protocols from docs/25-CLINIC-MODE.md §1, none of which ship yet. */
private val PLANNED = listOf(
    "Five-times sit-to-stand" to "The same movement, timed to five reps instead of counted for thirty seconds",
    "Single-leg stance" to "Static balance, timed until the raised foot touches down",
    "Functional reach" to "How far you can reach forward with your feet planted",
    "Timed up-and-go" to "Stand, walk three metres, turn, come back, sit",
)
