package com.clashfit.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.Family
import com.clashfit.data.AlarmEntity
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Tag
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
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
    }

    // Filter exercises to REP_CYCLE family only
    val repCycleExercises = exercises.filterValues { it.family == Family.REP_CYCLE.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Headline(if (alarmId > 0) "EDIT ALARM" else "NEW ALARM")

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TIME", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel)
                            .border(1.dp, Rule)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeSelector(
                            value = hour,
                            range = 0..23,
                            label = "HH",
                            onValueChange = { hour = it }
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = Ink)
                        TimeSelector(
                            value = minute,
                            range = 0..59,
                            label = "MM",
                            onValueChange = { minute = it }
                        )
                    }
                }
            }

            // Days selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RECURRENCE", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    RecurrenceSelector(
                        daysMask = daysMask,
                        onDaysMaskChange = { daysMask = it }
                    )
                }
            }

            // Exercise picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("EXERCISE", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel)
                            .border(1.dp, Rule)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(repCycleExercises.values.toList()) { exercise ->
                            Text(
                                text = exercise.name.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (exercise.id == selectedExercise) Color(0xFFFF4F1F) else Ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedExercise = exercise.id }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            // Rep count stepper (3..50)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("REPS TO DISMISS", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel)
                            .border(1.dp, Rule)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "-",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            modifier = Modifier.clickable { if (reps > 3) reps-- }
                        )
                        Text(reps.toString(), style = MaterialTheme.typography.headlineMedium, color = Ink)
                        Text(
                            "+",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            modifier = Modifier.clickable { if (reps < 50) reps++ }
                        )
                    }
                }
            }

            // Snooze limit stepper
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SNOOZE LIMIT", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel)
                            .border(1.dp, Rule)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "-",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            modifier = Modifier.clickable { if (snoozeLimit > 0) snoozeLimit-- }
                        )
                        Text(snoozeLimit.toString(), style = MaterialTheme.typography.headlineMedium, color = Ink)
                        Text(
                            "+",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            modifier = Modifier.clickable { if (snoozeLimit < 10) snoozeLimit++ }
                        )
                    }
                }
            }

            // Label field
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LABEL", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    TextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Rule),
                        placeholder = { Text("e.g., Morning Session", color = InkFaint) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Panel,
                            focusedContainerColor = Panel
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = LocalContext.current

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Panel)
                    .border(1.dp, Rule)
                    .clickable { navController.popBackStack() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CANCEL", style = MaterialTheme.typography.labelMedium, color = Ink)
            }

            EmberButton(
                text = if (alarmId > 0) "UPDATE" else "CREATE",
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

@Composable
private fun TimeSelector(value: Int, range: IntRange, label: String, onValueChange: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .background(Ground)
            .border(1.dp, Rule)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format(Locale.US, "%02d", value),
            style = MaterialTheme.typography.headlineMedium,
            color = Ink
        )
    }
}

@Composable
private fun RecurrenceSelector(daysMask: Int, onDaysMaskChange: (Int) -> Unit) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "One-shot",
            style = MaterialTheme.typography.labelMedium,
            color = if (daysMask == 0) Color(0xFFFF4F1F) else Ink,
            modifier = Modifier.clickable { onDaysMaskChange(0) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { i, day ->
                Tag(
                    text = day,
                    modifier = Modifier.clickable {
                        onDaysMaskChange(daysMask xor (1 shl i))
                    },
                    color = if ((daysMask and (1 shl i)) != 0) Color(0xFFFF4F1F) else Color(0xA8F3EFE8)
                )
            }
        }
    }
}

