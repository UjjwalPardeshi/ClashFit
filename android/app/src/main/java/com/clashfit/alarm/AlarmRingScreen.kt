package com.clashfit.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clashfit.AppGraph
import com.clashfit.core.pose.CameraFacing
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.pose.SyntheticPoseSource
import com.clashfit.perception.CameraPermissionGate
import com.clashfit.perception.MediaPipePoseSource
import com.clashfit.perception.SkeletonOverlay
import com.clashfit.ui.screens.session.SessionWiring
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Working
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full-screen alarm ring UI. The camera counts verified reps of the alarm's exercise and the
 * alarm keeps ringing until the configured number of them is done — that is the whole promise.
 *
 * The camera occupies the top of the screen and is the only part behind the permission gate: the
 * numeral, the snooze button and the emergency hold stay reachable whether or not the camera is
 * allowed, so a denied permission never traps anyone in a ringing alarm.
 */
@Composable
fun AlarmRingScreen(alarmId: Long, onDismissed: () -> Unit) {
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.of(context) }
    val vm: AlarmRingViewModel = viewModel(
        key = "alarm-$alarmId",
        factory = AlarmRingViewModel.factory(context, alarmId),
    )

    val uiState by vm.uiState.collectAsState()
    val state = uiState

    var currentTime by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = currentTimeString()
        }
    }

    // The reps are in, or the emergency hold ran its course: let the ring go. Snoozing does not
    // end it — the alarm keeps ringing until the reps are verified.
    LaunchedEffect(state?.finished) {
        if (state?.finished == true) {
            delay(400)
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            // This draws over the lock screen, so nothing may sit under a notch or a gesture bar.
            .safeDrawingPadding()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        if (state == null) {
            // The alarm row is still loading. Show the clock rather than a black screen.
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayMedium,
                color = Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                // The camera stage: roughly 40% of the screen, square, at the top.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.40f),
                    contentAlignment = Alignment.Center,
                ) {
                    RepCamera(graph, vm, state)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RepCountdown(state)
                    RepProgress(counted = state.repEvent.counted, target = state.targetReps)
                    DismissControls(
                        state = state,
                        onSnooze = { vm.onSnoozePressed() },
                        onHoldStart = { vm.onEmergencyDismissPressedDown() },
                        onHoldEnd = { vm.onEmergencyDismissPressedUp() },
                    )
                }
            }
        }
    }
}

/**
 * The rep-counting half of the screen. Everything inside needs the camera, so it — and only it —
 * sits behind [CameraPermissionGate]. The gate reads the permission once per composition, so it is
 * re-keyed when the grant lands and the stage appears without leaving the ring screen.
 */
@Composable
private fun RepCamera(graph: AppGraph, vm: AlarmRingViewModel, state: AlarmRingUiState) {
    // The emulator demo has no camera; the scripted body drives the gate exactly the same way.
    val synthetic = remember { SessionWiring.forceSynthetic }
    if (synthetic) {
        RepCameraStage(graph, vm, state, synthetic = true)
    } else {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasCameraPermission(context)) }
        // The gate reads the permission with a keyless `remember`, so it cannot notice a grant on
        // its own. Polling while it is missing, and re-keying it when the grant lands, does that.
        LaunchedEffect(granted) {
            while (!granted) {
                delay(500)
                granted = hasCameraPermission(context)
            }
        }

        key(granted) {
            CameraPermissionGate {
                RepCameraStage(graph, vm, state, synthetic = false)
            }
        }
    }
}

/**
 * Owns the pose source for as long as it is on screen and hands the gate to the view model.
 * [MediaPipePoseSource] runs off `ImageAnalysis` and exposes no `PreviewView`, so there is no
 * camera image to show: the skeleton drawn from the frames is the preview.
 */
@Composable
private fun RepCameraStage(
    graph: AppGraph,
    vm: AlarmRingViewModel,
    state: AlarmRingUiState,
    synthetic: Boolean,
) {
    val owner = LocalLifecycleOwner.current
    val poseSource: PoseSource = remember(owner, synthetic) {
        if (synthetic) SyntheticPoseSource(graph.scope)
        else MediaPipePoseSource(graph.app, graph.config.pose.value, graph.clock, owner, graph.scope)
    }
    val gate = remember(poseSource, state.exercise, state.targetReps) {
        RealRepGate(graph.app, poseSource, graph.config, graph.json, graph.clock, target = state.targetReps)
    }

    LaunchedEffect(gate, state.alarmId, state.exercise) { vm.attachGate(gate) }

    DisposableEffect(gate) {
        poseSource.start(CameraFacing.FRONT)
        onDispose {
            gate.stop()
            poseSource.stop()
        }
    }

    val skeleton by gate.skeleton.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(PanelLift),
    ) {
        Text(
            text = if (synthetic) "Synthetic body" else "Camera on",
            style = MaterialTheme.typography.labelMedium,
            color = if (skeleton == null) InkFaint else Ember,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        )
        SkeletonOverlay(skeleton, Modifier.fillMaxSize())
        if (skeleton == null) {
            Text(
                text = "FIND YOUR FRAME",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The number that matters: reps still owed, then the exercise, then the coach's cue. */
@Composable
private fun RepCountdown(state: AlarmRingUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.remainingReps.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = if (state.remainingReps == 0) Ember else Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${state.exerciseName} to dismiss",
            style = MaterialTheme.typography.labelLarge,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
        val cue = state.repEvent.cue
        val verdict = state.repEvent.lastVerdict
        Spacer(Modifier.height(8.dp))
        Text(
            text = cue ?: verdict?.let { "LAST REP: $it" } ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (cue != null) Working else InkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pips while they fit on one line, a bar once the alarm asks for more reps than that. */
@Composable
private fun RepProgress(counted: Int, target: Int) {
    if (target in 1..12) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(target) { index ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(if (index < counted) Ember else Rule),
                )
            }
        }
    } else {
        val fraction = if (target > 0) (counted.toFloat() / target).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(10.dp)
                .background(Rule),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Ember),
            )
        }
    }
}

/** Snooze while snoozes remain, and the five-second emergency hold. Never behind the camera gate. */
@Composable
private fun DismissControls(
    state: AlarmRingUiState,
    onSnooze: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.snoozeLimitRemaining > 0) {
            SnoozeButton(remaining = state.snoozeLimitRemaining, onSnooze = onSnooze)
        } else {
            Text(
                text = "NO SNOOZES LEFT",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
            )
        }

        EmergencyDismissButton(
            isActive = state.isEmergencyHoldActive,
            progress = state.emergencyHoldProgress,
            onPressedDown = onHoldStart,
            onPressedUp = onHoldEnd,
        )
    }
}

@Composable
private fun SnoozeButton(remaining: Int, onSnooze: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .background(Ember)
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSnooze() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SNOOZE ($remaining LEFT)",
            style = MaterialTheme.typography.labelLarge,
            color = Ground,
        )
    }
}

@Composable
private fun EmergencyDismissButton(
    isActive: Boolean,
    progress: Float,
    onPressedDown: () -> Unit,
    onPressedUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(48.dp)
            .background(Panel)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressedDown()
                        tryAwaitRelease()
                        onPressedUp()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(EmberDeep),
        )

        Text(
            text = if (isActive) "HOLD ${(progress * 5).toInt()}s" else "EMERGENCY DISMISS",
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun currentTimeString(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
