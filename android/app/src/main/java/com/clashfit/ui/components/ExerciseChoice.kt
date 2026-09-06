package com.clashfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ground2
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.RuleSoft

/**
 * One selectable movement: name, difficulty, and a drawing of it behind a chevron.
 *
 * Shared because the same row is now offered in two places — the mode picker before a solo
 * fight, and inside the versus lobby, where the host chooses for both phones. Two copies of it
 * would drift, and a movement that looks different in the lobby than in the picker reads as a
 * different movement.
 */
@Composable
fun ExerciseChoice(ex: ExerciseSpec, selected: Boolean, reduceMotion: Boolean, onSelect: () -> Unit) {
    var showing by rememberSaveable(ex.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(if (selected) EmberTint else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSelect).heightIn(min = 60.dp)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioDot(selected)
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(if (i < ex.difficulty) Ember else RuleSoft))
                    }
                    Text(
                        when (ex.difficulty) { 1 -> "Easy"; 2 -> "Moderate"; else -> "Hard" },
                        style = MaterialTheme.typography.labelSmall, color = InkMuted, modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (ex.tags.isNotEmpty()) Tag(ex.tags.first())
            // The drawing is a second, quieter affordance: tapping it must not change the selection.
            if (hasExerciseDemo(ex.id)) {
                Box(
                    Modifier.size(40.dp).clickable { showing = !showing },
                    contentAlignment = Alignment.Center,
                ) {
                    val turn by animateFloatAsState(if (showing) -90f else 90f, label = "demo-chevron")
                    Icon(
                        AppIcons.Chevron,
                        contentDescription = if (showing) "Hide how to do it" else "Show how to do it",
                        tint = InkMuted, modifier = Modifier.size(18.dp).rotate(turn),
                    )
                }
            }
        }
        AnimatedVisibility(visible = showing && hasExerciseDemo(ex.id)) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                ExerciseDemo(ex.id, reduceMotion)
                ex.cues["enter"]?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
            }
        }
    }
}

@Composable
fun RadioDot(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (on) Ember else Panel),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(AppIcons.Check, contentDescription = "Selected", tint = Ground, modifier = Modifier.size(14.dp))
        else Box(Modifier.size(18.dp).clip(CircleShape).background(Ground2))
    }
}
