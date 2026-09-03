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

/** How the four kinds of mode are named to a player. */
fun ModeKind.label(): String = when (this) {
    ModeKind.SOLO -> "Solo"
    ModeKind.VERSUS -> "Head to head"
    ModeKind.GROUP -> "Group"
    ModeKind.FAMILY -> "By movement"
    ModeKind.CLINIC -> "Clinic"
}

/** One way to play, as a card. Fixed width in a carousel, full width in a grid. */
@Composable
fun ModeCard(mode: GameMode, onClick: () -> Unit, modifier: Modifier = Modifier, width: Int? = null) {
    val m = if (width != null) modifier.width(width.dp) else modifier.fillMaxWidth()
    AppCard(
        m.heightIn(min = 140.dp),
        onClick = if (mode.enabled) onClick else null,
        container = if (mode.enabled) Panel else Panel.copy(alpha = 0.55f),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                mode.title, style = MaterialTheme.typography.titleMedium,
                color = if (mode.enabled) Ink else InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                mode.blurb, style = MaterialTheme.typography.bodySmall, color = if (mode.enabled) InkMuted else InkFaint,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !mode.enabled -> Tag("Coming online", color = InkMuted)
                    mode.timed -> Tag("Timed", color = Brass)
                    mode.kind == ModeKind.CLINIC -> Tag("Protocol", color = Success)
                    else -> Tag(mode.kind.label())
                }
            }
        }
    }
}
