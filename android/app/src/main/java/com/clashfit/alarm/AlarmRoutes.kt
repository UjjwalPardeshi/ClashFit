package com.clashfit.alarm

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.nav.AlarmEdit
import com.clashfit.ui.nav.Alarms

/**
 * Navigation routes for the alarm feature.
 * Expose both the Alarms list screen and the AlarmEdit screen.
 */
fun NavGraphBuilder.alarmRoutes(graph: AppGraph, nav: NavHostController) {
    composable<Alarms> {
        AlarmsScreen(graph = graph, navController = nav)
    }

    composable<AlarmEdit> { backStackEntry ->
        val alarmId = backStackEntry.arguments?.getLong("alarmId", 0L) ?: 0L
        AlarmEditScreen(graph = graph, alarmId = alarmId, navController = nav)
    }
}
