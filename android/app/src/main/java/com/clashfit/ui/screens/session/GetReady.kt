package com.clashfit.ui.screens.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import kotlinx.coroutines.delay

/**
 * Five seconds to put the phone down and walk into frame.
 *
 * Without it a session began the instant the screen appeared, which meant the first thing the
 * camera ever saw was an arm reaching away from the lens. You cannot be in position and holding
 * the phone at the same time, and the app is useless if you are holding it.
 *
 * The camera and the rig are already live underneath, so the count is also the moment you use to
 * check you actually fit in the shot.
 *
 * @param seconds how long to count
 * @param onDone called once, when the count reaches zero
 */
@Composable
fun GetReady(
    exerciseName: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    seconds: Int = 5,
    onDone: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    val pop = remember { Animatable(1f) }

    LaunchedEffect(seconds) {
        remaining = seconds
        while (remaining > 0) {
            if (!reduceMotion) {
                pop.snapTo(1.35f)
                pop.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(1000)
            remaining -= 1
        }
        onDone()
    }

    if (remaining <= 0) return

    Box(
        modifier.fillMaxSize().background(Ground.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "GET IN FRAME",
                style = MaterialTheme.typography.labelLarge,
                color = Ember,
            )
            Text(
                "$remaining",
                fontSize = 148.sp,
                lineHeight = 152.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                color = Ink,
                modifier = Modifier.scale(if (reduceMotion) 1f else pop.value),
            )
            Text(
                exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
            Text(
                "Prop the phone up and step back until your whole body fits.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, start = 32.dp, end = 32.dp).alpha(0.95f),
            )
        }
    }
}
