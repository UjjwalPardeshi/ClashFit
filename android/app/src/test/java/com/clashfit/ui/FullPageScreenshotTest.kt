package com.clashfit.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.clashfit.AppGraph
import com.clashfit.data.RepEntity
import com.clashfit.data.SessionEntity
import com.clashfit.data.SetEntity
import com.clashfit.data.StreakEntity
import com.clashfit.ui.nav.Compete
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.MainScaffold
import com.clashfit.ui.nav.Progress
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.You
import com.clashfit.ui.screens.home.HomeScreen
import com.clashfit.ui.screens.progress.ProgressScreen
import com.clashfit.ui.screens.social.CompeteScreen
import com.clashfit.ui.screens.you.YouScreen
import com.clashfit.ui.theme.ClashFitTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin
import kotlin.random.Random

/**
 * The scrolling tabs, rendered whole on an impossibly tall screen.
 *
 * Every other screenshot captures a viewport, which means the bottom of a long page has never been
 * looked at — not in review, not in this suite, not by anybody. That is exactly where layout rots:
 * a section with the wrong gap above it, a card that runs to the edge, a row that was never
 * finished because nobody scrolled that far.
 *
 * 411dp by 2400dp is not a device. It is a way to see the whole page at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp-xhdpi")
class FullPageScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

    /** Six weeks of plausible training, so every section has something in it. */
    @Before
    fun seed() = runBlocking {
        val db = graph.db
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rng = Random(11)
        val exercises = listOf("squat", "push_up", "lunge", "glute_bridge", "plank", "jumping_jack")
        val modes = listOf("BOSS_FIGHT", "TIME_ATTACK", "SURVIVAL", "BOSS_FIGHT", "TEMPO_TRIAL")

        var index = 0
        for (back in 41 downTo 0) {
            if (rng.nextInt(10) < 3) continue
            val day = today.minusDays(back.toLong())
            val startedAt = day.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
            val progress = (41 - back) / 41f
            val form = (0.54f + progress * 0.28f + sin(index * 1.9f) * 0.05f).coerceIn(0.30f, 0.98f)
            val reps = 14 + rng.nextInt(16)
            val band = when {
                form > 0.8f -> "WORKING"
                form > 0.6f -> "FADING"
                else -> "GASSED"
            }
            val id = db.sessions().insertSession(
                SessionEntity(
                    startedAtMs = startedAt,
                    endedAtMs = startedAt + 1000L * (200 + rng.nextInt(400)),
                    mode = modes[index % modes.size],
                    exerciseId = exercises[index % exercises.size],
                    bossId = "pacemaker",
                    outcome = if (rng.nextInt(10) < 7) "BOSS_DOWN" else "SET_SAVED",
                    totalDamage = (reps * 90 * form).toInt(),
                    totalReps = reps,
                    formMean = form,
                    peakFatigue = 0.2f + progress * 0.4f,
                    peakBand = band,
                ),
            )
            val setId = db.sessions().insertSet(
                SetEntity(
                    sessionId = id, setIndex = 1, exerciseId = exercises[index % exercises.size],
                    startedAtMs = startedAt, endedAtMs = startedAt + 120_000, reps = reps,
                    formMean = form, fatigueEnd = 0.3f, fatigueBandEnd = band,
                    coachLine = "Depth held. Keep the same tempo.", bossLine = null,
                    coachSource = "template", restSec = 45,
                ),
            )
            db.sessions().insertReps(
                (1..reps).map { r ->
                    val f = (form + sin(r * 0.8f) * 0.08f).coerceIn(0.15f, 1f)
                    RepEntity(
                        sessionId = id, setId = setId, repIndex = r,
                        tStartMs = startedAt + r * 3000L, tEndMs = startedAt + r * 3000L + 1800L,
                        formScore = f, depth = f, rom = f, tempo = 0.7f, alignment = f,
                        reason = "ok", verdict = if (f > 0.75f) "CLEAN" else if (f > 0.5f) "OK" else "SHALLOW",
                        concentricVelocity = 0.8f, damage = (90 * f).toInt(), comboAtRep = 1f + r * 0.02f,
                        fatigueValue = r / reps.toFloat() * 0.5f, fatigueBand = band,
                        validFrameRatio = 0.95f, depthCm = null, heightCm = null, holdSec = null,
                    )
                },
            )
            index++
        }
        db.streak().upsert(
            StreakEntity(
                id = 1, current = 9, best = 21, lastDayKey = today.toString(),
                freezes = 2, restDaysUsedThisWeek = 1, weekKey = "2026-W36",
            ),
        )
    }

    private fun shot(name: String, route: Route, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            ClashFitTheme {
                val nav = rememberNavController()
                MainScaffold(nav) { padding ->
                    NavHost(nav, startDestination = route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        composable(route::class) { content(nav) }
                    }
                }
            }
        }
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun trainWhole() = shot("60-whole-train", Home) { HomeScreen(graph, it) }
    @Test fun progressWhole() = shot("61-whole-progress", Progress) { ProgressScreen(graph, it) }
    @Test fun competeWhole() = shot("62-whole-compete", Compete) { CompeteScreen(graph, it) }
    @Test fun youWhole() = shot("63-whole-you", You) { YouScreen(graph, it) }
}
