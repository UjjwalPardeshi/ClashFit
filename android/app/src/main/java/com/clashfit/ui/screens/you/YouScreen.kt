package com.clashfit.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.nav.Alarms
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.nav.Challenge
import com.clashfit.ui.nav.Clinic
import com.clashfit.ui.nav.Desk
import com.clashfit.ui.nav.Posture
import com.clashfit.ui.nav.Preflight
import com.clashfit.ui.nav.Privacy
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.Settings
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel

/**
 * The You tab: who you are, the tools that are not a game, and the app's settings. Until an
 * account exists this shows a local profile; sign-in lands here when it arrives.
 */
@Composable
fun YouScreen(graph: AppGraph, nav: NavHostController) {
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val countFlow = remember(graph) { graph.db.sessions().count() }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessionCount by countFlow.collectAsStateWithLifecycle(initialValue = 0)

    ScreenScaffold(title = "You") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ProfileHeader(
                name = "Athlete",
                line = "$sessionCount sessions · ${streak?.current ?: 0} day streak",
            )

            SectionGap(28)
            Kicker("Health tools")
            SectionGap(4)
            NavRow("Run tracker", { nav.navigate(RunHome) }, supporting = "The route never leaves your device")
            NavRow("Wake-up alarm", { nav.navigate(Alarms) }, supporting = "Stops when the reps stop it")
            NavRow("Desk timer", { nav.navigate(Desk) }, supporting = "Sixty seconds, every fifty minutes")
            NavRow("Posture", { nav.navigate(Posture) }, supporting = "A frame every few minutes, read and discarded")
            NavRow("Breathing", { nav.navigate(Breathing) }, supporting = "Verified from your chest, not assumed")
            NavRow("Clinic", { nav.navigate(Clinic) }, supporting = "The tests a physio uses")

            SectionGap(28)
            Kicker("Play with others")
            SectionGap(4)
            NavRow("Challenge codes", { nav.navigate(Challenge) }, supporting = "A dare, shared without a server")

            SectionGap(28)
            Kicker("App")
            SectionGap(4)
            NavRow("Camera check", { nav.navigate(Preflight) }, supporting = "Is the referee ready")
            NavRow("Settings", { nav.navigate(Settings) })
            NavRow("Privacy", { nav.navigate(Privacy) }, supporting = "What stays on the phone, and what does not")

            SectionGap(32)
        }
    }
}

@Composable
private fun ProfileHeader(name: String, line: String) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(Ember), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Person, contentDescription = null, tint = Ground, modifier = Modifier.size(30.dp))
        }
        Column {
            Text(name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(line, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}
