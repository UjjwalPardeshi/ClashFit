package com.clashfit.ui.screens.about

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.BuildConfig
import com.clashfit.core.model.GameMode
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.screens.picker.FEATURED_EXERCISES
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import java.io.File

/**
 * What this app is, who made it, what it runs on and what it is built from. Every number here is
 * read from the build or from the running app, so the screen cannot drift out of date.
 */
@Composable
fun AboutScreen(graph: AppGraph, nav: NavHostController, onPrivacy: () -> Unit, onHelp: () -> Unit) {
    val context = LocalContext.current
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val modelInstalled = remember { graph.llmEngine.modelInstalled }

    ScreenScaffold(title = "About", onBack = { nav.navigateUp() }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Ember), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(40.dp))
                }
                Text("ClashFit", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }

            SectionGap(24)
            AppCard(Modifier.fillMaxWidth(), padding = 18) {
                Text(
                    "Your body is the controller and your camera is the referee. Every rep is measured for " +
                        "depth, range, tempo and alignment, and a clean rep does more damage than a sloppy one. " +
                        "The pose model and the coach both run on this phone.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                )
            }

            SectionGap(24)
            SectionTitle("What's inside")
            SectionGap(10)
            ListGroup {
                RuleRow("Game modes", "${GameMode.grid.size}")
                InnerDivider()
                RuleRow("Exercises", "${exercises.values.count { it.id in FEATURED_EXERCISES }}")
                InnerDivider()
                RuleRow("Coach", if (modelInstalled) "Runs on this phone" else "Built-in coaching")
                InnerDivider()
                RuleRow("Your account", if (graph.auth.isCloud) "Syncs across devices" else "Stays on this phone")
            }

            SectionGap(24)
            SectionTitle("Help and privacy")
            SectionGap(10)
            ListGroup {
                NavRow("How to play", onHelp, icon = AppIcons.Bolt, supporting = "What the camera needs, and how a fight works")
                InnerDivider()
                NavRow("Privacy", onPrivacy, icon = AppIcons.Shield, supporting = "What stays on the phone, and what does not")
                InnerDivider()
                NavRow(
                    "Open the website",
                    {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://clash-fit.vercel.app/".toUri()))
                        }
                    },
                    icon = AppIcons.Grid,
                    supporting = "clash-fit.vercel.app",
                )
            }

            SectionGap(24)
            SectionTitle("Made by")
            SectionGap(10)
            ListGroup {
                RuleRow("Team", "Da Goats")
                InnerDivider()
                RuleRow("Omkar Kadam", "Engineering")
                InnerDivider()
                RuleRow("Ujjwal Pardeshi", "Engineering")
                InnerDivider()
                RuleRow("For", "iQOO Hackathon 2026 · Pune")
            }

            SectionGap(24)
            SectionTitle("Built with")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIBRARIES.forEach { (name, what) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                            Text(what, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                    Text(
                        "Each is used under its own licence. Apache 2.0 unless the project states otherwise.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SectionGap(24)
            Text(
                "Not a medical device. Nothing here diagnoses, treats or prevents anything.",
                style = MaterialTheme.typography.bodySmall, color = InkFaint,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val LIBRARIES = listOf(
    "Jetpack Compose" to "the whole interface",
    "MediaPipe Tasks" to "pose landmarks, on-device",
    "MediaPipe GenAI" to "the coach, on-device",
    "CameraX" to "the camera",
    "Room" to "every session, locally",
    "DataStore" to "preferences",
    "Nearby Connections" to "phone-to-phone play",
    "Firebase Auth and Firestore" to "accounts and leaderboards",
)

fun NavGraphBuilder.aboutRoutes(graph: AppGraph, nav: NavHostController, onPrivacy: () -> Unit, onHelp: () -> Unit) {
    composable<com.clashfit.ui.nav.About> { AboutScreen(graph, nav, onPrivacy, onHelp) }
}
