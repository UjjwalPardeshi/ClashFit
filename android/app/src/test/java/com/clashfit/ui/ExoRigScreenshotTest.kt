package com.clashfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Verdict
import com.clashfit.perception.ExoRig
import com.clashfit.ui.theme.ClashFitTheme
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.InkMuted
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The exo-suit, over a standing figure.
 *
 * The rig only exists on top of a live camera, so on a phone it can only be judged mid-set with
 * your arms full. These renders put it over a plain background with a fixed synthetic pose, which
 * is the only way to see whether a plate lands on the limb it belongs to and whether the tiers a
 * player unlocks are actually distinguishable from each other.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ExoRigScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /**
     * A standing figure in normalised image space. Written out rather than captured so the render
     * is identical every run and a change to the rig is the only thing that can move a pixel.
     */
    private fun standing(): Landmarks {
        val pts = mutableMapOf(
            0 to (0.500f to 0.130f),   // nose
            11 to (0.395f to 0.265f),  // shoulders
            12 to (0.605f to 0.265f),
            13 to (0.345f to 0.410f),  // elbows
            14 to (0.655f to 0.410f),
            15 to (0.320f to 0.550f),  // wrists
            16 to (0.680f to 0.550f),
            23 to (0.440f to 0.520f),  // hips
            24 to (0.560f to 0.520f),
            25 to (0.428f to 0.700f),  // knees
            26 to (0.572f to 0.700f),
            27 to (0.418f to 0.880f),  // ankles
            28 to (0.582f to 0.880f),
        )
        return (0 until 33).map { i ->
            val p = pts[i]
            if (p == null) Landmark(0.5f, 0.5f, 0f, visibility = 0f)
            else Landmark(p.first, p.second, 0f, visibility = 0.95f)
        }
    }

    private fun panel(label: String, level: Int, band: FatigueBand, flash: Float, verdict: Verdict?) =
        Triple(label, level, Triple(band, flash, verdict))

    @Test
    fun tiers() {
        val cases = listOf(
            panel("Level 1 · fresh", 1, FatigueBand.FRESH, 0f, null),
            panel("Level 7 · working", 7, FatigueBand.WORKING, 0f, null),
            panel("Level 12 · gassed", 12, FatigueBand.GASSED, 0f, null),
            panel("Level 20 · clean rep", 20, FatigueBand.WORKING, 1f, Verdict.CLEAN),
        )
        compose.setContent {
            ClashFitTheme {
                Column(Modifier.fillMaxSize().background(Ground)) {
                    cases.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { (label, level, rest) ->
                                val (band, flash, verdict) = rest
                                Box(
                                    Modifier.weight(1f).aspectRatio(0.62f).padding(4.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    ExoRig(standing(), band, flash, verdict, Modifier.fillMaxSize(), level = level)
                                    Text(label, color = InkMuted, modifier = Modifier.padding(bottom = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/56-exo-tiers.png")
    }
}
