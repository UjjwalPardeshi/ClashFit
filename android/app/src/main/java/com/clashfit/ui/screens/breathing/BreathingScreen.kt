package com.clashfit.ui.screens.breathing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.engine.summary.BreathPhase
import com.clashfit.engine.summary.BreathTick
import com.clashfit.engine.summary.BreathingSession
import com.clashfit.engine.summary.recoveryFraction
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.PrimaryButton
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

// The between-rounds recovery ritual. Pick a pattern, follow the square, bank the fatigue drop.
// docs/22-HEALTH-DOMAINS.md §3 — breathing during rest recovers a fatigue band faster.

private enum class Stage { INTRO, RUNNING, DONE }

/** Everything BreathingSession's constructor can express: four phase lengths and a cycle count. */
private data class Protocol(
    val id: String,
    val name: String,
    val detail: String,
    val inSec: Float,
    val holdInSec: Float,
    val outSec: Float,
    val holdOutSec: Float,
)

private val Protocols = listOf(
    Protocol("box", "Box breathing", "4 · 4 · 4 · 4", 4f, 4f, 4f, 4f),
    Protocol("winddown", "Wind-down", "4 in · 8 out", 4f, 0f, 8f, 0f),
    Protocol("478", "Four seven eight", "4 · 7 · 8", 4f, 7f, 8f, 0f),
)

private val CycleChoices = listOf(4, 6, 8)
private const val MinScale = 0.45f
private const val MaxScale = 1f

@Composable
fun BreathingScreen(graph: AppGraph, nav: NavHostController, modifier: Modifier = Modifier) {
    var stage by remember { mutableStateOf(Stage.INTRO) }
    var protocolId by rememberSaveable { mutableStateOf(Protocols.first().id) }
    var cycles by rememberSaveable { mutableIntStateOf(CycleChoices[1]) }
    val protocol = Protocols.firstOrNull { it.id == protocolId } ?: Protocols.first()

    val session = remember(protocol, cycles) {
        BreathingSession(
            inSec = protocol.inSec,
            holdInSec = protocol.holdInSec,
            outSec = protocol.outSec,
            holdOutSec = protocol.holdOutSec,
            cycles = cycles,
        )
    }
    var tick by remember(session) { mutableStateOf(session.tick(0L)) }
    var elapsedSec by remember(session) { mutableFloatStateOf(0f) }
    var bankedCycles by remember { mutableIntStateOf(0) }
    var bankedSec by remember { mutableIntStateOf(0) }

    // Leaving the screen mid-session simply cancels this loop. No dialog, no confirmation.
    LaunchedEffect(stage, session) {
        if (stage != Stage.RUNNING) return@LaunchedEffect
        val startMs = graph.clock.nowMs()
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
                stage = Stage.DONE
                break
            }
            if (t.changed) graph.haptics.tempoPulse()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (stage) {
            Stage.INTRO -> Intro(
                protocol = protocol,
                cycles = cycles,
                totalSec = session.totalSec,
                onProtocol = { protocolId = it },
                onCycles = { cycles = it },
                onBegin = { stage = Stage.RUNNING },
            )

            Stage.RUNNING -> Running(
                protocol = protocol,
                tick = tick,
                cycles = cycles,
                overall = if (session.totalSec > 0f) (elapsedSec / session.totalSec).coerceIn(0f, 1f) else 0f,
                remainingSec = (session.totalSec - elapsedSec).toInt(),
                onStop = {
                    bankedCycles = tick.cycle
                    bankedSec = elapsedSec.toInt()
                    stage = Stage.DONE
                },
            )

            Stage.DONE -> Done(
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
    protocol: Protocol,
    cycles: Int,
    totalSec: Float,
    onProtocol: (String) -> Unit,
    onCycles: (Int) -> Unit,
    onBegin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Recovery", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap(12)
        Text("Breathe between rounds", style = MaterialTheme.typography.displayMedium, color = Ink)
        SectionGap(12)
        Text(
            "Follow the square. Every cycle you finish pulls the fatigue band back before the next set.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
        )
        SectionGap(28)

        Text("Pattern", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap(12)
        Protocols.forEach { p ->
            val selected = p.id == protocol.id
            AppCard(
                Modifier.fillMaxWidth(),
                onClick = { onProtocol(p.id) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                    Tag(p.detail, color = if (selected) Ember else InkMuted)
                }
            }
        }
        SectionGap(24)

        Text("Cycles", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CycleChoices.forEach { n ->
                Tag("$n", Modifier.clickable { onCycles(n) }, color = if (n == cycles) Ember else InkMuted)
            }
            Text(mmss(totalSec.toInt()), style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        SectionGap(28)

        PrimaryButton("Begin", Modifier.fillMaxWidth(), onClick = onBegin)
        SectionGap(20)
    }
}

@Composable
private fun Running(
    protocol: Protocol,
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
                Text(protocol.name, style = MaterialTheme.typography.labelMedium, color = InkMuted)
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
            Tag(protocol.detail)
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
private fun Done(completed: Int, target: Int, seconds: Int, onBack: () -> Unit, onAgain: () -> Unit) {
    val recovered = (recoveryFraction(completed, target) * 100f).toInt()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Recovery", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap(12)
        Text("Recovered", style = MaterialTheme.typography.displayMedium, color = Ink, modifier = Modifier)
        if (recovered > 0) {
            Text("-$recovered% fatigue", style = MaterialTheme.typography.titleSmall, color = Fresh)
        } else {
            Text("Nothing banked", style = MaterialTheme.typography.titleSmall, color = InkMuted)
        }
        SectionGap(12)
        Text(
            "$completed of $target cycles. The band drops that much before the next round.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
        )
        SectionGap(24)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("$completed", "Cycles", Modifier.weight(1f), color = Ember)
            StatTile("${seconds}s", "Seconds", Modifier.weight(1f))
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

fun NavGraphBuilder.breathingRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Breathing> {
        BreathingScreen(graph, nav)
    }
}
