package com.clashfit.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.LinkState
import com.clashfit.play.LinkHud
import com.clashfit.ui.components.Tag
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Rule
import androidx.compose.foundation.layout.fillMaxSize

/**
 * The other phones, in one row above the boss. A duel shows their damage against yours, a rep
 * race shows the rep count to beat, a raid shows the top of the live leaderboard. Nothing here
 * animates on its own — the numbers move, that is the animation.
 */
@Composable
fun LinkStrip(link: LinkHud, myReps: Int, myDamage: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(link.mode.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Ember)
            Tag(
                text = when (link.state) {
                    LinkState.LINKED -> "LINKED · ${link.peers}"
                    LinkState.IDLE -> "NO LINK"
                    LinkState.ADVERTISING -> "WAITING"
                    LinkState.SEARCHING -> "LOOKING"
                    LinkState.LOST -> "LINK LOST"
                },
                color = if (link.state == LinkState.LINKED) Ink else InkMuted,
            )
        }
        when (link.mode) {
            GameMode.REP_RACE -> Versus(leftLabel = "YOU", left = myReps, rightLabel = "THEM", right = link.opponentReps)
            GameMode.RAID, GameMode.TEAM_VS_TEAM -> Leaderboard(link)
            else -> Versus(leftLabel = "YOU", left = myDamage, rightLabel = "THEM", right = link.opponentDamage)
        }
    }
}

/**
 * Two scores and the rope between them.
 *
 * Two numbers side by side make you do the arithmetic mid-set, which is exactly when you have no
 * arithmetic to spare. The rope does it for you: the knot sits wherever the lead is, so a glance
 * tells you whether you are winning without reading a digit. The numbers stay, because after the
 * set you want to know by how much.
 */
@Composable
private fun Versus(leftLabel: String, left: Int, rightLabel: String, right: Int) {
    // The knot's position, as a share of the total. Dead even sits in the middle; nothing scored
    // yet also sits in the middle, rather than lurching to one end on the first rep.
    val total = (left + right).coerceAtLeast(1)
    val target = if (left + right == 0) 0.5f else left.toFloat() / total
    val knot by animateFloatAsState(target, animationSpec = Motion.fill, label = "tug")

    val youAhead = left > right
    val theyAhead = right > left

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Side(leftLabel, "$left", Alignment.Start, leading = youAhead)
            Text(
                when {
                    left == right -> "LEVEL"
                    youAhead -> "+${left - right}"
                    else -> "-${right - left}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (youAhead) Ember else if (theyAhead) Gassed else InkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Side(rightLabel, "$right", Alignment.End, leading = theyAhead)
        }

        // The rope. Your pull comes from the left in Ember, theirs from the right.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Rule)
                .semantics {
                    contentDescription = when {
                        left == right -> "Level, $left each"
                        youAhead -> "You lead by ${left - right}"
                        else -> "Behind by ${right - left}"
                    }
                },
        ) {
            // Your pull, from the left. The knot rides its leading edge, which is the lead.
            Box(Modifier.fillMaxHeight().fillMaxWidth(knot)) {
                Box(Modifier.fillMaxSize().background(Ember))
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp).background(Ink))
            }
        }
    }
}

@Composable
private fun Side(label: String, value: String, align: Alignment.Horizontal, leading: Boolean) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (leading) Ember else InkMuted)
        Text(value, style = MaterialTheme.typography.headlineLarge, color = Ink)
    }
}

@Composable
private fun Leaderboard(link: LinkHud) {
    val rows = link.standings.take(4)
    if (rows.isEmpty()) {
        Text("WAITING FOR THE OTHERS · KEEP GOING", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        return
    }
    rows.forEachIndexed { i, s ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayName = s.name.takeIf { it.isNotBlank() }?.uppercase() ?: s.playerId
            Text("${i + 1}  $displayName${if (s.me) " ·YOU" else ""}${if (s.out) " ·OUT" else ""}",
                style = MaterialTheme.typography.labelLarge, color = if (s.me) Ember else Ink)
            Text("${s.damage}  ·  ${s.reps} REPS", style = MaterialTheme.typography.labelLarge, color = if (s.out) InkMuted else Ink)
        }
    }
}

/** The duel strip on its own, for the screenshot suite. */
@Composable
fun LinkStripPreview(myDamage: Int, theirDamage: Int, modifier: Modifier = Modifier) {
    LinkStrip(
        LinkHud(
            mode = GameMode.DUEL,
            state = LinkState.LINKED,
            isHost = true,
            peers = 1,
            opponentDamage = theirDamage,
            opponentReps = 0,
            standings = emptyList(),
            raceStartedMs = null,
        ),
        myReps = 0,
        myDamage = myDamage,
        modifier = modifier,
    )
}
