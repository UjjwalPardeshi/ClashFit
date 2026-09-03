package com.clashfit.ui.screens.challenge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.config.GhostData
import com.clashfit.core.model.GameMode
import com.clashfit.data.GhostEntity
import com.clashfit.data.Prefs
import com.clashfit.engine.core.ChallengeCard
import com.clashfit.engine.core.ChallengeCodec
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.Challenge
import com.clashfit.ui.nav.Session
import com.clashfit.ui.screens.ghosts.title
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.MonoReadout
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A challenge encodes a target or ghost. Camera frames, pose landmarks, and rep timelines stay on device; only scores, names, and levels sync. Peer-to-peer racing over Nearby needs no server. */
private const val MAX_GHOST_EVENTS = 48
private const val DEFAULT_TARGET = 20
private const val MIN_TARGET = 1
private const val MAX_TARGET = 200

private enum class ChallengeKind(val label: String) { TARGET("Reps target"), GHOST("My last ghost") }

@Composable
fun ChallengeScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val dao = remember(graph) { graph.db.ghosts() }
    val saved by remember(dao) { dao.all() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(ChallengeKind.TARGET) }
    var exerciseId by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(DEFAULT_TARGET) }

    LaunchedEffect(exercises, settings.preferredExercise) {
        if (exerciseId.isEmpty() && exercises.isNotEmpty()) {
            exerciseId = settings.preferredExercise.takeIf(exercises::containsKey) ?: exercises.keys.first()
        }
    }

    val lastGhost = saved.firstOrNull()
    val ghostEvents = remember(lastGhost) { decodeEvents(graph.json, lastGhost?.eventsJson.orEmpty()) }
    val card = remember(kind, exerciseId, target, lastGhost, ghostEvents) {
        buildCard(kind, exerciseId, target, lastGhost, ghostEvents)
    }
    val code = remember(card) { card?.let { c -> runCatching { ChallengeCodec.encode(c) }.getOrNull() } }

    ScreenScaffold(title = "Challenge", onBack = { nav.navigateUp() }) { padding ->
        Column(
            modifier
                .fillMaxWidth().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            MakeOneSection(
                exercises = exercises, exerciseId = exerciseId, onExercise = { exerciseId = it },
                kind = kind, onKind = { kind = it },
                target = target, onTarget = { target = it.coerceIn(MIN_TARGET, MAX_TARGET) },
                lastGhost = lastGhost, ghostEventCount = ghostEvents.size, code = code,
            )

            SectionGap()

            GotOneSection { pasted ->
                acceptCode(
                    raw = pasted, graph = graph, exercises = exercises,
                    onGhost = { entity ->
                        scope.launch {
                            dao.upsert(entity)
                            nav.navigate(Session(GameMode.GHOST_RACE.name, entity.exerciseId, ghostId = entity.id))
                        }
                    },
                    onTargetCard = { mode, exercise -> nav.navigate(Session(mode = mode, exerciseId = exercise)) },
                )
            }

            SectionGap(20)
        }
    }
}

// ---------------------------------------------------------------- make one

@Composable
private fun MakeOneSection(
    exercises: Map<String, ExerciseSpec>, exerciseId: String, onExercise: (String) -> Unit,
    kind: ChallengeKind, onKind: (ChallengeKind) -> Unit,
    target: Int, onTarget: (Int) -> Unit,
    lastGhost: GhostEntity?, ghostEventCount: Int, code: String?,
) {
    val context = LocalContext.current

    SectionTitle("Make a dare")
    SectionGap(12)

    ChipRow(
        items = exercises.values.sortedBy { it.name }.map { it.id to it.name },
        selectedId = exerciseId,
        onSelect = onExercise,
    )
    SectionGap(14)

    ChipRow(
        items = ChallengeKind.entries.map { it.name to it.label },
        selectedId = kind.name,
        onSelect = { onKind(ChallengeKind.valueOf(it)) },
    )
    SectionGap(14)

    when (kind) {
        ChallengeKind.TARGET -> TargetStepper(target, onTarget)
        ChallengeKind.GHOST -> GhostSummary(lastGhost, exercises, ghostEventCount)
    }
    SectionGap(16)

    if (code == null) {
        Text("Nothing to encode yet.", style = MaterialTheme.typography.bodyMedium, color = InkFaint)
    } else {
        AppCard(Modifier.fillMaxWidth(), container = PanelLift, padding = 12) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(code, style = MonoReadout, color = Ink)
                Text("${code.length} characters", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
        }
        SectionGap(12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Share", Modifier.weight(1f)) { shareText(context, code) }
            SecondaryButton("Copy", Modifier.weight(1f)) { copyText(context, code) }
        }
    }
}

@Composable
private fun TargetStepper(target: Int, onTarget: (Int) -> Unit) {
    AppCard(Modifier.fillMaxWidth(), container = Panel, padding = 16) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperKey("−") { onTarget(target - 1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(target.toString(), style = MaterialTheme.typography.headlineLarge, color = Ember)
                Text("reps to beat", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
            StepperKey("+") { onTarget(target + 1) }
        }
    }
}

@Composable
private fun StepperKey(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(PanelLift, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.headlineMedium,
            color = Ember,
        )
    }
}

@Composable
private fun GhostSummary(ghost: GhostEntity?, exercises: Map<String, ExerciseSpec>, eventCount: Int) {
    if (ghost == null || eventCount == 0) {
        Text("No ghost to send yet. Finish a fight first.", style = MaterialTheme.typography.bodyMedium, color = InkFaint)
        return
    }
    AppCard(Modifier.fillMaxWidth(), container = Panel, padding = 12) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ghost.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(exercises.title(ghost.exerciseId), style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("${minOf(eventCount, MAX_GHOST_EVENTS)} reps", color = Ember)
                Tag("${ghost.totalDamage} dmg")
                if (eventCount > MAX_GHOST_EVENTS) Tag("trimmed to fit")
            }
        }
    }
}

// ---------------------------------------------------------------- got one

@Composable
private fun GotOneSection(onAccept: (String) -> AcceptResult) {
    var pasted by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AcceptResult?>(null) }

    SectionTitle("Accept a dare")
    SectionGap(12)

    OutlinedTextField(
        value = pasted,
        onValueChange = { pasted = it; result = null },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("CF1:…", style = MonoReadout, color = InkFaint) },
        textStyle = MonoReadout,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ember,
            unfocusedBorderColor = Panel,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = Ember,
        ),
    )
    SectionGap(12)

    PrimaryButton("Accept", Modifier.fillMaxWidth(), enabled = pasted.isNotBlank()) {
        result = onAccept(pasted.trim())
    }

    val failure = result?.error
    val accepted = result?.target
    if (failure != null) {
        SectionGap(10)
        Text(failure, style = MaterialTheme.typography.bodyMedium, color = Ember)
    } else if (accepted != null) {
        SectionGap(10)
        Tag("target · $accepted reps", color = Ember)
    }
}

// ---------------------------------------------------------------- chips

@Composable
private fun ChipRow(items: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, label) ->
            val on = id == selectedId
            Box(
                modifier = Modifier
                    .background(if (on) Ember else PanelLift, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Ground else Ink,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- codes

/** What ACCEPT produced: a friendly error, or the informational target we navigated with. */
private data class AcceptResult(val error: String? = null, val target: Int? = null)

/**
 * Decode a pasted code into a destination. Everything past the codec is hostile input: the
 * exercise must exist in this build and the mode must be a real [GameMode] before we navigate.
 */
private fun acceptCode(
    raw: String, graph: AppGraph, exercises: Map<String, ExerciseSpec>,
    onGhost: (GhostEntity) -> Unit, onTargetCard: (String, String) -> Unit,
): AcceptResult {
    val card = runCatching { ChallengeCodec.decode(raw) }.getOrElse { failure ->
        // Only the codec's own IllegalArgumentException messages are written for players;
        // anything deeper (serialization internals) gets the plain line instead of a stack-trace voice.
        val friendly = failure.takeIf { it.javaClass == IllegalArgumentException::class.java }?.message
        return AcceptResult(error = friendly?.takeIf { it.isNotBlank() } ?: "That code did not read. Check you copied all of it.")
    }
    if (exercises.isNotEmpty() && !exercises.containsKey(card.exerciseId)) {
        return AcceptResult(error = "This build has no exercise called ${card.exerciseId}.")
    }
    val mode = runCatching { GameMode.valueOf(card.mode) }.getOrDefault(GameMode.BOSS_FIGHT)
    val events = card.ghost?.events.orEmpty().map { GhostData.Event(t = it.t, damage = it.damage) }

    if (events.isEmpty()) {
        onTargetCard(mode.name, card.exerciseId)
        return AcceptResult(target = card.target)
    }
    onGhost(
        GhostEntity(
            id = "challenge-" + shortHash(raw),
            name = safeName(card.name ?: "Challenger"),
            exerciseId = card.exerciseId,
            reps = card.target ?: events.size,
            totalDamage = events.sumOf { it.damage },
            createdAtMs = graph.clock.nowMs(),
            eventsJson = encodeEvents(graph.json, events),
            shipped = false,
        ),
    )
    return AcceptResult()
}

/** Build the card for the current selection, or null when there is nothing to encode. */
private fun buildCard(
    kind: ChallengeKind, exerciseId: String, target: Int,
    ghost: GhostEntity?, events: List<GhostData.Event>,
): ChallengeCard? = when {
    exerciseId.isEmpty() -> null

    kind == ChallengeKind.TARGET -> ChallengeCard(
        kind = "TARGET",
        exerciseId = exerciseId,
        mode = GameMode.BOSS_FIGHT.name,
        name = null,
        target = target,
        ghost = null,
    )

    ghost == null || events.isEmpty() -> null

    else -> events.take(MAX_GHOST_EVENTS).let { trimmed ->
        ChallengeCard(
            kind = "GHOST",
            exerciseId = ghost.exerciseId,
            mode = GameMode.GHOST_RACE.name,
            name = safeName(ghost.name),
            target = trimmed.size,
            ghost = ChallengeCard.GhostDataPayload(
                events = trimmed.map { ChallengeCard.GhostDataPayload.GhostEventPayload(it.t, it.damage) },
            ),
        )
    }
}

/** A stored ghost is a list of events; older rows may hold a whole ghost file, so read both. */
private fun decodeEvents(json: Json, raw: String): List<GhostData.Event> {
    if (raw.isBlank()) return emptyList()
    val asEvents = runCatching { json.decodeFromString(EventListSerializer, raw) }
    if (asEvents.isSuccess) return asEvents.getOrDefault(emptyList())
    return runCatching { json.decodeFromString(GhostData.serializer(), raw).events }.getOrDefault(emptyList())
}

private fun encodeEvents(json: Json, events: List<GhostData.Event>): String =
    json.encodeToString(EventListSerializer, events)

private val EventListSerializer = ListSerializer(GhostData.Event.serializer())

/** The codec interpolates the name straight into JSON, so only ever hand it plain text. */
private fun safeName(raw: String): String =
    raw.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(24).ifEmpty { "Challenger" }

/** Stable, short, offline id for a received challenge, so re-pasting overwrites rather than piles up. */
private fun shortHash(text: String): String {
    var h = 5381
    for (c in text) h = (h * 33) xor c.code
    return (h.toLong() and 0xFFFFFFFFL).toString(36)
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Send challenge")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("ClashFit challenge", text)) }
}

fun NavGraphBuilder.challengeRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Challenge> { ChallengeScreen(graph, nav) }
}
