package com.clashfit.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.clashfit.perception.gesture.HandGesture
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.RuleSoft
import kotlinx.coroutines.delay

/** What a held shape is about to do, in one word, so the player can let go if it is the wrong one. */
fun HandGesture.meaning(resting: Boolean): String? = when (this) {
    HandGesture.OPEN_PALM -> if (resting) null else "pause"
    HandGesture.THUMB_UP -> if (resting) "next set" else "set done"
    HandGesture.CLOSED_FIST -> if (resting) "next set" else null
    else -> null
}

/**
 * A ring that fills while a hand shape is held.
 *
 * A command that fires after six hundred milliseconds with no warning feels like the phone
 * misbehaving. A ring that fills over those six hundred milliseconds, with the word for what is
 * about to happen under it, feels like the phone listening — and gives the player the half second
 * they need to lower the hand if that was not what they meant.
 */
@Composable
fun GestureRing(hold: Pair<HandGesture, Float>?, resting: Boolean, modifier: Modifier = Modifier) {
    val meaning = hold?.first?.meaning(resting)
    AnimatedVisibility(visible = hold != null && meaning != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val progress = hold?.second ?: 0f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(MaterialTheme.shapes.extraLarge).background(Panel.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(48.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f
                    val box = Size(size.width - stroke, size.height - stroke)
                    drawArc(RuleSoft, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                    drawArc(
                        Ember, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), box,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    when (hold?.first) {
                        HandGesture.OPEN_PALM -> "✋"
                        HandGesture.THUMB_UP -> "👍"
                        HandGesture.CLOSED_FIST -> "✊"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                meaning ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The word for what just happened, shown for a moment where the eye already is.
 *
 * Fired-and-forgotten like the other HUD events: it carries the event so a second identical
 * gesture shows again rather than being deduplicated away.
 */
@Composable
fun GestureToast(event: HudEvent.Gesture?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<HudEvent.Gesture?>(null) }
    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect
        shown = event
        delay(1400)
        if (shown === event) shown = null
    }
    AnimatedVisibility(visible = shown != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier.clip(MaterialTheme.shapes.large).background(Ink.copy(alpha = 0.92f)).padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(shown?.label ?: "", style = MaterialTheme.typography.titleMedium, color = Ground)
        }
    }
}
