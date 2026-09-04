package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Verdict
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.FatiguePips
import com.clashfit.ui.components.SegmentedBar
import com.clashfit.ui.components.color
import androidx.compose.material3.Icon
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.meta.MetaState
import com.clashfit.ui.components.Bar
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Success

// The fight HUD: three zones, nothing else. Boss + HP on top, the boss and the hit surface in
// the centre, reps · fatigue · combo along the bottom. Everything reads at two metres.
// docs/03-UI-UX-SPEC.md §3

/** Boss identity and HP. The bar overshoots slightly on every hit. */
@Composable
fun BossHeader(combat: CombatState, modifier: Modifier = Modifier, timeLeftMs: Long? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(combat.bossName, style = MaterialTheme.typography.headlineMedium, color = Ink)
            if (timeLeftMs != null) {
                Text(formatClock(timeLeftMs), style = MaterialTheme.typography.headlineMedium, color = Ember)
            } else {
                Text("${(combat.hpPct * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = hpColor(combat.hpPct))
            }
        }
        Spacer(Modifier.height(8.dp))
        SegmentedBar(fraction = combat.hpPct, color = hpColor(combat.hpPct), height = 10, segments = 20)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(combat.phaseLabel, style = MaterialTheme.typography.labelSmall, color = InkFaint)
            if (combat.staggered) Text("Staggered", style = MaterialTheme.typography.labelSmall, color = Heavy)
            if (combat.mercyActive) Text("Finish it", style = MaterialTheme.typography.labelSmall, color = Ember)
        }
    }
}

fun hpColor(pct: Float): Color = when {
    pct > 0.6f -> Ink
    pct > 0.25f -> Heavy
    else -> Ember
}

fun formatClock(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// The boss character lives in BossCharacter.kt: THE PACEMAKER, drawn in code with the six
// states the asset brief specifies. docs/15-ASSET-BRIEF.md §1

/** Damage numeral: punches in at the boss, scales 1.0 → 1.25 → 1.0, drifts up, fades over 700ms. */
@Composable
fun DamageNumeral(hit: HudEvent.Hit?, modifier: Modifier = Modifier) {
    if (hit == null) return
    val scale = remember(hit.id) { Animatable(0.6f) }
    val rise = remember(hit.id) { Animatable(0f) }
    val alpha = remember(hit.id) { Animatable(1f) }
    LaunchedEffect(hit.id) {
        launch { scale.animateTo(1.25f, Motion.damageNumeral); scale.animateTo(1f, Motion.damageNumeral) }
        launch { rise.animateTo(-48f, tween(700)) }
        launch { alpha.animateTo(0f, tween(700, delayMillis = 220)) }
    }
    Column(modifier.offset { IntOffset(0, rise.value.dp.roundToPx()) }.scale(scale.value).alpha(alpha.value), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+${hit.damage}", fontSize = 96.sp, lineHeight = 96.sp, fontWeight = FontWeight.Black, fontFamily = MaterialTheme.typography.displayLarge.fontFamily, color = hit.verdict.color())
        Text(hit.verdict.name, style = MaterialTheme.typography.labelLarge, color = hit.verdict.color())
    }
}

/** Rep count with a scale pop on change. 80–140sp is the point. */
@Composable
fun RepCounter(reps: Int, modifier: Modifier = Modifier, label: String = "Reps") {
    val pop = remember { Animatable(1f) }
    var last by remember { mutableStateOf(reps) }
    LaunchedEffect(reps) {
        if (reps != last) { last = reps; pop.snapTo(1.18f); pop.animateTo(1f, Motion.counterPop) }
    }
    AppCard(modifier, padding = 12) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$reps", modifier = Modifier.scale(pop.value), fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black, fontFamily = MaterialTheme.typography.displayLarge.fontFamily, color = Ink)
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkFaint)
        }
    }
}

/**
 * The combo rail, which catches fire once the streak is worth protecting.
 *
 * Above ×2 the number breathes and the rail glows. That threshold is deliberate: a combo you can
 * hold without trying is not worth lighting up, and a rail that is always on fire tells you
 * nothing about the rep you just did.
 */
@Composable
fun ComboRail(multiplier: Float, streak: Int, modifier: Modifier = Modifier) {
    val hot = multiplier >= 2f
    val burn by rememberInfiniteTransition(label = "combo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Reverse),
        label = "comboBurn",
    )
    val heat = if (hot) burn else 0f

    AppCard(modifier, padding = 12, container = if (hot) EmberDeep.copy(alpha = 0.20f + 0.14f * heat) else Panel) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "×${"%.1f".format(multiplier)}",
                fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                color = if (multiplier > 1f) Ember else Ink,
                modifier = Modifier.scale(1f + 0.06f * heat),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                repeat(10) { i ->
                    val lit = i < streak.coerceAtMost(10)
                    Box(
                        Modifier.width(8.dp)
                            .height(if (lit && hot) (4 + 3 * heat).dp else 4.dp)
                            .background(if (lit) Ember else Rule),
                    )
                }
            }
            Text(if (hot) "On fire" else "Combo", style = MaterialTheme.typography.labelMedium, color = if (hot) Ember else InkFaint)
        }
    }
}

@Composable
fun FatigueTile(band: FatigueBand, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 12) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FatiguePips(band, showLabel = false)
            Text(band.label, style = MaterialTheme.typography.headlineSmall, color = band.color())
            Text("Fatigue", style = MaterialTheme.typography.labelMedium, color = InkFaint)
        }
    }
}

/**
 * Full-screen edge flash, 120ms ease-out, no ease-in — impact has no wind-up. With reduced motion
 * it becomes a border pulse. The verdict word always accompanies the colour.
 */
@Composable
fun HitFlash(hit: HudEvent.Hit?, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (hit == null) return
    val a = remember(hit.id) { Animatable(if (reduceMotion) 0.55f else 0.35f) }
    LaunchedEffect(hit.id) { a.animateTo(0f, if (reduceMotion) tween(260) else Motion.hitFlash) }
    if (reduceMotion) {
        Box(modifier.fillMaxSize().border(8.dp, hit.verdict.color().copy(alpha = a.value)))
    } else {
        Box(modifier.fillMaxSize().background(hit.verdict.color().copy(alpha = a.value)))
    }
}

/** Calibration guide: a silhouette in the target framing band, the named cue, the 2s hold ring. */
@Composable
fun CalibrationOverlay(cue: String?, progress: Float, tooFar: Boolean, tooClose: Boolean, exerciseId: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Target band: the pose's bounding box should sit between 55% and 92% of frame height.
            val top = h * 0.06f; val bottom = h * 0.94f
            val stroke = Stroke(width = 4f)
            drawRect(Ember.copy(alpha = 0.55f), topLeft = Offset(w * 0.22f, top), size = Size(w * 0.56f, bottom - top), style = stroke)

            // Draw appropriate silhouette based on exercise
            val isProne = isProneExercise(exerciseId)
            val cx = w / 2f
            val silhouetteAlpha = 0.35f

            if (isProne) {
                // Horizontal prone figure (push-up, plank position)
                val neckY = h * 0.45f
                val shoulderY = neckY + h * 0.06f
                val hipY = shoulderY + h * 0.2f
                val footY = hipY + h * 0.12f

                // Head
                drawCircle(Ink.copy(alpha = silhouetteAlpha), radius = w * 0.04f, center = Offset(cx - w * 0.2f, neckY - h * 0.06f), style = stroke)
                // Horizontal torso
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx - w * 0.15f, shoulderY), Offset(cx + w * 0.22f, hipY), strokeWidth = 4f)
                // Legs
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx + w * 0.22f, hipY), Offset(cx + w * 0.28f, footY), strokeWidth = 4f)
                // Arms extended
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx - w * 0.15f, shoulderY), Offset(cx - w * 0.28f, shoulderY - h * 0.08f), strokeWidth = 4f)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx - w * 0.15f, shoulderY), Offset(cx - w * 0.28f, shoulderY + h * 0.08f), strokeWidth = 4f)
            } else {
                // Standing figure (default: squat, side/front standing)
                drawCircle(Ink.copy(alpha = silhouetteAlpha), radius = w * 0.06f, center = Offset(cx, top + h * 0.12f), style = stroke)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx, top + h * 0.19f), Offset(cx, top + h * 0.52f), strokeWidth = 4f)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx, top + h * 0.52f), Offset(cx - w * 0.09f, bottom - h * 0.02f), strokeWidth = 4f)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx, top + h * 0.52f), Offset(cx + w * 0.09f, bottom - h * 0.02f), strokeWidth = 4f)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx, top + h * 0.25f), Offset(cx - w * 0.14f, top + h * 0.42f), strokeWidth = 4f)
                drawLine(Ink.copy(alpha = silhouetteAlpha), Offset(cx, top + h * 0.25f), Offset(cx + w * 0.14f, top + h * 0.42f), strokeWidth = 4f)
            }

            // Hold ring.
            if (progress > 0f) {
                drawArc(Ember, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(cx - 70f, h * 0.5f - 70f), size = Size(140f, 140f), style = Stroke(width = 10f))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp).background(Ground.copy(alpha = 0.7f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (tooFar) Text("Too far", fontSize = 28.sp, style = MaterialTheme.typography.labelMedium, color = Ember)
            if (tooClose) Text("Too close", fontSize = 28.sp, style = MaterialTheme.typography.labelMedium, color = Ember)
            Text(cue ?: "Step into frame.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = Ink)
        }
    }
}

/** Helper to classify whether an exercise is prone (horizontal) or upright. */
private fun isProneExercise(exerciseId: String?): Boolean {
    if (exerciseId == null) return false
    val id = exerciseId.lowercase()
    return id.contains("plank") || id.contains("push") || id.contains("burpee") ||
           id.contains("prone") || id.contains("mountain_climber")
}

/** A framing-lost banner inline over the fight; the fight pauses, it never navigates away. */
@Composable
fun FramingLostBanner(cue: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(Ember).padding(20.dp)) {
        Text(cue ?: "I lost you. Step back into frame.", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ground)
    }
}

/** The one mid-set tap target. Deliberately large; the player is at two metres. */
/**
 * How far the fight's top row must start from the left to clear the pause target: the target is a
 * 72dp square with 12dp of padding, the surrounding column already contributes 16dp, and the rest
 * is breathing room.
 */
val PAUSE_TARGET_INSET = 76.dp

@Composable
fun PauseTarget(paused: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.size(72.dp), padding = 0, container = if (paused) Ember else Panel, onClick = onToggle) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                if (paused) AppIcons.Check else AppIcons.Close,
                contentDescription = if (paused) "Resume" else "Pause",
                tint = if (paused) Ground else Ink,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Your rank and this week's target, in the fight.
 *
 * Level and badges lived on a profile screen and changed nothing about playing, which made them
 * decoration. Here they are where the work happens: the rank you fight under, and the weekly
 * target filling up rep by rep while you watch. Deliberately small and low-contrast — it is
 * context, not something to read mid-set.
 */
@Composable
fun RankChip(meta: MetaState?, modifier: Modifier = Modifier) {
    if (meta == null) return
    val weekly = meta.weekly
    AppCard(modifier.width(132.dp), padding = 10, container = Panel.copy(alpha = 0.82f)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "LVL ${meta.progress.level}",
                style = MaterialTheme.typography.labelLarge,
                color = Brass,
            )
            Text(
                meta.progress.title,
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
                maxLines = 1,
            )
            Bar(meta.progress.fraction, color = Brass, height = 4)
            Text(
                if (weekly.done) "Weekly done" else weekly.challenge.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (weekly.done) Success else InkFaint,
                maxLines = 1,
            )
            Bar(weekly.fraction, color = if (weekly.done) Success else Ember, height = 4)
        }
    }
}
