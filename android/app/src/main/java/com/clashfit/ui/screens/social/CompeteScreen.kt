package com.clashfit.ui.screens.social

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.auth.AuthState
import com.clashfit.cloud.Board
import com.clashfit.cloud.CloudAvailability
import com.clashfit.meta.AchievementCatalog
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.NavRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.nav.Achievements
import com.clashfit.ui.nav.Challenge
import com.clashfit.ui.nav.Friends
import com.clashfit.ui.nav.Leaderboard
import com.clashfit.ui.nav.Weekly
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success
import com.clashfit.ui.components.RankInsignia

/**
 * Everything to do with other people, in one place.
 *
 * These five screens used to live halfway down the profile page, under the stats and above the
 * app settings, which put "race your friends" in the same list as "privacy policy". Competing is
 * a whole half of the game and it gets its own door.
 *
 * The hub shows the state of each thing rather than only its name: where you sit this week, how
 * far the weekly target has got, how many badges are left. A row that says only "Badges" makes
 * you open it to learn anything.
 */
@Composable
fun CompeteScreen(graph: AppGraph, nav: NavHostController) {
    val meta by graph.meta.state.collectAsStateWithLifecycle(initialValue = null)
    val auth by graph.auth.state.collectAsStateWithLifecycle()
    val signedIn = auth is AuthState.SignedIn

    val boardFlow = remember(graph) { graph.leaderboard.top(Board.WEEKLY_DAMAGE, limit = 3) }
    val top by boardFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val availability by graph.leaderboard.availability
        .collectAsStateWithLifecycle(initialValue = CloudAvailability.Available)

    val friendsFlow = remember(graph) { graph.friends.friends }
    val friends by friendsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val badges = meta?.unlocked?.size ?: 0
    val weekly = meta?.weekly

    ScreenScaffold(title = "Compete") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            // This week, live. The top of the board is the reason to come here, so it is the
            // first thing on the page rather than a row you have to open.
            AppCard(Modifier.fillMaxWidth(), padding = 18, onClick = { nav.navigate(Leaderboard) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("This week", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text("Damage", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                    }
                    when {
                        !signedIn -> Text(
                            "Sign in and your week appears here, next to everyone else's.",
                            style = MaterialTheme.typography.bodySmall, color = InkFaint,
                        )
                        availability is CloudAvailability.Unavailable -> Text(
                            (availability as CloudAvailability.Unavailable).reason,
                            style = MaterialTheme.typography.bodySmall, color = InkFaint,
                        )
                        top.isEmpty() -> Text(
                            "Nobody has scored this week yet. Finish a fight and the board starts with you.",
                            style = MaterialTheme.typography.bodySmall, color = InkFaint,
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            top.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        RankInsignia(entry.level, size = 26)
                                        Text(
                                            "${entry.rank}   ${entry.displayName}${if (entry.isMe) "  ·  you" else ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (entry.isMe) Ember else Ink,
                                        )
                                    }
                                    Text(
                                        "${entry.value}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (entry.isMe) Ember else InkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionGap(16)

            // The weekly target, with its bar, because a number without a bar is homework.
            weekly?.let { w ->
                AppCard(Modifier.fillMaxWidth(), padding = 18, onClick = { nav.navigate(Weekly) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Weekly challenge", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                            Text(
                                if (w.done) "Done" else "${w.value} / ${w.challenge.target}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (w.done) Success else Ember,
                            )
                        }
                        Text(w.challenge.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                        Bar(w.fraction, color = if (w.done) Success else Ember, height = 8)
                        Text(
                            w.challenge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkFaint,
                        )
                    }
                }
                SectionGap(20)
            }

            SectionTitle("People")
            SectionGap(10)
            ListGroup {
                NavRow(
                    "Leaderboard",
                    { nav.navigate(Leaderboard) },
                    icon = AppIcons.Trophy,
                    supporting = "Four boards: this week, all time, and both by friends",
                )
                InnerDivider()
                NavRow(
                    "Friends",
                    { nav.navigate(Friends) },
                    icon = AppIcons.People,
                    value = if (friends.isEmpty()) "" else "${friends.size}",
                    supporting = if (friends.isEmpty()) "Add by code. No email, no phone number." else "Race them on the friends board",
                )
                InnerDivider()
                NavRow(
                    "Challenge codes",
                    { nav.navigate(Challenge) },
                    icon = AppIcons.Bolt,
                    supporting = "A dare, sent as text. Works with no server and no account.",
                )
            }

            SectionGap(20)
            SectionTitle("Earned")
            SectionGap(10)
            ListGroup {
                NavRow(
                    "Badges",
                    { nav.navigate(Achievements) },
                    icon = AppIcons.Star,
                    tint = Brass,
                    value = "$badges of ${AchievementCatalog.all.size}",
                    supporting = "From showing up and from clean reps. None can be bought.",
                )
            }

            SectionGap(16)
            Text(
                "Only a display name, a level, a streak and a score ever leave this phone. " +
                    "Never a rep, never a frame, never your email address.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )
        }
    }
}

fun NavGraphBuilder.competeRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Compete> { CompeteScreen(graph, nav) }
}
