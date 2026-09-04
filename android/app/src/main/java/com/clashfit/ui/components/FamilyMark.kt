package com.clashfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.Family
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working
import androidx.compose.ui.graphics.StrokeCap

/** The colour a movement family is drawn in, so a list of fifty-one sorts itself by eye. */
fun Family.tint(): Color = when (this) {
    Family.REP_CYCLE -> Ember
    Family.ISOMETRIC_HOLD -> Brass
    Family.CADENCE -> Success
    Family.BALLISTIC -> Working
    Family.POSE_MATCH -> Fresh
}

/**
 * A movement family, as a mark.
 *
 * Fifty-one exercises in one list is a wall of names. The five families are the only structure
 * that list has, and until now they were carried by a filter chip above it — so a row told you
 * nothing about what kind of movement it was until you opened it.
 *
 * Each mark is the shape of the movement it names: a cycle for reps that go down and up, a bar for
 * a hold, rising steps for cadence, an arrow for a jump, an outline to match for a pose.
 */
@Composable
fun FamilyMark(family: Family, modifier: Modifier = Modifier, size: Int = 34) {
    val colour = family.tint()
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(percent = 30)).background(colour.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size((size * 0.56f).dp)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.16f
            when (family) {
                // Reps: down and back up.
                // A loop with an arrowhead. Two chevrons were tried first and read as an X — a V
                // above an inverted V is an hourglass, and an hourglass says "wrong", not "again".
                Family.REP_CYCLE -> {
                    val inset = stroke * 0.9f
                    // The gap sits at the top, not at the right. With the opening on the right
                    // this was the letter C, and a list of fifty-one movements showed a column of
                    // nine identical Cs where a mark was supposed to be. A ring broken at the top
                    // with a head pointing into the break is the shape everything else in the
                    // world uses for "again".
                    val startAngle = -55f
                    val sweep = 290f
                    drawArc(
                        colour,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(w - inset * 2, h - inset * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    // The head, at the end of the travel, pointing on into the opening.
                    val a = Math.toRadians((startAngle + sweep).toDouble())
                    val rx = (w - inset * 2) / 2f
                    val cosA = kotlin.math.cos(a).toFloat()
                    val sinA = kotlin.math.sin(a).toFloat()
                    val px = w / 2f + cosA * rx
                    val py = h / 2f + sinA * rx
                    // Clockwise tangent, and the radius as its normal.
                    val tx = -sinA
                    val ty = cosA
                    val len = stroke * 1.9f
                    val half = stroke * 1.15f
                    drawPath(
                        Path().apply {
                            moveTo(px + tx * len, py + ty * len)
                            lineTo(px - tx * len * 0.25f + cosA * half, py - ty * len * 0.25f + sinA * half)
                            lineTo(px - tx * len * 0.25f - cosA * half, py - ty * len * 0.25f - sinA * half)
                            close()
                        },
                        colour,
                    )
                }
                // A hold: one unmoving bar.
                Family.ISOMETRIC_HOLD -> drawRect(
                    colour,
                    topLeft = Offset(w * 0.08f, h * 0.42f),
                    size = Size(w * 0.84f, h * 0.16f),
                )
                // Cadence: a rhythm, rising.
                Family.CADENCE -> for (i in 0 until 4) {
                    val bh = h * (0.28f + i * 0.18f)
                    drawRect(
                        colour,
                        topLeft = Offset(w * (0.10f + i * 0.23f), h - bh),
                        size = Size(w * 0.14f, bh),
                    )
                }
                // Ballistic: up and away.
                Family.BALLISTIC -> {
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.50f, h * 0.08f)
                            lineTo(w * 0.86f, h * 0.52f)
                            lineTo(w * 0.62f, h * 0.52f)
                            lineTo(w * 0.62f, h * 0.92f)
                            lineTo(w * 0.38f, h * 0.92f)
                            lineTo(w * 0.38f, h * 0.52f)
                            lineTo(w * 0.14f, h * 0.52f)
                            close()
                        },
                        colour,
                    )
                }
                // A pose: an outline to match.
                Family.POSE_MATCH -> {
                    drawCircle(colour, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.18f))
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.50f, h * 0.34f)
                            lineTo(w * 0.50f, h * 0.66f)
                            moveTo(w * 0.16f, h * 0.44f)
                            lineTo(w * 0.84f, h * 0.44f)
                            moveTo(w * 0.50f, h * 0.66f)
                            lineTo(w * 0.24f, h * 0.94f)
                            moveTo(w * 0.50f, h * 0.66f)
                            lineTo(w * 0.76f, h * 0.94f)
                        },
                        colour, style = Stroke(width = stroke * 0.9f),
                    )
                }
            }
        }
    }
}
