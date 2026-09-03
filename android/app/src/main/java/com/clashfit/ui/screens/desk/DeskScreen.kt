package com.clashfit.ui.screens.desk

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.nav.Desk
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
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

    Column(
        modifier
            .fillMaxSize()
            .background(Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Headline("DESK")
        Text("TIMER", style = MaterialTheme.typography.headlineLarge, color = Ember)
        SectionGap(24)

        // Toggle
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable", style = MaterialTheme.typography.bodyMedium, color = Ink)
            Switch(enabled, onCheckedChange = { enabled = it })
        }

        if (!enabled) return@Column

        SectionGap(24)

        // Interval chips
        Kicker("Interval")
        SectionGap(12)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(30, 50, 60)) { minutes ->
                Text(
                    "$minutes min",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (intervalMin == minutes) Ground else Ink,
                    modifier = Modifier
                        .background(if (intervalMin == minutes) Ember else Panel)
                        .border(1.dp, if (intervalMin == minutes) Ember else Rule)
                        .clickable { intervalMin = minutes }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        SectionGap(24)

        // Exercise picker
        Kicker("Exercise")
        SectionGap(12)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(repCycleExercises) { (id, _) ->
                Text(
                    id.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (exerciseId == id) Ground else Ink,
                    modifier = Modifier
                        .background(if (exerciseId == id) Ember else Panel)
                        .border(1.dp, if (exerciseId == id) Ember else Rule)
                        .clickable { exerciseId = id }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        SectionGap(24)

        // Reps stepper
        Kicker("Reps")
        SectionGap(12)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                modifier = Modifier
                    .background(Panel)
                    .border(1.dp, Rule)
                    .clickable { if (reps > 1) reps-- }
                    .padding(10.dp)
                    .width(40.dp)
            )
            Text("$reps", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                "+",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                modifier = Modifier
                    .background(Panel)
                    .border(1.dp, Rule)
                    .clickable { reps++ }
                    .padding(10.dp)
                    .width(40.dp)
            )
        }

        SectionGap(28)

        // Quiet hours
        Kicker("Quiet Hours")
        SectionGap(12)
        RuleRow(
            "From",
            "$quietFromHour:00",
            onClick = { quietFromHour = (quietFromHour + 1) % 24 }
        )
        Spacer(Modifier.height(8.dp))
        RuleRow(
            "To",
            "$quietToHour:00",
            onClick = { quietToHour = (quietToHour + 1) % 24 }
        )

        SectionGap(24)

        // Snooze button
        EmberButton(
            "QUIET FOR 2H",
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

fun NavGraphBuilder.deskRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Desk> {
        DeskScreen(graph, nav)
    }
}
