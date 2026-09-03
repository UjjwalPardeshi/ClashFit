package com.clashfit.ui.screens.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.clashfit.AppGraph
import com.clashfit.core.model.GameMode
import com.clashfit.perception.CameraPermissionGate
import com.clashfit.perception.SkeletonOverlay
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.Session
import com.clashfit.ui.nav.Summary

/**
 * Session and Summary destinations. The camera, coach and audio implementations are assembled
 * by [SessionWiring] per screen; the session screen itself only sees the [SessionDeps] ports.
 */
fun NavGraphBuilder.sessionRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Session> { entry ->
        val route = entry.toRoute<Session>()
        val args = SessionArgs(
            mode = runCatching { GameMode.valueOf(route.mode) }.getOrDefault(GameMode.BOSS_FIGHT),
            exerciseId = route.exerciseId,
            casual = route.casual,
            ghostId = route.ghostId,
            durationSec = route.durationSec,
        )
        val owner = LocalLifecycleOwner.current
        val deps = remember(route, owner) { SessionWiring.deps(graph, owner) }
        CameraPermissionGate {
            SessionScreen(
                graph, deps, args,
                skeleton = { m -> LiveSkeleton(graph, deps, args, m) },
                onSummary = { id -> nav.navigate(Summary(id)) { popUpTo(Home) } },
                onExit = { nav.popBackStack(Home, inclusive = false) },
            )
        }
    }
    composable<Summary> { entry ->
        val route = entry.toRoute<Summary>()
        SummaryScreen(
            graph, route.sessionId,
            onHome = { nav.popBackStack(Home, inclusive = false) },
            onAgain = { s ->
                nav.navigate(Session(mode = s.mode, exerciseId = s.exerciseId, casual = s.casual, ghostId = s.ghostId)) { popUpTo(Home) }
            },
        )
    }
}

/** Binds the view model's image-space landmarks to the perception package's overlay. */
@Composable
private fun LiveSkeleton(graph: AppGraph, deps: SessionDeps, args: SessionArgs, modifier: Modifier) {
    val vm: SessionViewModel = viewModel(key = "session-${args.hashCode()}", factory = SessionViewModel.factory(graph, deps, args))
    val landmarks by vm.skeleton.collectAsStateWithLifecycle()
    SkeletonOverlay(landmarks, modifier)
}
