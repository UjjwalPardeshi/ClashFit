package com.clashfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.PanelLift

/**
 * A breathing pattern, drawn as the ring it actually is.
 *
 * "4 · 4 · 4 · 4" and "4 · 7 · 8" are the same three characters-and-dots to anybody not already
 * fluent in breathwork notation, which is nearly everybody. As a ring they are obviously different
 * shapes: box breathing is four equal quarters, wind-down is a third in and two thirds out, and
 * four-seven-eight is a small arc followed by two long ones.
 *
 * Inhale is warm and exhale is cool, so the direction of the pattern is visible before any number
 * is read. Holds are drawn faint — they are the pauses, and they should look like pauses.
 *
 * @param selected fills the centre, so the chosen pattern is not distinguished by colour alone.
 */
@Composable
fun BreathMark(
    inSec: Float,
    holdInSec: Float,
    outSec: Float,
    holdOutSec: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 30,
) {
    val phases = listOf(
        inSec to Ember,
        holdInSec to InkFaint,
        outSec to Fresh,
        holdOutSec to InkFaint,
    )
    val total = phases.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.01f)

    Canvas(modifier.size(size.dp)) {
        val stroke = this.size.minDimension * 0.16f
        val inset = stroke / 2f
        val box = Size(this.size.width - stroke, this.size.height - stroke)

        // The unlit ring underneath, so a short phase still reads against something.
        drawArc(
            PanelLift, 0f, 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = box,
            style = Stroke(width = stroke),
        )

        // Starting at the top and going clockwise, the way a clock face is already read.
        var at = -90f
        phases.forEach { (seconds, colour) ->
            if (seconds <= 0f) return@forEach
            val sweep = seconds / total * 360f
            drawArc(
                colour.copy(alpha = if (colour == InkFaint) 0.55f else 1f),
                startAngle = at + 2f,
                sweepAngle = (sweep - 4f).coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            at += sweep
        }

        if (selected) {
            drawCircle(Ember, radius = this.size.minDimension * 0.17f, center = center)
        }
    }
}

/** The same ring, filled to [progress] — the live pattern during a session. */
@Composable
fun BreathDial(progress: Float, tint: Color, modifier: Modifier = Modifier, size: Int = 220) {
    Canvas(modifier.size(size.dp)) {
        val stroke = this.size.minDimension * 0.055f
        val inset = stroke / 2f
        val box = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            PanelLift, 0f, 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = box, style = Stroke(width = stroke),
        )
        drawArc(
            tint, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(inset, inset), size = box,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
