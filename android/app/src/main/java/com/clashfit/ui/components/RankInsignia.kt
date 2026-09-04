package com.clashfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gold
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.Silver
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/**
 * A rank, as an emblem rather than as the words "Level 7 · Fighter".
 *
 * A number and a noun are a database row. An emblem is a thing you recognise across a room, want
 * the next version of, and notice on somebody else's screen — which is the entire mechanism by
 * which a rank means anything at all.
 *
 * It earns its detail as you climb. The plate is steel, then brass, then gold. Chevrons accumulate
 * to three and then reset a tier higher, so the shape changes rather than merely growing. A ring
 * appears at eight, a star at twelve, and a crown of points at twenty — the ranks the level titles
 * already call Warrior, Champion and Legend.
 *
 * Everything is drawn. There is no image to ship, and it is sharp at any size from a 24dp chip to
 * a 96dp header.
 */
@Composable
fun RankInsignia(level: Int, modifier: Modifier = Modifier, size: Int = 44) {
    val tier = tierOf(level)
    // Coerced, because Kotlin's modulo keeps the sign of the dividend: at level 0 this was
    // ((-1) % 3) + 1 = 0 and the emblem drew no chevrons at all. A level below one should
    // never reach here, and if it does the answer is the lowest rank, not a blank plate.
    val chevrons = ((level.coerceAtLeast(1) - 1) % 3) + 1

    Canvas(modifier.size(size.dp)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        val metal = tier.metal
        val plate = Brush.verticalGradient(
            listOf(metal.copy(alpha = 0.34f), metal.copy(alpha = 0.10f)),
        )

        // The plate: a hexagon, because a circle is a profile picture and a shield is a coat of
        // arms, and this is neither.
        val body = hexPath(c, r * 0.82f)
        drawPath(body, plate)
        drawPath(body, metal.copy(alpha = 0.85f), style = Stroke(width = r * 0.09f))

        // A crown of points, from twenty.
        if (tier.crown) {
            for (i in 0 until 5) {
                val a = -TAU / 4f + (i - 2) * 0.32f
                drawLine(
                    metal,
                    Offset(c.x + cos(a) * r * 0.80f, c.y + sin(a) * r * 0.80f),
                    Offset(c.x + cos(a) * r * 1.02f, c.y + sin(a) * r * 1.02f),
                    strokeWidth = r * 0.10f,
                )
            }
        }

        // A ring, from eight.
        if (tier.ring) {
            drawCircle(metal.copy(alpha = 0.55f), radius = r * 0.62f, center = c, style = Stroke(r * 0.05f))
        }

        // Chevrons: the rank within its tier, stacked upward the way rank marks always are.
        val span = r * 0.46f
        for (i in 0 until chevrons) {
            val y = c.y + r * 0.30f - i * r * 0.26f
            val path = Path().apply {
                moveTo(c.x - span, y)
                lineTo(c.x, y - r * 0.24f)
                lineTo(c.x + span, y)
            }
            drawPath(path, metal, style = Stroke(width = r * 0.13f))
        }

        // A star, from twelve, sitting in the notch the chevrons make.
        if (tier.star) {
            starPath(Offset(c.x, c.y - r * 0.34f), r * 0.24f, metal).let { drawPath(it, metal) }
        }
    }
}

/** What the emblem is made of, and what it has earned, at a given level. */
private data class Tier(val metal: Color, val ring: Boolean, val star: Boolean, val crown: Boolean)

private fun tierOf(level: Int): Tier = when {
    level >= 20 -> Tier(Gold, ring = true, star = true, crown = true)
    level >= 12 -> Tier(Gold, ring = true, star = true, crown = false)
    level >= 8 -> Tier(Brass, ring = true, star = false, crown = false)
    level >= 5 -> Tier(Brass, ring = false, star = false, crown = false)
    level >= 3 -> Tier(Silver, ring = false, star = false, crown = false)
    else -> Tier(Ink.copy(alpha = 0.72f), ring = false, star = false, crown = false)
}

private fun hexPath(centre: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = i * TAU / 6f - TAU / 4f
        val p = Offset(centre.x + cos(a) * radius, centre.y + sin(a) * radius)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.starPath(centre: Offset, radius: Float, colour: Color): Path = Path().apply {
    for (i in 0 until 10) {
        val a = i * TAU / 10f - TAU / 4f
        val rad = if (i % 2 == 0) radius else radius * 0.44f
        val p = Offset(centre.x + cos(a) * rad, centre.y + sin(a) * rad)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** The accent a rank is drawn in, for text that sits beside the emblem. */
fun rankColour(level: Int): Color = when {
    level >= 12 -> Gold
    level >= 5 -> Brass
    level >= 3 -> Silver
    else -> Ember
}
