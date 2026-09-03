package com.clashfit.ui.screens.preflight

import android.os.Build

import android.content.Context
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
        )
    }

    ScreenScaffold(title = "Camera check", onBack = { nav.navigateUp() }) { padding ->
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

fun NavGraphBuilder.preflightRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Preflight> {
        PreflightScreen(graph, nav)
    }
}
