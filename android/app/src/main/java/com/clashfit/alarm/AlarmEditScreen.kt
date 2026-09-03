package com.clashfit.alarm
import android.text.format.DateFormat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.Family
import com.clashfit.data.AlarmEntity
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.LoadingState
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen to create or edit an alarm.
 * Includes time picker, day selector, exercise picker (filtered to REP_CYCLE),
 * rep count stepper, snooze limit, and label field.
 */
@Composable
fun AlarmEditScreen(graph: AppGraph, alarmId: Long, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val dao = graph.db.alarms()
    val configStore = graph.config
    val exercises by configStore.exercises.collectAsStateWithLifecycle()

    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var daysMask by remember { mutableStateOf(0) } // 0 = one-shot
    var selectedExercise by remember { mutableStateOf("squat") }
    var reps by remember { mutableStateOf(5) }
    var snoozeLimit by remember { mutableStateOf(1) }
    var label by remember { mutableStateOf("") }
    // The picker seeds its own state once, so it must not build until the alarm has loaded.
    var loaded by remember(alarmId) { mutableStateOf(alarmId <= 0L) }

    // Load existing alarm if editing
    LaunchedEffect(alarmId) {
        if (alarmId > 0) {
            val alarm = dao.get(alarmId)
            if (alarm != null) {
                hour = alarm.hour
                minute = alarm.minute
                daysMask = alarm.daysMask
                selectedExercise = alarm.exerciseId
                reps = alarm.reps
                snoozeLimit = alarm.snoozeLimit
                label = alarm.label
            }
        }
        loaded = true
    }

    // Filter exercises to REP_CYCLE family only
    val repCycleExercises = exercises.filterValues { it.family == Family.REP_CYCLE.name }

    ScreenScaffold(title = if (alarmId > 0) "Edit alarm" else "New alarm", onBack = { navController.navigateUp() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            // The system decides 24-hour or AM/PM, the way every other alarm app does.
            val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                if (loaded) {
                    key(alarmId) {
                        val timeState = rememberTimePickerState(
                            initialHour = hour,
                            initialMinute = minute,
                            is24Hour = use24Hour,
                        )
                        LaunchedEffect(timeState.hour, timeState.minute) {
                            hour = timeState.hour
                            minute = timeState.minute
                        }
                        TimeInput(state = timeState, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    LoadingState("Loading the alarm", Modifier.height(120.dp))
                }
            }

            SectionGap(24)

            // Days selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recurrence", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = daysMask == 0,
                            onClick = { daysMask = 0 },
                            label = { Text("One-shot") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = PanelLift,
                                labelColor = InkMuted,
                                selectedContainerColor = EmberTint,
                                selectedLabelColor = Ember
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = daysMask == 0,
                                borderColor = Rule,
                                selectedBorderColor = Ember
                            )
                        )
                    }

                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0..2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val idx = row * 3 + col
                                    if (idx < days.size) {
                                        val day = days[idx]
                                        val isSelected = (daysMask and (1 shl idx)) != 0
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { daysMask = daysMask xor (1 shl idx) },
                                            label = { Text(day) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = PanelLift,
                                                labelColor = InkMuted,
                                                selectedContainerColor = EmberTint,
                                                selectedLabelColor = Ember
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = Rule,
                                                selectedBorderColor = Ember
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SectionGap(24)

            // Exercise picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exercise", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                ListGroup {
                    repCycleExercises.values.toList().forEachIndexed { index, exercise ->
                        RuleRow(
                            label = exercise.name,
                            value = if (exercise.id == selectedExercise) "Selected" else null,
                            onClick = { selectedExercise = exercise.id },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (index < repCycleExercises.size - 1) {
                            InnerDivider()
                        }
                    }
                }
            }

            SectionGap(24)

            // Rep count stepper
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reps to dismiss", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                ListGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (reps > 3) reps-- },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text("−", style = MaterialTheme.typography.headlineMedium, color = Ember)
                        }
                        Text(
                            reps.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink
                        )
                        IconButton(
                            onClick = { if (reps < 50) reps++ },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium, color = Ember)
                        }
                    }
                }
            }

            SectionGap(24)

            // Snooze limit stepper
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Snooze limit", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                ListGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (snoozeLimit > 0) snoozeLimit-- },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text("−", style = MaterialTheme.typography.headlineMedium, color = Ember)
                        }
                        Text(
                            snoozeLimit.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink
                        )
                        IconButton(
                            onClick = { if (snoozeLimit < 10) snoozeLimit++ },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium, color = Ember)
                        }
                    }
                }
            }

            SectionGap(24)

            // Label field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Label", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional label", color = InkMuted) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = PanelLift,
                        focusedContainerColor = PanelLift,
                        unfocusedIndicatorColor = Rule,
                        focusedIndicatorColor = Ember,
                        unfocusedTextColor = Ink,
                        focusedTextColor = Ink,
                        cursorColor = Ember
                    ),
                    singleLine = true
                )
            }

            SectionGap(32)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val context = LocalContext.current

                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.popBackStack() }
                )

                PrimaryButton(
                    text = if (alarmId > 0) "Update" else "Create",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            val alarm = AlarmEntity(
                                id = alarmId,
                                hour = hour,
                                minute = minute,
                                daysMask = daysMask,
                                enabled = true,
                                exerciseId = selectedExercise,
                                reps = reps,
                                snoozeLimit = snoozeLimit,
                                label = label
                            )
                            val id = dao.upsert(alarm)
                            AlarmScheduler.schedule(context, alarm.copy(id = id))
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}


