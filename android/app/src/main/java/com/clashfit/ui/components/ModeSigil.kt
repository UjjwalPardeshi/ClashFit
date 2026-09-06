package com.clashfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.ui.theme.Rival
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** The colour a kind of mode is drawn in, so a glance sorts them before any word is read. */
fun ModeKind.tint(): Color = when (this) {
    ModeKind.SOLO -> Ember
    ModeKind.VERSUS -> Rival
    ModeKind.GROUP -> Success
    ModeKind.FAMILY -> Fresh
    ModeKind.CLINIC -> Working
    // Never actually drawn: the workout log is not a card in a list of modes. It is named here
    // because the compiler is right to insist that every kind has an answer.
    ModeKind.WORKOUT -> Ink
}

/**
 * A drawn mark for one game mode.
 *
 * Sixteen modes were sixteen paragraphs. A card led by three lines of prose asks to be read before
 * it can be chosen, and nobody reads sixteen of anything while deciding what to do for ten minutes.
 * A sigil is chosen at a glance and remembered between sessions, which is what an icon is for.
 *
 * Nothing is an asset. The frame says what kind of mode it is — one player, two, a group, a
 * movement family, a clinical protocol — and the tick count around the ring makes each mode within
 * its kind distinct, so no two marks in the same colour are the same shape.
 */
@Composable
fun ModeSigil(mode: GameMode, modifier: Modifier = Modifier, size: Int = 34, enabled: Boolean = true) {
    val colour = if (enabled) mode.kind.tint() else InkFaint
    val ordinal = GameMode.entries.filter { it.kind == mode.kind }.indexOf(mode).coerceAtLeast(0)

    Canvas(modifier.size(size.dp)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)

        // The ring, broken into ticks. How many ticks separates modes of the same kind. It is
        // deliberately quiet — it used to be the loudest thing in the mark, which left six solo
        // modes reading as six identical orange starbursts on a card.
        val ticks = 5 + ordinal
        for (i in 0 until ticks) {
            val a = i * TAU / ticks - TAU / 4f
            val inner = r * 0.80f
            val outer = r * 0.99f
            drawLine(
                colour.copy(alpha = 0.40f),
                Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                strokeWidth = r * 0.10f,
            )
        }

        // The core mark. Colour says what KIND of mode this is and gives the page its blocks of
        // rhythm; the silhouette says WHICH one. Six genuinely different shapes, because a
        // hexagon turned 54 degrees is a hexagon — the old variants were a rotation and a fill
        // on one shape, and at the 28dp a card gives them they were indistinguishable.
        val core = r * 0.62f
        when (ordinal % 6) {
            0 -> hexagon(c, core, colour, fill = 0)
            1 -> triangleUp(c, core, colour)
            2 -> diamond(c, core, colour, hollow = true)
            3 -> {
                drawCircle(colour, radius = core * 0.92f, center = c, style = Stroke(width = r * 0.16f))
                drawCircle(colour, radius = core * 0.30f, center = c)
            }
            4 -> {
                val half = core * 0.78f
                drawRect(
                    colour, Offset(c.x - half, c.y - half),
                    androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    style = Stroke(width = r * 0.16f),
                )
            }
            else -> star(c, core, colour)
        }

        // A timed mode gets a sweep on its ring, which is the one property worth seeing before you
        // commit to a mode you cannot pause.
        if (mode.timed) {
            drawArc(
                colour,
                startAngle = -90f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(r * 0.06f, r * 0.06f),
                size = androidx.compose.ui.geometry.Size(r * 1.88f, r * 1.88f),
                style = Stroke(width = r * 0.13f),
            )
        }
    }
}

/** @param fill 0 solid, 1 hollow, 2 hollow with a core */
private fun DrawScope.hexagon(centre: Offset, radius: Float, colour: Color, fill: Int = 0) {
    val path = Path()
    for (i in 0 until 6) {
        val a = i * TAU / 6f - TAU / 4f
        val p = Offset(centre.x + cos(a) * radius, centre.y + sin(a) * radius)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    when (fill) {
        0 -> drawPath(path, colour)
        1 -> drawPath(path, colour, style = Stroke(width = radius * 0.30f))
        else -> {
            drawPath(path, colour, style = Stroke(width = radius * 0.24f))
            drawCircle(colour, radius = radius * 0.34f, center = centre)
        }
    }
}

private fun DrawScope.triangle(
    centre: Offset,
    radius: Float,
    colour: Color,
    pointingRight: Boolean,
    fill: Int = 0,
) {
    val d = if (pointingRight) 1f else -1f
    val path = Path().apply {
        moveTo(centre.x + d * radius, centre.y)
        lineTo(centre.x - d * radius * 0.55f, centre.y - radius * 0.85f)
        lineTo(centre.x - d * radius * 0.55f, centre.y + radius * 0.85f)
        close()
    }
    if (fill == 1) drawPath(path, colour, style = Stroke(width = radius * 0.30f))
    else drawPath(path, colour)
}

private fun DrawScope.triangleUp(centre: Offset, radius: Float, colour: Color) {
    val path = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + radius * 0.92f, centre.y + radius * 0.72f)
        lineTo(centre.x - radius * 0.92f, centre.y + radius * 0.72f)
        close()
    }
    drawPath(path, colour)
}

private fun DrawScope.diamond(centre: Offset, radius: Float, colour: Color, hollow: Boolean) {
    val path = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + radius * 0.82f, centre.y)
        lineTo(centre.x, centre.y + radius)
        lineTo(centre.x - radius * 0.82f, centre.y)
        close()
    }
    if (hollow) drawPath(path, colour, style = Stroke(width = radius * 0.34f)) else drawPath(path, colour)
}

private fun DrawScope.star(centre: Offset, radius: Float, colour: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = i * TAU / 10f - TAU / 4f
        val rad = if (i % 2 == 0) radius else radius * 0.46f
        val p = Offset(centre.x + cos(a) * rad, centre.y + sin(a) * rad)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, colour)
}
