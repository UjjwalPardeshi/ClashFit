package com.clashfit.ui.screens.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.engine.games.Roster as GameRoster
import com.clashfit.engine.games.RosterMode
import com.clashfit.engine.games.RosterPlayer
import com.clashfit.engine.games.RosterState
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.Roster
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.LocalReduceMotion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft

/**
 * One phone, a room full of people. Relay, Last Standing, Team vs Team and Circuit all land here.
 * docs/20-MULTIPLAYER-MODES.md §6
 *
 * Three states, driven entirely by `playHub.roster`: SETUP while it is null, BOARD while it runs,
 * FINISHED once it has a winner. The Session screen reports each turn back to the hub and pops
 * here, so this screen never owns a turn result. System back simply leaves — the roster stays
 * alive, because the user may well be coming back to it.
 */
fun NavGraphBuilder.rosterRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Roster> { entry -> RosterScreen(graph, nav, entry.toRoute<Roster>()) }
}

private const val MIN_PLAYERS = 2
private const val MAX_PLAYERS = 8
private val TURN_CHOICES = listOf(20, 30, 45, 60)

@Composable
private fun RosterScreen(graph: AppGraph, nav: NavHostController, route: Roster) {
    val hub = graph.playHub
    val state by hub.roster.collectAsStateWithLifecycle()
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()

    val mode = remember(route.mode) { runCatching { GameMode.valueOf(route.mode) }.getOrDefault(GameMode.RELAY) }
    val rosterMode = remember(mode) { mode.toRosterMode() }
    val teamed = mode == GameMode.RELAY || mode == GameMode.TEAM_VS_TEAM
    // The modes screen sends a blank exercise for group modes; fall back to whatever we have.
    val exerciseId = remember(route.exerciseId, exercises) {
        route.exerciseId.takeIf { it.isNotBlank() }
            ?: exercises.keys.firstOrNull { it == "squat" } ?: exercises.keys.firstOrNull() ?: "squat"
    }
    val exerciseName = exercises[exerciseId]?.name ?: exerciseId.uppercase()

    val names = rememberNames("roster-names", "PLAYER 1", "PLAYER 2")
    val teams = rememberNames("roster-teams", "A", "B")
    var turnSec by rememberSaveable { mutableIntStateOf(30) }

    Column(
        Modifier.fillMaxSize().background(Ground).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val snapshot = state
        when {
            snapshot == null -> Setup(
                mode = mode, exerciseName = exerciseName, teamed = teamed,
                names = names, teams = teams, turnSec = turnSec, onTurnSec = { turnSec = it },
                onStart = {
                    val entered = names.indices
                        .map { i -> names[i].trim() to teams.getOrElse(i) { "A" } }
                        .filter { it.first.isNotBlank() }
                    hub.startRoster(
                        names = entered.map { it.first },
                        opts = GameRoster.RosterOptions(
                            mode = rosterMode,
                            turnSec = turnSec,
                            teams = if (teamed) entered.map { it.second } else null,
                        ),
                        exerciseId = exerciseId,
                        mode = mode,
                    )
                },
            )

            snapshot.finished -> Finished(
                state = snapshot,
                onAgain = {
                    reseed(names, teams, snapshot.players)
                    hub.clearRoster()
                },
                onHome = {
                    hub.clearRoster()
                    nav.popBackStack(Home, inclusive = false)
                },
            )

            else -> Board(
                state = snapshot,
                exerciseName = exerciseName,
                modeTitle = mode.title,
                // Last Standing is decided by measured fatigue, never by a clock — so no cap.
                turnCapSec = if (rosterMode == RosterMode.LAST_STANDING) null else hub.rosterTurnSec(),
                onStartTurn = { cap ->
                    hub.rosterTurnStarted()
                    nav.navigate(Session(mode = route.mode, exerciseId = exerciseId, durationSec = cap))
                },
                onSkip = { hub.rosterSkip("SKIPPED") },
                onEnd = { hub.rosterFinish() },
            )
        }
        SectionGap(24)
    }
}

// ------------------------------------------------------------------ setup

@Composable
private fun Setup(
    mode: GameMode, exerciseName: String, teamed: Boolean,
    names: SnapshotStateList<String>, teams: SnapshotStateList<String>,
    turnSec: Int, onTurnSec: (Int) -> Unit, onStart: () -> Unit,
) {
    Kicker("Pass the phone")
    SectionGap(12)
    Headline("ONE PHONE", accent = mode.title)
    SectionGap(10)
    Text("$exerciseName · ${mode.blurb}", style = MaterialTheme.typography.bodySmall, color = InkFaint)
    SectionGap(28)

    Kicker("Players · ${names.size}")
    SectionGap(12)
    names.forEachIndexed { i, name ->
        NameRow(
            index = i, name = name, team = teams.getOrElse(i) { "A" }, teamed = teamed,
            removable = names.size > MIN_PLAYERS,
            onName = { names[i] = it }, onTeam = { if (i < teams.size) teams[i] = it },
            onRemove = {
                names.removeAt(i)
                if (i < teams.size) teams.removeAt(i)
            },
        )
        Spacer(Modifier.height(10.dp))
    }
    if (names.size < MAX_PLAYERS) {
        OutlineButton("+ Add player", Modifier.fillMaxWidth()) {
            names.add("PLAYER ${names.size + 1}")
            teams.add(if (names.size % 2 == 1) "A" else "B")
        }
    }
    SectionGap(28)

    Kicker("Turn length")
    SectionGap(12)
    TurnChips(turnSec, onTurnSec)
    SectionGap(28)

    val ready = names.count { it.isNotBlank() } >= MIN_PLAYERS
    EmberButton("Start", Modifier.fillMaxWidth(), enabled = ready, onClick = onStart)
    if (!ready) {
        SectionGap(10)
        Text("Two names, minimum.", style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
private fun NameRow(
    index: Int, name: String, team: String, teamed: Boolean, removable: Boolean,
    onName: (String) -> Unit, onTeam: (String) -> Unit, onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.weight(1f),
            shape = RectangleShape,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("PLAYER ${index + 1}", style = MaterialTheme.typography.bodyLarge, color = InkFaint) },
        )
        if (teamed) TeamToggle(team, onTeam)
        if (removable) {
            Text(
                "×", Modifier.border(1.dp, Rule).clickable(onClick = onRemove).padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge, color = InkMuted,
            )
        }
    }
}

@Composable
private fun TeamToggle(team: String, onTeam: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("A", "B").forEach { t ->
            val on = t == team
            Text(
                t, Modifier.background(if (on) Ember else Panel).border(1.dp, if (on) Ember else Rule)
                    .clickable { onTeam(t) }.padding(horizontal = 12.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge, color = if (on) Ground else InkMuted,
            )
        }
    }
}

@Composable
private fun TurnChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TURN_CHOICES.forEach { sec ->
            val on = sec == selected
            Box(
                Modifier.weight(1f).background(if (on) Ember else Panel).border(1.dp, if (on) Ember else Rule)
                    .clickable { onSelect(sec) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${sec}S", style = MaterialTheme.typography.labelLarge, color = if (on) Ground else InkMuted)
            }
        }
    }
}

// ------------------------------------------------------------------ board

@Composable
private fun Board(
    state: RosterState, exerciseName: String, modeTitle: String, turnCapSec: Int?,
    onStartTurn: (Int?) -> Unit, onSkip: () -> Unit, onEnd: () -> Unit,
) {
    val current = state.current
    Kicker("Hand the phone to")
    SectionGap(12)
    if (current == null) {
        EmptyState("NOBODY LEFT", "Everyone is out. End the game to crown the winner.")
    } else {
        Text(current.name.uppercase(), style = bigName(current.name), color = Ink)
        val team = current.team
        if (team != null) {
            SectionGap(10)
            Tag("Team $team", color = Ember)
        }
    }
    SectionGap(14)
    Text("$exerciseName · ${modeTitle.uppercase()}", style = MaterialTheme.typography.labelSmall, color = InkFaint)
    SectionGap(20)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(turnCapSec?.let { "${it}S" } ?: "∞", "Turn", Modifier.weight(1f), color = Ember)
        StatTile("${state.standing}", "Standing", Modifier.weight(1f))
        StatTile("${current?.turns ?: 0}", "Turns taken", Modifier.weight(1f))
    }
    SectionGap(28)

    Leaderboard(state.players)
    SectionGap(28)

    val cta = if (current != null) "Start ${current.name}'s turn" else "No one is up"
    EmberButton(cta, Modifier.fillMaxWidth(), enabled = current != null) { onStartTurn(turnCapSec) }
    SectionGap(10)
    OutlineButton("Skip", Modifier.fillMaxWidth(), onClick = onSkip)
    SectionGap(18)
    Text(
        "END GAME", Modifier.fillMaxWidth().clickable(onClick = onEnd).padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium, color = InkFaint, textAlign = TextAlign.Center,
    )
}

// ------------------------------------------------------------------ finished

@Composable
private fun Finished(state: RosterState, onAgain: () -> Unit, onHome: () -> Unit) {
    val winner = state.winner
    Kicker("Winner")
    SectionGap(12)
    if (winner == null) {
        EmptyState("NO WINNER", "Nobody put a rep on the board.")
    } else {
        Text(winner.name.uppercase(), style = bigName(winner.name), color = Ember)
        val team = winner.team
        if (team != null) {
            SectionGap(10)
            Tag("Team $team", color = Ember)
        }
        SectionGap(20)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${winner.damage}", "Damage", Modifier.weight(1f), color = Ember)
            StatTile("${winner.reps}", "Reps", Modifier.weight(1f))
            StatTile("${winner.turns}", "Turns", Modifier.weight(1f))
        }
    }
    SectionGap(28)

    Leaderboard(state.players)
    SectionGap(28)

    EmberButton("Again", Modifier.fillMaxWidth(), onClick = onAgain)
    SectionGap(10)
    OutlineButton("Home", Modifier.fillMaxWidth(), onClick = onHome)
}

// ------------------------------------------------------------------ shared

/** Out last, then damage, then reps — the order the engine itself ranks by. */
@Composable
private fun Leaderboard(players: List<RosterPlayer>, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val ranked = remember(players) {
        players.sortedWith(compareBy<RosterPlayer> { it.out }.thenByDescending { it.damage }.thenByDescending { it.reps })
    }
    Column(modifier.fillMaxWidth()) {
        Kicker("Standings")
        SectionGap(12)
        PanelBox(Modifier.fillMaxWidth(), padding = 4) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(ranked, key = { _, p -> p.id }) { i, p ->
                    LeaderRow(i + 1, p, if (reduce) Modifier else Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun LeaderRow(rank: Int, player: RosterPlayer, modifier: Modifier = Modifier) {
    val dim = player.out
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$rank", Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall, color = if (dim) InkFaint else Ember)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(player.name.uppercase(), style = MaterialTheme.typography.titleSmall, color = if (dim) InkFaint else Ink, maxLines = 1)
                val reason = player.outReason
                if (dim && reason != null) {
                    Text(reason.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
            }
            val team = player.team
            if (team != null) Tag(team, color = if (dim) InkMuted else Brass)
            Text("${player.damage}", style = MaterialTheme.typography.headlineSmall, color = if (dim) InkFaint else Ink)
            Text(
                "${player.reps} REPS", Modifier.width(60.dp),
                style = MaterialTheme.typography.labelSmall, color = InkFaint, textAlign = TextAlign.End,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RuleSoft))
    }
}

/** The name is the loudest thing on the board, but it still has to fit on one line. */
@Composable
private fun bigName(name: String): TextStyle = when {
    name.length <= 6 -> MaterialTheme.typography.displayLarge
    name.length <= 10 -> MaterialTheme.typography.displayMedium
    else -> MaterialTheme.typography.displaySmall
}

private fun GameMode.toRosterMode(): RosterMode = when (this) {
    GameMode.RELAY, GameMode.TEAM_VS_TEAM -> RosterMode.RELAY
    GameMode.LAST_STANDING -> RosterMode.LAST_STANDING
    else -> RosterMode.PASS_THE_PHONE
}

/** AGAIN keeps the room: the finished line-up becomes the new setup form. */
private fun reseed(names: SnapshotStateList<String>, teams: SnapshotStateList<String>, players: List<RosterPlayer>) {
    if (players.size < MIN_PLAYERS) return
    names.clear()
    names.addAll(players.map { it.name })
    teams.clear()
    teams.addAll(players.map { it.team ?: "A" })
}

/** Survives rotation and process death; a room's names are worth keeping. */
@Composable
private fun rememberNames(key: String, vararg initial: String): SnapshotStateList<String> =
    rememberSaveable(
        key = key,
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { initial.toList().toMutableStateList() }
