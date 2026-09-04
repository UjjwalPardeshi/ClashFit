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
import com.clashfit.ui.nav.Character
import com.clashfit.ui.nav.History
import com.clashfit.ui.nav.MainScaffold
import com.clashfit.ui.nav.Progress
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.Streaks
import com.clashfit.ui.nav.You
import com.clashfit.ui.screens.character.CharacterScreen
import com.clashfit.ui.screens.history.HistoryScreen
import com.clashfit.ui.screens.progress.ProgressScreen
import com.clashfit.ui.screens.streaks.StreaksScreen
import com.clashfit.ui.screens.you.YouScreen
import com.clashfit.ui.theme.ClashFitTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
import com.clashfit.ui.nav.Compete
import com.clashfit.ui.screens.social.CompeteScreen
import com.clashfit.ui.screens.session.SummaryScreen

/**
 * The data screens, with a database that has something in it.
 *
 * Every other screenshot test renders a fresh install, which is the right thing to check for empty
 * states but tells you nothing about whether a chart reads, whether a long list crowds its header,
 * or whether a number overflows its tile. This seeds a plausible six weeks of training first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SeededScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** The most recent seeded session, so the summary has a real one to open. */
    private var lastSessionId: Long = 0

    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

    @Before
    fun seed() = runBlocking {
        val db = graph.db
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rng = Random(11)
        val exercises = listOf("squat", "push_up", "lunge", "glute_bridge", "plank", "jumping_jack")
        val modes = listOf("BOSS_FIGHT", "TIME_ATTACK", "SURVIVAL", "BOSS_FIGHT", "TEMPO_TRIAL")

        // Six weeks, training most days, form improving slowly with the odd bad session.
        var sessionIndex = 0
        for (back in 41 downTo 0) {
            if (rng.nextInt(10) < 3) continue // rest days
            val day = today.minusDays(back.toLong())
            val startedAt = day.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
            val progress = (41 - back) / 41f
            val form = (0.54f + progress * 0.28f + sin(sessionIndex * 1.9f) * 0.05f).coerceIn(0.30f, 0.98f)
            val reps = 14 + rng.nextInt(16)
            val damage = (reps * 90 * form).toInt()
            val band = when {
                form > 0.8f -> "WORKING"
                form > 0.6f -> "FADING"
                else -> "GASSED"
            }
            val id = db.sessions().insertSession(
                SessionEntity(
                    startedAtMs = startedAt,
                    endedAtMs = startedAt + 1000L * (200 + rng.nextInt(400)),
                    mode = modes[sessionIndex % modes.size],
                    exerciseId = exercises[sessionIndex % exercises.size],
                    bossId = "pacemaker",
                    outcome = if (rng.nextInt(10) < 7) "BOSS_DOWN" else "SET_SAVED",
                    totalDamage = damage,
                    totalReps = reps,
                    formMean = form,
                    peakFatigue = 0.2f + progress * 0.4f,
                    peakBand = band,
                ),
            )
            // One set and a handful of reps, so the summary and the per-rep charts have something.
            val setId = db.sessions().insertSet(
                SetEntity(
                    sessionId = id, setIndex = 1, exerciseId = exercises[sessionIndex % exercises.size],
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
            lastSessionId = id
            sessionIndex++
        }
        db.streak().upsert(StreakEntity(id = 1, current = 9, best = 21, lastDayKey = today.toString(), freezes = 2, restDaysUsedThisWeek = 1, weekKey = "2026-W36"))
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

    @Test fun progressWithData() = shot("30-seeded-progress", Progress) { ProgressScreen(graph, it) }
    @Test fun streaksWithData() = shot("31-seeded-streaks", Streaks) { StreaksScreen(graph, it) }
    @Test fun historyWithData() = shot("32-seeded-history", History) { HistoryScreen(graph, it) }
    @Test fun characterWithData() = shot("33-seeded-character", Character) { CharacterScreen(graph, it) }
    @Test fun youWithData() = shot("34-seeded-you", You) { YouScreen(graph, it) }
    @Test fun competeWithData() = shot("35-seeded-compete", Compete) { CompeteScreen(graph, it) }

    /**
     * The summary, which is seen after every single fight and had never been rendered once.
     *
     * It has no reward attached here — banking happens in the view model after a real session
     * ends, and this opens a row the seeder wrote directly — so this is the screen at its most
     * bare. That is worth looking at: if it reads well with no XP block, it reads well always.
     */
    @Test
    fun summaryWithData() {
        compose.setContent {
            ClashFitTheme {
                SummaryScreen(graph, lastSessionId, onHome = {}, onAgain = {})
            }
        }
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/36-seeded-summary.png")
    }
}
