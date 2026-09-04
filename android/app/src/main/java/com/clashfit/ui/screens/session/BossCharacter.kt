package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.clashfit.core.model.CombatState
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.EmberLift
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.PanelTop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * THE PACEMAKER. A mechanical construct that mirrors the player's tempo, drawn in code so the
 * APK carries no art that can go stale and so it reads at any size. docs/15-ASSET-BRIEF.md §1
 *
 * Six states, all of them from the brief:
 *   idle       a three second breathing loop; the screen is never dead
 *   hit        120ms, a bright rim, and the silhouette still reads
 *   phase 2    from 60% HP: colour shift, plates flare, visibly angrier
 *   phase 3    from 25% HP: cracked and unstable
 *   stagger    on FADING fatigue: the shell opens, the core is exposed, "push now"
 *   death      a 1.2s collapse, which is the one big moment in the product
 *
 * Everything is drawn from a handful of numbers so the whole character is one Canvas pass. The
 * camera and the pose model own this frame budget, not the boss.
 */

private const val PLATES = 8
private const val TAU = (2 * PI).toFloat()

/** Everything the drawing needs, derived once per frame from the fight's own state. */
private data class Look(
    val shell: Color,
    val rim: Color,
    val core: Color,
    /** 0 calm, 1 furious. Drives the eye, the pulse rate and the plate flare. */
    val rage: Float,
    /** 0 intact, 1 falling apart. Drives cracks and jitter. */
    val damage: Float,
    /** 0 closed, 1 wide open. The stagger posture. */
    val open: Float,
)

private fun lookFor(combat: CombatState): Look {
    val hp = combat.hpPct.coerceIn(0f, 1f)
    return when {
        combat.staggered -> Look(PanelTop, Heavy, Heavy, rage = 0.5f, damage = 1f - hp, open = 1f)
        combat.phaseLabel == "desperation" -> Look(PanelLift, Gassed, Gassed, rage = 1f, damage = 1f - hp, open = 0.25f)
        combat.phaseLabel == "enrage" -> Look(PanelLift, EmberDeep, Ember, rage = 0.6f, damage = 1f - hp, open = 0.1f)
        else -> Look(Panel, Brass, Ember, rage = 0.15f, damage = 1f - hp, open = 0f)
    }
}

/**
 * The boss.
 *
 * @param jolt 0..1, spikes on every landed rep
 * @param shake horizontal offset in pixels, from a phase change or a heavy hit
 */
@Composable
fun BossFigure(combat: CombatState, jolt: Float, shake: Float, modifier: Modifier = Modifier) {
    val look = lookFor(combat)

    // The breath is the tempo it mirrors: faster the angrier it is.
    val breathMs = (2600 - 900 * look.rage).toInt()
    val breath by rememberInfinite(breathMs, 0.96f, 1.04f, "breath")
    // The core beats at double the breath, like a metronome over it.
    val beat by rememberInfinite(breathMs / 2, 0f, 1f, "beat")
    // A slow rotation on the outer ring so the thing never looks frozen.
    val spin by rememberInfinite(14000, 0f, TAU, "spin", reverse = false)

    // Death is a one-shot: plates blow outward, the core collapses, everything fades.
    val death = remember { Animatable(0f) }
    val dead by rememberUpdatedState(combat.dead)
    LaunchedEffect(combat.dead) {
        if (dead) death.animateTo(1f, tween(1200, easing = LinearEasing)) else death.snapTo(0f)
    }
    val d = death.value

    Canvas(modifier) {
        val centre = Offset(size.width / 2f + shake, size.height / 2f)
        val unit = size.minDimension * 0.5f
        // It recoils on a hit and shrinks as it dies.
        val scale = breath * (1f - 0.07f * jolt) * (1f - 0.35f * d)
        // Phase three is unstable: a small constant tremor, worse the closer to death.
        val tremor = if (look.damage > 0.75f) sin(beat * TAU * 6f) * unit * 0.012f * look.damage else 0f

        translate(centre.x + tremor, centre.y) {
            drawRing(unit * scale, look, spin, beat, d)
            drawPlates(unit * scale, look, beat, jolt, d)
            drawArms(unit * scale, look, breath, d)
            drawCore(unit * scale, look, combat.hpPct, beat, jolt, d)
            if (d == 0f) drawEye(unit * scale, look, beat)
            if (jolt > 0.01f && d == 0f) drawHitRim(unit * scale, jolt)
        }
    }
}

/** The outer ring: a slow orbit of ticks, the metronome it keeps. */
private fun DrawScope.drawRing(u: Float, look: Look, spin: Float, beat: Float, d: Float) {
    val alpha = (1f - d).coerceAtLeast(0f)
    if (alpha <= 0f) return
    val r = u * (0.94f + 0.30f * d)
    val ticks = 24
    for (i in 0 until ticks) {
        val a = spin + i * TAU / ticks
        val lit = i % 6 == 0
        val len = if (lit) u * 0.09f else u * 0.045f
        val from = Offset(cos(a) * (r - len), sin(a) * (r - len))
        val to = Offset(cos(a) * r, sin(a) * r)
        drawLine(
            color = (if (lit) look.rim else look.shell).copy(alpha = alpha * if (lit) 0.9f else 0.45f),
            start = from, end = to,
            strokeWidth = if (lit) u * 0.022f else u * 0.012f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The armoured shell: eight plates around the core. They flare with rage, swing wide on a stagger,
 * crack as HP falls, and fly apart on death.
 */
private fun DrawScope.drawPlates(u: Float, look: Look, beat: Float, jolt: Float, d: Float) {
    val alpha = (1f - d * 0.9f).coerceAtLeast(0f)
    if (alpha <= 0f) return
    // How far each plate sits from the centre.
    val flare = 0.06f * look.rage + 0.22f * look.open + 0.04f * beat * look.rage + 0.9f * d + 0.05f * jolt
    val inner = u * (0.34f + flare)
    val outer = u * (0.70f + flare)
    val half = TAU / PLATES * (0.34f - 0.10f * look.open)

    for (i in 0 until PLATES) {
        val a = i * TAU / PLATES - TAU / 4f
        // On death each plate drifts on its own heading and spins away.
        val drift = if (d > 0f) d * u * 0.55f * (0.6f + (i % 3) * 0.2f) else 0f
        val path = Path().apply {
            moveTo(cos(a - half) * inner, sin(a - half) * inner)
            lineTo(cos(a - half * 0.8f) * outer, sin(a - half * 0.8f) * outer)
            lineTo(cos(a + half * 0.8f) * outer, sin(a + half * 0.8f) * outer)
            lineTo(cos(a + half) * inner, sin(a + half) * inner)
            close()
        }
        translate(cos(a) * drift, sin(a) * drift) {
            rotate(d * 40f * (if (i % 2 == 0) 1f else -1f), Offset(cos(a) * outer, sin(a) * outer)) {
                drawPath(path, look.shell.copy(alpha = alpha))
                drawPath(path, look.rim.copy(alpha = alpha * (0.5f + 0.5f * look.rage)), style = Stroke(u * 0.016f))
                // Phase three cracks: a split down the plate, widening with damage.
                if (look.damage > 0.4f) {
                    val t = ((look.damage - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    drawLine(
                        Ground.copy(alpha = alpha * t),
                        Offset(cos(a) * inner, sin(a) * inner),
                        Offset(cos(a + half * 0.35f) * outer, sin(a + half * 0.35f) * outer),
                        strokeWidth = u * 0.010f * t,
                    )
                }
            }
        }
    }
}

/** Two piston arms that pump with the breath. They drop open when it staggers. */
private fun DrawScope.drawArms(u: Float, look: Look, breath: Float, d: Float) {
    val alpha = (1f - d).coerceAtLeast(0f)
    if (alpha <= 0f) return
    val drop = look.open * 0.55f
    for (side in intArrayOf(-1, 1)) {
        val a = (if (side < 0) PI.toFloat() else 0f) + side * (0.35f + drop)
        val shoulder = Offset(cos(a) * u * 0.52f, sin(a) * u * 0.52f)
        val reach = u * (0.42f + 0.06f * (breath - 1f) * 10f)
        val hand = Offset(shoulder.x + cos(a) * reach, shoulder.y + sin(a) * reach + u * drop * 0.5f)
        drawLine(look.shell.copy(alpha = alpha), shoulder, hand, strokeWidth = u * 0.075f, cap = StrokeCap.Round)
        drawLine(look.rim.copy(alpha = alpha * 0.8f), shoulder, hand, strokeWidth = u * 0.022f, cap = StrokeCap.Round)
        drawCircle(look.rim.copy(alpha = alpha), radius = u * 0.055f, center = hand)
    }
}

/** The core: the pacemaker itself. It beats, and its size is the health that is left. */
private fun DrawScope.drawCore(u: Float, look: Look, hpPct: Float, beat: Float, jolt: Float, d: Float) {
    val alpha = (1f - d).coerceAtLeast(0f)
    if (alpha <= 0f) return
    val hp = hpPct.coerceIn(0.04f, 1f)
    // A double thump, like a heartbeat rather than a sine.
    val thump = (sin(beat * TAU) * 0.5f + sin(beat * TAU * 2f) * 0.25f + 0.75f).coerceIn(0f, 1.5f)
    val r = u * (0.16f + 0.16f * hp) * (1f + 0.10f * thump * (0.4f + look.rage)) * (1f - 0.5f * d)

    // The glow around it, which is how a dying boss still reads at two metres.
    drawCircle(look.core.copy(alpha = alpha * 0.18f * (0.5f + 0.5f * thump)), radius = r * 2.1f, center = Offset.Zero)
    drawCircle(look.core.copy(alpha = alpha * 0.30f), radius = r * 1.35f, center = Offset.Zero)
    // A hexagonal core, so it reads as built rather than organic.
    val hex = Path().apply {
        for (i in 0 until 6) {
            val a = i * TAU / 6f + TAU / 12f
            val p = Offset(cos(a) * r, sin(a) * r)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(hex, look.core.copy(alpha = alpha))
    drawPath(hex, Ink.copy(alpha = alpha * (0.35f + 0.65f * jolt)), style = Stroke(u * 0.014f))
}

/** A slit eye above the core. It narrows as the boss gets angry and shuts when it staggers. */
private fun DrawScope.drawEye(u: Float, look: Look, beat: Float) {
    val y = -u * 0.30f
    val w = u * 0.30f
    val openness = (1f - look.rage * 0.55f) * (1f - look.open * 0.8f)
    val h = u * 0.055f * openness.coerceAtLeast(0.06f)
    drawOval(
        color = look.rim,
        topLeft = Offset(-w / 2f, y - h / 2f),
        size = Size(w, h),
    )
    // The pupil tracks the beat, which is what makes it feel alive rather than drawn.
    drawCircle(Ground, radius = h * 0.42f, center = Offset(sin(beat * TAU) * w * 0.16f, y))
}

/** The 120ms hit rim. Bright, brief, and it never hides the silhouette. */
private fun DrawScope.drawHitRim(u: Float, jolt: Float) {
    drawCircle(
        color = EmberLift.copy(alpha = 0.55f * jolt),
        radius = u * (0.80f + 0.28f * jolt),
        center = Offset.Zero,
        style = Stroke(width = u * 0.05f * jolt),
    )
}

@Composable
private fun rememberInfinite(
    durationMs: Int,
    from: Float,
    to: Float,
    label: String,
    reverse: Boolean = true,
) = androidx.compose.animation.core.rememberInfiniteTransition(label = label).animateFloat(
    initialValue = from,
    targetValue = to,
    animationSpec = infiniteRepeatable(
        tween(durationMs.coerceAtLeast(1), easing = LinearEasing),
        if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
    ),
    label = label,
)
