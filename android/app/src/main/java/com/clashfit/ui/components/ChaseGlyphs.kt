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
     * A pursuer: a hunched figure with its arms out, facing the runner.
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
        val stroke = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.6f, s * 0.16f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = body.toArgb()
        }

        // The figure, built around the centre. Proportions are deliberately not human: the head is
        // large and the stance is low and wide, because at map size a correct silhouette reads as
        // a smudge and a caricature reads as a monster.
        val headR = s * 0.40f
        val headY = cy - s * 0.52f
        val hipY = cy + s * 0.42f

        // Shoulders and torso, leaning towards the runner.
        val torso = Path().apply {
            moveTo(cx - s * 0.34f, headY + headR * 0.85f)
            lineTo(cx + s * 0.34f, headY + headR * 0.85f)
            lineTo(cx + s * 0.22f, hipY)
            lineTo(cx - s * 0.22f, hipY)
            close()
        }
        canvas.drawPath(torso, fill)

        // Arms reaching forward, the pose everybody reads as "coming for you".
        canvas.drawLine(cx - s * 0.30f, headY + headR, cx - s * 0.86f, cy + s * 0.10f, stroke)
        canvas.drawLine(cx + s * 0.30f, headY + headR, cx + s * 0.86f, cy + s * 0.10f, stroke)

        // Legs, mid-stride so it never looks like it is standing still.
        canvas.drawLine(cx - s * 0.14f, hipY, cx - s * 0.42f, cy + s * 1.12f, stroke)
        canvas.drawLine(cx + s * 0.14f, hipY, cx + s * 0.36f, cy + s * 1.05f, stroke)

        canvas.drawCircle(cx, headY, headR, fill)

        // Eyes. The only bright thing on the figure, so they are what the eye locks onto, and they
        // brighten as it closes. Two dots at map scale is enough to make a shape feel like it is
        // looking back.
        val eyePaint = paint().apply {
            style = Paint.Style.FILL
            color = glow.toArgb()
        }
        val eyeR = max(1.2f, headR * 0.26f)
        canvas.drawCircle(cx - headR * 0.40f, headY - headR * 0.08f, eyeR, eyePaint)
        canvas.drawCircle(cx + headR * 0.40f, headY - headR * 0.08f, eyeR, eyePaint)
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
    private val EYE = Color(0xFFFFE8B0)

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
