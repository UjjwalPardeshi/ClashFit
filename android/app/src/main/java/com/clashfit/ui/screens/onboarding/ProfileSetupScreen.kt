package com.clashfit.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.auth.AuthState
import com.clashfit.data.Prefs
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Avatar
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.screens.picker.FEATURED_EXERCISES
import androidx.compose.ui.text.style.TextOverflow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.AvatarPalette
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.launch

/** Three quick choices after the account exists: a colour, a goal, a favourite exercise. */
@Composable
fun ProfileSetupScreen(graph: AppGraph, onDone: () -> Unit) {
    val auth by graph.auth.state.collectAsStateWithLifecycle()
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val scope = rememberCoroutineScope()

    val name = (auth as? AuthState.SignedIn)?.user?.shownName("You") ?: "You"
    var color by rememberSaveable { mutableStateOf(settings.avatarColor) }
    var goal by rememberSaveable { mutableStateOf(Prefs.Goal.GET_STRONGER) }
    var exercise by rememberSaveable { mutableStateOf(settings.preferredExercise) }
    var saving by remember { mutableStateOf(false) }

    // The same four the picker offers. Letting somebody choose a favourite they can never fight
    // with is a promise the next screen breaks.
    val popular = remember(exercises) {
        FEATURED_EXERCISES.mapNotNull { id -> exercises.values.firstOrNull { it.id == id } }
    }

    ScreenScaffold(title = "Your profile") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, size = 96, color = AvatarPalette.at(color))
                Spacer(Modifier.height(12.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text("Pick a colour", style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarPalette.colors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c)
                                .border(if (i == color) 3.dp else 0.dp, Ink, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Icon(AppIcons.Check, contentDescription = "Selected", tint = Ground, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            SectionGap(30)
            SectionTitle("What are you here for?")
            SectionGap(10)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Prefs.Goal.entries.forEach { g ->
                    val on = g == goal
                    AppCard(Modifier.fillMaxWidth().then(if (on) Modifier.border(1.5.dp, Ember, MaterialTheme.shapes.medium) else Modifier),
                        onClick = { goal = g }, container = if (on) EmberTint else Panel, padding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(g.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(g.blurb, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            }
                            if (on) Icon(AppIcons.Check, contentDescription = null, tint = Ember, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            SectionGap(30)
            SectionTitle("Favourite exercise")
            Text("The one the big button starts. Change it any time.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            SectionGap(10)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(popular, key = { it.id }) { ex ->
                    FilterChip(
                        selected = ex.id == exercise,
                        onClick = { exercise = ex.id },
                        label = { Text(ex.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Panel, labelColor = InkMuted, selectedContainerColor = Ember, selectedLabelColor = Ink,
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = ex.id == exercise, borderColor = Rule, selectedBorderColor = Ember),
                    )
                }
            }

            SectionGap(32)
            PrimaryButton(if (saving) "Saving…" else "Let's go", enabled = !saving) {
                saving = true
                scope.launch {
                    graph.prefs.completeOnboarding(goal, exercise, color)
                    onDone()
                }
            }
        }
    }
}
