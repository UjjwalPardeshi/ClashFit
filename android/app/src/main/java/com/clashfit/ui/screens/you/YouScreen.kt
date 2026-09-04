package com.clashfit.ui.screens.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.clashfit.auth.AuthState
import com.clashfit.data.Prefs
import com.clashfit.meta.AchievementCatalog
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Avatar
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.BarAction
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.LevelRing
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.XpBar
import com.clashfit.ui.nav.About
import com.clashfit.ui.nav.Account
import com.clashfit.ui.nav.Achievements
import com.clashfit.ui.nav.Alarms
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.nav.Challenge
import com.clashfit.ui.nav.Clinic
import com.clashfit.ui.nav.Desk
import com.clashfit.ui.nav.Friends
import com.clashfit.ui.nav.Help
import com.clashfit.ui.nav.Leaderboard
import com.clashfit.ui.nav.Posture
import com.clashfit.ui.nav.Preflight
import com.clashfit.ui.nav.Privacy
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.Settings
import com.clashfit.ui.nav.Weekly
import com.clashfit.ui.theme.AvatarPalette
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success

/** The You tab: who you are, how far you have come, who you play with, and the tools. */
@Composable
fun YouScreen(graph: AppGraph, nav: NavHostController) {
    val auth by graph.auth.state.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val metaFlow = remember(graph) { graph.meta.state }
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val countFlow = remember(graph) { graph.db.sessions().count() }
    val meta by metaFlow.collectAsStateWithLifecycle(initialValue = null)
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessionCount by countFlow.collectAsStateWithLifecycle(initialValue = 0)

    val name = (auth as? AuthState.SignedIn)?.user?.displayName ?: "Athlete"
    val badges = meta?.unlocked?.size ?: 0

    ScreenScaffold(title = "You", actions = { BarAction(AppIcons.Gear, "Settings") { nav.navigate(Settings) } }) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Account) }, padding = 18) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(name, size = 64, color = AvatarPalette.at(settings.avatarColor))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge, color = Ink)
                            Text(
                                meta?.progress?.let { "Level ${it.level} · ${it.title}" } ?: "Level 1 · Recruit",
                                style = MaterialTheme.typography.bodyMedium, color = Brass,
                            )
                        }
                        meta?.progress?.let { LevelRing(it, size = 56) }
                    }
                    Spacer(Modifier.height(16.dp))
                    meta?.progress?.let { XpBar(it) }
                }
            }

            SectionGap(18)
            StatStrip(listOf("${streak?.current ?: 0}" to "Day streak", "$sessionCount" to "Sessions", "$badges" to "Badges"))

            meta?.weekly?.let { w ->
                SectionGap(20)
                AppCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Weekly) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Weekly challenge", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                                Text(w.challenge.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                            }
                            Text(
                                if (w.done) "Done" else "${w.value} / ${w.challenge.target}",
                                style = MaterialTheme.typography.titleSmall, color = if (w.done) Success else Ink,
                            )
                        }
                        Bar(w.fraction, Modifier.padding(top = 12.dp), color = if (w.done) Success else MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionGap(26)
            SectionTitle("Compete")
            SectionGap(10)
            ListGroup {
                NavRow("Leaderboard", { nav.navigate(Leaderboard) }, icon = AppIcons.Trophy, supporting = "This week and all time, everyone and friends")
                InnerDivider()
                NavRow("Friends", { nav.navigate(Friends) }, icon = AppIcons.People, supporting = "Add by code, race on the friends board")
                InnerDivider()
                NavRow("Badges", { nav.navigate(Achievements) }, icon = AppIcons.Star, value = "$badges of ${AchievementCatalog.all.size}")
                InnerDivider()
                NavRow("Challenge codes", { nav.navigate(Challenge) }, icon = AppIcons.Bolt, supporting = "A dare, sent as text")
            }

            SectionGap(26)
            SectionTitle("Health tools")
            SectionGap(10)
            ListGroup {
                NavRow("Run tracker", { nav.navigate(RunHome) }, icon = AppIcons.Run, tint = Success, supporting = "Distance, pace, splits. The route never leaves the phone")
                InnerDivider()
                NavRow("Wake-up alarm", { nav.navigate(Alarms) }, icon = AppIcons.Bell, tint = Success, supporting = "Stops when the reps stop it")
                InnerDivider()
                NavRow("Desk timer", { nav.navigate(Desk) }, icon = AppIcons.Bolt, tint = Success, supporting = "Sixty seconds, every fifty minutes")
                InnerDivider()
                NavRow("Posture", { nav.navigate(Posture) }, icon = AppIcons.Person, tint = Success, supporting = "A frame every few minutes, read and discarded")
                InnerDivider()
                NavRow("Breathing", { nav.navigate(Breathing) }, icon = AppIcons.Heart, tint = Success, supporting = "Verified from your chest, not assumed")
                InnerDivider()
                NavRow("Clinic", { nav.navigate(Clinic) }, icon = AppIcons.Heart, tint = Success, supporting = "The tests a physio uses")
            }

            SectionGap(26)
            SectionTitle("App")
            SectionGap(10)
            ListGroup {
                NavRow("Account", { nav.navigate(Account) }, icon = AppIcons.Person, tint = InkMuted)
                InnerDivider()
                NavRow("Camera check", { nav.navigate(Preflight) }, icon = AppIcons.Camera, tint = InkMuted)
                InnerDivider()
                NavRow("Settings", { nav.navigate(Settings) }, icon = AppIcons.Gear, tint = InkMuted)
                InnerDivider()
                NavRow("Privacy", { nav.navigate(Privacy) }, icon = AppIcons.Shield, tint = InkMuted, supporting = "What stays on the phone, and what does not")
                InnerDivider()
                NavRow("How to play", { nav.navigate(Help) }, icon = AppIcons.Bolt, tint = InkMuted, supporting = "Set the phone up, and what the numbers mean")
                InnerDivider()
                NavRow("About", { nav.navigate(About) }, icon = AppIcons.Grid, tint = InkMuted, supporting = "Version, what it is built from, and who made it")
            }
        }
    }
}
