package com.clashfit.ui.screens.session

import androidx.lifecycle.LifecycleOwner
import com.clashfit.AppGraph
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Verdict
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.pose.SyntheticPoseSource
import com.clashfit.perception.MediaPipePoseSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.clashfit.perception.TracePoseSource

/**
 * Builds the real session dependencies from the app graph. The camera source needs the screen's
 * lifecycle; everything else is a singleton. `forceSynthetic` swaps the camera for the scripted
 * body — the emulator demo and the preflight self-test run on it.
 */
object SessionWiring {
    @Volatile var forceSynthetic: Boolean = false

    /** The trace the demo falls back to when the room beats the camera. */
    const val DEMO_TRACE = "traces/synthetic-f3-to-failure.jsonl"

    fun deps(graph: AppGraph, owner: LifecycleOwner, replay: Boolean = false): SessionDeps = object : SessionDeps {
        override val pose: PoseSource = when {
            forceSynthetic -> SyntheticPoseSource(graph.scope)
            // A recorded set to failure, replayed at its original timing. The demo script names
            // this as the escape when the lighting beats the pose model, and until now it was a
            // class nothing constructed.
            replay -> TracePoseSource(
                // Passed as a reader, not as a String: this used to do 903 KB of asset IO on the
                // main thread inside a remember block, and would have crashed the app outright if
                // the asset were ever missing from a build.
                traceJsonLines = { graph.app.assets.open(DEMO_TRACE).bufferedReader().use { it.readText() } },
                clock = graph.clock,
                scope = graph.scope,
            )
            else -> MediaPipePoseSource(graph.app, graph.config.pose.value, graph.clock, owner, graph.scope)
        }

        override val coach: CoachPort = object : CoachPort {
            override suspend fun speakFor(telemetry: SetTelemetry, timeoutMs: Long): CoachOutput = graph.coachEngine.speakFor(telemetry)
        }

        override val sfx: SfxPort = object : SfxPort {
            override fun rep(verdict: Verdict, comboMultiplier: Float) = graph.sfx.rep(verdict.name, comboMultiplier)
            override fun milestone() = graph.sfx.milestone()
            override fun phase() = graph.sfx.phase()
            override fun bossDown() = graph.sfx.bossDown()
            override fun framingLost() = graph.sfx.framingLost()
            override fun tick() = graph.sfx.tick()
            override fun band(band: FatigueBand) {}
        }

        override val haptics: HapticsPort = object : HapticsPort {
            override fun rep(verdict: Verdict) = graph.haptics.repTick()
            override fun milestone() = graph.haptics.milestone()
            override fun phase() = graph.haptics.phase()
        }

        override val speech: SpeechPort = object : SpeechPort {
            override fun speak(text: String, flush: Boolean) {
                graph.mainScope.launch {
                    if (!graph.prefs.settings.first().speech) return@launch
                    if (flush) graph.speechOut.stop()
                    graph.speechOut.speakCoach(text)
                }
            }
            override fun stop() { graph.mainScope.launch { graph.speechOut.stop() } }
        }
    }
}
