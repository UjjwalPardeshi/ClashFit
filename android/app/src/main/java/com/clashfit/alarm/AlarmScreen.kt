package com.clashfit.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.data.AlarmEntity
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen showing all alarms with enable/disable toggles and next-ring time.
 */
@Composable
fun AlarmsScreen(graph: AppGraph, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val dao = graph.db.alarms()
    val alarms by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Wake-up alarm", onBack = { navController.navigateUp() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            if (alarms.isEmpty()) {
                EmptyState(
                    title = "No alarms yet",
                    body = "Create one to wake up on workout days",
                    icon = AppIcons.Bell,
                )
            } else {
                ListGroup {
                    alarms.forEachIndexed { index, alarm ->
                        AlarmListItem(
                            alarm = alarm,
                            exerciseName = exercises[alarm.exerciseId]?.name ?: alarm.exerciseId,
                            dao = dao,
                            scope = scope,
                            onEdit = { navController.navigate(com.clashfit.ui.nav.AlarmEdit(alarm.id)) }
                        )
                        if (index < alarms.size - 1) {
                            InnerDivider()
                        }
                    }
                }
            }

            SectionGap(24)
            PrimaryButton(
                text = "Add alarm",
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(com.clashfit.ui.nav.AlarmEdit(0L)) }
            )
            SectionGap(8)
        }
    }
}

@Composable
private fun AlarmListItem(
    alarm: AlarmEntity,
    exerciseName: String,
    dao: com.clashfit.data.AlarmDao,
    scope: kotlinx.coroutines.CoroutineScope,
    onEdit: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(buildSupportingText(alarm, exerciseName), style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEdit() }, modifier = Modifier.size(40.dp)) {
                    Icon(AppIcons.Chevron, contentDescription = "Edit", tint = InkFaint, modifier = Modifier.size(18.dp))
                }
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
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ground, checkedTrackColor = Ember,
                        uncheckedThumbColor = InkMuted, uncheckedTrackColor = PanelLift, uncheckedBorderColor = Rule,
                    ),
                )
            }
        }
    }
}

@Composable
private fun buildSupportingText(alarm: AlarmEntity, exerciseName: String): String {
    val dayText = formatDaysMask(alarm.daysMask)
    val exerciseText = exerciseName
    val repsText = "${alarm.reps} rep" + (if (alarm.reps != 1) "s" else "")

    return "$dayText · $exerciseText · $repsText"
}

private fun formatDaysMask(daysMask: Int): String {
    if (daysMask == 0) return "One-shot"

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selected = days.filterIndexed { i, _ -> (daysMask and (1 shl i)) != 0 }

    return if (selected.size == 7) {
        "Daily"
    } else {
        selected.joinToString(" ")
    }
}
