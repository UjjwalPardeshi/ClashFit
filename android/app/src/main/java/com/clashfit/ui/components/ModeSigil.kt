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
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
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
    ModeKind.VERSUS -> Brass
    ModeKind.GROUP -> Success
    ModeKind.FAMILY -> Fresh
    ModeKind.CLINIC -> Working
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

        // The ring, broken into ticks. How many ticks separates modes of the same kind.
        val ticks = 5 + ordinal
        for (i in 0 until ticks) {
            val a = i * TAU / ticks - TAU / 4f
            // Long enough to read as a segmented ring rather than a scatter of dots. Short
            // ticks at this size disappear into the card.
            val inner = r * 0.70f
            val outer = r * 0.98f
            drawLine(
                colour.copy(alpha = 0.62f),
                Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                strokeWidth = r * 0.14f,
            )
        }

        // The core mark. Its shape says what KIND of mode this is; how it is filled and how far
        // it is turned says WHICH one, so two modes of the same kind never share a mark. Tick
        // count alone was too subtle at 34dp — four orange hexagons in a row were four orange
        // hexagons.
        val fill = ordinal % 3
        val turn = (ordinal * 18f) % 60f
        rotate(turn, c) {
            when (mode.kind) {
                ModeKind.SOLO -> hexagon(c, r * 0.52f, colour, fill)
                ModeKind.VERSUS -> {
                    triangle(c + Offset(-r * 0.22f, 0f), r * 0.40f, colour, pointingRight = true, fill = fill)
                    triangle(c + Offset(r * 0.22f, 0f), r * 0.40f, colour, pointingRight = false, fill = fill)
                }
                ModeKind.GROUP -> for (i in 0 until 3 + fill) {
                    val a = i * TAU / (3 + fill) - TAU / 4f
                    drawCircle(colour, radius = r * 0.15f, center = c + Offset(cos(a) * r * 0.38f, sin(a) * r * 0.38f))
                }
                ModeKind.FAMILY -> rotate(45f, c) {
                    val half = r * 0.36f
                    val box = androidx.compose.ui.geometry.Size(half * 2, half * 2)
                    val at = Offset(c.x - half, c.y - half)
                    when (fill) {
                        0 -> drawRect(colour, at, box)
                        1 -> drawRect(colour, at, box, style = Stroke(width = r * 0.14f))
                        else -> {
                            drawRect(colour, at, box, style = Stroke(width = r * 0.12f))
                            drawRect(colour, Offset(c.x - half * 0.34f, c.y - half * 0.34f),
                                androidx.compose.ui.geometry.Size(half * 0.68f, half * 0.68f))
                        }
                    }
                }
                ModeKind.CLINIC -> {
                    val arm = r * 0.46f
                    val thick = r * 0.20f
                    drawRect(colour, Offset(c.x - arm, c.y - thick / 2), androidx.compose.ui.geometry.Size(arm * 2, thick))
                    drawRect(colour, Offset(c.x - thick / 2, c.y - arm), androidx.compose.ui.geometry.Size(thick, arm * 2))
                }
            }
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
