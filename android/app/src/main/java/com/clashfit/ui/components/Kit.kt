package com.clashfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Verdict
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.theme.Ember
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
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Shallow
import com.clashfit.ui.theme.Working

// The shared kit. Square corners, hard rules, one accent. Every screen builds from these so
// the app reads as one object, not eleven.

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

/** Small uppercase label with the ember dash: the section eyebrow. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Ember) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(18.dp).height(2.dp).background(color))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Condensed uppercase headline; pass `accent` for the orange second line. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, accent: String? = null) {
    Column(modifier) {
        Text(text.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Ink)
        if (accent != null) Text(accent.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Ember)
    }
}

@Composable
fun EmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .background(if (enabled) Ember else Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (enabled) Ground else InkFaint)
        Text("↗", style = MaterialTheme.typography.titleMedium, color = if (enabled) Ground else InkFaint)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.border(1.dp, Rule).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Ink)
        Text("↗", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

/** A bordered surface. Square by design. */
@Composable
fun PanelBox(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable () -> Unit) {
    Box(modifier.background(Panel).border(1.dp, Rule).padding(padding.dp)) { content() }
}

/** Big number, small label. Reads at two metres. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink) {
    PanelBox(modifier, padding = 12) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) {
    Text(
        text.uppercase(),
        modifier = modifier.border(1.dp, if (color == InkMuted) Rule else color).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** Four pips with a named band, never a percentage. */
@Composable
fun FatiguePips(band: FatigueBand, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val lit = band.ordinal + 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(Modifier.width(22.dp).height(6.dp).background(if (i < lit) band.color() else RuleSoft))
            }
        }
        if (showLabel) Text(band.label, style = MaterialTheme.typography.labelMedium, color = band.color())
    }
}

/** Thin horizontal bar with an overshooting spring, for HP and progress. */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = Ember, track: Color = RuleSoft, height: Int = 8) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = Motion.hpBar, label = "bar")
    Box(modifier.fillMaxWidth().height(height.dp).background(track)) {
        Box(Modifier.fillMaxWidth(f).height(height.dp).background(color))
    }
}

/** One rule-separated row: label left, value right. The library and settings are lists of these. */
@Composable
fun RuleRow(label: String, value: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (trailing != null) trailing() else if (value != null) Text(value, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
    }
}

@Composable
fun SectionGap(height: Int = 28) = Spacer(Modifier.height(height.dp))

/** The brand mark: an ember square. */
@Composable
fun Mark(size: Int = 12, color: Color = Ember) = Box(Modifier.size(size.dp).background(color))

/** Centred empty state, never a bare spinner. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun BrassText(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = Brass)

/** Clip helper for square-cornered images and canvases. */
fun Modifier.square(): Modifier = this.clip(RectangleShape)
