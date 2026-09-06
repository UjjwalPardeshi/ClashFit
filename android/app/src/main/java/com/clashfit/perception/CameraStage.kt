package com.clashfit.perception

import androidx.camera.view.PreviewView
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.viewinterop.AndroidView
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Verdict
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Working
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

/** A pose source that can also show the camera image it is reading. */
interface CameraPreviewSource {
    val previewView: PreviewView

    /**
     * Width over height of the analysis image, or null before the first frame.
     *
     * Needed to place an overlay: the preview is scaled to fill the view and centre-cropped, so
     * normalised landmarks only land on the body once the same crop is applied to them.
     */
    val sourceAspect: StateFlow<Float?>
}

/** The live camera image, filling whatever it is given. */
@Composable
fun CameraStage(source: CameraPreviewSource?, modifier: Modifier = Modifier) {
    if (source == null) return
    val view = remember(source) { source.previewView }
    AndroidView(
        factory = { view },
        modifier = modifier.fillMaxSize(),
        // The view is owned by the pose source and outlives this composition, so it must be
        // detached from any previous parent before it can be attached again.
        onReset = { },
    )
}

// MediaPipe's 33-point topology, by the joints this rig cares about.
private const val NOSE = 0
private const val SH_L = 11
private const val SH_R = 12
private const val EL_L = 13
private const val EL_R = 14
private const val WR_L = 15
private const val WR_R = 16
private const val HIP_L = 23
private const val HIP_R = 24
private const val KN_L = 25
private const val KN_R = 26
private const val AN_L = 27
private const val AN_R = 28

/** Which segments get an armour plate, and how wide the plate is relative to its length. */
private val LIMBS = listOf(
    Triple(SH_L, EL_L, 0.36f), Triple(EL_L, WR_L, 0.30f),
    Triple(SH_R, EL_R, 0.36f), Triple(EL_R, WR_R, 0.30f),
    Triple(HIP_L, KN_L, 0.40f), Triple(KN_L, AN_L, 0.32f),
    Triple(HIP_R, KN_R, 0.40f), Triple(KN_R, AN_R, 0.32f),
)

private val JOINTS = listOf(SH_L, SH_R, EL_L, EL_R, WR_L, WR_R, HIP_L, HIP_R, KN_L, KN_R, AN_L, AN_R)
