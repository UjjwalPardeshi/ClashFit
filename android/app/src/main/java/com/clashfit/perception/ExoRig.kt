package com.clashfit.perception

import androidx.camera.view.PreviewView
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

/**
 * An armoured exo-suit, drawn over the player on the live camera.
 *
 * The whole premise of this app is that the camera is the referee, and until now the only proof
 * of that on screen was a faint stick figure in a corner at thirty per cent opacity. This is the
 * same 33 landmarks, drawn as something worth looking at: plates down every limb that turn with
 * the joint, glowing joints between them, and a chest rig.
 *
 * It is also feedback, not decoration. The rig is coloured by fatigue band, so you can see
 * yourself tiring. It flares white on a clean rep and the plate on the limb that did the work
 * lights up, so a good rep is visible on your own body rather than only in a number.
 *
 * The suit is also where progression shows up. Levelling adds pieces you can see on your own
 * body — shoulder caps, then gauntlets and boots, then a crest — and warms the trim from steel
 * through brass to gold. A level that only changes a number on a profile page is a number; a
 * level that changes what you look like while training is a reason to come back.
 *
 * @param landmarks image-space landmarks, already mirrored to match the preview
 * @param band current fatigue, which sets the rig's colour
 * @param flash 0..1, spikes on a landed rep
 * @param verdict the grade of the last rep, which tints the flash
 * @param level the player's level, which decides how much suit they have earned
 */
@Composable
fun ExoRig(
    landmarks: Landmarks?,
    band: FatigueBand,
    flash: Float,
    verdict: Verdict?,
    modifier: Modifier = Modifier,
    level: Int = 1,
) {
    // A slow pulse along the energy lines, so the suit looks powered even when you are still.
    val pulse = rememberInfiniteTransition(label = "exo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "exoPulse",
    )
    // Reused across frames: a rig redraws at camera rate for the length of a set, and eight fresh
    // Paths per frame is exactly the kind of steady garbage that shows up as a stutter mid-rep.
    val plates = remember { List(LIMBS.size) { Path() } }

    Canvas(modifier.fillMaxSize()) {
        val lm = landmarks ?: return@Canvas
        if (lm.size < 33) return@Canvas

        val p = pulse.value
        val base = when (band) {
            FatigueBand.FRESH -> Fresh
            FatigueBand.WORKING -> Working
            FatigueBand.FADING -> Heavy
            FatigueBand.GASSED -> Gassed
        }
        val hot = when (verdict) {
            Verdict.CLEAN -> Color.White
            Verdict.OK -> Ember
            Verdict.SHALLOW -> Gassed
            null -> Color.White
        }
        val rig = lerpColor(base, hot, flash * 0.85f)
        val glow = 0.30f + 0.22f * p + 0.48f * flash

        // What the suit has earned. Each tier adds a piece you can point at.
        val caps = level >= 5          // shoulder caps
        val extremities = level >= 10  // gauntlets and boots
        val crest = level >= 16        // a crest on the helmet
        // Trim warms with rank: steel, then brass, then gold.
        val trim = when {
            level >= 20 -> Color(0xFFE8C25A)
            level >= 10 -> Color(0xFFBA7A42)
            else -> Color(0xFF8A93A6)
        }

        fun pt(i: Int) = Offset(lm[i].x * size.width, lm[i].y * size.height)
        fun visible(i: Int) = lm[i].visibility > 0.35f

        // Chest rig: shoulders to hips, as a single closed plate with a lit border.
        if (visible(SH_L) && visible(SH_R) && visible(HIP_L) && visible(HIP_R)) {
            val torso = Path().apply {
                val a = pt(SH_L); val b = pt(SH_R); val c = pt(HIP_R); val d = pt(HIP_L)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(torso, rig.copy(alpha = 0.13f + 0.16f * flash))
            drawPath(torso, rig.copy(alpha = 0.55f + 0.45f * flash), style = Stroke(width = 3.5f))

            // The core, on the sternum. It beats with the pulse and blazes on a clean rep.
            val chest = Offset((pt(SH_L).x + pt(SH_R).x + pt(HIP_L).x + pt(HIP_R).x) / 4f,
                               (pt(SH_L).y + pt(SH_R).y + pt(HIP_L).y + pt(HIP_R).y) / 4f)
            val span = hypot(pt(SH_L).x - pt(SH_R).x, pt(SH_L).y - pt(SH_R).y)
            val r = span * (0.13f + 0.03f * p + 0.09f * flash)
            drawCircle(rig.copy(alpha = 0.16f + 0.3f * flash), radius = r * 2.4f, center = chest)
            drawCircle(rig.copy(alpha = glow), radius = r, center = chest)
            drawCircle(Ink.copy(alpha = 0.55f), radius = r, center = chest, style = Stroke(2f))
        }

        // Armour down every limb, each plate turned to its own bone.
        LIMBS.forEachIndexed { i, (from, to, ratio) ->
            if (!visible(from) || !visible(to)) return@forEachIndexed
            val a = pt(from)
            val b = pt(to)
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < 8f) return@forEachIndexed
            val angle = atan2(b.y - a.y, b.x - a.x)
            val w = len * ratio

            val path = plates[i].apply {
                reset()
                // A tapered plate along +X, so rotating by the bone angle lands it on the limb.
                moveTo(0f, -w * 0.5f)
                lineTo(len * 0.82f, -w * 0.34f)
                lineTo(len, 0f)
                lineTo(len * 0.82f, w * 0.34f)
                lineTo(0f, w * 0.5f)
                close()
            }
            translate(a.x, a.y) {
                rotateRad(angle, pivot = Offset.Zero) {
                    drawPath(path, rig.copy(alpha = 0.17f + 0.28f * flash))
                    drawPath(path, rig.copy(alpha = 0.62f + 0.38f * flash), style = Stroke(3f))
                    // An energy line down the middle of the plate.
                    drawLine(
                        rig.copy(alpha = glow),
                        Offset(len * 0.12f, 0f), Offset(len * 0.86f, 0f),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        // Glowing joints, drawn last so they sit on top of the plates that meet there.
        for (j in JOINTS) {
            if (!visible(j)) continue
            val c = pt(j)
            drawCircle(rig.copy(alpha = 0.22f + 0.30f * flash), radius = 13f + 6f * flash, center = c)
            drawCircle(rig.copy(alpha = 0.85f), radius = 5f, center = c)
        }

        // Shoulder caps, from level five.
        if (caps) {
            for ((joint, toward) in listOf(SH_L to EL_L, SH_R to EL_R)) {
                if (!visible(joint) || !visible(toward)) continue
                val c = pt(joint)
                val a = pt(toward)
                val len = hypot(a.x - c.x, a.y - c.y)
                if (len < 8f) continue
                drawCircle(trim.copy(alpha = 0.28f + 0.3f * flash), radius = len * 0.30f, center = c)
                drawCircle(trim.copy(alpha = 0.80f), radius = len * 0.30f, center = c, style = Stroke(3.5f))
            }
        }

        // Gauntlets and boots, from level ten.
        if (extremities) {
            for ((joint, from) in listOf(WR_L to EL_L, WR_R to EL_R, AN_L to KN_L, AN_R to KN_R)) {
                if (!visible(joint) || !visible(from)) continue
                val c = pt(joint)
                val a = pt(from)
                val len = hypot(c.x - a.x, c.y - a.y)
                if (len < 8f) continue
                val angle = atan2(c.y - a.y, c.x - a.x)
                translate(c.x, c.y) {
                    rotateRad(angle, pivot = Offset.Zero) {
                        val w = len * 0.34f
                        drawRect(
                            trim.copy(alpha = 0.34f + 0.3f * flash),
                            topLeft = Offset(-len * 0.22f, -w / 2f),
                            size = androidx.compose.ui.geometry.Size(len * 0.30f, w),
                        )
                    }
                }
            }
        }

        // A helmet ring, so the head is part of the suit rather than left bare.
        if (visible(NOSE) && visible(SH_L) && visible(SH_R)) {
            val head = pt(NOSE)
            val span = hypot(pt(SH_L).x - pt(SH_R).x, pt(SH_L).y - pt(SH_R).y)
            val r = span * 0.42f
            drawCircle(rig.copy(alpha = 0.42f), radius = r, center = head, style = Stroke(3f))
            drawCircle(rig.copy(alpha = 0.10f + 0.2f * flash), radius = r, center = head)
            // A crest, from level sixteen. Three prongs, which is a crown without saying so.
            if (crest) {
                for (k in -1..1) {
                    val a = -1.5708f + k * 0.42f
                    drawLine(
                        trim.copy(alpha = 0.85f),
                        Offset(head.x + cos(a) * r * 0.92f, head.y + sin(a) * r * 0.92f),
                        Offset(head.x + cos(a) * r * 1.44f, head.y + sin(a) * r * 1.44f),
                        strokeWidth = 5f,
                    )
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f,
    )
}
