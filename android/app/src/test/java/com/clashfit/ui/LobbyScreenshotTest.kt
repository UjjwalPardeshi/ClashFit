package com.clashfit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.clashfit.AppGraph
import com.clashfit.alarm.AlarmEditScreen
import com.clashfit.core.model.GameMode
import com.clashfit.data.RunEntity
import com.clashfit.data.RunPointEntity
import com.clashfit.run.RunSummaryScreen
import com.clashfit.ui.nav.AlarmEdit
import com.clashfit.ui.nav.Roster
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.RunSummary
import com.clashfit.ui.screens.roster.RosterScreen
import com.clashfit.ui.theme.ClashFitTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * The screens you only reach with other people, a finished run, or an alarm in hand.
 *
 * Each of these needs a route argument or a database row before it will draw anything, which is
 * exactly why none of them had ever been rendered. They are also the screens a judge is most
 * likely to open second — after the fight, "so what else is in here".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LobbyScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

    private fun shot(name: String, route: Route, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            ClashFitTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = route) { composable(route::class) { content(nav) } }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /** A five-kilometre loop with a believable pace, so the map and the splits both have shape. */
    private fun seedRun(): Long = runBlocking {
        val dao = graph.db.runs()
        val start = 1_757_000_000_000L
        val id = dao.insertRun(
            RunEntity(
                startedAtMs = start, endedAtMs = start + 1_620_000L, distanceM = 5_040f,
                movingMs = 1_580_000L, avgPaceSecPerKm = 313f, cadenceSpm = 168,
                elevationGainM = 34f, splitsJson = "[298,306,311,320,331]",
            ),
        )
        dao.insertPoints(
            (0 until 120).map { i ->
                val a = i / 120f * 6.28f
                RunPointEntity(
                    runId = id, tMs = start + i * 13_500L,
                    lat = 18.5204 + sin(a) * 0.012 + sin(a * 3) * 0.002,
                    lon = 73.8567 + cos(a) * 0.016,
                    altM = 560.0 + sin(a * 2) * 9.0, accuracyM = 6f, speedMps = 3.1f,
                )
            },
        )
        id
    }

    @Test fun roster() = shot("2c-roster", Roster(GameMode.RELAY.name, "squat")) {
        RosterScreen(graph, it, Roster(GameMode.RELAY.name, "squat"))
    }

    @Test fun alarmEdit() = shot("2d-alarm-edit", AlarmEdit(0L)) { AlarmEditScreen(graph, 0L, it) }

    @Test fun runSummary() {
        val id = seedRun()
        shot("2e-run-summary", RunSummary(id)) { RunSummaryScreen(graph, it, id) }
    }
}
