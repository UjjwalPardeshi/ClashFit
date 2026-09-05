package com.clashfit.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clashfit.core.model.CombatState
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Verdict
import com.clashfit.ui.screens.session.BossFigure
import com.clashfit.ui.screens.session.BossHeader
import com.clashfit.ui.screens.session.ComboRail
import com.clashfit.ui.screens.session.DamageNumeral
import com.clashfit.ui.screens.session.FatigueTile
import com.clashfit.ui.screens.session.HudEvent
import com.clashfit.ui.screens.session.PAUSE_TARGET_INSET
import com.clashfit.ui.screens.session.PauseTarget
import com.clashfit.ui.screens.session.PlayerBar
import com.clashfit.ui.screens.session.RepCounter
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
import com.clashfit.meta.LevelProgress
import com.clashfit.meta.Metric
import com.clashfit.meta.MetaState
import com.clashfit.meta.WeeklyChallenge
import com.clashfit.meta.WeeklyProgress
import com.clashfit.ui.screens.session.RankChip
import com.clashfit.ui.screens.session.LinkStripPreview

/**
 * The fight, rendered without a camera.
 *
 * This is the screen the whole app exists for, and it was the one screen nobody could review
 * without holding a phone and doing squats — which meant a layout problem in it would only ever
 * be found mid-demo. The camera preview cannot exist on the JVM, but everything drawn on top of
 * it can, so these renders put the real HUD components in the real arrangement over a dark plate
 * standing in for the preview.
 *
 * The arrangement below deliberately mirrors `FightLayout` in SessionScreen.kt. If that layout
 * changes, change this to match, or these renders quietly stop telling the truth.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FightHudScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** A player mid-progression, so the rank chip has something to show. */
    private val META = MetaState(
        xp = 4_180,
        progress = LevelProgress(level = 7, title = "Fighter", xpIntoLevel = 340, xpForLevel = 620),
        unlocked = emptyList(),
        weekly = WeeklyProgress(
            challenge = WeeklyChallenge(
                weekKey = "2026-W36", title = "Clean Form",
                description = "150 clean reps", metric = Metric.CLEAN_REPS, target = 150,
            ),
            value = 94,
            completedAtMs = null,
        ),
    )

    private fun combat(
        hp: Int,
        maxHp: Int = 1200,
        playerHp: Int = 100,
        playerMaxHp: Int = 100,
        phase: String = "opening",
        combo: Float = 1f,
        streak: Int = 0,
        staggered: Boolean = false,
        dead: Boolean = false,
        damage: Int = 0,
    ) = CombatState(
        bossId = "pacemaker", bossName = "The Pacemaker", hp = hp, maxHp = maxHp,
        playerHp = playerHp, playerMaxHp = playerMaxHp,
        phaseLabel = phase, phaseModifier = 1f, reps = 12, totalDamage = damage,
        dead = dead, comboStreak = streak, comboMultiplier = combo, lastDamage = null,
        staggered = staggered, mercyActive = false,
    )

    private fun shot(
        name: String,
        state: CombatState,
        band: FatigueBand,
        reps: Int,
        cue: String?,
        hit: HudEvent.Hit? = null,
        timeLeftMs: Long? = null,
        lastPlayerHit: Int? = null,
        nextAttackInMs: Long? = null,
    ) {
        compose.setContent {
            ClashFitTheme {
                // Ground stands in for the camera preview, which cannot render on the JVM.
                Box(Modifier.fillMaxSize().background(Ground)) {
                    BossFigure(state, jolt = 0f, shake = 0f, Modifier.fillMaxSize().padding(top = 90.dp, bottom = 160.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 12.dp).size(width = 110.dp, height = 150.dp))
                    DamageNumeral(hit, Modifier.align(Alignment.Center).padding(bottom = 60.dp))

                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                        BossHeader(state, Modifier.padding(start = PAUSE_TARGET_INSET), timeLeftMs = timeLeftMs)
                        Spacer(Modifier.height(8.dp))
                        PlayerBar(state.copy(nextAttackInMs = nextAttackInMs, attackIntervalMs = nextAttackInMs?.let { 5_000L }), lastPlayerHit?.let { HudEvent.PlayerHit(it, state.playerHpPct) })
                        Spacer(Modifier.weight(1f))
                        cue?.let {
                            Text(
                                it, fontSize = 28.sp, lineHeight = 32.sp,
                                fontWeight = FontWeight.Medium, color = InkMuted,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            RepCounter(reps, Modifier.weight(1.1f))
                            FatigueTile(band, Modifier.weight(1f))
                            ComboRail(state.comboMultiplier, state.comboStreak, Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp)) {
                        PauseTarget(paused = false, onToggle = {})
                    }
                    RankChip(META, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 96.dp, end = 12.dp))
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /** The first rep of a fight: full health, no combo, nothing earned yet. */
    @Test fun opening() = shot(
        "50-fight-opening", combat(hp = 1200), FatigueBand.FRESH, reps = 0,
        cue = "Stand side-on, feet shoulder width.",
    )

    /** Mid-fight, going well: a clean hit landing on a four-rep combo. */
    @Test fun landing() = shot(
        "51-fight-landing", combat(hp = 640, phase = "enrage", combo = 1.6f, streak = 4, damage = 560),
        FatigueBand.WORKING, reps = 14, cue = "Depth held. Same tempo.",
        hit = HudEvent.Hit(id = 1, damage = 96, verdict = Verdict.CLEAN, combo = 1.6f),
    )

    /** Tired and slipping: the state the fatigue model exists to catch. */
    @Test fun gassed() = shot(
        "52-fight-gassed", combat(hp = 310, phase = "desperation", combo = 1f, streak = 0, damage = 890),
        FatigueBand.GASSED, reps = 27, cue = "You are stopping short. Rest forty seconds.",
    )

    /** Staggered: the shell is open and the next rep is worth double. */
    @Test fun staggered() = shot(
        "53-fight-staggered", combat(hp = 180, phase = "desperation", combo = 2.4f, streak = 9, staggered = true, damage = 1020),
        FatigueBand.FADING, reps = 31, cue = "Staggered. Hit it now.",
    )

    /** A duel: the rope, with you ahead. */
    @Test fun duelAhead() {
        compose.setContent {
            ClashFitTheme {
                Box(Modifier.fillMaxSize().background(Ground), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(16.dp)) {
                        LinkStripPreview(myDamage = 640, theirDamage = 415)
                        Spacer(Modifier.height(24.dp))
                        LinkStripPreview(myDamage = 380, theirDamage = 902)
                        Spacer(Modifier.height(24.dp))
                        LinkStripPreview(myDamage = 0, theirDamage = 0)
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/55-duel-rope.png")
    }

    /** A timed mode, so the clock is in frame beside the health bar. */
    @Test fun timed() = shot(
        "54-fight-timed", combat(hp = 820, combo = 1.2f, streak = 2, damage = 380),
        FatigueBand.WORKING, reps = 9, cue = null, timeLeftMs = 41_000,
    )

    /** Player bar showing damage taken, health remaining, and attack charge. */
    @Test fun playerHit() = shot(
        "56-fight-player-bar",
        combat(hp = 640, playerHp = 37, playerMaxHp = 100, phase = "enrage", combo = 1.6f, streak = 4, damage = 560),
        FatigueBand.WORKING, reps = 14, cue = "Depth held. Same tempo.",
        lastPlayerHit = 8, nextAttackInMs = 2100,
    )
}
