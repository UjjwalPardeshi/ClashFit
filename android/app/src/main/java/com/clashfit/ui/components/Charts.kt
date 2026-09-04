package com.clashfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.RuleSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The chart kit. The app measures a great deal about every rep and used to show almost none of it.
 *
 * Three rules hold across all of them:
 *  - Every chart animates its own value in, so a number never simply appears.
 *  - Every chart carries a contentDescription that states the reading in words, because a shape
 *    that only means something visually is invisible to a screen reader.
 *  - None of them draw a chart junk axis. The label goes next to the number it labels.
 */

private const val TAU = (2 * PI).toFloat()

// ── line ──────────────────────────────────────────────────────────────────────────────────

/**
 * A trend line with a soft fill under it. Points are 0..1 already normalised by the caller, so the
 * chart never has to guess what the units mean.
 */
@Composable
fun TrendLine(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Ember,
    height: Int = 120,
    /** Drawn as a dashed rule across the chart, for a target or an average. */
    marker: Float? = null,
    description: String = "Trend of ${points.size} points",
) {
    val grow by animateFloatAsState(if (points.isEmpty()) 0f else 1f, Motion.fill, label = "trend")
    Canvas(
        modifier.fillMaxWidth().height(height.dp)
            .semantics { contentDescription = description },
    ) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val pad = h * 0.10f
        fun x(i: Int) = w * i / (points.size - 1).toFloat()
        fun y(v: Float) = h - pad - (h - pad * 2) * v.coerceIn(0f, 1f)

        // Quarter lines, so a line sitting high on the chart is readable as "high" rather than
        // as a solid block of fill.
        for (q in 1..3) {
            val gy = y(q / 4f)
            drawLine(RuleSoft.copy(alpha = 0.6f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1.5f)
        }

        marker?.let {
            val my = y(it)
            var cx = 0f
            while (cx < w) {
                drawLine(RuleSoft, Offset(cx, my), Offset(min(cx + 10f, w), my), strokeWidth = 2f)
                cx += 18f
            }
        }

        val shown = (points.size * grow).toInt().coerceAtLeast(2)
        val line = Path()
        val fill = Path()
        for (i in 0 until shown) {
            val px = x(i)
            val py = y(points[i])
            if (i == 0) { line.moveTo(px, py); fill.moveTo(px, h) ; fill.lineTo(px, py) }
            else { line.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(x(shown - 1), h)
        fill.close()
        drawPath(fill, color.copy(alpha = 0.10f))
        drawPath(line, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        // The most recent point is the one that matters, so it gets a dot.
        drawCircle(color, radius = 8f, center = Offset(x(shown - 1), y(points[shown - 1])))
    }
}

// ── bars ──────────────────────────────────────────────────────────────────────────────────

/** Vertical bars with labels underneath. Values are raw; the chart scales to the largest. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = Ember,
    height: Int = 120,
    unit: String = "",
) {
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val reading = remember(values, labels) {
        if (values.isEmpty()) "No data yet"
        else labels.zip(values).joinToString(", ") { (l, v) -> "$l ${v.toInt()}$unit" }
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = reading }) {
        Row(
            Modifier.fillMaxWidth().height(height.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val f by animateFloatAsState((v / maxV).coerceIn(0f, 1f), Motion.fill, label = "bar$i")
                Box(
                    Modifier.weight(1f)
                        .height((height * max(f, 0.02f)).dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (v > 0f) color else RuleSoft),
                )
            }
        }
        if (labels.size == values.size) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One bar split into coloured parts. The rep-verdict breakdown for a session. */
@Composable
fun StackedBar(
    parts: List<Pair<Color, Int>>,
    modifier: Modifier = Modifier,
    height: Int = 14,
    description: String = "",
) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(RuleSoft)
            .semantics { if (description.isNotEmpty()) contentDescription = description },
    ) {
        parts.forEach { (c, n) ->
            if (n > 0) Box(Modifier.weight(n.toFloat() / total).fillMaxWidth().background(c).height(height.dp))
        }
    }
}

// ── heatmap ───────────────────────────────────────────────────────────────────────────────

/**
 * A training heatmap: one cell per day, weeks running left to right, the way a contribution graph
 * reads. `intensity` is 0..1 per day, oldest first.
 */
@Composable
fun Heatmap(
    intensity: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Fresh,
    weeks: Int = 12,
    description: String = "Training over the last $weeks weeks",
) {
    val days = weeks * 7
    val padded = remember(intensity, days) {
        if (intensity.size >= days) intensity.takeLast(days) else List(days - intensity.size) { 0f } + intensity
    }
    // Square cells: the width decides the cell size, so the height must follow it.
    Canvas(
        modifier.fillMaxWidth().aspectRatio(weeks / 7f)
            .semantics { contentDescription = description },
    ) {
        // At least one column. Float division by zero does not throw, it yields Infinity, which
    // travels silently into every cell position and draws nothing at all.
    val cols = weeks.coerceAtLeast(1)
        val gap = size.width * 0.006f
        val cell = (size.width - gap * (cols - 1)) / cols
        val cellH = cell
        padded.forEachIndexed { i, v ->
            val col = i / 7
            val row = i % 7
            val x = col * (cell + gap)
            val y = row * (cellH + gap)
            val c = if (v <= 0f) PanelLift else color.copy(alpha = 0.30f + 0.70f * v.coerceIn(0f, 1f))
            drawRoundRect(
                color = c,
                topLeft = Offset(x, y),
                size = Size(cell, cellH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.22f),
            )
        }
    }
}

// ── radar ─────────────────────────────────────────────────────────────────────────────────

/**
 * A radar of named stats, each 0..1. This is how seven interacting numbers become one shape you
 * can read at a glance, which is the whole point of the character sheet. docs/22-HEALTH-DOMAINS.md
 */
@Composable
fun RadarChart(
    stats: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    color: Color = Ember,
    size: Int = 240,
) {
    val grow by animateFloatAsState(if (stats.isEmpty()) 0f else 1f, Motion.fill, label = "radar")
    val reading = remember(stats) {
        stats.joinToString(", ") { (n, v) -> "$n ${(v * 100).toInt()} of 100" }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = InkMuted)
    Canvas(
        modifier.size(size.dp).semantics { contentDescription = reading },
    ) {
        if (stats.size < 3) return@Canvas
        val n = stats.size
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        // Leave room for the labels around the outside. The web used to take 0.34 of the box and
        // put its labels at 1.34 of that, which left about twelve device-independent pixels of
        // clearance at the widest points — enough for "RES" and nothing else, which is why the
        // domains were showing as three-letter codes nobody can read.
        val r = this.size.minDimension * 0.30f
        fun point(i: Int, frac: Float): Offset {
            val a = -TAU / 4f + i * TAU / n
            return Offset(c.x + cos(a) * r * frac, c.y + sin(a) * r * frac)
        }

        // Web rings, so the shape has a scale to be read against.
        for (ring in 1..4) {
            val f = ring / 4f
            val web = Path()
            for (i in 0 until n) {
                val p = point(i, f)
                if (i == 0) web.moveTo(p.x, p.y) else web.lineTo(p.x, p.y)
            }
            web.close()
            drawPath(web, RuleSoft, style = Stroke(width = 2f))
        }
        for (i in 0 until n) drawLine(RuleSoft, c, point(i, 1f), strokeWidth = 2f)

        // The shape itself.
        val shape = Path()
        for (i in 0 until n) {
            val p = point(i, stats[i].second.coerceIn(0f, 1f) * grow)
            if (i == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
        }
        shape.close()
        drawPath(shape, color.copy(alpha = 0.28f))
        drawPath(shape, color, style = Stroke(width = 4f))
        for (i in 0 until n) drawCircle(color, radius = 6f, center = point(i, stats[i].second.coerceIn(0f, 1f) * grow))

        // Labels, placed just outside their spoke.
        for (i in 0 until n) {
            val a = -TAU / 4f + i * TAU / n
            val lp = Offset(c.x + cos(a) * r * 1.22f, c.y + sin(a) * r * 1.22f)
            val laid = measurer.measure(stats[i].first, labelStyle)
            // Held inside the canvas whatever the word is, so a longer domain name shifts rather
            // than running off the edge of the card.
            val x = (lp.x - laid.size.width / 2f).coerceIn(0f, (this.size.width - laid.size.width).coerceAtLeast(0f))
            val y = (lp.y - laid.size.height / 2f).coerceIn(0f, (this.size.height - laid.size.height).coerceAtLeast(0f))
            drawText(textLayoutResult = laid, topLeft = Offset(x, y))
        }
    }
}

// ── donut ─────────────────────────────────────────────────────────────────────────────────

/** A ring split into parts, with whatever you want in the middle. */
@Composable
fun DonutChart(
    parts: List<Pair<Color, Int>>,
    modifier: Modifier = Modifier,
    size: Int = 132,
    thickness: Int = 16,
    description: String = "",
    centre: @Composable () -> Unit = {},
) {
    val total = parts.sumOf { it.second }
    val grow by animateFloatAsState(if (total > 0) 1f else 0f, Motion.fill, label = "donut")
    Box(
        modifier.size(size.dp).semantics { if (description.isNotEmpty()) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size.dp)) {
            val sw = thickness.dp.toPx()
            val inset = sw / 2f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(RuleSoft, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (total <= 0) return@Canvas
            var start = -90f
            parts.forEach { (c, n) ->
                if (n <= 0) return@forEach
                val sweep = 360f * n / total * grow
                drawArc(c, start + 1f, (sweep - 2f).coerceAtLeast(0f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                start += sweep
            }
        }
        centre()
    }
}

// ── legend ────────────────────────────────────────────────────────────────────────────────

/** A dot, a name and a number. Charts that use colour need one of these to be readable. */
@Composable
fun ChartLegend(items: List<Triple<Color, String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (c, name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Text(name, style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall, color = Ink)
            }
        }
    }
}

/** A chart with a title, an optional one-line reading, and room to breathe. */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    AppCard(modifier.fillMaxWidth(), container = Panel, padding = 16) {
        Column {
            SectionTitle(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.padding(top = 14.dp)) { content() }
        }
    }
}
