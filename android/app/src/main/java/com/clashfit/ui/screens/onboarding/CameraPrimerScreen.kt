package com.clashfit.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success

/**
 * Why the app wants the camera, before the system asks. If permission is already granted this
 * screen is skipped by the caller. Declining is allowed; the fight screen asks again.
 */
@Composable
fun CameraPrimerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) onDone()
    }

    Column(Modifier.fillMaxSize().background(Ground).safeDrawingPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBubble(AppIcons.Camera, size = 96)
            Spacer(Modifier.height(24.dp))
            Text("WE GRADE YOUR REPS", style = MaterialTheme.typography.headlineLarge, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "The camera watches your form, counts every rep, and scores quality. Everything runs on this phone.",
                style = MaterialTheme.typography.bodyLarge, color = InkMuted, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        AppCard(Modifier.fillMaxWidth(), padding = 6) {
            Column {
                Promise(AppIcons.Shield, "No frames leave the phone", "Pose runs on-device. Nothing is uploaded, ever.")
                Promise(AppIcons.Close, "Nothing is recorded", "Frames are read, scored and discarded in the same instant.")
                Promise(AppIcons.Check, "Only your scores sync", "Reps, damage and levels go to the leaderboard. That is all.")
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Allow camera") { launcher.launch(Manifest.permission.CAMERA) }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LinkButton("Not now", onClick = onDone) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBubble(icon, tint = Success, size = 40)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}
