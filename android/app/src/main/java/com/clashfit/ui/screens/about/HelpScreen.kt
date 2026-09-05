package com.clashfit.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Ok
import com.clashfit.ui.theme.Shallow
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working

/**
 * How to play, for someone who has never seen the app. Written so it can be read once, standing
 * up, before a first fight. No jargon that the app does not explain on the same line.
 */
@Composable
fun HelpScreen(graph: AppGraph, nav: NavHostController, onStart: () -> Unit) {
    ScreenScaffold(title = "How to play", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                "You exercise. The camera counts and grades every rep. A better rep does more damage. " +
                    "That is the whole game.",
                style = MaterialTheme.typography.bodyLarge, color = Ink,
            )

            SectionGap(24)
            SectionTitle("Set the phone up")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth(), padding = 6) {
                Column {
                    Step(1, "Stand the phone against something", "Two to three metres away, at about knee or hip height. Lean it on a wall, a bottle or a bag.")
                    Step(2, "Get your whole body in frame", "The app tells you if you are too close or too far. It will not start until it can see you.")
                    Step(3, "Give it a moment to calibrate", "It watches your first movement to learn your range, so it grades you against you, not against an average.")
                }
            }

            SectionGap(24)
            SectionTitle("Control it from where you stand")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth(), padding = 6) {
                Column {
                    Meaning(AppIcons.Person, Ember, "Open palm — pause", "Hold your hand up flat to the camera for half a second. Again to resume.")
                    Meaning(AppIcons.Check, Success, "Thumb up — set done", "Closes the set and starts the next one straight away. The coach talks over it; you never leave the fight.")
                    Text(
                        "The camera reads your hand the same way it reads your body — on the phone. A ring fills while you hold, so you can let go if it is the wrong shape.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(12.dp),
                    )
                }
            }

            SectionGap(24)
            SectionTitle("What the numbers mean")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth(), padding = 6) {
                Column {
                    Meaning(AppIcons.Check, Clean, "Clean", "Full depth, controlled tempo, aligned. Worth the most damage.")
                    Meaning(AppIcons.Bolt, Ok, "Ok", "It counted, but something slipped. Depth, speed or alignment.")
                    Meaning(AppIcons.Close, Shallow, "Shallow", "Counted, barely. Half-reps never count at all.")
                }
            }

            SectionGap(20)
            SectionTitle("Fatigue is measured, not guessed")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth(), padding = 6) {
                Column {
                    Meaning(AppIcons.Heart, Fresh, "Fresh", "You are barely warm. The boss shrugs it off.")
                    Meaning(AppIcons.Heart, Working, "Working", "The pace is honest. This is where most of a set lives.")
                    Meaning(AppIcons.Heart, Heavy, "Fading", "Your reps are slowing. The boss opens up. Push here.")
                    Meaning(AppIcons.Heart, Gassed, "Gassed", "You are done. It ends the set for you and you still win.")
                    Text(
                        "Fatigue comes from how your own reps change, not from a timer. Being tired never loses you a fight.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(12.dp),
                    )
                }
            }

            SectionGap(20)
            SectionTitle("Things worth knowing")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth(), padding = 6) {
                Column {
                    Meaning(AppIcons.Star, Ember, "A combo builds", "Consecutive clean reps multiply your damage. One sloppy rep does not wipe it out.")
                    Meaning(AppIcons.Trophy, Brass, "The boss has phases", "It changes at 60 percent and again at 25 percent. The last stretch is meant to feel fast.")
                    Meaning(AppIcons.Flame, Success, "Rest days count", "A protected rest day each week keeps your streak. Rest is training.")
                    Meaning(AppIcons.Shield, InkMuted, "Casual mode exists", "In Settings. The boss cannot win, and your sets still count.")
                }
            }

            SectionGap(28)
            PrimaryButton("Start a fight", icon = AppIcons.Bolt, onClick = onStart)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp).semantics { contentDescription = "Step $n. $title. $body" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$n", style = MaterialTheme.typography.headlineSmall, color = Ember)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun Meaning(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp).semantics { contentDescription = "$title. $body" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBubble(icon, tint = tint, size = 36)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

fun NavGraphBuilder.helpRoutes(graph: AppGraph, nav: NavHostController, onStart: () -> Unit) {
    composable<com.clashfit.ui.nav.Help> { HelpScreen(graph, nav, onStart) }
}
