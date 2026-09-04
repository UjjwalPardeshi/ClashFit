package com.clashfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clashfit.meta.Achievement
import com.clashfit.meta.LevelProgress
import com.clashfit.meta.Tier
import com.clashfit.ui.theme.Bronze
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gold
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Silver
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width

/* The progression widgets: rings, XP bars and badges. All animate on first composition. */

/** A ring that fills clockwise from twelve, with whatever you want in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Int = 72,
    stroke: Int = 7,
    color: Color = Ember,
    track: Color = RuleSoft,
    content: @Composable () -> Unit = {},
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = Motion.fill, label = "ring")
    val pct = (f * 100).toInt()
    Box(modifier.size(size.dp).semantics { contentDescription = "$pct percent complete" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = stroke.dp.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (f > 0f) {
                drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** The level in a ring that shows how far to the next one. */
@Composable
fun LevelRing(progress: LevelProgress, modifier: Modifier = Modifier, size: Int = 72) {
    ProgressRing(progress.fraction, modifier, size = size) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${progress.level}", style = if (size >= 64) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, color = Ink)
            if (size >= 88) Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = InkMuted)
        }
    }
}

/** XP into the level, XP needed, and the rank title. */
@Composable
fun XpBar(progress: LevelProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Level ${progress.level}, ${progress.xpIntoLevel} of ${progress.xpForLevel} XP" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${progress.level} · ${progress.title}", style = MaterialTheme.typography.labelLarge, color = Ink)
            Text("${progress.xpIntoLevel} / ${progress.xpForLevel} XP", style = MaterialTheme.typography.labelSmall, color = InkMuted)
        }
        Bar(progress.fraction, height = 8, modifier = Modifier.clearAndSetSemantics {})
    }
}

fun Tier.color(): Color = when (this) {
    Tier.BRONZE -> Bronze
    Tier.SILVER -> Silver
    Tier.GOLD -> Gold
}

/** One badge. Locked badges are dim and keep their secret. */
@Composable
fun BadgeTile(achievement: Achievement, unlocked: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    val tint = if (unlocked) achievement.tier.color() else InkFaint
    val tierName = if (unlocked) when (achievement.tier) {
        Tier.BRONZE -> "Bronze"
        Tier.SILVER -> "Silver"
        Tier.GOLD -> "Gold"
    } else ""
    val contentDesc = if (unlocked) "${achievement.title}, ${achievement.description}, $tierName" else "${achievement.title}, Locked"
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(if (unlocked) Panel else Panel.copy(alpha = 0.6f)).padding(12.dp).semantics { contentDescription = contentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A medal, not a circle with a glyph in it.
        //
        // A badge that looks like every other badge is a checkbox. The tier is the whole point of
        // an achievement — bronze, silver, gold say how hard it was — and it was carried only by
        // the tint of an icon and a word underneath. Now the shape itself says it: bronze gets a
        // plain plate, silver a notched one, gold a rayed one, and a locked badge is the same
        // outline with nothing in it, so you can see what shape is missing from the set.
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(r, r)
                val rays = achievement.tier == Tier.GOLD
                val notches = achievement.tier != Tier.BRONZE

                if (unlocked && rays) {
                    for (i in 0 until 12) {
                        val a = i * (2 * Math.PI.toFloat()) / 12
                        drawLine(
                            tint.copy(alpha = 0.55f),
                            Offset(c.x + cos(a) * r * 0.80f, c.y + sin(a) * r * 0.80f),
                            Offset(c.x + cos(a) * r * 0.99f, c.y + sin(a) * r * 0.99f),
                            strokeWidth = r * 0.10f,
                        )
                    }
                }

                val sides = if (notches) 8 else 6
                val plate = Path().apply {
                    for (i in 0 until sides) {
                        val a = i * (2 * Math.PI.toFloat()) / sides - (Math.PI.toFloat() / 2)
                        val rad = r * 0.72f
                        val p = Offset(c.x + cos(a) * rad, c.y + sin(a) * rad)
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(plate, if (unlocked) tint.copy(alpha = 0.20f) else PanelLift)
                drawPath(
                    plate,
                    if (unlocked) tint else Rule,
                    style = Stroke(width = r * 0.09f),
                )
            }
            Icon(
                icon,
                contentDescription = null,
                tint = if (unlocked) tint else InkFaint.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            achievement.title, style = MaterialTheme.typography.labelLarge, color = if (unlocked) Ink else InkMuted,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (unlocked) achievement.description else "Locked",
            style = MaterialTheme.typography.bodySmall, color = InkFaint,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A horizontal stat strip: three or four numbers with labels, no cards. */
@Composable
fun StatStrip(stats: List<Pair<String, String>>, modifier: Modifier = Modifier, framed: Boolean = true) {
    // Framed, because three bare numbers floating on the background under a full-bleed hero read
    // as something not yet finished. On a plate, divided, they read as an instrument panel — the
    // same three numbers, and the page stops looking like a draft.
    val row: @Composable () -> Unit = {
        // Weighted columns sit flush against each other, so SpaceBetween adds no gap and a label
        // wide enough to fill its column touches its neighbour. At 1.5x text "BEST STREAK" and
        // "CLEAN THIS WEEK" ran together into one unreadable line. A real gap, and centred
        // wrapping, fixes it at every text size.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (framed) 0.dp else 12.dp)) {
            stats.forEachIndexed { i, (value, label) ->
                if (framed && i > 0) {
                    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 2.dp).background(RuleSoft))
                }
                Column(
                    Modifier.weight(1f)
                        .padding(horizontal = if (framed) 8.dp else 0.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$value ${label.lowercase()}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = Ink, maxLines = 1)
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    if (framed) AppCard(modifier.fillMaxWidth(), padding = 14) { row() } else Box(modifier) { row() }
}

/** Seven small bars for the week, today on the right. */
@Composable
fun WeekBars(values: List<Float>, modifier: Modifier = Modifier, height: Int = 56, labels: List<String> = emptyList()) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(modifier.fillMaxWidth().height((height + 18).dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val dayLabel = if (labels.size == values.size) labels[i] else "Day $i"
            Column(Modifier.weight(1f).semantics { contentDescription = "$dayLabel, ${v.toInt()} reps" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val f by animateFloatAsState((v / max).coerceIn(0.04f, 1f), animationSpec = Motion.fill, label = "bar$i")
                // A pill, not a circle. Clipping to CircleShape rounds a Box fully once it is as tall as
                // it is wide, which turned a good training week into a row of dots.
                Box(Modifier.fillMaxWidth().height((height * f).dp).clip(RoundedCornerShape(5.dp)).background(if (v > 0f) Ember else RuleSoft).clearAndSetSemantics {})
                if (labels.size == values.size) {
                    Text(labels[i], style = MaterialTheme.typography.labelSmall, color = InkFaint, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
