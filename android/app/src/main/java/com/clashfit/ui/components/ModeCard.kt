package com.clashfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Success
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush

/** How the four kinds of mode are named to a player. */
fun ModeKind.label(): String = when (this) {
    ModeKind.SOLO -> "Solo"
    ModeKind.VERSUS -> "Duo"
    ModeKind.GROUP -> "Group"
    ModeKind.FAMILY -> "By movement"
    ModeKind.CLINIC -> "Clinic"
    ModeKind.WORKOUT -> "Workout"
}

/**
 * One way to play, as a tile.
 *
 * Sixteen of these sit in a row on Train and in a grid on Modes. Each used to carry two lines of
 * the mode's description, which made the page a wall of prose that had to be read before anything
 * could be chosen — and at card width most of those lines ellipsised mid-word anyway, so the
 * reading did not even pay off. A tile is a mark, a name, and a hook.
 *
 * The mark sits on a plate tinted to its own kind, which gives the row a rhythm of colour: the
 * six solo modes read as one block, the head-to-heads as another, before a single word is read.
 */
@Composable
fun ModeCard(mode: GameMode, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int? = null) {
    val m = if (width != null) modifier.width(width.dp) else modifier.fillMaxWidth()
    val tint = if (mode.enabled) mode.kind.tint() else InkFaint
    AppCard(
        m.heightIn(min = 148.dp),
        onClick = if (mode.enabled) onClick else null,
        container = if (mode.enabled) Panel else Panel.copy(alpha = 0.55f),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The mark on its own lit plate. It is what gets recognised on the second visit, and
            // it says the kind and whether the mode is timed before a word has been read.
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f)),
                        ),
                    )
                    .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ModeSigil(mode, enabled = mode.enabled, size = 28)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    mode.title, style = MaterialTheme.typography.titleMedium,
                    color = if (mode.enabled) Ink else InkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // One line. The sentence that explains the mode properly is on the mode's own
                // screen, where there is room for it and a reason to read it.
                Text(
                    mode.hook, style = MaterialTheme.typography.labelMedium,
                    color = if (mode.enabled) InkMuted else InkFaint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Not a weighted spacer: the card's height is a minimum, not a fixed size, so the
            // column measures unbounded and a weight would resolve to nothing at all.
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !mode.enabled -> Tag("Coming soon", color = InkMuted)
                    mode.timed -> Tag("Timed", color = Brass)
                    mode.kind == ModeKind.CLINIC -> Tag("Protocol", color = Success)
                    else -> Tag(mode.kind.label())
                }
            }
        }
    }
}
