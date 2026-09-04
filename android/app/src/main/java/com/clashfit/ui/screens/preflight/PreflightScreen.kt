package com.clashfit.ui.screens.preflight

import android.os.BatteryManager
import android.os.Build

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Working
import java.io.File
import java.util.Locale
import com.clashfit.core.model.GameMode
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.components.SwitchRow
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.util.CrashLog

enum class PreflightStatus { PASS, WARN, FAIL }

/** Run Preflight-style checks: camera permission, pose model, config version, TTS, debug, calibration, ghosts, alarm, storage. */
@Composable
fun PreflightScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    var checks by remember { mutableStateOf<List<PreflightCheck>>(emptyList()) }
    val configVersion by graph.config.version.collectAsState(initial = 0)
    val ghosts by graph.config.ghosts.collectAsState(initial = emptyMap())
    val settings by graph.prefs.settings.collectAsState(initial = com.clashfit.data.Prefs.Settings())
    val scope = rememberCoroutineScope()

    LaunchedEffect(configVersion, ghosts, settings) {
        var calibStatus = PreflightStatus.WARN
        scope.launch {
            val calib = graph.db.calibration().get("squat")
            calibStatus = if (calib != null) PreflightStatus.PASS else PreflightStatus.WARN
        }

        checks = listOf(
            PreflightCheck("Camera", checkCamera(graph.app), "Required to measure movement"),
            PreflightCheck("Pose Model", checkPoseModel(graph.app), "MediaPipe landmarks detector"),
            PreflightCheck("Config", checkConfig(configVersion), "Game balance and exercise data"),
            PreflightCheck("Text-to-Speech", checkTts(graph.app), "Coach audio and voice feedback"),
            PreflightCheck("Debug Overlay", if (settings.debugOverlay) PreflightStatus.WARN else PreflightStatus.PASS, "Turn off before playing"),
            PreflightCheck("Calibration", calibStatus, "Per-exercise form baseline"),
            PreflightCheck("Ghosts", if (ghosts.isNotEmpty()) PreflightStatus.PASS else PreflightStatus.WARN, "Ghost race opponents"),
            PreflightCheck("Exact Alarms", checkAlarms(graph.app), "Wake-up and training bells"),
            PreflightCheck("Storage", checkStorage(graph.app), "Database and model cache"),
            PreflightCheck("Battery", checkBattery(graph.app), "A fight is hard on the camera and the screen"),
        )
    }

    ScreenScaffold(title = "System check", onBack = { nav.navigateUp() }) { padding ->
        Column(modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val passCount = checks.count { it.status == PreflightStatus.PASS }
            val failCount = checks.count { it.status == PreflightStatus.FAIL }
            val isReady = failCount == 0 && passCount == checks.size

            AppCard(Modifier.fillMaxWidth(), padding = 16) {
                Column {
                    Text(
                        if (isReady) "Referee ready" else "Not ready yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Text(
                        "$passCount of ${checks.size} checks passed",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionGap(20)
            Kicker("System checks")
            SectionGap(12)

            ListGroup {
                checks.forEachIndexed { index, check ->
                    PreflightRow(check)
                    if (index < checks.size - 1) InnerDivider()
                }
            }

            // Whatever last went wrong, readable without a laptop.
            val crash = remember { CrashLog.latest(graph.app) }
            if (crash != null) {
                SectionGap(28)
                SectionTitle("Last crash")
                SectionGap(10)
                AppCard(Modifier.fillMaxWidth(), padding = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(crash, style = MaterialTheme.typography.bodySmall, color = Gassed)
                        Text(
                            "Written to the app's own storage, so it survives the restart. " +
                                "Read it out and it is usually enough to say what happened.",
                            style = MaterialTheme.typography.bodySmall, color = InkFaint,
                        )
                        SecondaryButton("Clear", Modifier.fillMaxWidth()) { CrashLog.clear(graph.app) }
                    }
                }
            }

            RescueSection(nav, settings.arenaMode) { v -> scope.launch { graph.prefs.setArenaMode(v) } }

            SectionGap(20)
        }
    }
}

@Composable
private fun PreflightRow(check: PreflightCheck) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(statusIcon(check.status), tint = statusColor(check.status))
        Column(Modifier.weight(1f)) {
            Text(check.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (check.description.isNotEmpty()) {
                Text(check.description, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Tag(check.status.name, color = statusColor(check.status))
    }
}

private fun statusIcon(status: PreflightStatus): ImageVector = when (status) {
    PreflightStatus.PASS -> AppIcons.Check
    PreflightStatus.WARN -> AppIcons.Bolt
    PreflightStatus.FAIL -> AppIcons.Close
}

private fun statusColor(status: PreflightStatus) = when (status) {
    PreflightStatus.PASS -> Fresh
    PreflightStatus.WARN -> Working
    PreflightStatus.FAIL -> Gassed
}

private data class PreflightCheck(
    val name: String,
    val status: PreflightStatus,
    val description: String = ""
)

private fun checkCamera(context: Context): PreflightStatus {
    return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            PreflightStatus.PASS
        } else {
            PreflightStatus.WARN
        }
    } else {
        PreflightStatus.FAIL
    }
}

private fun checkPoseModel(context: Context): PreflightStatus {
    val modelFile = File(context.filesDir, "models/pose_landmarker_full.task")
    return if (modelFile.exists()) PreflightStatus.PASS else PreflightStatus.WARN
}

private fun checkConfig(version: Int): PreflightStatus {
    return if (version > 0) PreflightStatus.PASS else PreflightStatus.WARN
}

private fun checkTts(context: Context): PreflightStatus {
    val tts = TextToSpeech(context, null)
    val available = tts.isLanguageAvailable(Locale.US)
    tts.shutdown()
    return if (available >= TextToSpeech.LANG_AVAILABLE) PreflightStatus.PASS else PreflightStatus.WARN
}

private fun checkAlarms(context: Context): PreflightStatus {
    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
    // canScheduleExactAlarms exists from API 31; below that exact alarms need no consent.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return PreflightStatus.PASS
    return if (alarmManager?.canScheduleExactAlarms() == true) PreflightStatus.PASS else PreflightStatus.WARN
}

private fun checkStorage(context: Context): PreflightStatus {
    val db = File(context.filesDir, "clashfit.db")
    val space = context.filesDir.usableSpace
    return if (space > 100 * 1024 * 1024) PreflightStatus.PASS else PreflightStatus.WARN
}

/**
 * Battery headroom. Item one of the pre-demo ritual in docs/14-TEST-PLAN.md §6, and the failure
 * most likely to end a session halfway through: a fight holds the camera, the screen and an
 * inference loop open at once. Charging counts as fine whatever the level.
 */
private fun checkBattery(context: Context): PreflightStatus {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return PreflightStatus.WARN
    val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    if (plugged) return PreflightStatus.PASS
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return PreflightStatus.WARN
    val pct = level * 100 / scale
    return when {
        pct >= 50 -> PreflightStatus.PASS
        pct >= 20 -> PreflightStatus.WARN
        else -> PreflightStatus.FAIL
    }
}

/**
 * The escapes the demo script names, as buttons.
 *
 * docs/11-DEMO-SCRIPT.md §7 lists what to do when something fails in front of a judge: switch to
 * the recorded trace when the reps miscount, switch to Arena Mode when the pose will not detect.
 * Both were prose. A playbook you cannot execute in ten seconds under a judge's eye is not a
 * playbook, so both are one tap from the screen you already open before a round.
 */
@Composable
private fun RescueSection(nav: NavHostController, arena: Boolean, onArena: (Boolean) -> Unit) {
    SectionGap(28)
    SectionTitle("If it goes wrong")
    SectionGap(10)
    AppCard(Modifier.fillMaxWidth(), padding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Two escapes, from the demo playbook. Both are honest about what they are.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint,
            )
            SecondaryButton("Replay a recorded set", Modifier.fillMaxWidth()) {
                nav.navigate(
                    com.clashfit.ui.nav.Session(
                        mode = GameMode.BOSS_FIGHT.name,
                        exerciseId = "squat",
                        replay = true,
                    ),
                )
            }
            Text(
                "A real squat set to failure, recorded and replayed through the same engine. " +
                    "Labelled REPLAY on screen throughout. Use it when the room's light beats the camera.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint,
            )
            SwitchRow(
                "Arena mode",
                arena,
                onArena,
                supporting = "Bigger numerals, for a phone on the floor. Takes effect on the next session.",
            )
        }
    }
}

fun NavGraphBuilder.preflightRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Preflight> {
        PreflightScreen(graph, nav)
    }
}
