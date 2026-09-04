package com.clashfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.GameMode
import com.clashfit.meta.Levels
import com.clashfit.ui.components.ModeSigil
import com.clashfit.ui.components.RankInsignia
import com.clashfit.ui.theme.ClashFitTheme
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.InkMuted
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.clashfit.ui.components.FamilyMark
import com.clashfit.core.model.Family

/**
 * The drawn marks, on a sheet, at the sizes they are used.
 *
 * Both of these earn their detail from a number — a rank from your level, a sigil from its mode's
 * kind and position. That makes them exactly the kind of thing that looks right for the two cases
 * anybody checks and wrong for the fourteen nobody does. Rendering the whole set is the only way
 * to see that no two modes share a mark and that every rank tier is distinguishable from the one
 * below it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1400dp-xhdpi")
class GameArtScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rankLadder() {
        val levels = listOf(1, 2, 3, 5, 7, 8, 10, 12, 16, 20, 30, 40)
        compose.setContent {
            ClashFitTheme {
                Column(
                    Modifier.fillMaxSize().background(Ground).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    levels.chunked(4).forEach { row ->
                        Row(Modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { level ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RankInsignia(level, size = 68)
                                    Text(
                                        "$level · ${Levels.forXp(Levels.xpForLevel(level)).title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/70-rank-ladder.png")
    }

    @Test
    fun familyMarks() {
        compose.setContent {
            ClashFitTheme {
                Row(
                    Modifier.fillMaxSize().background(Ground).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Family.entries.forEach { family ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FamilyMark(family, size = 56)
                            Text(
                                family.name.replace('_', ' ').lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/72-family-marks.png")
    }

    @Test
    fun modeSigils() {
        compose.setContent {
            ClashFitTheme {
                Column(
                    Modifier.fillMaxSize().background(Ground).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GameMode.grid.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { mode ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ModeSigil(mode, size = 56, enabled = mode.enabled)
                                    Text(
                                        mode.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/71-mode-sigils.png")
    }
}
