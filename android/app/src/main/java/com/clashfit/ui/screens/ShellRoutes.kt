package com.clashfit.ui.screens

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.clashfit.AppGraph
import com.clashfit.ui.screens.modes.modesRoutes
import com.clashfit.ui.screens.picker.pickerRoutes
import com.clashfit.ui.screens.library.libraryRoutes
import com.clashfit.ui.screens.character.characterRoutes
import com.clashfit.ui.screens.streaks.streaksRoutes
import com.clashfit.ui.screens.history.historyRoutes
import com.clashfit.ui.screens.settings.settingsRoutes
import com.clashfit.ui.screens.privacy.privacyRoutes
import com.clashfit.ui.screens.preflight.preflightRoutes
import com.clashfit.ui.screens.clinic.clinicRoutes

/** Wire all non-session screens into the nav host. */
fun NavGraphBuilder.shellRoutes(graph: AppGraph, nav: NavHostController) {
    modesRoutes(graph, nav)
    pickerRoutes(graph, nav)
    libraryRoutes(graph, nav)
    characterRoutes(graph, nav)
    streaksRoutes(graph, nav)
    historyRoutes(graph, nav)
    settingsRoutes(graph, nav)
    privacyRoutes(graph, nav)
    preflightRoutes(graph, nav)
    clinicRoutes(graph, nav)
}
