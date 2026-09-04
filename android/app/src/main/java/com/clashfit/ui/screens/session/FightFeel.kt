package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashfit.core.model.Verdict
import com.clashfit.ui.components.color
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import kotlin.math.cos
import kotlin.math.sin

/**
 * Game feel: the half-second after a rep lands.
 *
 * The fight already had the numbers right — damage, combo, fatigue — and none of it felt like
 * anything. These are the pieces that make a rep read as an impact rather than a counter
 * incrementing: sparks that leave the point of contact, a stamp when a rep is worth stamping, the
 * combo rail catching fire, and a killing blow that stops the world for a moment.
 *
 * Every one of them checks reduced motion. Someone who has asked their phone to stop moving things
 * has asked for a reason, and a fitness game is exactly the wrong place to ignore that.
 */

private const val TAU = (2 * Math.PI).toFloat()

/**
 * Sparks thrown off the boss on a landed rep.
 *
 * Count and reach scale with damage, so a clean rep at a high combo visibly throws more off the
 * thing than a scraped one. The spread is derived from the hit id rather than random, so the same
 * hit always looks the same — which matters when the screenshot suite renders it.
 */
@Composable
fun ImpactBurst(
    hit: HudEvent.Hit?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (hit == null || reduceMotion) return
    val t = remember(hit.id) { Animatable(0f) }
    LaunchedEffect(hit.id) { t.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }

    val colour = hit.verdict.color()
    val count = (10 + hit.damage / 12).coerceIn(10, 30)
    val reach = (0.16f + hit.damage / 900f).coerceIn(0.16f, 0.42f)

    Canvas(modifier.fillMaxSize()) {
        val f = t.value
        if (f >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.34f)
        val unit = size.minDimension
        val fade = (1f - f) * (1f - f)

        for (i in 0 until count) {
            // A deterministic spread: golden-angle steps, jittered by the hit id.
            val a = (i * 2.399963f + hit.id * 0.7f) % TAU
            val speed = 0.55f + ((i * 37 + hit.id.toInt() * 11) % 100) / 100f * 0.75f
            val d = unit * reach * speed * f
            val p = Offset(origin.x + cos(a) * d, origin.y + sin(a) * d * 0.82f)
            val tail = Offset(origin.x + cos(a) * d * 0.72f, origin.y + sin(a) * d * 0.72f * 0.82f)
            drawLine(colour.copy(alpha = fade * 0.9f), tail, p, strokeWidth = 3.5f * fade + 1f)
        }
        // A shockwave ring, which is what sells the moment of contact.
        drawCircle(
            colour.copy(alpha = fade * 0.5f),
            radius = unit * reach * 1.15f * f,
            center = origin,
            style = Stroke(width = 6f * fade + 1f),
        )
    }
}

/**
 * A stamp for a rep that deserves one.
 *
 * Only fires for a clean rep at a combo of two or better, so it stays rare enough to mean
 * something. A stamp on every rep is wallpaper.
 */
@Composable
fun CritStamp(hit: HudEvent.Hit?, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (hit == null || hit.verdict != Verdict.CLEAN || hit.combo < 2f) return
    val t = remember(hit.id) { Animatable(0f) }
    LaunchedEffect(hit.id) {
        t.snapTo(0f)
        t.animateTo(1f, tween(if (reduceMotion) 1 else 140, easing = FastOutSlowInEasing))
        t.animateTo(2f, tween(520, easing = LinearEasing))
    }
    val f = t.value
    if (f >= 2f) return
    // Slams in oversized, settles, then fades.
    val scale = if (f < 1f) 1.9f - 0.9f * f else 1f
    val alpha = if (f < 1f) f else (2f - f).coerceIn(0f, 1f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CRITICAL",
                fontSize = 34.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                color = Color.White,
                modifier = Modifier.scale(scale).alpha(alpha),
            )
            Text(
                "×${"%.1f".format(hit.combo)} combo",
                style = MaterialTheme.typography.labelLarge,
                color = Ember,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}

/**
 * The killing blow.
 *
 * A white flash, then the words, then it clears out of the way of the summary. Held long enough
 * to register as an ending and short enough that nobody has to sit through it twice.
 */
@Composable
fun BossDownStamp(dead: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (!dead) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(if (reduceMotion) 400 else 1_500, easing = LinearEasing)) }
    val f = t.value

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The flash: instant on, gone in a fifth of a second. Impact has no wind-up.
        if (!reduceMotion && f < 0.14f) {
            Box(Modifier.fillMaxSize().alpha((1f - f / 0.14f) * 0.85f)) {
                Canvas(Modifier.fillMaxSize()) { drawRect(Color.White) }
            }
        }
        val wordsIn = ((f - 0.12f) / 0.18f).coerceIn(0f, 1f)
        val wordsOut = 1f - ((f - 0.80f) / 0.20f).coerceIn(0f, 1f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).alpha(wordsIn * wordsOut),
        ) {
            Text(
                "BOSS DOWN",
                fontSize = 56.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(if (reduceMotion) 1f else 1.5f - 0.5f * wordsIn),
            )
            Text(
                "Every rep of that was measured.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
