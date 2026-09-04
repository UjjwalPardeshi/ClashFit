package com.clashfit.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.clashfit.AppGraph
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.Library
import com.clashfit.ui.nav.MainScaffold
import com.clashfit.ui.nav.Progress
import com.clashfit.ui.nav.Route
import com.clashfit.ui.nav.Settings
import com.clashfit.ui.nav.Weekly
import com.clashfit.ui.nav.You
import com.clashfit.ui.screens.home.HomeScreen
import com.clashfit.ui.screens.library.LibraryScreen
import com.clashfit.ui.screens.progress.ProgressScreen
import com.clashfit.ui.screens.settings.SettingsScreen
import com.clashfit.ui.screens.social.WeeklyScreen
import com.clashfit.ui.screens.you.YouScreen
import com.clashfit.ui.theme.ClashFitTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen at 1.5× text, which is where a layout usually breaks.
 *
 * Android lets anyone scale text up in system settings, and a large share of people over fifty
 * have. A design that only holds at 1.0 is a design that quietly excludes them, and the failure is
 * invisible on the developer's own phone. These renders make it visible: labels that clip, rows
 * that collide, tiles whose numbers no longer fit, a bottom bar whose captions run together.
 *
 * 1.5 is the check because it is the largest of Android's ordinary font-size steps, before the
 * accessibility-specific ones. If the app survives it, the common case is safe.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LargeFontScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val graph: AppGraph get() = AppGraph.of(ApplicationProvider.getApplicationContext())

    private fun shot(name: String, route: Route, content: @Composable (NavHostController) -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.5f),
            ) {
                ClashFitTheme {
                    val nav = rememberNavController()
                    MainScaffold(nav) { padding ->
                        NavHost(
                            nav,
                            startDestination = route,
                            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                        ) {
                            composable(route::class) { content(nav) }
                        }
                    }
                }
            }
        }
        repeat(3) { Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun trainLarge() = shot("40-large-train", Home) { HomeScreen(graph, it) }
    @Test fun libraryLarge() = shot("41-large-library", Library) { LibraryScreen(graph, it) }
    @Test fun progressLarge() = shot("42-large-progress", Progress) { ProgressScreen(graph, it) }
    @Test fun youLarge() = shot("43-large-you", You) { YouScreen(graph, it) }
    @Test fun settingsLarge() = shot("44-large-settings", Settings) { SettingsScreen(graph, it) }
    @Test fun weeklyLarge() = shot("45-large-weekly", Weekly) {
        WeeklyScreen(graph, onBack = {}, onFight = {}, onLeaderboard = {})
    }
}
