package com.clashfit.ui.screens.privacy

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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Success

/**
 * The privacy contract, in the app, in the same words as the manifest comment and the README.
 * Every line here must stay true of the build it ships in. If a claim stops being true, it is
 * deleted, not softened.
 */
@Composable
fun PrivacyScreen(graph: AppGraph, nav: NavHostController) {
    ScreenScaffold(title = "Privacy", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 18) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconBubble(AppIcons.Shield, size = 48, tint = Success)
                        Text("Your camera never leaves your phone", style = MaterialTheme.typography.titleLarge, color = Ink)
                    }
                    Text(
                        "The pose model and the coach both run on this device. Frames are read, scored and discarded in the same instant. " +
                            "Only your scores, your name and your level are sent anywhere.",
                        style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }

            SectionGap(26)
            SectionTitle("Never leaves this phone")
            SectionGap(10)
            ListGroup {
                Promise(AppIcons.Camera, "Camera frames", "No video, no images. Nothing is recorded or uploaded, in any mode.", Success)
                InnerDivider()
                Promise(AppIcons.Person, "Pose landmarks", "The skeleton the referee reads is computed here and thrown away.", Success)
                InnerDivider()
                Promise(AppIcons.Chart, "Rep timelines", "Depth, range, tempo and fatigue for every rep stay in the database on this phone.", Success)
                InnerDivider()
                Promise(
                    AppIcons.Run, "Run routes",
                    "Your route is stored on this phone and is never uploaded. The streets under it " +
                        "are map tiles fetched from OpenStreetMap as you look at them, which tells " +
                        "their servers roughly where you were. Turn Street maps off in Settings and " +
                        "the route draws on our own grid with the radio off.",
                    Success,
                )
                InnerDivider()
                Promise(AppIcons.Heart, "Posture and breathing", "Sampled, scored, discarded. Never stored as an image.", Success)
            }

            SectionGap(26)
            SectionTitle("Goes to the cloud")
            SectionGap(10)
            ListGroup {
                Promise(AppIcons.Person, "Your account", "Email and display name, so you can sign in on another phone.", Brass)
                InnerDivider()
                Promise(AppIcons.Trophy, "Scores", "Reps, damage, XP, level and streak length. The numbers the leaderboard ranks.", Brass)
                InnerDivider()
                Promise(AppIcons.People, "Friends", "The friend codes you add, so the friends board knows who to show.", Brass)
            }

            SectionGap(26)
            SectionTitle("Never collected at all")
            SectionGap(10)
            ListGroup {
                Promise(AppIcons.Close, "No analytics", "No usage telemetry, no crash beacons, no third-party SDK watching you.", InkMuted)
                InnerDivider()
                Promise(AppIcons.Close, "No ads", "None. There is nothing here to sell.", InkMuted)
                InnerDivider()
                Promise(AppIcons.Close, "No device identifiers", "No advertising ID, no fingerprint, no contact list.", InkMuted)
            }

            SectionGap(26)
            SectionTitle("Permissions, and why")
            SectionGap(10)
            ListGroup {
                Permission("Camera", "Measure your movement. The referee's eye.")
                InnerDivider()
                Permission("Internet", "Sign in, sync scores, read the leaderboard. The camera pipeline never touches it.")
                InnerDivider()
                Permission("Microphone", "Hear the rep impact even with the screen off. Audio is never recorded.")
                InnerDivider()
                Permission("Notifications", "Wake-up alarms and the desk timer.")
                InnerDivider()
                Permission("Location", "Run tracking only. The route stays on the phone.")
                InnerDivider()
                Permission("Bluetooth and Wi-Fi", "Phone-to-phone multiplayer over Nearby. No router, no server, no account needed for a duel.")
            }

            SectionGap(26)
            AppCard(Modifier.fillMaxWidth(), container = PanelLift, padding = 16) {
                Column {
                    Text("Check it yourself", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(
                        "The manifest says exactly this, and the network layer lives in one package. Open cloud/ and read every " +
                            "call the app can make. There is no other one.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBubble(icon, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun Permission(name: String, reason: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = Ink)
        Text(reason, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

fun NavGraphBuilder.privacyRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Privacy> { PrivacyScreen(graph, nav) }
}
