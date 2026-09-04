package com.clashfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Verdict
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Eyebrow
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Ok
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Shallow
import com.clashfit.ui.theme.Working

/*
 * The shared kit. Material 3 underneath, one accent on top. Every screen builds from these so
 * the app reads as one object. Older names (EmberButton, PanelBox, RuleRow) stay as thin wrappers
 * so nothing breaks while screens move over.
 */

fun FatigueBand.color(): Color = when (this) {
    FatigueBand.FRESH -> Fresh
    FatigueBand.WORKING -> Working
    FatigueBand.FADING -> Heavy
    FatigueBand.GASSED -> Gassed
}

fun Verdict.color(): Color = when (this) {
    Verdict.CLEAN -> Clean
    Verdict.OK -> Ok
    Verdict.SHALLOW -> Shallow
}

// ── text ──────────────────────────────────────────────────────────────────────────────────

/** Small spaced eyebrow above a group. Quiet by default; pass a colour to make it a warning. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) {
    Text(text.uppercase(), modifier, style = Eyebrow, color = color)
}

/** A section title with an optional trailing action, the way every list screen names its groups. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Ember)
            }
        }
    }
}

/** Condensed uppercase headline; pass `accent` for the ember second line. For heroes and fights. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, accent: String? = null) {
    Column(modifier) {
        Text(text.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Ink)
        if (accent != null) Text(accent.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Ember)
    }
}

@Composable
fun BrassText(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = Brass)

// ── buttons ───────────────────────────────────────────────────────────────────────────────

/** The one filled button. A pill, 52dp tall, full width unless told otherwise. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ember, contentColor = Ground,
            disabledContainerColor = PanelLift, disabledContentColor = InkFaint,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The tonal second choice next to a PrimaryButton. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = PanelLift, contentColor = Ink,
            disabledContainerColor = Panel, disabledContentColor = InkFaint,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A quiet inline action: "Forgot password?", "See all". */
@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, color: Color = Ember, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun EmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    PrimaryButton(text, modifier, enabled, onClick = onClick)

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    SecondaryButton(text, modifier, onClick = onClick)

// ── surfaces ──────────────────────────────────────────────────────────────────────────────

/** A card. Tonal, rounded, no border. Tappable when given onClick. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = Panel,
    padding: Int = 16,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = Ink)
    val shape = MaterialTheme.shapes.medium
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Box(Modifier.padding(padding.dp)) { content() }
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Box(Modifier.padding(padding.dp)) { content() }
        }
    }
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable () -> Unit) =
    AppCard(modifier, padding = padding, content = content)

/** Big number, small label. Reads at two metres. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink) {
    AppCard(modifier, padding = 14) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$value ${label.lowercase()}" }) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A small tinted pill. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) {
    val tint = if (color == InkMuted) PanelLift else color.copy(alpha = 0.16f)
    Text(
        text,
        modifier = modifier.clip(CircleShape).background(tint).padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (color == InkMuted) Ink else color,
    )
}

/** A circle with a glyph in it, for the front of a list row. */
@Composable
fun IconBubble(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Ember, size: Int = 40) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Initials in a coloured circle. The player's face until they pick one. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Int = 56, color: Color = Ember) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }.ifEmpty { "?" }
    Box(modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(
            initials,
            style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = Ground,
        )
    }
}

// ── data ──────────────────────────────────────────────────────────────────────────────────

/** Four pips with a named band, never a percentage. */
@Composable
fun FatiguePips(band: FatigueBand, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val lit = band.ordinal + 1
    Column(modifier.semantics { contentDescription = "${band.label}, $lit of 4" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(Modifier.width(22.dp).height(6.dp).clip(CircleShape).background(if (i < lit) band.color() else RuleSoft))
            }
        }
        if (showLabel) Text(band.label, style = MaterialTheme.typography.labelMedium, color = band.color())
    }
}

/** Thin rounded bar with an overshooting spring, for HP and progress. */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = Ember, track: Color = RuleSoft, height: Int = 8) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = Motion.hpBar, label = "bar")
    val pct = (f * 100).toInt()
    Box(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(track).semantics { contentDescription = "$pct percent" }) {
        Box(Modifier.fillMaxWidth(f).height(height.dp).clip(CircleShape).background(color))
    }
}

/** Segmented bar with discrete filled segments, overshooting spring, readable at distance. */
@Composable
fun SegmentedBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ember,
    track: Color = RuleSoft,
    height: Int = 8,
    segments: Int = 20,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = Motion.hpBar, label = "segmentedBar")
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val w = size.width
        val h = size.height
        val segmentWidth = w / segments
        val gapWidth = segmentWidth * 0.12f // ~12% gap between segments
        val drawWidth = segmentWidth - gapWidth

        // Draw track background
        for (i in 0 until segments) {
            val x = i * segmentWidth + gapWidth / 2
            drawRect(track, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(drawWidth, h))
        }

        // Draw filled segments
        val filledSegments = (f * segments).toInt()
        for (i in 0 until filledSegments) {
            val x = i * segmentWidth + gapWidth / 2
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(drawWidth, h))
        }

        // Draw partial segment if needed
        val partial = (f * segments) - filledSegments
        if (partial > 0.01f && filledSegments < segments) {
            val x = filledSegments * segmentWidth + gapWidth / 2
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(drawWidth * partial, h))
        }
    }
}

/** One row: label left, value right, 56dp tall. Group several inside a ListGroup. */
@Suppress("ModifierParameter") // call sites pass `value` positionally
@Composable
fun RuleRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
        if (trailing != null) trailing() else if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

/** A card that stacks rows with hairlines between them: the settings-group pattern. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Panel, contentColor = Ink),
    ) { Column { content() } }
}

@Composable
fun SectionGap(height: Int = 24) = Spacer(Modifier.height(height.dp))

/** The brand mark: an ember dot. */
@Composable
fun Mark(size: Int = 12, color: Color = Ember) = Box(Modifier.size(size.dp).clip(CircleShape).background(color))

/** A hairline inside a card. */
@Composable
fun InnerDivider(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(RuleSoft))

/** Kept for the camera overlays that clip a square. */
fun Modifier.square(): Modifier = this.clip(androidx.compose.ui.graphics.RectangleShape)

/** Centred body copy for empty and error states. */
@Composable
fun CenteredCopy(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) =
    Text(text, modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)

/** A one-pixel border for the rare element that needs an outline instead of a tone. */
fun Modifier.outlined(): Modifier = this.border(1.dp, Rule, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
