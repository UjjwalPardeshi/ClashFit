package com.clashfit.perception

import androidx.lifecycle.compose.LifecycleResumeEffect

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Panel

/**
 * Draws the 11 ClashFit skeleton joints and their connections on a canvas overlay.
 * Landm landmarks are expected to be normalized 0..1 in image space.
 */
@Composable
fun SkeletonOverlay(
    imageLandmarks: Landmarks?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (imageLandmarks != null && imageLandmarks.size >= 33) {
            drawSkeleton(imageLandmarks, size.width, size.height)
        }
    }
}

private fun DrawScope.drawSkeleton(landmarks: Landmarks, canvasWidth: Float, canvasHeight: Float) {
    // ClashFit uses 11 joints. Landmark indices from MediaPipe Pose:
    // 11: left shoulder, 12: right shoulder
    // 13: left elbow, 14: right elbow
    // 15: left wrist, 16: right wrist
    // 23: left hip, 24: right hip
    // 25: left knee, 26: right knee
    // 27: left ankle, 28: right ankle (not in 11-joint set)
    // Also include: 0: nose (for head reference)

    val joints = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
    val bones = listOf(
        // Right side (left from viewer's perspective)
        Pair(12, 14), // shoulder to elbow
        Pair(14, 16), // elbow to wrist
        Pair(12, 24), // shoulder to hip
        Pair(24, 26), // hip to knee
        Pair(26, 28), // knee to ankle
        // Left side (right from viewer's perspective)
        Pair(11, 13), // shoulder to elbow
        Pair(13, 15), // elbow to wrist
        Pair(11, 23), // shoulder to hip
        Pair(23, 25), // hip to knee
        Pair(25, 27), // knee to ankle
        // Connection
        Pair(23, 24), // hips
        Pair(11, 12), // shoulders
    )

    val opacity = 0.3f
    val jointColor = Ember.copy(alpha = opacity)
    val boneColor = Ink.copy(alpha = opacity)
    val jointRadius = 4f

    // Draw bones first (so they appear behind joints)
    for ((from, to) in bones) {
        if (from in landmarks.indices && to in landmarks.indices) {
            val fromLm = landmarks[from]
            val toLm = landmarks[to]
            if (fromLm.visibility > 0.3f && toLm.visibility > 0.3f) {
                val fromPos = Offset(fromLm.x * canvasWidth, fromLm.y * canvasHeight)
                val toPos = Offset(toLm.x * canvasWidth, toLm.y * canvasHeight)
                drawLine(
                    color = boneColor,
                    start = fromPos,
                    end = toPos,
                    strokeWidth = 2f
                )
            }
        }
    }

    // Draw joints
    for (idx in joints) {
        if (idx in landmarks.indices) {
            val lm = landmarks[idx]
            if (lm.visibility > 0.3f) {
                val pos = Offset(lm.x * canvasWidth, lm.y * canvasHeight)
                drawCircle(
                    color = jointColor,
                    radius = jointRadius,
                    center = pos
                )
            }
        }
    }
}

/**
 * Small camera preview composable. Hides behind a panel by default.
 */
@Composable
fun CameraPreview(
    previewView: PreviewView?,
    modifier: Modifier = Modifier,
    show: Boolean = false,
) {
    if (show && previewView != null) {
        AndroidView(
            factory = { previewView },
            modifier = modifier
        )
    }
}

/**
 * Permission gate for camera access. Shows a rationale screen before requesting permission.
 * Passes through to content once permission is granted.
 */
@Composable
fun CameraPermissionGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    // Coming back from system settings must count too.
    LifecycleResumeEffect(Unit) {
        granted = check()
        onPauseOrDispose { }
    }
    if (granted) {
        content()
    } else {
        CameraPermissionRationale(
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onDismiss = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}

/**
 * Plain-language camera permission rationale screen.
 */
@Composable
private fun CameraPermissionRationale(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Panel)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera Access",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "ClashFit needs your camera to track your movement and count reps. " +
                    "Your pose landmarks are processed locally on your phone and never sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = onAllow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text("Allow Camera", color = Color.Black)
            }

            // Secondary action. Two identical filled buttons give a dialog no hierarchy
            // and make declining look as encouraged as allowing.
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not Now")
            }
        }
    }
}
