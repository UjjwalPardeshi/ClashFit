package com.clashfit.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.toArgb
import com.clashfit.R
import com.clashfit.data.RunPointEntity
import com.clashfit.run.LocalPoint
import com.clashfit.run.distanceParts
import com.clashfit.run.formatDistance
import com.clashfit.run.formatPaceSecPerKm
import com.clashfit.run.medianSpeedMps
import com.clashfit.run.metresEastNorth
import com.clashfit.run.paceZone
import com.clashfit.run.simplifyRoute
import com.clashfit.ui.components.paceColour
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Success
import kotlin.math.max
import kotlin.math.min

/**
 * The shareable picture of an activity.
 *
 * Rendered straight onto a [Canvas] rather than through Compose, because what is wanted is an
 * off-screen bitmap at a fixed 1080×1920 — a size no phone's screen actually is — and capturing
 * Compose off-screen at an arbitrary size is a fight that buys nothing here.
 *
 * **The route is drawn on ClashFit's own grid, never on map tiles.** Two reasons, and the second
 * is the load-bearing one: the picture looks like this product rather than like every other run
 * app, and redistributing OpenStreetMap imagery carries an attribution requirement that a picture
 * pasted into an Instagram story cannot reliably honour. Our own line on our own grid owes nobody
 * a credit line.
 *
 * What the picture *does* carry is where the player was. That is said plainly before the first
 * share rather than discovered afterwards; see `ShareNotice` in the run screens.
 */
object ShareCard {

    /** Instagram and WhatsApp both want a 9:16 story. Everything is laid out against this. */
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** Which of the three looks to render. The player picks; nothing here is a default forever. */
    enum class Style(val label: String, val blurb: String) {
        HERO("Hero", "Route large, distance front and centre"),
        SPLIT("Split", "Route on top, the numbers underneath"),
        EFFORT("Effort", "Leads with what only this app measures"),
    }

    /**
     * Everything a card can show. Filled by the caller so this file never touches a database and
     * can be rendered from a test with made-up numbers.
     */
    data class Stats(
        val title: String,
        val dateLabel: String,
        val distanceM: Float,
        val movingMs: Long,
        val paceSecPerKm: Float,
        val climbM: Float,
        val cadenceSpm: Int,
        val points: List<RunPointEntity>,
        val levelLabel: String?,
        val records: List<String> = emptyList(),
        val fastestKmSec: Float? = null,
        /** 0..1, how much of the route was run in the two quickest pace bands. */
        val effort: Float = 0f,
        val effortLabel: String = "",
    )

    fun render(context: Context, stats: Stats, style: Style): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)
        background(canvas)
        when (style) {
            Style.HERO -> hero(canvas, stats, fonts)
            Style.SPLIT -> split(canvas, stats, fonts)
            Style.EFFORT -> effort(canvas, stats, fonts)
        }
        return bmp
    }

    // ── shared furniture ────────────────────────────────────────────────────

    private class Fonts(context: Context) {
        val display: Typeface = font(context, R.font.bigshoulders_variable) ?: Typeface.DEFAULT_BOLD
        val bold: Typeface = font(context, R.font.barlow_bold) ?: Typeface.DEFAULT_BOLD
        val medium: Typeface = font(context, R.font.barlow_medium) ?: Typeface.DEFAULT
        val mono: Typeface = font(context, R.font.plexmono_medium) ?: Typeface.MONOSPACE

        private fun font(context: Context, id: Int): Typeface? =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
    }

    private fun paint(colour: Int, size: Float, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            textSize = size
            typeface = face
            textAlign = align
        }

    /** A warm-to-cold vertical wash, so the card is not a flat rectangle of one colour. */
    private fun background(canvas: Canvas) {
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(Panel.toArgb(), Ground.toArgb(), Ground.toArgb()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), p)
    }

    private fun wordmark(canvas: Canvas, fonts: Fonts, x: Float, y: Float) {
        canvas.drawText("CLASHFIT", x, y, paint(Ink.toArgb(), 46f, fonts.display).apply { letterSpacing = 0.14f })
        val p = paint(Ember.toArgb(), 46f, fonts.display)
        canvas.drawRect(x, y + 14f, x + 96f, y + 20f, p)
    }

    private fun footer(canvas: Canvas, fonts: Fonts) {
        canvas.drawText(
            "Every rep graded by the camera.",
            WIDTH / 2f, HEIGHT - 72f,
            paint(InkFaint.toArgb(), 34f, fonts.medium, Paint.Align.CENTER),
        )
    }

    /** One stat: a big value over a small caption, left-aligned at [x]. */
    private fun stat(canvas: Canvas, fonts: Fonts, x: Float, y: Float, value: String, caption: String, colour: Int = Ink.toArgb()) {
        canvas.drawText(value, x, y, paint(colour, 68f, fonts.bold))
        canvas.drawText(caption.uppercase(), x, y + 40f, paint(InkFaint.toArgb(), 28f, fonts.medium).apply { letterSpacing = 0.16f })
    }

    /**
     * The big number on the card, and the unit under it.
     *
     * A card that says "0.00" over "KILOMETRES" is the worst thing this app could put on somebody's
     * story, so a short activity says its metres instead. Shared with the rest of the app through
     * [distanceParts], so the picture and the screen behind it can never disagree.
     */
    private fun heroDistance(distanceM: Float): Pair<String, String> {
        val (value, unit) = distanceParts(distanceM)
        return value to if (unit == "km") "KILOMETRES" else "METRES"
    }

    private fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    // ── the three looks ─────────────────────────────────────────────────────

    private fun hero(canvas: Canvas, s: Stats, fonts: Fonts) {
        wordmark(canvas, fonts, 80f, 140f)
        canvas.drawText(s.dateLabel, WIDTH - 80f, 140f, paint(InkFaint.toArgb(), 34f, fonts.medium, Paint.Align.RIGHT))

        drawRoute(canvas, s.points, RectF(60f, 260f, WIDTH - 60f, 1080f), strokeWidth = 16f, paceColoured = true)

        val (heroValue, heroUnit) = heroDistance(s.distanceM)
        canvas.drawText(heroValue, 80f, 1330f, paint(Ink.toArgb(), 250f, fonts.display))
        canvas.drawText(
            heroUnit, 80f, 1390f,
            paint(Ember.toArgb(), 40f, fonts.medium).apply { letterSpacing = 0.24f },
        )

        val y = 1560f
        stat(canvas, fonts, 80f, y, duration(s.movingMs), "Moving")
        stat(canvas, fonts, 430f, y, formatPaceSecPerKm(s.paceSecPerKm), "Pace /km")
        stat(canvas, fonts, 760f, y, "%.0f m".format(s.climbM), "Climb")

        if (s.levelLabel != null) {
            canvas.drawText(s.levelLabel.uppercase(), 80f, 1700f, paint(InkMuted.toArgb(), 34f, fonts.mono).apply { letterSpacing = 0.14f })
        }
        recordChips(canvas, fonts, s.records, 80f, 1780f)
        footer(canvas, fonts)
    }

    private fun split(canvas: Canvas, s: Stats, fonts: Fonts) {
        drawRoute(canvas, s.points, RectF(0f, 0f, WIDTH.toFloat(), 1140f), strokeWidth = 18f, paceColoured = true)

        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Panel.toArgb() }
        canvas.drawRect(0f, 1140f, WIDTH.toFloat(), HEIGHT.toFloat(), panel)
        canvas.drawRect(0f, 1140f, WIDTH.toFloat(), 1146f, Paint().apply { color = Ember.toArgb() })

        canvas.drawText(s.title, 80f, 1265f, paint(Ink.toArgb(), 62f, fonts.bold))
        canvas.drawText(s.dateLabel, 80f, 1320f, paint(InkFaint.toArgb(), 34f, fonts.medium))

        val rows = buildList {
            add("Distance" to formatDistance(s.distanceM))
            add("Moving" to duration(s.movingMs))
            add("Avg pace" to "${formatPaceSecPerKm(s.paceSecPerKm)} /km")
            if (s.climbM > 0f) add("Climb" to "%.0f m".format(s.climbM))
            if (s.cadenceSpm > 0) add("Cadence" to "${s.cadenceSpm} spm")
            s.fastestKmSec?.let { add("Fastest km" to formatPaceSecPerKm(it)) }
        }
        var y = 1420f
        rows.forEach { (label, value) ->
            canvas.drawText(label, 80f, y, paint(InkMuted.toArgb(), 44f, fonts.medium))
            canvas.drawText(value, WIDTH - 80f, y, paint(Ink.toArgb(), 48f, fonts.bold, Paint.Align.RIGHT))
            canvas.drawLine(80f, y + 26f, WIDTH - 80f, y + 26f, Paint().apply { color = Rule.toArgb() })
            y += 92f
        }
        wordmark(canvas, fonts, 80f, HEIGHT - 110f)
        recordChips(canvas, fonts, s.records, 80f, y + 40f)
    }

    private fun effort(canvas: Canvas, s: Stats, fonts: Fonts) {
        wordmark(canvas, fonts, 80f, 140f)
        if (s.levelLabel != null) {
            canvas.drawText(s.levelLabel.uppercase(), WIDTH - 80f, 140f, paint(InkMuted.toArgb(), 34f, fonts.mono, Paint.Align.RIGHT))
        }

        val label = s.effortLabel.ifBlank { "EFFORT" }
        canvas.drawText(
            label.uppercase(), WIDTH / 2f, 480f,
            paint(Ember.toArgb(), 150f, fonts.display, Paint.Align.CENTER).apply { letterSpacing = 0.18f },
        )

        // The bar. Empty track first so the fill reads as a proportion rather than a floating blob.
        val barL = 100f
        val barR = WIDTH - 100f
        val barY = 570f
        val track = RectF(barL, barY, barR, barY + 44f)
        canvas.drawRoundRect(track, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelLift.toArgb() })
        val fill = RectF(barL, barY, barL + (barR - barL) * s.effort.coerceIn(0f, 1f), barY + 44f)
        if (fill.width() > 4f) {
            canvas.drawRoundRect(fill, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ember.toArgb() })
        }
        canvas.drawText(
            "Time spent in the two quickest bands",
            WIDTH / 2f, barY + 110f,
            paint(InkFaint.toArgb(), 32f, fonts.medium, Paint.Align.CENTER),
        )

        val y = 820f
        stat(canvas, fonts, 80f, y, formatDistance(s.distanceM), "Distance")
        stat(canvas, fonts, 500f, y, duration(s.movingMs), "Moving")
        val y2 = 990f
        stat(canvas, fonts, 80f, y2, formatPaceSecPerKm(s.paceSecPerKm), "Pace /km")
        stat(canvas, fonts, 500f, y2, s.fastestKmSec?.let { formatPaceSecPerKm(it) } ?: "—", "Fastest km")

        drawRoute(canvas, s.points, RectF(80f, 1100f, WIDTH - 80f, 1660f), strokeWidth = 12f, paceColoured = true)
        recordChips(canvas, fonts, s.records, 80f, 1760f)
        footer(canvas, fonts)
    }

    /** Personal records, as chips. Skipped entirely when there are none rather than left blank. */
    private fun recordChips(canvas: Canvas, fonts: Fonts, records: List<String>, x: Float, y: Float) {
        if (records.isEmpty()) return
        var cx = x
        val textPaint = paint(Ground.toArgb(), 32f, fonts.bold)
        records.take(2).forEach { label ->
            val text = label.uppercase()
            val w = textPaint.measureText(text) + 56f
            if (cx + w > WIDTH - 60f) return
            canvas.drawRoundRect(
                RectF(cx, y - 44f, cx + w, y + 16f), 30f, 30f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Success.toArgb() },
            )
            canvas.drawText(text, cx + 28f, y - 4f, textPaint)
            cx += w + 18f
        }
    }

    // ── the route ───────────────────────────────────────────────────────────

    /**
     * Draws the route inside [box], preserving aspect ratio.
     *
     * The same projection and simplification as every other view of a route in the app, so the
     * shape a player shares is the shape they saw. An empty or single-point route draws the panel
     * and a line of explanation instead of nothing, because a blank rectangle on a shared image
     * reads as a broken app.
     */
    private fun drawRoute(canvas: Canvas, points: List<RunPointEntity>, box: RectF, strokeWidth: Float, paceColoured: Boolean) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelLift.toArgb() }
        canvas.drawRoundRect(box, 28f, 28f, bg)

        if (points.size < 2) return
        val origin = points.first()
        val simplified = simplifyRoute(points, toleranceM = 1.5f)
        if (simplified.size < 2) return

        val local = simplified.map { metresEastNorth(origin.lat, origin.lon, it.lat, it.lon) }
        val minE = local.minOf { it.eastM }
        val maxE = local.maxOf { it.eastM }
        val minN = local.minOf { it.northM }
        val maxN = local.maxOf { it.northM }

        val pad = strokeWidth * 2f + 40f
        val usableW = max(1f, box.width() - pad * 2)
        val usableH = max(1f, box.height() - pad * 2)
        val scale = min(
            if (maxE - minE > 0f) usableW / (maxE - minE) else Float.MAX_VALUE,
            if (maxN - minN > 0f) usableH / (maxN - minN) else Float.MAX_VALUE,
        ).let { if (it == Float.MAX_VALUE) 1f else it }

        val offsetX = box.left + pad + (usableW - (maxE - minE) * scale) / 2f
        val offsetY = box.top + pad + (usableH - (maxN - minN) * scale) / 2f
        fun sx(p: LocalPoint) = offsetX + (p.eastM - minE) * scale
        fun sy(p: LocalPoint) = offsetY + (maxN - p.northM) * scale // north is up

        val median = if (paceColoured) medianSpeedMps(simplified) else 0f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // One path per pace band so the whole run is a handful of draws rather than thousands.
        var i = 1
        while (i < simplified.size) {
            val zone = if (paceColoured) paceZone(simplified[i].speedMps, median) else 2
            val path = Path().apply { moveTo(sx(local[i - 1]), sy(local[i - 1])) }
            while (i < simplified.size &&
                (if (paceColoured) paceZone(simplified[i].speedMps, median) else 2) == zone
            ) {
                path.lineTo(sx(local[i]), sy(local[i]))
                i++
            }
            line.color = if (paceColoured) paceColour(zone).toArgb() else Ember.toArgb()
            canvas.drawPath(path, line)
        }

        val startX = sx(local.first())
        val startY = sy(local.first())
        val endX = sx(local.last())
        val endY = sy(local.last())
        canvas.drawCircle(startX, startY, strokeWidth * 1.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelLift.toArgb() })
        canvas.drawCircle(
            startX, startY, strokeWidth * 1.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Success.toArgb(); style = Paint.Style.STROKE; this.strokeWidth = strokeWidth * 0.8f
            },
        )
        canvas.drawCircle(endX, endY, strokeWidth * 1.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelLift.toArgb() })
        canvas.drawCircle(endX, endY, strokeWidth * 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.toArgb() })
    }

    /** How much of a route was spent in the two quickest bands. The "effort" the effort card shows. */
    fun effortFraction(points: List<RunPointEntity>): Float {
        if (points.size < 2) return 0f
        val median = medianSpeedMps(points)
        if (median <= 0f) return 0f
        val quick = points.count { paceZone(it.speedMps, median) >= 3 }
        return quick.toFloat() / points.size
    }
}
