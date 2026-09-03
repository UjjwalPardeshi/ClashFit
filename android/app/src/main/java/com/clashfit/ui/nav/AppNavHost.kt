package com.clashfit.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clashfit.AppGraph
import com.clashfit.alarm.alarmRoutes
import com.clashfit.run.runRoutes
import com.clashfit.ui.screens.home.HomeScreen
import com.clashfit.ui.screens.session.sessionRoutes
import com.clashfit.ui.screens.shellRoutes
import com.clashfit.ui.screens.splash.SplashScreen
import com.clashfit.ui.theme.Motion

/**
 * The whole navigation graph. Screen transitions are a 200ms cross-fade — no slides, no
 * shared-element choreography. docs/03-UI-UX-SPEC.md §7
 */
@Composable
fun AppNavHost(graph: AppGraph, nav: NavHostController = rememberNavController()) {
    NavHost(
        navController = nav,
        startDestination = Splash,
        enterTransition = { fadeIn(Motion.screen) },
        exitTransition = { fadeOut(Motion.screen) },
        popEnterTransition = { fadeIn(Motion.screen) },
        popExitTransition = { fadeOut(Motion.screen) },
    ) {
        composable<Splash> {
            SplashScreen(graph) {
                nav.navigate(Home) { popUpTo(Splash) { inclusive = true } }
            }
        }
        composable<Home> { HomeScreen(graph, nav) }

        shellRoutes(graph, nav)      // modes · picker · library · character · streaks · history · settings · privacy · preflight · clinic
        sessionRoutes(graph, nav)    // calibration → fight → rest → victory, and the summary
        runRoutes(graph, nav)        // the run tracker
        alarmRoutes(graph, nav)      // the rep-gated wake-up alarm

        // Phase 2: multiplayer and community screens.
        composable<DuelLobby> { Stub("Duel lobby") }
        composable<RaidRoom> { Stub("Raid room") }
        composable<Roster> { Stub("Pass the phone") }
        composable<Ghosts> { Stub("Ghosts") }
        composable<Challenge> { Stub("Challenge") }
        composable<Breathing> { Stub("Breathing") }
    }
}

@Composable
private fun Stub(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge)
            Text("Coming in this build.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
