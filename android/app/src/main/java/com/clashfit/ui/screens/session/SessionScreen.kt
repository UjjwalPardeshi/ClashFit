package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clashfit.AppGraph
import com.clashfit.core.model.EndReason
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Phase
import com.clashfit.core.model.SessionState
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.FatiguePips
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.components.color
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.LocalReduceMotion
import com.clashfit.ui.theme.Motion
import com.clashfit.ui.theme.Panel

/**
 * One screen for the whole fight. The engine's phase decides what is drawn: calibration guide,
 * fight HUD, rest panel, or the ending. Navigating away mid-set is infuriating, so it never does.
 */
@Composable
fun SessionScreen(
    graph: AppGraph,
    deps: SessionDeps,
    args: SessionArgs,
    skeleton: @Composable (Modifier) -> Unit,
    onSummary: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val vm: SessionViewModel = viewModel(key = "session-${args.hashCode()}", factory = SessionViewModel.factory(graph, deps, args))
    val state by vm.state.collectAsStateWithLifecycle()
    val restLeft by vm.restRemainingSec.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current

    var lastHit by remember { mutableStateOf<HudEvent.Hit?>(null) }
    val jolt = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is HudEvent.Hit -> { lastHit = e; if (!reduceMotion) { jolt.snapTo(1f); jolt.animateTo(0f, tween(180)) } }
                is HudEvent.PhaseShift -> if (!reduceMotion) {
                    repeat(3) { shake.animateTo(Motion.shakePx, tween(Motion.shakeMs / 6)); shake.animateTo(-Motion.shakePx, tween(Motion.shakeMs / 6)) }
                    shake.animateTo(0f, tween(Motion.shakeMs / 6))
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) { vm.finished.collect { id -> onSummary(id) } }

    val s = state ?: return
    Box(Modifier.fillMaxSize().background(Ground)) {
        when (s.phase) {
            Phase.CALIBRATING -> {
                skeleton(Modifier.fillMaxSize())
                CalibrationOverlay(
                    cue = s.cue, progress = s.calibProgress,
                    tooFar = s.calib == com.clashfit.core.model.CalibState.TOO_FAR,
                    tooClose = s.calib == com.clashfit.core.model.CalibState.TOO_CLOSE,
                )
                ExitCorner(onExit)
            }
            Phase.FIGHTING, Phase.FRAMING_LOST -> {
                FightLayout(s, vm, lastHit, jolt.value, shake.value, paused, reduceMotion, skeleton)
            }
            Phase.REST -> RestPanel(s, restLeft, onSkip = vm::skipRest, onStop = vm::stop)
            Phase.DEAD -> EndPanel(s, onSummary = { /* wait for persistence */ }, onExit = onExit)
        }
        if (s.ended && s.phase != Phase.DEAD) EndPanel(s, onSummary = {}, onExit = onExit)
    }
}

@Composable
private fun FightLayout(
    s: SessionState, vm: SessionViewModel, lastHit: HudEvent.Hit?, jolt: Float, shake: Float,
    paused: Boolean, reduceMotion: Boolean, skeleton: @Composable (Modifier) -> Unit,
) {
    val prone = vm.isProne
    Box(Modifier.fillMaxSize()) {
        // The boss and the hit surface fill the screen; the skeleton sits in a dim corner inset.
        BossFigure(s.combat, jolt, shake, Modifier.fillMaxSize().padding(bottom = if (prone) 220.dp else 160.dp, top = 90.dp))
        Box(Modifier.align(if (prone) Alignment.TopEnd else Alignment.TopEnd).padding(top = 96.dp, end = 12.dp).size(width = 110.dp, height = 150.dp)) {
            skeleton(Modifier.fillMaxSize())
        }
        DamageNumeral(lastHit, Modifier.align(Alignment.Center).padding(bottom = 60.dp))

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            if (!prone) BossHeader(s.combat, timeLeftMs = s.timeLeftMs)
            Spacer(Modifier.weight(1f))
            if (s.phase == Phase.FRAMING_LOST) { FramingLostBanner(s.cue); Spacer(Modifier.height(12.dp)) }
            else s.cue?.let { cue ->
                Text(cue, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = InkMuted, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (prone) { BossHeader(s.combat, timeLeftMs = s.timeLeftMs); Spacer(Modifier.height(12.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                RepCounter(if (s.mode == com.clashfit.core.model.GameMode.SURVIVAL) s.reps else s.setReps, Modifier.weight(1.1f), label = if (s.wave > 1) "REPS · WAVE ${s.wave}" else "REPS")
                FatigueTile(s.fatigue.band, Modifier.weight(1f))
                ComboRail(s.combat.comboMultiplier, s.combat.comboStreak, Modifier.weight(1f))
            }
        }
        HitFlash(lastHit, reduceMotion)
        Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp)) {
            PauseTarget(paused, onToggle = { if (paused) vm.resume() else vm.pause() })
        }
        if (paused) PausedOverlay(onResume = vm::resume, onStop = vm::stop)
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ground.copy(alpha = 0.86f)).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("PAUSED", style = MaterialTheme.typography.displayMedium, color = Ink)
        Text("The boss is frozen. Your fatigue baseline is held.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        Spacer(Modifier.height(28.dp))
        EmberButton("Resume", Modifier.fillMaxWidth(), onClick = onResume)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Stop and save", Modifier.fillMaxWidth(), onClick = onStop)
    }
}

/** The moment the coach earns its place. The player is on the floor; the line is spoken too. */
@Composable
fun RestPanel(s: SessionState, restLeft: Int?, onSkip: () -> Unit, onStop: () -> Unit) {
    val t = s.telemetry
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kicker("Rest · set ${s.setIndex}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text((restLeft ?: s.restSec ?: 0).toString(), style = MaterialTheme.typography.displayLarge, color = Ember)
            FatiguePips(s.fatigue.band)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${t?.reps ?: s.setReps}", "reps", Modifier.weight(1f))
            StatTile("${t?.formMeanPct ?: 0}%", "form", Modifier.weight(1f))
            StatTile("${t?.velocityLossPct ?: 0}%", "velocity lost", Modifier.weight(1f), color = s.fatigue.band.color())
        }
        val coach = s.coach
        PanelBox(Modifier.fillMaxWidth(), padding = 20) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COACH", style = MaterialTheme.typography.labelMedium, color = Ember)
                Text(coach?.coachLine ?: "…", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
        }
        PanelBox(Modifier.fillMaxWidth(), padding = 20) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.combat.bossName.uppercase(), style = MaterialTheme.typography.labelMedium, color = InkFaint)
                Text(coach?.bossLine ?: "…", style = MaterialTheme.typography.headlineSmall, color = Ember)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Boss at ${(s.combat.hpPct * 100).toInt()}%${coach?.let { " · " + it.source.name.lowercase() + " coach" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        EmberButton("Next set", Modifier.fillMaxWidth(), onClick = onSkip)
        OutlineButton("Stop and save", Modifier.fillMaxWidth(), onClick = onStop)
    }
}

/** Victory, or "you stopped". There is no fail state for being tired. */
@Composable
fun EndPanel(s: SessionState, onSummary: () -> Unit, onExit: () -> Unit) {
    val won = s.endReason == EndReason.BOSS_DOWN || s.endReason == EndReason.GAME_WON
    val title = when (s.endReason) {
        EndReason.BOSS_DOWN -> "BOSS DOWN"
        EndReason.GAME_WON -> "YOU WON"
        EndReason.GAME_LOST -> "IT CAUGHT YOU"
        EndReason.TIME -> "TIME"
        EndReason.STOPPED, EndReason.WALKED_AWAY, null -> "YOU STOPPED"
    }
    Column(Modifier.fillMaxSize().background(Ground.copy(alpha = 0.94f)).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Kicker(if (won) "Victory" else "Set saved")
        Text(title, style = MaterialTheme.typography.displayMedium, color = if (won) Ember else Ink)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${s.reps}", "reps", Modifier.weight(1f))
            StatTile("${s.playerDamage}", "damage", Modifier.weight(1f))
            StatTile(s.fatigue.band.label, "peak", Modifier.weight(1f), color = s.fatigue.band.color())
        }
        if (s.ghostName != null) {
            Spacer(Modifier.height(10.dp))
            Text("${s.ghostName}: ${s.ghostDamage} · you: ${s.playerDamage}", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        }
        Spacer(Modifier.height(28.dp))
        Text("Saving…", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        Spacer(Modifier.height(8.dp))
        OutlineButton("Home", Modifier.fillMaxWidth(), onClick = onExit)
    }
}

@Composable
private fun ExitCorner(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Box(Modifier.align(Alignment.TopStart).background(Panel).clickable(onClick = onExit).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("BACK", style = MaterialTheme.typography.labelLarge, color = Ink)
        }
    }
}
