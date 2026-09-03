package com.clashfit.alarm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * The full-screen alarm ring UI. Displays the time, target reps, live rep count with progress pips,
 * snooze button, and an emergency dismiss button that requires a 5-second hold.
 */
@Composable
fun AlarmRingScreen(alarmId: Long, onDismissed: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AlarmRingViewModel = remember {
        AlarmRingViewModel(context, alarmId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val state = uiState ?: return

    var currentTime by remember { mutableStateOf(getCurrentTimeString()) }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = getCurrentTimeString()
        }
    }

    // Observe rep gate events
    val repGate = viewModel.repGate
    val repEvent by remember(state.exercise) {
        repGate.observe(state.exercise)
    }.collectAsState(initial = RepGateEvent(0, state.targetReps, null, null))

    LaunchedEffect(repEvent) {
        viewModel.onRepCountUpdate(repEvent)
        if (repEvent.counted >= repEvent.target) {
            delay(500)
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0C)) // Ground color
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Time display (top)
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFF3EFE8), // Ink
                fontSize = 64.sp,
                textAlign = TextAlign.Center
            )

            // Rep counter and progress pips (center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "${repEvent.counted} PUSH-UPS TO DISMISS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF3EFE8),
                    textAlign = TextAlign.Center
                )

                // Progress pips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(state.targetReps) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (index < repEvent.counted) Color(0xFFFF4F1F) // Ember
                                    else Color(0x29F3EFE8) // Rule
                                )
                        )
                    }
                }

                // Last rep verdict
                if (repEvent.lastVerdict != null) {
                    Text(
                        text = "Last: ${repEvent.lastVerdict}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xA8F3EFE8), // InkMuted
                        textAlign = TextAlign.Center
                    )
                }

                // Cue if available
                val cue = repEvent.cue
                if (cue != null) {
                    Text(
                        text = cue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF2C14E), // Working/yellow
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Buttons (bottom)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Snooze button
                if (state.snoozeLimitRemaining > 0) {
                    SnoozeButton(
                        remaining = state.snoozeLimitRemaining,
                        onSnooze = { viewModel.onSnoozePressed() }
                    )
                }

                // Emergency dismiss button (5-second hold)
                EmergencyDismissButton(
                    isActive = state.isEmergencyHoldActive,
                    progress = state.emergencyHoldProgress,
                    onPressedDown = { viewModel.onEmergencyDismissPressedDown() },
                    onPressedUp = { viewModel.onEmergencyDismissPressedUp() }
                )
            }
        }
    }
}

@Composable
private fun SnoozeButton(remaining: Int, onSnooze: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .background(Color(0xFFFF4F1F)) // Ember
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSnooze() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SNOOZE (${remaining} LEFT)",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF0B0B0C) // Ground
        )
    }
}

@Composable
private fun EmergencyDismissButton(
    isActive: Boolean,
    progress: Float,
    onPressedDown: () -> Unit,
    onPressedUp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(48.dp)
            .background(
                Color(0xFF17171A) // Panel
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressedDown()
                        tryAwaitRelease()
                        onPressedUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Color(0xFFC2330F)) // EmberDeep
        )

        Text(
            text = if (isActive) {
                "HOLD ${(progress * 5).toInt()}s"
            } else {
                "EMERGENCY DISMISS"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFF3EFE8) // Ink
        )
    }
}

private fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}
