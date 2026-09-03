package com.clashfit.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.clashfit.AppGraph
import com.clashfit.data.AlarmEntity
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.Headline
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen showing all alarms with enable/disable toggles and next-ring time.
 */
@Composable
fun AlarmsScreen(graph: AppGraph, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val dao = graph.db.alarms()
    val alarms by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Headline("ALARMS")

        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Panel)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO ALARMS YET",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkFaint
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmListItem(
                        alarm = alarm,
                        dao = dao,
                        scope = scope,
                        onEdit = { navController.navigate(com.clashfit.ui.nav.AlarmEdit(alarm.id)) }
                    )
                }
            }
        }

        EmberButton(
            text = "NEW ALARM",
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate(com.clashfit.ui.nav.AlarmEdit(0L)) }
        )
    }
}

@Composable
private fun AlarmListItem(
    alarm: AlarmEntity,
    dao: com.clashfit.data.AlarmDao,
    scope: kotlinx.coroutines.CoroutineScope,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )

                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            dao.upsert(alarm.copy(enabled = enabled))
                            if (enabled) {
                                AlarmScheduler.schedule(context, alarm)
                            } else {
                                AlarmScheduler.cancel(context, alarm.id)
                            }
                        }
                    }
                )
            }

            // Label
            if (alarm.label.isNotEmpty()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Days and exercise
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatDaysMask(alarm.daysMask),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )

                Text(
                    text = alarm.exerciseId.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )

                Text(
                    text = "${alarm.reps} REPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )
            }

            // Next ring time and edit button
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next: ${getNextRingTime(alarm)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint
                )

                Text(
                    text = "EDIT ↗",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF4F1F),
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onEdit() })
                        }
                        .clickable { onEdit() }
                )
            }
        }
    }
}

private fun formatDaysMask(daysMask: Int): String {
    if (daysMask == 0) return "ONE-SHOT"

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selected = days.filterIndexed { i, _ -> (daysMask and (1 shl i)) != 0 }

    return if (selected.size == 7) {
        "DAILY"
    } else {
        selected.joinToString(" ")
    }
}

private fun getNextRingTime(alarm: AlarmEntity): String {
    // Simplified: just show the time. A full implementation would calculate the next occurrence.
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
    cal.set(Calendar.MINUTE, alarm.minute)

    val sdf = SimpleDateFormat("EEE", Locale.getDefault())
    val dayName = sdf.format(cal.time)

    return "$dayName @ ${String.format("%02d:%02d", alarm.hour, alarm.minute)}"
}

// Import the necessary function
