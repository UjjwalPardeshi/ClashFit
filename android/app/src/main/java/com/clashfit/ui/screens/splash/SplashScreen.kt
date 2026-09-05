package com.clashfit.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.auth.AuthState
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.Onboarding
import com.clashfit.ui.nav.ProfileSetup
import com.clashfit.ui.nav.Route
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import com.clashfit.data.Prefs

/**
 * Absorbs config load, then decides the first screen: a stranger sees the welcome, a signed-in
 * player who never finished setup lands on it, everyone else goes home. The coach's readiness is
 * reported separately and never gates the first fight. docs/02-APP-FLOW.md §2
 */
@Composable
fun SplashScreen(graph: AppGraph, onReady: (Route) -> Unit) {
    val version by graph.config.version.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        graph.config.version.first { it > 0 }
        // The LLM cold load runs behind the splash and never gates it; templates speak until then.
        graph.scope.launch { runCatching { graph.llmEngine.warmUp() } }
        // If the backend is unreachable the listener may never resolve, and a splash that never
        // ends is the worst possible failure on a stage. Wait a moment, then assume signed out:
        // the welcome screen is a safe place to land, and signing in there will still work.
        val auth = withTimeoutOrNull(AUTH_WAIT_MS) {
            graph.auth.state.first { it !is AuthState.Loading }
        } ?: AuthState.SignedOut
        val settings = graph.prefs.settings.first()
        onReady(firstRoute(auth, settings))
    }
    Box(Modifier.fillMaxSize().background(Ground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Ember), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(40.dp))
            }
            Text("CLASHFIT", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text(
                if (version > 0) "Ready." else "Loading the referee…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Long enough for a real sign-in check, short enough that nobody watching notices. */
private const val AUTH_WAIT_MS = 2_500L

/**
 * Which screen a launch lands on.
 *
 * A pure function because the rule it encodes is the one that decides whether the app can be
 * opened at all, and it was wrong: a signed-out person went to the welcome carousel, whose only
 * two doors were "create account" and "sign in". Both of those talk to Firebase. A fresh install
 * with no signal — a venue's wifi, a plane, a phone handed over with data off — could not get
 * past the first screen, and nothing on it said so.
 *
 * The third door sets [Prefs.Settings.guest]. A guest is signed out and has still been here
 * before, so they go straight in; sending them back to the carousel every launch would make the
 * door pointless. Signing in later does not clear the flag and does not need to — a signed-in
 * person never reaches that branch.
 */
internal fun firstRoute(auth: AuthState, settings: Prefs.Settings): Route = when {
    auth is AuthState.SignedOut && !settings.guest -> Onboarding
    !settings.onboarded -> ProfileSetup
    else -> Home
}
