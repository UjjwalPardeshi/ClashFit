package com.clashfit.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Absorbs config load and warm-ups. Exit condition is config loaded; the coach's readiness is
 * reported separately and never gates the first fight. docs/02-APP-FLOW.md §2
 */
@Composable
fun SplashScreen(graph: AppGraph, onReady: () -> Unit) {
    val version by graph.config.version.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        graph.config.version.first { it > 0 }
        // The LLM cold load runs behind the splash and never gates it; templates speak until then.
        graph.scope.launch { runCatching { graph.llmEngine.warmUp() } }
        onReady()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Text("CLASHFIT", style = MaterialTheme.typography.displayMedium)
            Text(
                if (version > 0) "Ready." else "Loading the referee…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
