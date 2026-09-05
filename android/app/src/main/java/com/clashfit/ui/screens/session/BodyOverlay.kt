package com.clashfit.ui.screens.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Verdict
import com.clashfit.perception.ExercisePoints
import com.clashfit.perception.ExoRig

/**
 * What is drawn on the player's body: the measured points, or the suit.
 *
 * Two overlays read the same landmarks and say different things. The dots are the joints the
 * referee is actually reading, with the angle it scored printed beside them — the honest view, and
 * the one that answers "how do you know?" without a word. The suit is the same data dressed as
 * armour: it reads better across a room and it is what the deck and the landing page describe.
 *
 * Both are worth having, and which one is right depends on who is holding the phone, so it is a
 * switch rather than a decision. The dots are the default because the honest view should be what
 * ships. One function so the two can never drift apart or both draw at once.
 */
@Composable
fun BodyOverlay(
    landmarks: Landmarks?,
    spec: ExerciseSpec?,
    angleLeft: Float,
    angleRight: Float,
    band: FatigueBand,
    flash: Float,
    verdict: Verdict?,
    level: Int,
    sourceAspect: Float?,
    exoSuit: Boolean,
    modifier: Modifier = Modifier,
) {
    if (exoSuit) {
        ExoRig(landmarks, band, flash, verdict, modifier, level = level, sourceAspect = sourceAspect)
    } else {
        ExercisePoints(landmarks, spec, angleLeft, angleRight, modifier, sourceAspect = sourceAspect)
    }
}
