package com.clashfit.ui.screens.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.play.Standing
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.GameMode
import com.clashfit.core.model.LinkState
import com.clashfit.duel.NearbyPermissionGate
import com.clashfit.play.PlayHub
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.screens.picker.featuredRank
import com.clashfit.ui.screens.picker.FEATURED_EXERCISES
import com.clashfit.ui.components.ExerciseChoice
import com.clashfit.data.Prefs
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Avatar
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.IconBubble
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.grouped
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.Tag
import com.clashfit.ui.components.color
import com.clashfit.ui.nav.DuelLobby
import com.clashfit.ui.nav.RaidRoom
import com.clashfit.ui.nav.Session
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.LocalReduceMotion
import com.clashfit.ui.theme.MonoReadout
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working
import kotlinx.coroutines.launch

/**
 * The two nearby lobbies. Both are the same three beats — name yourself, link the phones, start —
 * and every link state is named on screen, never a bare spinner. docs/07-MULTIPLAYER-SPEC.md §7
 */
fun NavGraphBuilder.duelRoutes(graph: AppGraph, nav: NavHostController) {
    composable<DuelLobby> { entry ->
        val route = entry.toRoute<DuelLobby>()
        val mode = runCatching { GameMode.valueOf(route.mode) }.getOrDefault(GameMode.DUEL)
        NearbyPermissionGate {
            DuelLobbyScreen(graph, nav, mode)
        }
    }
    composable<RaidRoom> { entry ->
        val route = entry.toRoute<RaidRoom>()
        NearbyPermissionGate {
            RaidRoomScreen(graph, nav, route.exerciseId.ifBlank { DEFAULT_EXERCISE })
        }
    }
}

/** The raid room can arrive with an empty id from the mode grid; squat is the house default. */
private const val DEFAULT_EXERCISE = "squat"
private val RACE_DURATIONS = listOf(30, 60, 90)

/** Which end of the link this phone is. Null until the player has claimed one. */
private enum class Seat { HOST, GUEST }

// ---------------------------------------------------------------- duel lobby

/**
 * The 1v1 lobby, in three beats: claim a seat, agree on the movement, go.
 *
 * The movement is chosen here rather than before the phones are linked, and only by the host.
 * Picking one first meant each end walked in holding its own answer and the two only reconciled
 * on the START packet — a guest could sit through a whole lobby believing it was about to do
 * squats. Now the host's pick is broadcast the moment it changes, and the guest is shown it
 * read-only, so what is on the two screens is the same thing before anyone presses start.
 */
@Composable
private fun DuelLobbyScreen(graph: AppGraph, nav: NavHostController, mode: GameMode) {
    val hub = graph.playHub
    val scope = rememberCoroutineScope()
    val state by hub.linkState.collectAsStateWithLifecycle()
    val hud by hub.link.collectAsStateWithLifecycle()
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())

    var name by rememberSaveable { mutableStateOf(hub.playerName) }
    var seconds by rememberSaveable { mutableStateOf(60) }
    var seat by rememberSaveable { mutableStateOf<Seat?>(null) }
    var chosen by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var startedHere by remember { mutableStateOf(false) }

    // Only the movements whose angles were measured by hand, and only the families this mode
    // allows — the same set the solo picker offers.
    val choices = remember(exercises, mode) {
        exercises.values
            .filter { it.id in FEATURED_EXERCISES }
            .filter { mode.family == null || it.familyEnum == mode.family }
            .sortedWith(compareBy({ featuredRank(it.id) }, { it.name }))
    }
    val duration = if (mode == GameMode.REP_RACE) seconds else null
    val hosting = seat == Seat.HOST
    // What this phone believes the match is. The host's own pick; on a guest, whatever the host
    // has told it, which is blank until the first SETUP lands.
    val agreed = if (hosting) chosen else hud?.exerciseId.orEmpty()
    val armed = hud != null

    LaunchedEffect(name) { hub.playerName = name.ifBlank { "YOU" } }
    LaunchedEffect(choices, settings.preferredExercise) {
        if (choices.none { it.id == chosen }) {
            chosen = choices.firstOrNull { it.id == settings.preferredExercise }?.id ?: choices.firstOrNull()?.id ?: ""
        }
    }
    // Arm the moment the link opens, not once a second phone arrives: a host advertising on its
    // own that was never armed had no Start button, so one phone could not start a race at all.
    // The host re-arms whenever it changes the movement or the clock, which is what broadcasts
    // the change. The guest arms once, empty, and is filled in by the host's SETUP — re-arming it
    // from here would overwrite the host's answer with a blank every time the link flapped.
    LaunchedEffect(state, seat, chosen, duration) {
        if (state == LinkState.IDLE) return@LaunchedEffect
        when (seat) {
            Seat.HOST -> hub.arm(mode, chosen, duration)
            Seat.GUEST -> if (hub.armedSignal == null) hub.arm(mode, "", null)
            null -> Unit
        }
    }
    LaunchedEffect(Unit) {
        hub.started.collect { signal ->
            startedHere = true
            nav.navigate(
                Session(
                    mode = signal.mode.name,
                    exerciseId = signal.exerciseId.ifBlank { DEFAULT_EXERCISE },
                    durationSec = signal.durationSec,
                ),
            )
        }
    }
    DisposableEffect(Unit) { onDispose { if (!startedHere) hub.release() } }

    Column(
        Modifier.fillMaxSize().background(Ground).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Kicker("1v1 · Nearby")
        SectionGap(12)
        Headline("VERSUS", accent = mode.title)
        SectionGap(10)
        Text(mode.blurb, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        SectionGap(8)
        Text(
            matchLabel(seat, agreed, exercises[agreed]?.name),
            style = MaterialTheme.typography.labelMedium,
            color = InkFaint,
        )

        SectionGap(24)
        NameField(name) { name = it }

        SectionGap(24)
        LinkStrip(state, hud?.peers ?: 0)
        SectionGap(14)
        HostJoinRow(
            enabled = seat == null || state == LinkState.LOST,
            onHost = {
                seat = Seat.HOST
                scope.launch { error = failure(hub.host("ClashFit · " + mode.title)) }
            },
            onJoin = {
                seat = Seat.GUEST
                scope.launch { error = failure(hub.join()) }
            },
        )

        when (seat) {
            // The host sets the match up for both phones. Live: every change is broadcast, so a
            // guest already linked watches the list change under it.
            Seat.HOST -> {
                SectionGap(24)
                Kicker("The movement")
                SectionGap(10)
                if (choices.isEmpty()) {
                    Text("Loading movements…", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                } else {
                    ListGroup {
                        choices.forEachIndexed { i, ex ->
                            ExerciseChoice(ex, ex.id == chosen, settings.reduceMotion) { chosen = ex.id }
                            if (i < choices.lastIndex) InnerDivider()
                        }
                    }
                }
                if (mode == GameMode.REP_RACE) {
                    SectionGap(20)
                    Kicker("Clock")
                    SectionGap(10)
                    DurationChips(seconds, enabled = hud?.raceStartedMs == null) { seconds = it }
                }
            }
            // The guest chooses nothing. It is shown what it is about to do, or told plainly that
            // it does not know yet — never a bare spinner. docs/07-MULTIPLAYER-SPEC.md §7
            Seat.GUEST -> {
                SectionGap(24)
                GuestCard(
                    exerciseName = exercises[agreed]?.name,
                    durationSec = hud?.durationSec.takeIf { mode == GameMode.REP_RACE },
                )
            }
            null -> Unit
        }

        if (armed && seat != null) {
            SectionGap(20)
            ReadyBlock(isHost = hosting, peers = hud?.peers ?: 0, ready = agreed.isNotBlank()) {
                scope.launch { error = failure(hub.start()) }
            }
        }

        ErrorLine(error)
        SectionGap(24)
    }
}

/** The line under the headline: what this phone knows the match to be, at every stage. */
private fun matchLabel(seat: Seat?, exerciseId: String, exerciseName: String?): String = when {
    seat == null -> "HOST OR JOIN TO BEGIN"
    exerciseId.isBlank() -> "WAITING FOR THE HOST'S MOVEMENT"
    else -> (exerciseName ?: exerciseId).uppercase()
}

/** What the host has set up, as the guest sees it. Read-only: the host chooses for both. */
@Composable
private fun GuestCard(exerciseName: String?, durationSec: Int?) {
    AppCard(Modifier.fillMaxWidth(), padding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Kicker("The host picks")
            if (exerciseName == null) {
                Text("Not chosen yet", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("You'll see the movement here as soon as they pick one.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            } else {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (durationSec != null) "$durationSec seconds, same clock on both phones."
                    else "Same movement on both phones.",
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- raid room

@Composable
private fun RaidRoomScreen(graph: AppGraph, nav: NavHostController, exerciseId: String) {
    val hub = graph.playHub
    val scope = rememberCoroutineScope()
    val state by hub.linkState.collectAsStateWithLifecycle()
    val hud by hub.link.collectAsStateWithLifecycle()
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf(hub.playerName) }
    var error by remember { mutableStateOf<String?>(null) }
    var startedHere by remember { mutableStateOf(false) }
    val armed = hud != null

    LaunchedEffect(name) { hub.playerName = name.ifBlank { "YOU" } }
    // Same as the duel lobby: the host arms while advertising so Start is there before guests are.
    LaunchedEffect(state) { if (state != LinkState.IDLE) hub.arm(GameMode.RAID, exerciseId, null) }
    LaunchedEffect(Unit) {
        hub.started.collect { signal ->
            startedHere = true
            nav.navigate(Session(mode = signal.mode.name, exerciseId = signal.exerciseId, durationSec = signal.durationSec))
        }
    }
    DisposableEffect(Unit) { onDispose { if (!startedHere) hub.release() } }

    Column(
        Modifier.fillMaxSize().background(Ground).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Kicker("Raid · Live leaderboard")
        SectionGap(12)
        Headline("RAID")
        SectionGap(10)
        Text(GameMode.RAID.blurb, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        SectionGap(8)
        Text(
            (exercises[exerciseId]?.name ?: exerciseId).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = InkFaint,
        )

        SectionGap(24)
        NameField(name) { name = it }

        SectionGap(24)
        LinkStrip(state, hud?.peers ?: 0)
        SectionGap(14)
        HostJoinRow(
            enabled = state == LinkState.IDLE || state == LinkState.LOST,
            onHost = { scope.launch { error = failure(hub.host("ClashFit · Raid")) } },
            onJoin = { scope.launch { error = failure(hub.join()) } },
        )

        if (armed) {
            SectionGap(24)
            Standings(hub)
            SectionGap(20)
            ReadyBlock(isHost = hud?.isHost == true, peers = hud?.peers ?: 0, hostLabel = "Start raid") {
                scope.launch { error = failure(hub.start()) }
            }
        }

        ErrorLine(error)
        SectionGap(24)
    }
}

/** The room, live, while everyone waits. Names arrive as people link; the list never empties. */
@Composable
private fun Standings(hub: PlayHub) {
    val hud by hub.link.collectAsStateWithLifecycle()
    val rows = hud?.standings.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Kicker("The room · ${rows.size}")
        SectionGap(10)
        if (rows.isEmpty()) {
            Text("Nobody else yet. They join by opening the same mode nearby.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        } else {
            ListGroup {
                rows.forEachIndexed { index, s ->
                    StandingRow(s)
                    if (index < rows.size - 1) InnerDivider()
                }
            }
        }
    }
}

@Composable
private fun StandingRow(s: Standing) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (s.me) EmberTint else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(s.name, size = 40)
        Column(Modifier.weight(1f)) {
            Text(
                if (s.me) "${s.name} (you)" else s.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (s.out) InkFaint else Ink,
            )
            // A raid is scored on damage. Showing only a fatigue tag hides the whole contest.
            Text("${s.reps.grouped()} reps · ${s.damage.grouped()} damage", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        when {
            s.out -> Tag("Out", color = Gassed)
            s.finished -> Tag("Done", color = Fresh)
            else -> Tag(bandLabel(s.band), color = bandColor(s.band))
        }
    }
}

// ---------------------------------------------------------------- shared parts

@Composable
private fun NameField(name: String, onName: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Kicker("Your name")
        SectionGap(10)
        Box(Modifier.fillMaxWidth().background(Panel).padding(12.dp)) {
            BasicTextField(
                value = name,
                onValueChange = { onName(it.take(12)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ember),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text("Your name", style = MaterialTheme.typography.titleLarge, color = InkFaint)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun DurationChips(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RACE_DURATIONS.forEach { sec ->
            val on = sec == selected
            Text(
                "${sec}S",
                modifier = Modifier
                    .background(if (on) Ember else Panel)
                    .border(1.dp, if (on) Ember else Rule)
                    .clickable(enabled = enabled) { onSelect(sec) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Ground else if (enabled) Ink else InkFaint,
            )
        }
    }
}

/** Named state, always. A judge watching an unexplained spinner assumes it is broken. */
@Composable
private fun LinkStrip(state: LinkState, peers: Int) {
    val (color, icon) = when (state) {
        LinkState.LINKED -> Success to AppIcons.Check
        LinkState.LOST -> Gassed to AppIcons.Close
        LinkState.ADVERTISING -> Working to AppIcons.Bell
        LinkState.SEARCHING -> Working to AppIcons.Search
        LinkState.IDLE -> InkMuted to AppIcons.Gear
    }

    AppCard(Modifier.fillMaxWidth(), padding = 16) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconBubble(icon = icon, tint = color)
            Text(linkLabel(state, peers), style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
    }
}

@Composable
private fun HostJoinRow(enabled: Boolean, onHost: () -> Unit, onJoin: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Host", Modifier.weight(1f), enabled = enabled, onClick = onHost)
        SecondaryButton("Join", Modifier.weight(1f), enabled = enabled, onClick = onJoin)
    }
}

/**
 * The host's go button, live from the moment they start hosting. A host with nobody joined yet is
 * told so rather than left without a button: a single phone is still a valid run, and hiding Start
 * until a peer arrives made the lobby look broken to anyone testing on their own. [ready] is false
 * only in the blink before the movement list has loaded — going then would start a match on a
 * movement nobody chose.
 */
@Composable
private fun ReadyBlock(isHost: Boolean, peers: Int, ready: Boolean = true, hostLabel: String = "Start", onStart: () -> Unit) {
    if (isHost) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(hostLabel, Modifier.fillMaxWidth(), enabled = ready, onClick = onStart)
            Text(
                when {
                    !ready -> "Pick a movement first."
                    peers > 0 -> "Both phones go at once."
                    else -> "Nobody has joined yet — start anyway and you run it solo."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Waiting for host…", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("They press start; both phones go at once.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
    }
}

@Composable
private fun ErrorLine(error: String?) {
    if (error == null) return
    SectionGap(16)
    Text(error, style = MaterialTheme.typography.bodySmall, color = Ember)
}

// ---------------------------------------------------------------- plain helpers

private fun failure(result: Result<Unit>): String? =
    result.exceptionOrNull()?.let { it.message ?: "Could not reach the other phone" }

private fun linkLabel(state: LinkState, peers: Int): String = when (state) {
    LinkState.IDLE -> "Not linked"
    LinkState.ADVERTISING -> "Waiting for a phone…"
    LinkState.SEARCHING -> "Searching…"
    LinkState.LINKED -> if (peers <= 1) "Linked · 1 peer" else "Linked · $peers peers"
    LinkState.LOST -> "Lost"
}

private fun stateColor(state: LinkState): Color = when (state) {
    LinkState.LINKED -> Fresh
    LinkState.LOST -> Ember
    else -> InkMuted
}

private fun bandOf(band: String): FatigueBand? = when (band.uppercase()) {
    "HEAVY" -> FatigueBand.FADING
    else -> runCatching { FatigueBand.valueOf(band.uppercase()) }.getOrNull()
}

private fun bandLabel(band: String): String = bandOf(band)?.label ?: band

private fun bandColor(band: String): Color = bandOf(band)?.color() ?: InkMuted
