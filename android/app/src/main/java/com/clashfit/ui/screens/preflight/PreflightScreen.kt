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
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.util.CrashLog
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class PreflightStatus { PASS, WARN, FAIL }

/**
 * Everything that has to be true before a set can be measured, in the words a player would use.
 *
 * These were named for whoever wrote them — "Pose Model", "Exact Alarms", "PASS" — which is fine
 * in a log and wrong on a screen somebody opens because their reps are not counting. Each row now
 * says what it does for the player and what to do when it is not green.
 */
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
            PreflightCheck("Camera", checkCamera(graph.app), "ClashFit cannot count a rep it cannot see"),
            PreflightCheck("Movement tracking", checkPoseModel(graph.app), "Reads your body from the camera, on this phone"),
            PreflightCheck("Hand gestures", checkGestureModel(graph.app), "Palm, thumb and fist, to control a set without touching the phone"),
            PreflightCheck("Exercise data", checkConfig(configVersion), "The movements, their targets and how they score"),
            PreflightCheck("Coach voice", checkTts(graph.app), "So your feedback can be spoken while you move"),
            PreflightCheck("Your baseline", calibStatus, "Measured once, so depth is judged against your body"),
            PreflightCheck("Pacers", if (ghosts.isNotEmpty()) PreflightStatus.PASS else PreflightStatus.WARN, "Opponents to race in Ghost Race"),
            PreflightCheck("Alarms", checkAlarms(graph.app), "So a training bell rings at the minute you set"),
            PreflightCheck("Space", checkStorage(graph.app), "Room for your sessions and the coach"),
            PreflightCheck("Battery", checkBattery(graph.app), "A fight is hard on the camera and the screen"),
        )
    }

    ScreenScaffold(title = "Check your setup", onBack = { nav.navigateUp() }) { padding ->
        Column(modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val passCount = checks.count { it.status == PreflightStatus.PASS }
            val failCount = checks.count { it.status == PreflightStatus.FAIL }
            val isReady = failCount == 0 && passCount == checks.size

            AppCard(Modifier.fillMaxWidth(), padding = 16) {
                Column {
                    Text(
                        if (isReady) "You are ready to train" else "A few things to look at",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Text(
                        if (isReady) "Everything the camera needs is in place."
                        else "${checks.size - passCount} of ${checks.size} need a look. You can still train — these only limit what works.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionGap(20)

            ListGroup {
                checks.forEachIndexed { index, check ->
                    PreflightRow(check)
                    if (index < checks.size - 1) InnerDivider()
                }
            }

            // Whatever last went wrong, readable without a laptop.
            //
            // Loaded off the main thread: a remember block runs during composition, so reading the
            // file there is disk IO on the UI thread — precisely what the StrictMode policy added
            // alongside this is there to catch.
            var crash by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                crash = withContext(Dispatchers.IO) { CrashLog.latest(graph.app) }
            }
            val lastCrash = crash
            if (lastCrash != null) {
                var showTrace by remember { mutableStateOf(false) }
                SectionGap(28)
                SectionTitle("ClashFit closed unexpectedly")
                SectionGap(10)
                AppCard(Modifier.fillMaxWidth(), padding = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "It happened once and the app recovered. Nothing you had finished was lost.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                        if (showTrace) {
                            Text(lastCrash, style = MaterialTheme.typography.bodySmall, color = Gassed)
                        }
                        SecondaryButton(
                            if (showTrace) "Hide details" else "Show details",
                            Modifier.fillMaxWidth(),
                        ) { showTrace = !showTrace }
                        SecondaryButton("Dismiss", Modifier.fillMaxWidth()) {
                            CrashLog.clear(graph.app)
                            crash = null
                        }
                    }
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
        Tag(
            when (check.status) {
                PreflightStatus.PASS -> "Ready"
                PreflightStatus.WARN -> "Check"
                PreflightStatus.FAIL -> "Fix"
            },
            color = statusColor(check.status),
        )
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

/**
 * The models ship inside the APK, so the check opens the asset rather than looking in filesDir.
 * The old check looked in filesDir, where the pose model has never lived, and reported WARN on
 * every phone for a model that was loading fine.
 */
private fun assetPresent(context: Context, path: String): PreflightStatus =
    runCatching { context.assets.openFd(path).use { it.length > 0 } }
        .getOrDefault(false)
        .let { if (it) PreflightStatus.PASS else PreflightStatus.FAIL }

private fun checkPoseModel(context: Context): PreflightStatus = assetPresent(context, "models/pose_landmarker_full.task")

private fun checkGestureModel(context: Context): PreflightStatus = assetPresent(context, "models/gesture_recognizer.task")

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

fun NavGraphBuilder.preflightRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Preflight> {
        PreflightScreen(graph, nav)
    }
}
