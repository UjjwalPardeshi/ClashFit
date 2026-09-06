package com.clashfit.ui.screens.breathing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.data.BreathingEntity
import com.clashfit.engine.summary.BreathPhase
import com.clashfit.engine.summary.BreathTick
import com.clashfit.engine.summary.BreathingGoal
import com.clashfit.engine.summary.BreathingPattern
import com.clashfit.engine.summary.BreathingPatterns
import com.clashfit.engine.summary.BreathingSession
import com.clashfit.engine.summary.recoveryFraction
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.BreathMark
import com.clashfit.ui.components.FilterPill
import com.clashfit.ui.components.FitHeadline
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.components.Tag
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.LocalReduceMotion
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Guided breathing, and the recovery mechanic that makes it part of the fight.
// docs/22-HEALTH-DOMAINS.md §3 — breathing during rest recovers a fatigue band faster.
//
// Nothing here measures you. No camera, no microphone, no sensor, no permission: the app names the
// phase, counts the seconds and buzzes when it changes. Every technique in the catalogue is taught
// by a person saying "in for four, out for six", and a phone can do exactly that much honestly.

private enum class Stage { INTRO, RUNNING, DONE }

private const val MinScale = 0.45f
private const val MaxScale = 1f

@Composable
fun BreathingScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(Stage.INTRO) }
    var patternId by rememberSaveable { mutableStateOf(BreathingPatterns.DEFAULT.id) }
    var durationSec by rememberSaveable { mutableIntStateOf(BreathingPatterns.DURATIONS_SEC[1]) }

    val pattern = BreathingPatterns.byId(patternId) ?: BreathingPatterns.DEFAULT
    val cycles = remember(pattern, durationSec) {
        BreathingSession.cyclesForSeconds(pattern, durationSec)
    }

    val session = remember(pattern, cycles) { pattern.session(cycles) }
    var tick by remember(session) { mutableStateOf(session.tick(0L)) }
    var elapsedSec by remember(session) { mutableFloatStateOf(0f) }
    var bankedCycles by remember { mutableIntStateOf(0) }
    var bankedSec by remember { mutableIntStateOf(0) }

    val history by graph.db.breathing().recent(12)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    /**
     * Writes the session down, finished or not.
     *
     * Called from both endings on purpose. A record that only kept the sessions somebody saw
     * through to the end would be a flattering record rather than a true one, and the totals would
     * stop agreeing with the days practised.
     */
    fun record(startedAtMs: Long, completedCycles: Int, seconds: Int, completed: Boolean) {
        scope.launch {
            runCatching {
                graph.db.breathing().insert(
                    BreathingEntity(
                        patternId = pattern.id,
                        startedAtMs = startedAtMs,
                        endedAtMs = startedAtMs + seconds * 1000L,
                        cyclesCompleted = completedCycles,
                        cyclesTarget = cycles,
                        seconds = seconds,
                        completed = completed,
                    ),
                )
            }
        }
    }

    var startedAtMs by remember { mutableStateOf(0L) }

    // Leaving the screen mid-session simply cancels this loop. No dialog, no confirmation.
    LaunchedEffect(stage, session) {
        if (stage != Stage.RUNNING) return@LaunchedEffect
        val startMs = graph.clock.nowMs()
        startedAtMs = startMs
        session.start(startMs)
        while (true) {
            withFrameMillis { }
            val now = graph.clock.nowMs()
            val t = session.tick(now)
            tick = t
            elapsedSec = (now - startMs) / 1000f
            if (t.done) {
                bankedCycles = t.cycles
                bankedSec = session.totalSec.toInt()
                graph.haptics.milestone()
                record(startMs, t.cycles, session.totalSec.toInt(), completed = true)
                stage = Stage.DONE
                break
            }
            // One buzz to breathe in, two to breathe out, and silence to hold.
            //
            // Silence is a real cue rather than a gap: the holds are the only phases where nothing
            // happens, so nothing happening is exactly right, and it keeps the wrist quiet through
            // the seven-second hold in 4-7-8. The physiological sigh's second inhale gets the same
            // single buzz as the first, because it is the same instruction: keep breathing in.
            if (t.changed) {
                when (t.phase) {
                    BreathPhase.IN -> graph.haptics.tempoPulse()
                    BreathPhase.OUT -> graph.haptics.phase()
                    BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> Unit
                }
            }
        }
    }

    // The intro is a normal screen and gets the app's top bar, which is also the only way back out
    // of here. A running session is deliberately bare: nothing to press but stop.
    if (stage == Stage.INTRO) {
        ScreenScaffold(title = "Breathing", onBack = { nav.popBackStack() }) { padding ->
            Column(
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .padding(horizontal = 20.dp),
            ) {
                Intro(
                    pattern = pattern,
                    cycles = cycles,
                    durationSec = durationSec,
                    history = history,
                    onPattern = { patternId = it },
                    onDuration = { durationSec = it },
                    onBegin = { stage = Stage.RUNNING },
                )
            }
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (stage) {
            Stage.INTRO -> Unit

            Stage.RUNNING -> Running(
                pattern = pattern,
                tick = tick,
                cycles = cycles,
                overall = if (session.totalSec > 0f) (elapsedSec / session.totalSec).coerceIn(0f, 1f) else 0f,
                remainingSec = (session.totalSec - elapsedSec).toInt(),
                onStop = {
                    bankedCycles = tick.cycle
                    bankedSec = elapsedSec.toInt()
                    record(startedAtMs, tick.cycle, elapsedSec.toInt(), completed = false)
                    stage = Stage.DONE
                },
            )

            Stage.DONE -> Done(
                pattern = pattern,
                completed = bankedCycles,
                target = cycles,
                seconds = bankedSec,
                onBack = { nav.popBackStack() },
                onAgain = { stage = Stage.INTRO },
            )
        }
    }
}

@Composable
private fun Intro(
    pattern: BreathingPattern,
    cycles: Int,
    durationSec: Int,
    history: List<BreathingEntity>,
    onPattern: (String) -> Unit,
    onDuration: (Int) -> Unit,
    onBegin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            "Nothing here watches you. The app counts; the lungs are yours. " +
                "Every cycle you finish also pulls the fatigue band back before the next set.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        SectionGap(20)

        // Grouped by what you came here for, because "eight patterns" is a list and
        // "settle down / get to sleep / train your lungs" is a decision.
        BreathingPatterns.BY_GOAL.forEach { (goal, patterns) ->
            Text(goal.label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Text(goal.blurb, style = MaterialTheme.typography.labelSmall, color = InkFaint)
            SectionGap(10)
            patterns.forEach { p -> PatternRow(p, selected = p.id == pattern.id, onClick = { onPattern(p.id) }) }
            SectionGap(18)
        }

        // What the chosen one asks of you before you start, and who should not.
        AppCard(Modifier.fillMaxWidth(), padding = 16) {
            Column {
                Text(pattern.whenToUse, style = MaterialTheme.typography.bodyMedium, color = Ink)
                SectionGap(10)
                Text(pattern.whyItWorks, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                if (pattern.setup.isNotBlank()) {
                    SectionGap(10)
                    Text("Before you start", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    Text(pattern.setup, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
                SectionGap(10)
                Text("Take care", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                Text(pattern.caution, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        SectionGap(20)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("How long", style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Text(
                "$cycles ${if (cycles == 1) "round" else "rounds"}",
                style = MaterialTheme.typography.labelMedium,
                color = Ember,
            )
        }
        SectionGap(12)
        // Chosen by time, not by a repetition count: one box breath is sixteen seconds and one
        // pursed-lip breath is six, so the same number of rounds means very different sessions.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BreathingPatterns.DURATIONS_SEC.forEach { sec ->
                FilterPill(
                    "${sec / 60} min",
                    selected = sec == durationSec,
                    onClick = { onDuration(sec) },
                )
            }
        }
        SectionGap(28)

        PrimaryButton("Begin", Modifier.fillMaxWidth(), onClick = onBegin)

        if (history.isNotEmpty()) {
            SectionGap(28)
            History(history)
        }
        SectionGap(20)
    }
}

@Composable
private fun PatternRow(pattern: BreathingPattern, selected: Boolean, onClick: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        container = if (selected) PanelLift else Panel,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val inSec = pattern.steps.firstOrNull { it.phase == BreathPhase.IN }?.seconds ?: 0f
            val holdIn = pattern.steps.firstOrNull { it.phase == BreathPhase.HOLD_IN }?.seconds ?: 0f
            val out = pattern.steps.firstOrNull { it.phase == BreathPhase.OUT }?.seconds ?: 0f
            val holdOut = pattern.steps.firstOrNull { it.phase == BreathPhase.HOLD_OUT }?.seconds ?: 0f
            BreathMark(inSec, holdIn, out, holdOut, selected)
            Column(Modifier.weight(1f)) {
                Text(
                    pattern.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) Ink else InkMuted,
                )
                Text(
                    "${pattern.rhythm} · ${"%.0f".format(pattern.breathsPerMinute)} a minute",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
            // A tick, not a colour: the chosen pattern stays obvious to anyone who cannot
            // separate an ember tag from a grey one.
            if (selected) Icon(AppIcons.Check, contentDescription = "Selected", tint = Ember)
        }
    }
}

/**
 * What you have actually done, which is the only form a breathing practice takes.
 *
 * One session is four minutes nobody remembers by Thursday. Forty of them is a habit, and the
 * difference is invisible unless the phone wrote each one down. Abandoned sessions are shown as
 * well as finished ones, marked, because a record that quietly drops the evenings you gave up is
 * not a record.
 */
@Composable
private fun History(history: List<BreathingEntity>) {
    val totalSec = history.sumOf { it.seconds }
    val days = history.map { dayKey(it.startedAtMs) }.distinct().size

    Text("Your practice", style = MaterialTheme.typography.labelMedium, color = InkMuted)
    SectionGap(12)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("${history.size}", "Sessions", Modifier.weight(1f), color = Ember)
        StatTile(mmss(totalSec), "Time", Modifier.weight(1f))
        StatTile("$days", if (days == 1) "Day" else "Days", Modifier.weight(1f), color = Fresh)
    }
    SectionGap(12)
    ListGroup {
        history.forEachIndexed { i, row ->
            val name = BreathingPatterns.byId(row.patternId)?.name ?: row.patternId
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Text(
                        "${row.cyclesCompleted} of ${row.cyclesTarget} rounds · ${mmss(row.seconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        dateLabel(row.startedAtMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                    )
                    if (!row.completed) {
                        Text(
                            "stopped early",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                }
            }
            if (i < history.lastIndex) InnerDivider()
        }
    }
}

@Composable
private fun Running(
    pattern: BreathingPattern,
    tick: BreathTick,
    cycles: Int,
    overall: Float,
    remainingSec: Int,
    onStop: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pattern.name, style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Text(mmss(remainingSec), style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
            IconButton(onClick = onStop, modifier = Modifier) {
                Icon(AppIcons.Close, contentDescription = "Exit", tint = Ink)
            }
        }
        SectionGap(16)

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            BreathBox(tick, reduceMotion, Modifier.aspectRatio(1f))
        }
        SectionGap(16)

        Text(tick.phase.word(), style = MaterialTheme.typography.displaySmall, color = Ink)
        SectionGap(4)
        // The pattern's own words for this phase. The big word says what to do; this says how, and
        // for alternate nostril it is the only thing that says which side.
        Text(tick.cue, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        SectionGap(8)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(tick.cycle + 1).coerceAtMost(cycles)} / $cycles",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
            Tag(pattern.rhythm)
        }
        SectionGap(12)
        Bar(overall)
        SectionGap(16)
        SecondaryButton("Stop", Modifier.fillMaxWidth(), onClick = onStop)
    }
}

/**
 * The pacer. Normally a square that grows on the inhale and shrinks on the exhale, driven straight
 * off phaseProgress so it is exact rather than merely animated. Reduced motion holds the size and
 * pulses the border instead. docs/03-UI-UX-SPEC.md §7
 */
@Composable
private fun BreathBox(tick: BreathTick, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val accent = tick.phase.accent()
    AppCard(modifier, padding = 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (reduceMotion) {
                Box(
                    Modifier.fillMaxSize(0.72f).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Bar(tick.phaseProgress, color = accent, height = 6)
                }
            } else {
                Box(Modifier.fillMaxSize(scaleFor(tick)).background(accent))
            }
        }
    }
}

@Composable
private fun Done(
    pattern: BreathingPattern,
    completed: Int,
    target: Int,
    seconds: Int,
    onBack: () -> Unit,
    onAgain: () -> Unit,
) {
    val recovered = (recoveryFraction(completed, target) * 100f).toInt()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(pattern.name, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap(12)
        FitHeadline("Recovered")
        if (recovered > 0) {
            Text("-$recovered% fatigue", style = MaterialTheme.typography.titleSmall, color = Fresh)
        } else {
            Text("No fatigue cleared yet", style = MaterialTheme.typography.titleSmall, color = InkMuted)
        }
        SectionGap(12)
        Text(
            "$completed of $target rounds. The band drops that much before the next one.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
        )
        SectionGap(24)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("$completed", "Rounds", Modifier.weight(1f), color = Ember)
            StatTile(mmss(seconds), "Time", Modifier.weight(1f))
            StatTile("$recovered%", "Recovered", Modifier.weight(1f), color = Fresh)
        }
        SectionGap(28)

        PrimaryButton("Back to the fight", Modifier.fillMaxWidth(), onClick = onBack)
        SectionGap(12)
        SecondaryButton("Again", Modifier.fillMaxWidth(), onClick = onAgain)
        SectionGap(20)
    }
}

private fun scaleFor(tick: BreathTick): Float {
    val p = tick.phaseProgress.coerceIn(0f, 1f)
    val span = MaxScale - MinScale
    return when (tick.phase) {
        BreathPhase.IN -> MinScale + span * p
        BreathPhase.HOLD_IN -> MaxScale
        BreathPhase.OUT -> MaxScale - span * p
        BreathPhase.HOLD_OUT -> MinScale
    }
}

/** Three words, readable at two metres. Colour never carries the phase on its own. */
private fun BreathPhase.word(): String = when (this) {
    BreathPhase.IN -> "IN"
    BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> "HOLD"
    BreathPhase.OUT -> "OUT"
}

private fun BreathPhase.accent(): Color = when (this) {
    BreathPhase.IN, BreathPhase.HOLD_IN -> Ember
    BreathPhase.OUT, BreathPhase.HOLD_OUT -> EmberDeep
}

private fun mmss(sec: Int): String {
    val total = sec.coerceAtLeast(0)
    val s = total % 60
    return "${total / 60}:${if (s < 10) "0$s" else "$s"}"
}

private fun dayKey(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))

private fun dateLabel(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

fun NavGraphBuilder.breathingRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Breathing> {
        BreathingScreen(graph, nav)
    }
}
