package com.clashfit.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Success
import kotlin.math.max
import kotlin.math.sin

/**
 * What a pursuer and a supply cache look like on the chase map.
 *
 * Drawn straight onto an [android.graphics.Canvas] rather than as a composable or a bitmap, for
 * one reason: it has to appear identically in two very different renderers. On top of the
 * OpenStreetMap view the chase draws into a Compose canvas laid over the map, and with tiles
 * switched off it draws inside the tile-free route trace. A single painter taking a plain canvas
 * is the only shape that both of those can call, so the game can never look like two games.
 *
 * It is a drawn figure, not an emoji and not an image asset. A figure that is drawn can be scaled
 * to any zoom without going soft, can change colour as it closes on you, and can breathe with the
 * heartbeat — none of which a PNG does. It costs nothing in the APK either.
 */
object ChaseGlyphs {

    /**
     * A pursuer: a horned, hunched thing with its claws out, facing the runner.
     *
     * @param closeness 0 when it is as far away as the chase ever spawns them, 1 when it is on
     *   top of you. Drives size, brightness and how hard the threat ring pulses, so the danger is
     *   readable from the corner of an eye at running pace.
     * @param pulse a 0..1 phase that advances with the heartbeat, so the whole pack breathes
     *   together and speeds up as the nearest one closes.
     */
    fun zombie(canvas: Canvas, cx: Float, cy: Float, radiusPx: Float, closeness: Float, pulse: Float) {
        val near = closeness.coerceIn(0f, 1f)
        // Never smaller than a thumb can see. A pursuer that is technically to scale and visually
        // absent is worse than one that is slightly too big.
        val r = max(radiusPx, 22f)
        val breathe = 1f + 0.10f * sin(pulse * TWO_PI) * (0.35f + 0.65f * near)
        val s = r * breathe

        val body = lerpColour(DISTANT, Gassed, near)
        val glow = lerpColour(DISTANT, EYE, near)

        // The threat ring. It is the thing the eye actually catches while running, so it grows and
        // brightens with proximity rather than staying a constant decoration.
        val ringAlpha = (40 + 150 * near).toInt().coerceIn(0, 255)
        canvas.drawCircle(
            cx, cy, s * (1.5f + 0.5f * near) * breathe,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, s * 0.14f)
                color = body.toArgb()
                alpha = ringAlpha
            },
        )
        canvas.drawCircle(
            cx, cy, s * 1.15f,
            paint().apply {
                style = Paint.Style.FILL
                color = body.toArgb()
                alpha = (30 + 60 * near).toInt().coerceIn(0, 255)
            },
        )

        val fill = paint().apply {
            style = Paint.Style.FILL
            color = body.toArgb()
        }

        // The silhouette is the whole design.
        //
        // At map size nobody resolves a face, so what has to read from the corner of an eye at
        // running pace is an outline: horns, hunched shoulders, and hands splayed into claws. An
        // upright figure with a round head and straight arms reads as the pictogram on a toilet
        // door, which is what the first version of this looked like. It is drawn as one closed
        // path so the shape stays solid at any size instead of coming apart into strokes.
        val figure = Path().apply {
            // Left horn, over the brow.
            moveTo(cx - s * 0.62f, cy - s * 1.22f)
            lineTo(cx - s * 0.30f, cy - s * 0.72f)
            // Skull, low and wide.
            cubicTo(
                cx - s * 0.16f, cy - s * 0.98f,
                cx + s * 0.16f, cy - s * 0.98f,
                cx + s * 0.30f, cy - s * 0.72f,
            )
            // Right horn.
            lineTo(cx + s * 0.62f, cy - s * 1.22f)
            lineTo(cx + s * 0.42f, cy - s * 0.52f)
            // Right shoulder, dropping into the reaching arm.
            lineTo(cx + s * 0.52f, cy - s * 0.30f)
            lineTo(cx + s * 1.02f, cy + s * 0.16f)
            // Three claws on the right hand.
            lineTo(cx + s * 0.84f, cy + s * 0.20f)
            lineTo(cx + s * 1.00f, cy + s * 0.40f)
            lineTo(cx + s * 0.76f, cy + s * 0.34f)
            lineTo(cx + s * 0.84f, cy + s * 0.56f)
            lineTo(cx + s * 0.60f, cy + s * 0.30f)
            // Down the right flank into the trailing leg.
            lineTo(cx + s * 0.30f, cy + s * 0.34f)
            lineTo(cx + s * 0.44f, cy + s * 1.10f)
            lineTo(cx + s * 0.16f, cy + s * 1.12f)
            lineTo(cx + s * 0.06f, cy + s * 0.52f)
            // Leading leg, mid-stride, so it never looks like it is standing still.
            lineTo(cx - s * 0.22f, cy + s * 1.16f)
            lineTo(cx - s * 0.50f, cy + s * 1.06f)
            lineTo(cx - s * 0.22f, cy + s * 0.30f)
            // Left arm and claws, mirrored.
            lineTo(cx - s * 0.58f, cy + s * 0.28f)
            lineTo(cx - s * 0.82f, cy + s * 0.54f)
            lineTo(cx - s * 0.74f, cy + s * 0.32f)
            lineTo(cx - s * 0.98f, cy + s * 0.38f)
            lineTo(cx - s * 0.82f, cy + s * 0.18f)
            lineTo(cx - s * 1.00f, cy + s * 0.14f)
            lineTo(cx - s * 0.52f, cy - s * 0.32f)
            lineTo(cx - s * 0.42f, cy - s * 0.52f)
            close()
        }

        // A dark outline first, so the figure holds its shape over a pale building or a road label.
        canvas.drawPath(
            figure,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, s * 0.22f)
                strokeJoin = Paint.Join.ROUND
                color = OUTLINE.toArgb()
            },
        )
        canvas.drawPath(figure, fill)

        // Eyes. The only bright thing on it, with a bloom behind them, so what the eye locks onto
        // is a thing looking back rather than a shape. They brighten as it closes.
        val eyeR = max(1.4f, s * 0.11f)
        val eyeY = cy - s * 0.62f
        val bloom = paint().apply {
            style = Paint.Style.FILL
            color = glow.toArgb()
            alpha = (70 + 120 * near).toInt().coerceIn(0, 255)
        }
        val core = paint().apply {
            style = Paint.Style.FILL
            color = glow.toArgb()
        }
        listOf(-1f, 1f).forEach { side ->
            val ex = cx + side * s * 0.17f
            canvas.drawCircle(ex, eyeY, eyeR * 2.3f, bloom)
            canvas.drawCircle(ex, eyeY, eyeR, core)
        }
    }

    /**
     * A supply cache: a hollow diamond with a cross inside.
     *
     * Deliberately geometric where the pursuer is organic, so the two are never confused at a
     * glance while running, even in peripheral vision and even for a colour-blind player.
     */
    fun cache(canvas: Canvas, cx: Float, cy: Float, radiusPx: Float, pulse: Float) {
        val r = max(radiusPx, 16f) * (1f + 0.06f * sin(pulse * TWO_PI))
        val stroke = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, r * 0.20f)
            strokeJoin = Paint.Join.ROUND
            color = Success.toArgb()
        }
        val diamond = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        canvas.drawPath(diamond, stroke)
        canvas.drawLine(cx - r * 0.34f, cy, cx + r * 0.34f, cy, stroke)
        canvas.drawLine(cx, cy - r * 0.34f, cx, cy + r * 0.34f, stroke)
    }

    /** The runner's own position: a solid dot inside a soft halo, so it is never lost in a route. */
    fun runner(canvas: Canvas, cx: Float, cy: Float, radiusPx: Float, colour: Color) {
        val r = max(radiusPx, 9f)
        canvas.drawCircle(
            cx, cy, r * 2.4f,
            paint().apply {
                style = Paint.Style.FILL
                color = colour.toArgb()
                alpha = 55
            },
        )
        canvas.drawCircle(cx, cy, r, paint().apply { style = Paint.Style.FILL; color = colour.toArgb() })
        canvas.drawCircle(
            cx, cy, r,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, r * 0.32f)
                color = android.graphics.Color.WHITE
            },
        )
    }

    private const val TWO_PI = 6.2831855f

    /** Far away: drained of colour, so distance is felt before the number is read. */
    private val DISTANT = Color(0xFF6B7280)
    private val EYE = Color(0xFFFFF1C4)

    /** Nearly black, for the outline that keeps the silhouette readable over any basemap. */
    private val OUTLINE = Color(0xFF120A08)

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun lerpColour(a: Color, b: Color, t: Float): Color {
        val k = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * k,
            green = a.green + (b.green - a.green) * k,
            blue = a.blue + (b.blue - a.blue) * k,
            alpha = 1f,
        )
    }
}
