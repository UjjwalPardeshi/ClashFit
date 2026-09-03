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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Verdict
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.FatiguePips
import com.clashfit.ui.components.color
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch

// The fight HUD: three zones, nothing else. Boss + HP on top, the boss and the hit surface in
// the centre, reps · fatigue · combo along the bottom. Everything reads at two metres.
// docs/03-UI-UX-SPEC.md §3

/** Boss identity and HP. The bar overshoots slightly on every hit. */
@Composable
fun BossHeader(combat: CombatState, modifier: Modifier = Modifier, timeLeftMs: Long? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(combat.bossName.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Ink)
            if (timeLeftMs != null) {
                Text(formatClock(timeLeftMs), style = MaterialTheme.typography.headlineMedium, color = Ember)
            } else {
                Text("${(combat.hpPct * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = hpColor(combat.hpPct))
            }
        }
        Spacer(Modifier.height(8.dp))
        Bar(fraction = combat.hpPct, color = hpColor(combat.hpPct), height = 10)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(combat.phaseLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
            if (combat.staggered) Text("STAGGERED", style = MaterialTheme.typography.labelSmall, color = Heavy)
            if (combat.mercyActive) Text("FINISH IT", style = MaterialTheme.typography.labelSmall, color = Ember)
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

/**
 * The boss. No key art in the APK, so the boss is the brand mark made large: a slow breathing
 * square that jolts on a hit, shifts colour with its phase and shakes on a phase change.
 */
@Composable
fun BossFigure(combat: CombatState, jolt: Float, shake: Float, modifier: Modifier = Modifier) {
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(Motion.bossBreathMs / 2), RepeatMode.Reverse), label = "breath",
    )
    val tone = when (combat.phaseLabel) { "enrage" -> EmberDeep; "desperation" -> Ember; else -> Panel }
    val edge = when (combat.phaseLabel) { "phase1" -> Ember; else -> Ink }
    Canvas(modifier.fillMaxSize()) {
        val side = size.minDimension * 0.42f * breath * (1f - 0.08f * jolt)
        val c = Offset(size.width / 2f + shake, size.height / 2f)
        rotate(45f, c) {
            drawRect(tone, topLeft = Offset(c.x - side / 2, c.y - side / 2), size = Size(side, side))
            drawRect(edge, topLeft = Offset(c.x - side / 2, c.y - side / 2), size = Size(side, side), style = Stroke(width = 6f + 30f * jolt))
        }
        // Health as an inner core that shrinks as HP falls.
        val core = side * 0.55f * combat.hpPct.coerceIn(0.05f, 1f)
        rotate(45f, c) {
            drawRect(edge.copy(alpha = 0.85f), topLeft = Offset(c.x - core / 2, c.y - core / 2), size = Size(core, core))
        }
    }
}

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
    Column(modifier.offset(y = rise.value.dp).scale(scale.value).alpha(alpha.value), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+${hit.damage}", fontSize = 96.sp, lineHeight = 96.sp, fontWeight = FontWeight.Black, fontFamily = MaterialTheme.typography.displayLarge.fontFamily, color = hit.verdict.color())
        Text(hit.verdict.name, style = MaterialTheme.typography.labelLarge, color = hit.verdict.color())
    }
}

/** Rep count with a scale pop on change. 80–140sp is the point. */
@Composable
fun RepCounter(reps: Int, modifier: Modifier = Modifier, label: String = "REPS") {
    val pop = remember { Animatable(1f) }
    var last by remember { mutableStateOf(reps) }
    LaunchedEffect(reps) {
        if (reps != last) { last = reps; pop.snapTo(1.18f); pop.animateTo(1f, Motion.counterPop) }
    }
    Column(modifier.background(Panel).border(1.dp, Rule).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$reps", modifier = Modifier.scale(pop.value), fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black, fontFamily = MaterialTheme.typography.displayLarge.fontFamily, color = Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkFaint)
    }
}

@Composable
fun ComboRail(multiplier: Float, streak: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel).border(1.dp, Rule).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("×${"%.1f".format(multiplier)}", fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, fontFamily = MaterialTheme.typography.displayLarge.fontFamily, color = if (multiplier > 1f) Ember else Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            repeat(10) { i -> Box(Modifier.width(8.dp).height(4.dp).background(if (i < streak.coerceAtMost(10)) Ember else Rule)) }
        }
        Text("COMBO", style = MaterialTheme.typography.labelMedium, color = InkFaint)
    }
}

@Composable
fun FatigueTile(band: FatigueBand, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel).border(1.dp, Rule).padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FatiguePips(band, showLabel = false)
        Text(band.label, style = MaterialTheme.typography.headlineSmall, color = band.color())
        Text("FATIGUE", style = MaterialTheme.typography.labelMedium, color = InkFaint)
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
fun CalibrationOverlay(cue: String?, progress: Float, tooFar: Boolean, tooClose: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Target band: the pose's bounding box should sit between 55% and 92% of frame height.
            val top = h * 0.06f; val bottom = h * 0.94f
            val stroke = Stroke(width = 4f)
            drawRect(Ember.copy(alpha = 0.55f), topLeft = Offset(w * 0.22f, top), size = Size(w * 0.56f, bottom - top), style = stroke)
            // Silhouette: head, torso, legs — enough to say "stand here, whole body".
            val cx = w / 2f
            drawCircle(Ink.copy(alpha = 0.35f), radius = w * 0.06f, center = Offset(cx, top + h * 0.12f), style = stroke)
            drawLine(Ink.copy(alpha = 0.35f), Offset(cx, top + h * 0.19f), Offset(cx, top + h * 0.52f), strokeWidth = 4f)
            drawLine(Ink.copy(alpha = 0.35f), Offset(cx, top + h * 0.52f), Offset(cx - w * 0.09f, bottom - h * 0.02f), strokeWidth = 4f)
            drawLine(Ink.copy(alpha = 0.35f), Offset(cx, top + h * 0.52f), Offset(cx + w * 0.09f, bottom - h * 0.02f), strokeWidth = 4f)
            drawLine(Ink.copy(alpha = 0.35f), Offset(cx, top + h * 0.25f), Offset(cx - w * 0.14f, top + h * 0.42f), strokeWidth = 4f)
            drawLine(Ink.copy(alpha = 0.35f), Offset(cx, top + h * 0.25f), Offset(cx + w * 0.14f, top + h * 0.42f), strokeWidth = 4f)
            // Hold ring.
            if (progress > 0f) {
                drawArc(Ember, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(cx - 70f, h * 0.5f - 70f), size = Size(140f, 140f), style = Stroke(width = 10f))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp).background(Ground.copy(alpha = 0.7f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (tooFar) Text("▲", fontSize = 40.sp, color = Ember)
            if (tooClose) Text("▼", fontSize = 40.sp, color = Ember)
            Text(cue ?: "Step into frame.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = Ink)
        }
    }
}

/** A framing-lost banner inline over the fight; the fight pauses, it never navigates away. */
@Composable
fun FramingLostBanner(cue: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(Ember).padding(20.dp)) {
        Text((cue ?: "I lost you — step back into frame.").uppercase(), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ground)
    }
}

/** The one mid-set tap target. Deliberately large; the player is at two metres. */
@Composable
fun PauseTarget(paused: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(72.dp).background(if (paused) Ember else Panel).border(1.dp, Rule).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (paused) "▶" else "❚❚", fontSize = 26.sp, color = if (paused) Ground else Ink)
    }
}
