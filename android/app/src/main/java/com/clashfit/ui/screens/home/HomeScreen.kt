package com.clashfit.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.ModeKind
import com.clashfit.data.Prefs
import com.clashfit.ui.screens.picker.FEATURED_EXERCISES
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.ModeCard
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.StatStrip
import com.clashfit.ui.components.label
import com.clashfit.ui.nav.ExercisePicker
import com.clashfit.ui.screens.modes.modeDestination
import com.clashfit.ui.nav.Modes
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.clashfit.data.SessionEntity
import com.clashfit.ui.theme.Success
import com.clashfit.ui.nav.RunHome
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.Posture
import com.clashfit.ui.nav.Desk
import com.clashfit.ui.nav.Clinic
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.nav.Alarms
import com.clashfit.ui.components.AppCard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import com.clashfit.ui.nav.Library
import com.clashfit.ui.theme.InkFaint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.clashfit.ui.components.Tag

/**
 * The Train tab. One tap into a fight, then every way to play as carousels grouped by how many
 * people they need. Everything that is not a fight lives on the other tabs. docs/02-APP-FLOW.md §2
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 60) }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val preferredId = settings.preferredExercise
    val preferredName = exercises[preferredId]?.name ?: preferredId.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val zone = remember { ZoneId.systemDefault() }
    val thisWeek = remember(sessions) {
        val weekAgo = LocalDate.now(zone).minusDays(6)
        sessions.count { !Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate().isBefore(weekAgo) }
    }

    ScreenScaffold(title = "ClashFit") { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
            val reason = remember(sessions, preferredId, zone) { todayLine(sessions, preferredId, zone) }
            HeroCard(
                streak = streak?.current ?: 0,
                reason = reason,
                onFight = { nav.navigate(ExercisePicker(GameMode.BOSS_FIGHT.name)) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            SectionGap(20)
            StatStrip(
                listOf(
                    "${streak?.current ?: 0}" to "Day streak",
                    "$thisWeek" to "This week",
                    "${streak?.best ?: 0}" to "Best streak",
                ),
                Modifier.padding(horizontal = 20.dp),
            )

            SectionGap(22)
            // Library lost its tab. It needs a door people can actually find, on the tab where
            // choosing what to train already happens.
            AppCard(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                padding = 16,
                onClick = { nav.navigate(Library) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Ember.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Grid, contentDescription = null, tint = Ember, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Exercise library", style = MaterialTheme.typography.titleSmall, color = Ink)
                        // One line. It wrapped to two and still said less than the count does.
                        Text(
                            "${exercises.values.count { it.id in FEATURED_EXERCISES }} movements",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 1,
                        )
                    }
                    // A row that opens something should look like it opens something.
                    Icon(
                        AppIcons.Chevron, contentDescription = null, tint = InkFaint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            SectionGap(28)
            SectionTitle("Ways to play", Modifier.padding(horizontal = 20.dp), action = "All modes") { nav.navigate(Modes) }

            for (kind in listOf(ModeKind.SOLO, ModeKind.VERSUS, ModeKind.GROUP, ModeKind.FAMILY, ModeKind.CLINIC)) {
                val modes = GameMode.grid.filter { it.kind == kind }
                if (modes.isEmpty()) continue
                Text(
                    kind.label(), style = MaterialTheme.typography.titleSmall, color = InkMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(modes, key = { it.name }) { mode ->
                        ModeCard(mode, onClick = { nav.navigate(modeDestination(mode)) }, width = 210)
                    }
                }
            }

            // The health tools, on the tab where training lives rather than three levels down a
            // profile page. Four of these never involve a boss at all, and that is the whole
            // argument that this is more than a game.
            Text(
                "Health", style = MaterialTheme.typography.titleSmall, color = InkMuted,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(HEALTH_TOOLS, key = { it.title }) { tool ->
                    ToolCard(tool) { nav.navigate(tool.route) }
                }
            }
        }
    }
}

/** One health tool on the Train tab. The hook is a card's worth; the blurb is a screen's. */
private data class Tool(val title: String, val hook: String, val icon: ImageVector, val route: Route)

private val HEALTH_TOOLS = listOf(
    Tool("Outdoors", "Run, walk or be chased", AppIcons.Run, RunHome),
    Tool("Breathing", "Pull the band back", AppIcons.Heart, Breathing),
    Tool("Clinic", "The sit-to-stand test", AppIcons.Heart, Clinic),
    Tool("Desk timer", "A minute every fifty", AppIcons.Bolt, Desk),
    Tool("Posture", "How you stand, scored", AppIcons.Person, Posture),
    Tool("Wake-up alarm", "Reps stop the ringing", AppIcons.Bell, Alarms),
)

@Composable
private fun ToolCard(tool: Tool, onClick: () -> Unit) {
    // The same tile as a mode, on the same page, at the same width. These sat directly beneath
    // sixteen mode tiles carrying three lines of prose each, which made the bottom of Train look
    // like a different app had been pasted onto the end of it.
    AppCard(Modifier.width(210.dp).heightIn(min = 148.dp), padding = 16, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Success.copy(alpha = 0.20f), Success.copy(alpha = 0.07f)),
                        ),
                    )
                    .border(1.dp, Success.copy(alpha = 0.28f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tool.icon, contentDescription = null, tint = Success, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                Text(tool.hook, style = MaterialTheme.typography.labelMedium, color = InkMuted, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Tag("Health", color = Success)
        }
    }
}

/** The one thing on the tab that asks for a tap. */
@Composable
private fun HeroCard(
    streak: Int,
    reason: String,
    onFight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Ember, EmberDeep)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Ground.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(16.dp))
            }
            Text(
                if (streak > 0) "Day $streak. Keep it." else "Your body is the controller.",
                style = MaterialTheme.typography.labelLarge, color = Ground,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("READY\nTO FIGHT?", style = MaterialTheme.typography.headlineLarge, color = Ground)
        // One line under the headline, and it is the reason rather than an instruction. The
        // button already says what happens next, so "pick a movement, then fight" was a caption
        // for a button sitting directly beneath it.
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium, color = Ground.copy(alpha = 0.92f),
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onFight,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Ground, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start a fight", style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * One line on the Today card explaining why this session, drawn from what the app measured.
 *
 * Deliberately never encouragement for its own sake. Every branch cites something the camera
 * recorded, because an app that invents enthusiasm is indistinguishable from one that is not
 * measuring anything.
 */
private fun todayLine(sessions: List<SessionEntity>, exerciseId: String, zone: ZoneId): String {
    if (sessions.isEmpty()) {
        return "Your first set writes the line everything after it is measured against."
    }
    val today = LocalDate.now(zone)
    fun dayOf(s: SessionEntity) = Instant.ofEpochMilli(s.startedAtMs).atZone(zone).toLocalDate()

    val todays = sessions.filter { dayOf(it) == today }
    if (todays.isNotEmpty()) {
        return "${todays.sumOf { it.totalReps }} reps banked today. Another set is a bonus, not a debt."
    }

    val mine = sessions.filter { it.exerciseId == exerciseId }
    if (mine.isNotEmpty()) {
        val last = mine.first()
        val days = ChronoUnit.DAYS.between(dayOf(last), today)
        val form = (last.formMean * 100).toInt()
        val best = mine.maxOf { it.totalReps }
        return when {
            days <= 1L -> "Last time: ${last.totalReps} reps at $form% clean. Match it."
            days <= 7L -> "$days days since the last one. Your best here is $best reps."
            else -> "It has been $days days. Start easy; the referee does not mind."
        }
    }
    return "You have not tried this movement yet. The first set sets its baseline."
}
