package com.clashfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.Family
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import com.clashfit.ui.screens.session.WorkoutHud
import com.clashfit.ui.theme.ClashFitTheme
import com.clashfit.ui.theme.Ground
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The workout HUD, which is the whole difference between the two tabs.
 *
 * Underneath it is the same session the boss fight runs, so nothing about the counting needs a
 * second test — but the screen is the point of the mode, and it cannot be reached by driving the
 * app because getting past calibration needs a real body in front of a real camera. Rendering it
 * directly is the only way anybody looks at it before a demo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h845dp-xxxhdpi")
class WorkoutHudScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun rep(i: Int, form: Float, verdict: Verdict) = RepRecord(
        repIndex = i, exerciseId = "squat", family = Family.REP_CYCLE,
        tStartMs = 0L, tEndMs = 1_000L,
        formScore = form, depth = form, rom = form, tempo = form, alignment = form,
        reason = "", verdict = verdict, concentricVelocity = 0.8f,
        fatigue = FatigueState(
            value = 0.4f, band = FatigueBand.WORKING,
            losses = emptyMap(), baselineReps = 3, hasBaseline = true,
        ),
        damage = 0, combo = 1f,
    )

    private fun shot(
        name: String,
        log: List<RepRecord>,
        band: FatigueBand,
        coachLine: String?,
        cue: String?,
        framingLost: Boolean = false,
    ) {
        compose.setContent {
            ClashFitTheme {
                // Ground stands in for the camera, which cannot render on the JVM.
                Box(Modifier.fillMaxSize().background(Ground)) {
                    WorkoutHud(
                        exerciseName = "Squat",
                        setNumber = 2,
                        reps = log.size,
                        lastRep = log.lastOrNull(),
                        log = log,
                        band = band,
                        coachLine = coachLine,
                        cue = cue,
                        framingLost = framingLost,
                        onEndSet = {},
                    )
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun midSet() = shot(
        "a0-workout-midset",
        log = listOf(
            rep(1, 0.92f, Verdict.CLEAN), rep(2, 0.90f, Verdict.CLEAN), rep(3, 0.88f, Verdict.CLEAN),
            rep(4, 0.74f, Verdict.OK), rep(5, 0.79f, Verdict.OK), rep(6, 0.61f, Verdict.SHALLOW),
            rep(7, 0.71f, Verdict.OK),
        ),
        band = FatigueBand.WORKING,
        coachLine = "Depth is going first. Sit back further before the knees travel.",
        cue = "Chest up",
    )

    @Test
    fun firstRep() = shot(
        "a1-workout-first-rep",
        log = listOf(rep(1, 0.95f, Verdict.CLEAN)),
        band = FatigueBand.FRESH,
        coachLine = "That is the depth. Hold it.",
        cue = null,
    )

    @Test
    fun beforeAnyRep() = shot(
        "a2-workout-empty",
        log = emptyList(),
        band = FatigueBand.FRESH,
        coachLine = null,
        cue = "Stand side-on, feet shoulder width.",
    )
}
