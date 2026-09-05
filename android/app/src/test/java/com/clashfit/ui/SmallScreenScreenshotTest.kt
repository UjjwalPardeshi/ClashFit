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
import com.clashfit.ui.nav.Compete
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.MainScaffold
import com.clashfit.ui.nav.Modes
import com.clashfit.ui.nav.Progress
import com.clashfit.ui.nav.Settings
import com.clashfit.ui.nav.Rewards
import com.clashfit.ui.nav.Breathing
import com.clashfit.ui.nav.ZombieRun
import com.clashfit.ui.screens.settings.SettingsScreen
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.You
import com.clashfit.ui.screens.home.HomeScreen
import com.clashfit.ui.screens.modes.ModesScreen
import com.clashfit.ui.screens.progress.ProgressScreen
import com.clashfit.ui.screens.social.CompeteScreen
import com.clashfit.ui.screens.you.YouScreen
import com.clashfit.ui.theme.ClashFitTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.clashfit.ui.screens.breathing.BreathingScreen
import com.clashfit.ui.screens.social.RewardsScreen
import com.clashfit.ui.screens.zombierun.ZombieRunScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The smallest screen Android still ships, with the largest text somebody might set.
 *
 * 320 dp is the floor of the platform — a cheap phone, a cover screen, a device held in a split
 * pane — and 1.3x is a font scale a great many people actually run. Every other baseline is drawn
 * at 384 dp or wider at 1.0x, which means the whole suite could be green while the app is broken
 * for anybody outside that one shape.
 *
 * These renders are here to be looked at rather than diffed: what they catch is a headline that
 * breaks mid-word, a three-up stat strip whose third label drops a line, and a button whose text
 * runs into its own edge. All three are invisible at 411 dp and obvious here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-hdpi")
class SmallScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

    private fun shot(name: String, route: Route, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                ClashFitTheme {
                    val nav = rememberNavController()
                    MainScaffold(nav) { padding ->
                        NavHost(nav, startDestination = route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                            composable(route::class) { content(nav) }
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun train() = shot("90-small-train", Home) { HomeScreen(graph, it) }
    @Test fun modes() = shot("91-small-modes", Modes) { ModesScreen(it) }
    @Test fun progress() = shot("92-small-progress", Progress) { ProgressScreen(graph, it) }
    @Test fun compete() = shot("93-small-compete", Compete) { CompeteScreen(graph, it) }
    @Test fun you() = shot("94-small-you", You) { YouScreen(graph, it) }
    @Test fun settings() = shot("95-small-settings", Settings) { SettingsScreen(graph, it) }
    @Test fun rewards() = shot("96-small-rewards", Rewards) { RewardsScreen(graph, onBack = {}) }
    @Test fun breathing() = shot("97-small-breathing", Breathing) { BreathingScreen(graph, it) }
    @Test fun zombieRun() = shot("98-small-zombie", ZombieRun) { ZombieRunScreen(graph, it) }
}
