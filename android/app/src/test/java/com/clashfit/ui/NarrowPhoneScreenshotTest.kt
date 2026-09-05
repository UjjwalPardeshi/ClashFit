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

/**
 * The demo phone, at its own size.
 *
 * The iQOO 15 is 1440 pixels across at 600 dpi, which is 384 dp — narrower than the 411 dp every
 * other baseline was drawn at, by about a card's margin. A two-column grid loses 13 dp per tile, a
 * three-up stat strip loses 9 dp per number, and a tab bar loses 7 dp per label. None of those
 * sounds like much, and each is exactly the kind of thing that wraps a word or clips a digit only
 * on the one phone the judges will be holding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h845dp-xxxhdpi")
class NarrowPhoneScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

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
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun train() = shot("80-iqoo-train", Home) { HomeScreen(graph, it) }
    @Test fun modes() = shot("81-iqoo-modes", Modes) { ModesScreen(it) }
    @Test fun progress() = shot("82-iqoo-progress", Progress) { ProgressScreen(graph, it) }
    @Test fun compete() = shot("83-iqoo-compete", Compete) { CompeteScreen(graph, it) }
    @Test fun you() = shot("84-iqoo-you", You) { YouScreen(graph, it) }
}
