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
                    else -> link.state.name
                },
                color = if (link.state == LinkState.LINKED) Ink else InkMuted,
            )
        }
        when (link.mode) {
            GameMode.REP_RACE -> Versus(leftLabel = "YOU", left = myReps.toString(), rightLabel = "THEM", right = link.opponentReps.toString())
            GameMode.RAID, GameMode.TEAM_VS_TEAM -> Leaderboard(link)
            else -> Versus(leftLabel = "YOU", left = myDamage.toString(), rightLabel = "THEM", right = link.opponentDamage.toString())
        }
    }
}

@Composable
private fun Versus(leftLabel: String, left: String, rightLabel: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Side(leftLabel, left, Alignment.Start)
        Text("VS", style = MaterialTheme.typography.labelMedium, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        Side(rightLabel, right, Alignment.End)
    }
}

@Composable
private fun Side(label: String, value: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Text(value, style = MaterialTheme.typography.headlineLarge, color = Ink)
    }
}

@Composable
private fun Leaderboard(link: LinkHud) {
    val rows = link.standings.take(4)
    if (rows.isEmpty()) {
        Text("WAITING FOR THE OTHERS…", style = MaterialTheme.typography.labelMedium, color = InkMuted)
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
