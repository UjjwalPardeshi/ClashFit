package com.clashfit.ui.screens.desk

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.desk.DeskScheduler
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SwitchRow
import com.clashfit.ui.nav.Desk
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.InkFaint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Desk timer configuration. */
@Composable
fun DeskScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsState(initial = null)
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    val settings_ = settings ?: return

    var enabled by remember { mutableStateOf(settings_.deskEnabled) }
    var intervalMin by remember { mutableStateOf(settings_.deskIntervalMin) }
    var exerciseId by remember { mutableStateOf(settings_.deskExerciseId) }
    var reps by remember { mutableStateOf(settings_.deskReps) }
    var quietFromHour by remember { mutableStateOf(settings_.deskQuietFromHour) }
    var quietToHour by remember { mutableStateOf(settings_.deskQuietToHour) }

    var hasNotificationPerm by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPerm = granted }

    val repCycleExercises = remember(exercises) {
        exercises.filter { it.value.family == "REP_CYCLE" }
            .toList()
            .sortedBy { it.first }
    }

    LaunchedEffect(enabled) {
        scope.launch {
            graph.prefs.setDeskEnabled(enabled)
            if (enabled && !hasNotificationPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (enabled) {
                DeskScheduler.schedule(context, graph.clock, scope)
            } else {
                DeskScheduler.cancel(context)
            }
        }
    }

    LaunchedEffect(intervalMin) {
        scope.launch {
            graph.prefs.setDeskIntervalMin(intervalMin)
            if (enabled) {
                DeskScheduler.schedule(context, graph.clock, scope)
            }
        }
    }

    LaunchedEffect(exerciseId) {
        scope.launch {
            graph.prefs.setDeskExerciseId(exerciseId)
        }
    }

    LaunchedEffect(reps) {
        scope.launch {
            graph.prefs.setDeskReps(reps)
        }
    }

    LaunchedEffect(quietFromHour) {
        scope.launch {
            graph.prefs.setDeskQuietFromHour(quietFromHour)
            if (enabled) {
                DeskScheduler.schedule(context, graph.clock, scope)
            }
        }
    }

    LaunchedEffect(quietToHour) {
        scope.launch {
            graph.prefs.setDeskQuietToHour(quietToHour)
            if (enabled) {
                DeskScheduler.schedule(context, graph.clock, scope)
            }
        }
    }

    ScreenScaffold(title = "Desk timer", onBack = { nav.navigateUp() }) { padding ->
        Column(
            modifier
                .fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Intro card with enable toggle
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Sixty seconds, every fifty minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    SwitchRow("Enable", enabled, onCheckedChange = { enabled = it })
                }
            }

            SectionGap(24)

            // Interval picker
            ListGroup(Modifier.alpha(if (enabled) 1f else 0.5f)) {
                RuleRow(
                    "Interval",
                    "$intervalMin min",
                    // `if (enabled) { … } else null` reads as a block, not a lambda, and yields Unit.
                    onClick = { intervalMin = when (intervalMin) { 30 -> 50; 50 -> 60; else -> 30 } }.takeIf { enabled },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionGap(20)

            // Exercise picker
            ListGroup(Modifier.alpha(if (enabled) 1f else 0.5f)) {
                // Kotlin cannot destructure a pair inside a for-loop component, so unpack by hand.
                repCycleExercises.forEachIndexed { idx, entry ->
                    val id = entry.first
                    RuleRow(
                        label = entry.second.name,
                        value = if (exerciseId == id) "Selected" else null,
                        onClick = { exerciseId = id }.takeIf { enabled },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (idx < repCycleExercises.size - 1) InnerDivider()
                }
            }

            SectionGap(20)

            // Reps stepper
            AppCard(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
                Column {
                    Text(
                        "Reps to clear",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "−",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .clickable(enabled = enabled) { if (reps > 1) reps-- }
                                .padding(10.dp)
                                .width(40.dp)
                        )
                        Text("$reps", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "+",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .clickable(enabled = enabled) { reps++ }
                                .padding(10.dp)
                                .width(40.dp)
                        )
                    }
                }
            }

            SectionGap(28)

            // Quiet hours
            ListGroup {
                RuleRow(
                    "Quiet from",
                    "$quietFromHour:00",
                    onClick = { quietFromHour = (quietFromHour + 1) % 24 }
                )
                InnerDivider()
                RuleRow(
                    "Quiet to",
                    "$quietToHour:00",
                    onClick = { quietToHour = (quietToHour + 1) % 24 }
                )
            }

            SectionGap(24)

            // Snooze button
            PrimaryButton(
                "Snooze for 2 hours",
                onClick = {
                    scope.launch {
                        val snoozedUntilMs = graph.clock.nowMs() + 2 * 60 * 60 * 1000
                        graph.prefs.setDeskSnoozedUntilMs(snoozedUntilMs)
                        DeskScheduler.schedule(context, graph.clock, scope)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionGap(24)

            // Info text
            Text(
                "The camera grades this minute like a boss fight. One clean set counts.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )

            SectionGap(40)
        }
    }
}

fun NavGraphBuilder.deskRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Desk> {
        DeskScreen(graph, nav)
    }
}
