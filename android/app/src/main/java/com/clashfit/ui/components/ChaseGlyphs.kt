package com.clashfit.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.clashfit.ui.theme.Gassed
import kotlin.math.max
import kotlin.math.sin

/**
 * What a pursuer looks like on the chase map.
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
     * A pursuer: a zombie's head, facing the runner.
     *
     * A head rather than a whole figure, because at map-marker size a full body is six or seven
     * pixels of limb and reads as a smudge — while a head is two eyes and a mouth, which is the
     * one arrangement human sight resolves instantly and from the corner of an eye. It is drawn
     * rather than dropped in as an image so it can be any size on any screen without going soft,
     * costs nothing in the APK, and can react: the skin runs from sickly green to hot as it
     * closes, the eyes widen, and the whole pack breathes on one phase so a tightening chase is
     * visible before the distance readout is read.
     *
     * @param closeness 0 when it is as far away as the chase ever spawns them, 1 when it is on
     *   top of you.
     * @param pulse a 0..1 phase that advances with the heartbeat.
     */
    fun zombie(canvas: Canvas, cx: Float, cy: Float, radiusPx: Float, closeness: Float, pulse: Float) {
        val near = closeness.coerceIn(0f, 1f)
        // Never smaller than a thumb can see. A pursuer that is technically to scale and visually
        // absent is worse than one that is slightly too big.
        val r = max(radiusPx, 22f)
        val breathe = 1f + 0.10f * sin(pulse * TWO_PI) * (0.35f + 0.65f * near)
        val s = r * breathe

        val body = lerpColour(DISTANT, Gassed, near)

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

        // The skull. Wider at the temples than the jaw, with a dented crown, so it reads as
        // something that used to be a person rather than as a ball.
        val skull = Path().apply {
            moveTo(cx - s * 0.86f, cy - s * 0.12f)
            cubicTo(
                cx - s * 0.92f, cy - s * 0.86f,
                cx - s * 0.34f, cy - s * 1.10f,
                cx - s * 0.06f, cy - s * 0.94f,
            )
            // The dent, which is what stops the crown reading as a smooth dome.
            cubicTo(
                cx + s * 0.06f, cy - s * 1.06f,
                cx + s * 0.52f, cy - s * 1.06f,
                cx + s * 0.84f, cy - s * 0.62f,
            )
            cubicTo(
                cx + s * 1.02f, cy - s * 0.18f,
                cx + s * 0.78f, cy + s * 0.62f,
                cx + s * 0.30f, cy + s * 0.90f,
            )
            // The jaw, hanging slightly to one side.
            cubicTo(
                cx - s * 0.06f, cy + s * 1.06f,
                cx - s * 0.66f, cy + s * 0.72f,
                cx - s * 0.86f, cy - s * 0.12f,
            )
            close()
        }

        // Three hairs. Not decoration: they break the outline at the top, which is what keeps the
        // marker from reading as a plain circle in peripheral vision.
        val hair = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.5f, s * 0.10f)
            strokeCap = Paint.Cap.ROUND
            color = OUTLINE.toArgb()
        }
        listOf(-0.34f to -1.02f, 0.02f to -1.10f, 0.34f to -0.98f).forEachIndexed { idx, (hx, hy) ->
            val lean = (idx - 1) * 0.16f
            canvas.drawLine(
                cx + s * hx, cy + s * hy,
                cx + s * (hx + lean), cy + s * (hy - 0.34f),
                hair,
            )
        }

        canvas.drawPath(
            skull,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, s * 0.20f)
                strokeJoin = Paint.Join.ROUND
                color = OUTLINE.toArgb()
            },
        )
        canvas.drawPath(skull, fill)

        // The eyes are the whole read. Big, white, and deliberately mismatched — one larger and
        // sitting lower — because two identical circles read as a machine and two different ones
        // read as something wrong with it. They widen as it closes.
        val white = paint().apply { style = Paint.Style.FILL; color = EYE.toArgb() }
        val pupil = paint().apply { style = Paint.Style.FILL; color = OUTLINE.toArgb() }
        val open = 1f + 0.22f * near
        listOf(
            Triple(-0.32f, -0.30f, 0.30f),
            Triple(0.30f, -0.24f, 0.34f),
        ).forEach { (ex, ey, er) ->
            val x = cx + s * ex
            val y = cy + s * ey
            val r2 = s * er * open
            canvas.drawCircle(x, y, r2, white)
            canvas.drawCircle(
                x, y, r2,
                paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, s * 0.07f)
                    color = OUTLINE.toArgb()
                },
            )
            // The pupil sits toward the runner, so every one of them is looking at you.
            canvas.drawCircle(x + r2 * 0.16f, y + r2 * 0.18f, r2 * 0.42f, pupil)
        }

        // A wide gap-toothed grin. Drawn as a dark mouth with a few pale teeth left in it, rather
        // than a row of teeth, so at small sizes it collapses into a dark bar and still reads.
        val mouth = Path().apply {
            moveTo(cx - s * 0.52f, cy + s * 0.36f)
            cubicTo(
                cx - s * 0.20f, cy + s * 0.78f,
                cx + s * 0.24f, cy + s * 0.76f,
                cx + s * 0.52f, cy + s * 0.30f,
            )
            cubicTo(
                cx + s * 0.24f, cy + s * 0.50f,
                cx - s * 0.20f, cy + s * 0.52f,
                cx - s * 0.52f, cy + s * 0.36f,
            )
            close()
        }
        canvas.drawPath(mouth, paint().apply { style = Paint.Style.FILL; color = OUTLINE.toArgb() })

        val tooth = paint().apply { style = Paint.Style.FILL; color = EYE.toArgb() }
        // Two teeth and a gap, which is more of a zombie than a full set would be.
        listOf(-0.30f, 0.06f).forEach { tx ->
            canvas.drawRect(
                cx + s * tx, cy + s * 0.36f,
                cx + s * (tx + 0.18f), cy + s * 0.58f,
                tooth,
            )
        }
    }

    /** The runner: you, a filled dot with a white ring so it never disappears into the map. */
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

    /** Far away: sickly and drained, so distance is felt before the number is read. */
    private val DISTANT = Color(0xFF7E9C74)
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
