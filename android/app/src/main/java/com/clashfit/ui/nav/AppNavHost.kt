package com.clashfit.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clashfit.AppGraph
import com.clashfit.alarm.alarmRoutes
import com.clashfit.core.model.GameMode
import com.clashfit.run.runRoutes
import com.clashfit.ui.screens.breathing.breathingRoutes
import com.clashfit.ui.screens.challenge.challengeRoutes
import com.clashfit.ui.screens.duel.duelRoutes
import com.clashfit.ui.screens.ghosts.ghostsRoutes
import com.clashfit.ui.screens.home.HomeScreen
import com.clashfit.ui.screens.roster.rosterRoutes
import com.clashfit.ui.screens.session.sessionRoutes
import com.clashfit.ui.screens.shellRoutes
import com.clashfit.ui.screens.splash.SplashScreen
import com.clashfit.ui.theme.Motion
import kotlinx.coroutines.flow.Flow

/**
 * The whole navigation graph. Screen transitions are a 200ms cross-fade — no slides, no
 * shared-element choreography. docs/03-UI-UX-SPEC.md §7
 */
@Composable
fun AppNavHost(
    graph: AppGraph,
    deskExerciseId: Flow<String?> = kotlinx.coroutines.flow.emptyFlow(),
    nav: NavHostController = rememberNavController(),
) {
    val deskExerciseIdValue = deskExerciseId.collectAsState(initial = null).value

    LaunchedEffect(deskExerciseIdValue) {
        if (deskExerciseIdValue != null) {
            nav.navigate(Session(mode = GameMode.BOSS_FIGHT.name, exerciseId = deskExerciseIdValue, casual = true, durationSec = 60))
        }
    }
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

        duelRoutes(graph, nav)       // 1v1 lobby and the raid room, phone-to-phone over Nearby
        rosterRoutes(graph, nav)     // pass the phone
        ghostsRoutes(graph, nav)     // race a recorded rep timeline
        challengeRoutes(graph, nav)  // dare codes, shared without a server
        breathingRoutes(graph, nav)  // recovery between rounds
    }
}
