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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.FatiguePips
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.SecondaryButton
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
import com.clashfit.core.model.Landmarks
import com.clashfit.perception.CameraPreviewSource
import com.clashfit.perception.CameraStage
import com.clashfit.perception.ExoRig
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import com.clashfit.meta.MetaState
import com.clashfit.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import com.clashfit.ui.theme.Heavy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxScope
import com.clashfit.perception.gesture.HandGesture

/**
 * One screen for the whole fight. The engine's phase decides what is drawn: calibration guide,
 * fight HUD, rest panel, or the ending. Navigating away mid-set is infuriating, so it never does.
 */
@Composable
fun SessionScreen(
    graph: AppGraph,
    deps: SessionDeps,
    args: SessionArgs,
    onSummary: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val vm: SessionViewModel = viewModel(key = "session-${args.hashCode()}", factory = SessionViewModel.factory(graph, deps, args))
    val state by vm.state.collectAsStateWithLifecycle()
    val restLeft by vm.restRemainingSec.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val landmarks by vm.skeleton.collectAsStateWithLifecycle()
    // The hand being held up to the camera, and the last command it gave.
    val gestureHold by vm.gestureHold.collectAsStateWithLifecycle()
    var lastGesture by remember { mutableStateOf<HudEvent.Gesture?>(null) }
    val reduceMotion = LocalReduceMotion.current
    // Not every pose source has a camera: a recorded trace and the synthetic source do not, and
    // the fight has to work with either.
    val camera = deps.pose as? CameraPreviewSource
    // Null until the first camera frame; the rig falls back to stretching until then.
    // remember-ed on the camera. Without it, a source-less run built a new MutableStateFlow every
    // recomposition and re-subscribed to it, which is a fresh collector per frame for the whole
    // session.
    val aspectFlow = remember(camera) { camera?.sourceAspect ?: MutableStateFlow(null) }
    val sourceAspect by aspectFlow.collectAsStateWithLifecycle(initialValue = null)
    // Your rank and this week's target, live, in the fight rather than on a profile page.
    val meta by graph.meta.state.collectAsStateWithLifecycle(initialValue = null)
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())

    var lastHit by remember { mutableStateOf<HudEvent.Hit?>(null) }
    val jolt = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is HudEvent.Hit -> {
                    lastHit = e
                    if (!reduceMotion) {
                        jolt.snapTo(1f)
                        jolt.animateTo(0f, tween(180))
                        // Weight the kick by the damage. A scraped rep and a critical used to
                        // shake the screen by exactly the same amount, which taught the player
                        // nothing and made every rep feel the same in the hand.
                        val kick = (e.damage / 130f).coerceIn(0.25f, 1f) * Motion.shakePx
                        launch {
                            shake.snapTo(kick)
                            shake.animateTo(-kick * 0.6f, tween(60))
                            shake.animateTo(0f, tween(90))
                        }
                    }
                }
                is HudEvent.PhaseShift -> if (!reduceMotion) {
                    repeat(3) { shake.animateTo(Motion.shakePx, tween(Motion.shakeMs / 6)); shake.animateTo(-Motion.shakePx, tween(Motion.shakeMs / 6)) }
                    shake.animateTo(0f, tween(Motion.shakeMs / 6))
                }
                is HudEvent.Gesture -> lastGesture = e
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) { vm.finished.collect { id -> onSummary(id) } }

    val s = state ?: return

    // Five seconds to put the phone down and walk into frame, over the live camera so you can see
    // whether you fit. The engine calibrates underneath: calibration needs you in shot too, so the
    // two want the same five seconds.
    var readying by remember { mutableStateOf(true) }
    val exerciseNames by graph.config.exercises.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Ground)) {
        when (s.phase) {
            Phase.CALIBRATING -> {
                // Full frame from the first second. Getting your whole body in shot is the thing
                // calibration is asking for, and it is very hard to do against a corner inset.
                CameraStage(camera, Modifier.fillMaxSize())
                CameraScrims(top = 0f, bottom = 0.28f)
                ExoRig(
                    landmarks, s.fatigue.band, 0f, null, Modifier.fillMaxSize(),
                    level = meta?.progress?.level ?: 1, sourceAspect = sourceAspect,
                )
                CalibrationOverlay(
                    cue = s.cue, progress = s.calibProgress,
                    tooFar = s.calib == com.clashfit.core.model.CalibState.TOO_FAR,
                    tooClose = s.calib == com.clashfit.core.model.CalibState.TOO_CLOSE,
                    exerciseId = s.exerciseId,
                )
                ExitCorner(onExit)
            }
            Phase.FIGHTING, Phase.FRAMING_LOST -> {
                FightLayout(s, vm, lastHit, jolt.value, shake.value, paused, reduceMotion, camera, landmarks, meta, settings, sourceAspect, gestureHold, lastGesture)
            }
            Phase.REST -> RestPanel(s, restLeft, onSkip = vm::skipRest, onStop = vm::stop, gestureHold = gestureHold, lastGesture = lastGesture)
            Phase.DEAD -> EndPanel(s, onSummary = { /* wait for persistence */ }, onExit = onExit)
        }
        if (s.ended && s.phase != Phase.DEAD) EndPanel(s, onSummary = {}, onExit = onExit)
        // Over the top of whatever is showing: the fight has to end on screen, not on the next one.
        BossDownStamp(s.combat.dead, reduceMotion)

        if (readying && !s.ended) {
            GetReady(
                exerciseName = exerciseNames[args.exerciseId]?.name
                    ?: args.exerciseId.replace('_', ' ').replaceFirstChar { it.uppercase() },
                reduceMotion = reduceMotion,
            ) { readying = false }
        }

        // A replayed session says so, the whole time, in the frame. A judge who is told this is a
        // recording is fine with it; a judge who works it out on their own is not.
        if (args.replay) {
            Box(
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.small).background(Heavy.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    "REPLAY · recorded set, not the camera",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ground,
                )
            }
        }
    }
}

@Composable
private fun FightLayout(
    s: SessionState, vm: SessionViewModel, lastHit: HudEvent.Hit?, jolt: Float, shake: Float,
    paused: Boolean, reduceMotion: Boolean, camera: CameraPreviewSource?, landmarks: Landmarks?,
    meta: MetaState?, settings: Prefs.Settings, sourceAspect: Float?,
    gestureHold: Pair<HandGesture, Float>? = null, lastGesture: HudEvent.Gesture? = null,
) {
    val prone = vm.isProne
    val link by vm.link.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // You, full frame, with the suit on. The camera is the referee and this is the proof of
        // it: the same landmarks the scorer reads, drawn on your own body as armour that lights up
        // when a rep lands clean and changes colour as you tire.
        CameraStage(camera, Modifier.fillMaxSize())
        CameraScrims()
        ExoRig(
            landmarks, s.fatigue.band, jolt, lastHit?.verdict, Modifier.fillMaxSize(),
            level = meta?.progress?.level ?: 1, sourceAspect = sourceAspect,
        )

        // The boss stands in the room with you, in the upper part of the frame so it never covers
        // the body it is reacting to. Its renderer draws with a transparent background.
        BossStage(
            s.combat, jolt, shake,
            allow3d = settings.boss3d,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(if (prone) 0.52f else 0.66f)
                .fillMaxHeight(if (prone) 0.34f else 0.44f)
                .padding(top = 88.dp),
        )
        // Sparks leave the point of contact, then the numeral, then a stamp if it earned one.
        ImpactBurst(lastHit, reduceMotion, Modifier.fillMaxSize())
        DamageNumeral(lastHit, Modifier.align(Alignment.Center).padding(bottom = 60.dp))
        CritStamp(lastHit, reduceMotion, Modifier.align(Alignment.Center).padding(bottom = 190.dp))

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            // Clear of the pause target, which is a 72dp square pinned to the top-left with 12dp
            // of padding and drawn after this. Without the inset it sat on top of the boss's name.
            if (!prone) BossHeader(s.combat, Modifier.padding(start = PAUSE_TARGET_INSET), timeLeftMs = s.timeLeftMs)
            link?.let { l -> Spacer(Modifier.height(8.dp)); LinkStrip(l, myReps = s.reps, myDamage = s.playerDamage) }
            Spacer(Modifier.weight(1f))
            if (s.phase == Phase.FRAMING_LOST) { FramingLostBanner(s.cue); Spacer(Modifier.height(12.dp)) }
            else s.cue?.let { cue ->
                Text(cue, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = InkMuted, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (prone) { BossHeader(s.combat, timeLeftMs = s.timeLeftMs); Spacer(Modifier.height(12.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                RepCounter(if (s.mode == com.clashfit.core.model.GameMode.SURVIVAL) s.reps else s.setReps, Modifier.weight(1.1f), label = if (s.wave > 1) "Reps · Wave ${s.wave}" else "Reps")
                FatigueTile(s.fatigue.band, Modifier.weight(1f))
                ComboRail(s.combat.comboMultiplier, s.combat.comboStreak, Modifier.weight(1f))
            }
        }
        HitFlash(lastHit, reduceMotion)
        Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp)) {
            PauseTarget(paused, onToggle = { if (paused) vm.resume() else vm.pause() })
        }
        // Top right, where the corner skeleton inset used to sit before the camera took the frame.
        RankChip(meta, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 96.dp, end = 12.dp))
        if (paused) PausedOverlay(onResume = vm::resume, onStop = vm::stop)
        // The hand: a ring that fills while a shape is held, then the word for what it did. Drawn
        // over the pause overlay too, because an open palm is how you resume from two metres away.
        // Mid-right, beside the boss: the one place on the HUD nothing else needs, so a held hand
        // never hides the health bar or the rank while the ring fills.
        GestureRing(gestureHold, resting = false, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 120.dp))
        GestureToast(lastGesture, Modifier.align(Alignment.Center).padding(bottom = 300.dp))
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ground.copy(alpha = 0.86f)).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Paused", style = MaterialTheme.typography.displayMedium, color = Ink)
        Text("The boss is frozen. Your fatigue baseline is held.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Resume", Modifier.fillMaxWidth(), onClick = onResume)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Stop and save", Modifier.fillMaxWidth(), onClick = onStop)
    }
}

/** The moment the coach earns its place. The player is on the floor; the line is spoken too. */
@Composable
fun RestPanel(
    s: SessionState, restLeft: Int?, onSkip: () -> Unit, onStop: () -> Unit,
    gestureHold: Pair<HandGesture, Float>? = null, lastGesture: HudEvent.Gesture? = null,
) {
    val t = s.telemetry
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Kicker("Rest · set ${s.setIndex}")
            // A thumb up or a fist from the floor starts the next set without walking to the phone.
            GestureRing(gestureHold, resting = true)
        }
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
        AppCard(Modifier.fillMaxWidth(), padding = 20) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coach", style = MaterialTheme.typography.labelMedium, color = Ember)
                Text(coach?.coachLine ?: "…", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
        }
        AppCard(Modifier.fillMaxWidth(), padding = 20) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.combat.bossName, style = MaterialTheme.typography.labelMedium, color = InkFaint)
                Text(coach?.bossLine ?: "…", style = MaterialTheme.typography.headlineSmall, color = Ember)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Boss at ${(s.combat.hpPct * 100).toInt()}%${coach?.let { " · " + it.source.name.lowercase() + " coach" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        PrimaryButton("Next set", Modifier.fillMaxWidth(), onClick = onSkip)
        SecondaryButton("Stop and save", Modifier.fillMaxWidth(), onClick = onStop)
    }
    GestureToast(lastGesture, Modifier.align(Alignment.Center))
    }
}

/** Victory, or "you stopped". There is no fail state for being tired. */
@Composable
fun EndPanel(s: SessionState, onSummary: () -> Unit, onExit: () -> Unit) {
    val won = s.endReason == EndReason.BOSS_DOWN || s.endReason == EndReason.GAME_WON
    val title = when (s.endReason) {
        EndReason.BOSS_DOWN -> "Boss down"
        EndReason.GAME_WON -> "You won"
        EndReason.GAME_LOST -> "It caught you"
        EndReason.TIME -> "Time"
        EndReason.STOPPED, EndReason.WALKED_AWAY, null -> "You stopped"
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
        SecondaryButton("Home", Modifier.fillMaxWidth(), onClick = onExit)
    }
}

@Composable
private fun ExitCorner(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        IconButton(onExit, Modifier.align(Alignment.TopStart).size(48.dp)) {
            Icon(AppIcons.Close, contentDescription = "Back", tint = Ink)
        }
    }
}

/**
 * A dark wash at the top and bottom of the camera, behind the text.
 *
 * Every number on this HUD was designed to sit on a near-black background. It now sits on a live
 * picture of whatever room you are in, and a white health bar over a sunlit wall is unreadable at
 * the two metres this screen is meant to be read from. These darken only the bands the text
 * occupies and leave the middle — where your body is — untouched.
 */
@Composable
private fun BoxScope.CameraScrims(top: Float = 0.30f, bottom: Float = 0.34f) {
    if (top > 0f) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(top)
                .background(Brush.verticalGradient(listOf(Ground.copy(alpha = 0.82f), Ground.copy(alpha = 0f)))),
        )
    }
    if (bottom > 0f) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(bottom)
                .background(Brush.verticalGradient(listOf(Ground.copy(alpha = 0f), Ground.copy(alpha = 0.88f)))),
        )
    }
}
