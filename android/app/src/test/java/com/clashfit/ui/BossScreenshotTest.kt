package com.clashfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.clashfit.core.model.CombatState
import com.clashfit.ui.screens.session.BossFigure
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
 * THE PACEMAKER in every state the asset brief calls for, on one sheet, so the character can be
 * judged without a phone and without fighting a boss to reach phase three.
 * docs/15-ASSET-BRIEF.md §1
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BossScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun boss(
        hpPct: Float,
        phase: String,
        staggered: Boolean = false,
        dead: Boolean = false,
    ) = CombatState(
        bossId = "pacemaker",
        bossName = "THE PACEMAKER",
        hp = (3000 * hpPct).toInt(),
        maxHp = 3000,
        phaseLabel = phase,
        phaseModifier = 1f,
        reps = 12,
        totalDamage = 1400,
        dead = dead,
        comboStreak = 3,
        comboMultiplier = 1.2f,
        lastDamage = 220,
        staggered = staggered,
        mercyActive = false,
    )

    @Test
    fun everyBossState() {
        val states = listOf(
            "Idle · phase 1" to Triple(boss(1.0f, "phase1"), 0f, 0f),
            "Hit" to Triple(boss(0.85f, "phase1"), 1f, 6f),
            "Enrage · 60%" to Triple(boss(0.55f, "enrage"), 0f, 0f),
            "Desperation · 20%" to Triple(boss(0.20f, "desperation"), 0f, 0f),
            "Staggered" to Triple(boss(0.40f, "enrage", staggered = true), 0f, 0f),
            "Death" to Triple(boss(0.0f, "desperation", dead = true), 0f, 0f),
        )
        compose.setContent {
            ClashFitTheme {
                Column(Modifier.fillMaxSize().background(Ground).padding(8.dp)) {
                    states.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, s) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                    Box(Modifier.fillMaxWidth().height(200.dp)) {
                                        BossFigure(s.first, s.second, s.third, Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/19-boss-states.png")
    }
}
