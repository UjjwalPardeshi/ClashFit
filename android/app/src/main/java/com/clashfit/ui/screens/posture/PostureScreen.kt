package com.clashfit.ui.screens.posture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.pose.PoseSource
import com.clashfit.engine.summary.PostureScorer
import com.clashfit.data.PostureSampleEntity
import com.clashfit.perception.CameraPermissionGate
import com.clashfit.perception.MediaPipePoseSource
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Posture measurement: one frame every few minutes, score 0–100, curve across the week. */
@Composable
fun PostureScreen(graph: AppGraph, nav: NavHostController) {
    val samples by graph.db.posture().since(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L).collectAsState(initial = emptyList())

    CameraPermissionGate {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Posture", style = MaterialTheme.typography.displayMedium, color = Ink)
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = Ink)
                }
            }
            SectionGap(24)

            PostureSampler(graph)

            SectionGap()
            Text("Seven-day curve", style = MaterialTheme.typography.labelMedium, color = InkMuted)
            SectionGap(12)
            AppCard(Modifier.fillMaxWidth(), padding = 12) {
                PostureCurve(samples, Modifier.fillMaxWidth().height(180.dp))
            }

            SectionGap()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val best = samples.maxByOrNull { it.score }
                val latest = samples.firstOrNull()
                StatTile(best?.score?.toString() ?: "—", "best", Modifier.weight(1f))
                StatTile(latest?.score?.toString() ?: "—", "latest", Modifier.weight(1f), color = Ember)
            }

            SectionGap()
        }
    }
}

@Composable
private fun PostureSampler(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current ?: return
    var sampling by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<PostureSampleResult?>(null) }
    var poseSource by remember { mutableStateOf<PoseSource?>(null) }
    var collectionJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(sampling) {
        if (!sampling) {
            collectionJob?.cancel()
            poseSource?.stop()
            poseSource = null
        }
    }

    AppCard(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (countdown > 0) {
                Text(countdown.toString(), style = MaterialTheme.typography.displayLarge, color = Ink)
            } else if (result != null) {
                val r = result!!
                Text("${r.score}", style = MaterialTheme.typography.displayLarge, color = Ember)
                SectionGap(8)
                Text(r.description, style = MaterialTheme.typography.bodySmall, color = Ink)
                SectionGap(12)
                Text("Frame read and discarded. Only the number is kept.", style = MaterialTheme.typography.labelSmall, color = InkMuted)
            } else {
                PrimaryButton("Sample", Modifier.fillMaxWidth()) {
                    sampling = true
                    countdown = 3
                    scope.launch {
                        // Countdown
                        repeat(3) {
                            delay(1000)
                            countdown--
                        }
                        // Open camera for one frame
                        poseSource = MediaPipePoseSource(
                            graph.app, graph.config.pose.value, graph.clock, owner, graph.scope
                        )
                        poseSource?.start(com.clashfit.core.pose.CameraFacing.FRONT)
                        // Collect one frame
                        collectionJob = scope.launch {
                            val frame = poseSource?.frames?.first()
                            poseSource?.stop()
                            if (frame?.world != null) {
                                val sample = PostureScorer.score(frame.world, frame.image)
                                if (sample != null) {
                                    // Store in database
                                    withContext(Dispatchers.IO) {
                                        graph.db.posture().insert(PostureSampleEntity(
                                            tMs = System.currentTimeMillis(),
                                            score = sample.score,
                                            neckDeg = sample.neckFlexionDeg,
                                            elevation = sample.shoulderElevation,
                                        ))
                                    }
                                    result = PostureSampleResult(sample.score, sample.description)
                                }
                            }
                            sampling = false
                        }
                    }
                }
            }
        }
    }
}

private data class PostureSampleResult(val score: Int, val description: String)

@Composable
private fun PostureCurve(samples: List<PostureSampleEntity>, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No data yet — take a sample", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        return
    }

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sorted = samples.sortedBy { it.tMs }

        // Grid lines at score boundaries
        for (score in listOf(0, 25, 50, 75, 100)) {
            val y = h * (1f - score / 100f)
            drawLine(Rule.copy(alpha = 0.2f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (sorted.size < 2) return@Canvas

        // Path
        val path = Path()
        sorted.forEachIndexed { i, s ->
            val x = w * i / (sorted.size - 1).toFloat()
            val y = h * (1f - s.score.coerceIn(0, 100) / 100f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Ember, style = Stroke(width = 3f))

        // Last point
        val last = sorted.last()
        drawCircle(Ember, radius = 5f, center = Offset(w, h * (1f - last.score.coerceIn(0, 100) / 100f)))
    }
}

fun NavGraphBuilder.postureRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Posture> {
        PostureScreen(graph, nav)
    }
}
