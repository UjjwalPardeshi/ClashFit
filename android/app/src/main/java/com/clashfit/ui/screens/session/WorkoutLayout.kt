package com.clashfit.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Phase
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SessionState
import com.clashfit.core.model.Verdict
import com.clashfit.data.Prefs
import com.clashfit.perception.CameraPreviewSource
import com.clashfit.perception.CameraStage
import com.clashfit.perception.gesture.HandGesture
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.color
import com.clashfit.ui.components.label
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.RuleSoft

/**
 * A set in a gym, counted by the referee that counts a boss fight.
 *
 * Same engine underneath — the same state machine, the same depth gate, the same form score, the
 * same fatigue estimate. Only the screen is different, and it is different because the screen is
 * the entire point of the mode: somebody logging a working set does not want a monster's health
 * bar between them and their rep count, and a damage numeral is a lie about what just happened.
 * Nothing here is themed. There is no boss, no HP, no combo, no XP.
 *
 * What is left is what a lifter actually looks at between reps: how many, how the last one scored,
 * how the set is holding up, and a way to stop. The camera and the drawn landmarks stay, because
 * they are not decoration — they are the proof that the number was measured rather than typed.
 */
@Composable
fun WorkoutLayout(
    s: SessionState,
    vm: SessionViewModel,
    paused: Boolean,
    camera: CameraPreviewSource?,
    landmarks: com.clashfit.core.model.Landmarks?,
    settings: Prefs.Settings,
    sourceAspect: Float?,
    spec: ExerciseSpec?,
    exerciseName: String,
    gestureHold: Pair<HandGesture, Float>? = null,
    lastGesture: HudEvent.Gesture? = null,
    onEndSet: () -> Unit,
) {
    // The reps of this set, kept here rather than asked of the engine: the engine already owns the
    // record it will bank, and the strip below only needs enough to show what has happened so far.
    val log = rememberRepLog(s.lastRep)

    Box(Modifier.fillMaxSize()) {
        CameraStage(camera, Modifier.fillMaxSize())
        CameraScrims(top = 0.22f, bottom = 0.40f)
        BodyOverlay(
            landmarks, spec, s.angleLeftDeg, s.angleRightDeg,
            band = s.fatigue.band,
            flash = 0f,
            verdict = s.lastRep?.verdict,
            level = 1,
            sourceAspect = sourceAspect,
            modifier = Modifier.fillMaxSize(),
        )

        WorkoutHud(
            exerciseName = exerciseName,
            setNumber = s.setIndex + 1,
            reps = s.setReps,
            lastRep = s.lastRep,
            log = log,
            band = s.fatigue.band,
            coachLine = s.coach?.coachLine,
            cue = s.cue,
            framingLost = s.phase == Phase.FRAMING_LOST,
            onEndSet = onEndSet,
        )

        Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp)) {
            PauseTarget(paused, onToggle = { if (paused) vm.resume() else vm.pause() })
        }
        if (paused) PausedOverlay(onResume = vm::resume, onStop = onEndSet)
        GestureRing(gestureHold, resting = false, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 160.dp))
        GestureToast(lastGesture, Modifier.align(Alignment.Center).padding(bottom = 280.dp))
    }
}

/**
 * Everything a lifter reads between reps, over whatever is behind it.
 *
 * Separated from [WorkoutLayout] so it can be rendered without a camera or a view model — which is
 * the only way this screen gets looked at before somebody stands in front of a phone with a
 * barbell, and the reason the fight HUD is split the same way.
 */
@Composable
fun BoxScope.WorkoutHud(
    exerciseName: String,
    setNumber: Int,
    reps: Int,
    lastRep: RepRecord?,
    log: List<RepRecord>,
    band: com.clashfit.core.model.FatigueBand,
    coachLine: String?,
    cue: String?,
    framingLost: Boolean,
    onEndSet: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
    ) {
            // What you are doing and which set it is. Plain, small, and out of the way — the
            // heading on a log page, not a title card.
            Column(Modifier.padding(start = PAUSE_TARGET_INSET)) {
                Text(
                    "YOUR COACH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember,
                )
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Set $setNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                )
            }

            Spacer(Modifier.weight(1f))

            // The coach's line, and under it the immediate cue.
            //
            // This is the difference between the two tabs in one place. A fight has a referee: it
            // rules on a rep and says nothing else, because a judge that coaches you mid-bout is
            // not a judge. A workout has a coach: it says what to change and why, out of the same
            // measurements, and it says it while you can still act on it.
            if (framingLost) {
                FramingLostBanner(cue)
                Spacer(Modifier.height(12.dp))
            } else {
                coachLine?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                cue?.let { c ->
                    Text(
                        c,
                        fontSize = 26.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium, color = InkMuted,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            // Every rep of this set, in order, as a bar coloured by how it scored. Twelve reps of
            // a working set fit across a phone, and the shape of the row is the thing worth
            // seeing: a set that started clean and drifted looks like a set that drifted.
            if (log.isNotEmpty()) {
                RepLogStrip(log)
                Spacer(Modifier.height(12.dp))
            }

            // One height for the three, so they read as a row of readouts rather than as three
            // cards that happen to be near each other. The rep counter sets it, because the
            // numeral is the tallest thing here and the one the eye goes to first.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RepCounter(reps, Modifier.weight(1.2f))
                LastRepTile(lastRep, Modifier.weight(1f).fillMaxHeight())
                FatigueTile(band, Modifier.weight(1f).fillMaxHeight())
            }

            Spacer(Modifier.height(14.dp))
            // The set ends when the person lifting says it does, so the way to say so is a button
            // rather than a menu behind a pause. It banks the set and hands you back the workout,
            // where the next one is one tap away.
            PrimaryButton("End set", Modifier.fillMaxWidth(), onClick = onEndSet)
    }
}

/**
 * The reps seen so far this set.
 *
 * Accumulated from [SessionState.lastRep] rather than read out of the engine, and keyed on the rep
 * index so a recomposition that re-delivers the same record cannot log it twice.
 */
@Composable
private fun rememberRepLog(lastRep: RepRecord?): SnapshotStateList<RepRecord> {
    val log = remember { mutableListOf<RepRecord>().toMutableStateList() }
    var lastSeen by remember { mutableStateOf(-1) }
    if (lastRep != null && lastRep.repIndex != lastSeen) {
        lastSeen = lastRep.repIndex
        log.add(lastRep)
    }
    return log
}

/** One bar per rep, height and colour from its form score. The newest is on the right. */
@Composable
private fun RepLogStrip(log: List<RepRecord>, modifier: Modifier = Modifier) {
    // The last sixteen. Beyond that the bars are too thin to read a colour off, and the reps that
    // matter for "how is this set going" are the recent ones anyway.
    val shown = log.takeLast(16)
    Row(
        modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        shown.forEach { rep ->
            val height = (14 + 30 * rep.formScore.coerceIn(0f, 1f)).dp
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(CircleShape)
                    .background(rep.verdict.color()),
            )
        }
        // Keep the bars the width they would be at sixteen, so a strip does not resize under the
        // eye every time a rep lands.
        repeat((16 - shown.size).coerceAtLeast(0)) {
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(RuleSoft))
        }
    }
}

/** How the last rep scored, in the words the rest of the app uses for a rep. */
@Composable
private fun LastRepTile(rep: RepRecord?, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 12, container = Panel) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                rep?.let { "${(it.formScore * 100).toInt()}%" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                color = rep?.verdict?.color() ?: InkFaint,
                maxLines = 1,
            )
            Text(
                rep?.verdict?.label() ?: "Last rep",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
