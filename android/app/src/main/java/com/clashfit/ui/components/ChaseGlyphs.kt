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

        // ── the skull ────────────────────────────────────────────────────────────────────
        //
        // Deliberately lopsided. A symmetrical head reads as a mask; this one leans, the crown is
        // dented on one side, and the jaw hangs off-centre, which is what makes it read as
        // something that used to be a person rather than as a green ball with a face on it.
        val skull = Path().apply {
            moveTo(cx - s * 0.88f, cy - s * 0.06f)
            cubicTo(
                cx - s * 0.94f, cy - s * 0.82f,
                cx - s * 0.40f, cy - s * 1.12f,
                cx - s * 0.10f, cy - s * 0.98f,
            )
            // The dent in the crown.
            cubicTo(
                cx + s * 0.04f, cy - s * 1.14f,
                cx + s * 0.30f, cy - s * 1.14f,
                cx + s * 0.46f, cy - s * 0.92f,
            )
            cubicTo(
                cx + s * 0.88f, cy - s * 0.72f,
                cx + s * 1.02f, cy - s * 0.10f,
                cx + s * 0.86f, cy + s * 0.34f,
            )
            // The jaw, hanging low and to the left.
            cubicTo(
                cx + s * 0.66f, cy + s * 0.92f,
                cx - s * 0.16f, cy + s * 1.12f,
                cx - s * 0.58f, cy + s * 0.72f,
            )
            cubicTo(
                cx - s * 0.84f, cy + s * 0.46f,
                cx - s * 0.84f, cy + s * 0.18f,
                cx - s * 0.88f, cy - s * 0.06f,
            )
            close()
        }

        // Hair: three strands, uneven, leaning the same way the skull does.
        val hair = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, s * 0.15f)
            strokeCap = Paint.Cap.ROUND
            color = OUTLINE.toArgb()
        }
        listOf(
            Triple(-0.28f, -1.00f, -0.12f),
            Triple(-0.02f, -1.10f, 0.02f),
            Triple(0.24f, -1.02f, 0.14f),
        ).forEach { (hx, hy, lean) ->
            canvas.drawLine(cx + s * hx, cy + s * hy, cx + s * (hx + lean), cy + s * (hy - 0.20f), hair)
        }

        canvas.drawPath(
            skull,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, s * 0.19f)
                strokeJoin = Paint.Join.ROUND
                color = OUTLINE.toArgb()
            },
        )
        canvas.drawPath(skull, fill)

        // ── shading ──────────────────────────────────────────────────────────────────────
        //
        // Flat fill is what made the first version read as a sticker. One darker wash along the
        // lower right and one light bloom on the upper left is the whole difference between a
        // shape and something with a surface; the skull is clipped so neither escapes the head.
        canvas.save()
        canvas.clipPath(skull)
        canvas.drawCircle(
            cx + s * 0.52f, cy + s * 0.46f, s * 1.05f,
            paint().apply {
                style = Paint.Style.FILL
                color = OUTLINE.toArgb()
                alpha = 46
            },
        )
        canvas.drawCircle(
            cx - s * 0.42f, cy - s * 0.60f, s * 0.62f,
            paint().apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
                alpha = 34
            },
        )
        canvas.restore()

        // ── the mouth, before the eyes, so a tooth can never sit over an eye ──────────────
        //
        // A hanging jaw rather than a grin: the dark cavity is the shape that survives at map
        // size, and the teeth are what make it a zombie rather than a hole.
        val mouth = Path().apply {
            moveTo(cx - s * 0.50f, cy + s * 0.30f)
            cubicTo(
                cx - s * 0.30f, cy + s * 0.86f,
                cx + s * 0.34f, cy + s * 0.82f,
                cx + s * 0.56f, cy + s * 0.24f,
            )
            cubicTo(
                cx + s * 0.30f, cy + s * 0.42f,
                cx - s * 0.22f, cy + s * 0.46f,
                cx - s * 0.50f, cy + s * 0.30f,
            )
            close()
        }
        canvas.drawPath(mouth, paint().apply { style = Paint.Style.FILL; color = OUTLINE.toArgb() })

        // Four teeth at four different angles, and a gap where a fifth should be. A straight row
        // of even teeth is a comb; this is a mouth something has happened to.
        val tooth = paint().apply { style = Paint.Style.FILL; color = EYE.toArgb() }
        canvas.save()
        canvas.clipPath(mouth)
        listOf(
            Triple(-0.34f, 0.26f, -14f),
            Triple(-0.08f, 0.30f, 6f),
            Triple(0.30f, 0.24f, -8f),
            Triple(0.04f, 0.62f, 12f),
        ).forEach { (tx, ty, deg) ->
            canvas.save()
            canvas.rotate(deg, cx + s * tx, cy + s * ty)
            canvas.drawRect(
                cx + s * (tx - 0.09f), cy + s * (ty - 0.16f),
                cx + s * (tx + 0.09f), cy + s * (ty + 0.16f),
                tooth,
            )
            canvas.restore()
        }
        canvas.restore()

        // ── the eyes ─────────────────────────────────────────────────────────────────────
        //
        // One big, one small, set at different heights. This is the single thing that carries the
        // whole read at twenty pixels, and two matching circles are the one arrangement that does
        // not: matched eyes look designed, mismatched ones look wrong with you.
        val white = paint().apply { style = Paint.Style.FILL; color = EYE.toArgb() }
        val pupil = paint().apply { style = Paint.Style.FILL; color = OUTLINE.toArgb() }
        val spark = paint().apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE }
        val open = 1f + 0.20f * near
        listOf(
            Triple(-0.34f, -0.36f, 0.36f),
            Triple(0.28f, -0.26f, 0.27f),
        ).forEach { (ex, ey, er) ->
            val x = cx + s * ex
            val y = cy + s * ey
            val rr = s * er * open
            canvas.drawCircle(x, y, rr, white)
            canvas.drawCircle(
                x, y, rr,
                paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, s * 0.06f)
                    color = OUTLINE.toArgb()
                },
            )
            // The pupil sits toward the runner, so every one of them is looking at you, and the
            // catchlight is what stops the eye reading as a printed dot.
            val px = x + rr * 0.20f
            val py = y + rr * 0.22f
            canvas.drawCircle(px, py, rr * 0.44f, pupil)
            canvas.drawCircle(px - rr * 0.16f, py - rr * 0.18f, rr * 0.13f, spark)
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
    private val DISTANT = Color(0xFF93C97C)
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
