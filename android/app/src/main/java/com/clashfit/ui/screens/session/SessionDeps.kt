package com.clashfit.ui.screens.session

import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Verdict
import com.clashfit.core.pose.PoseSource

/**
 * Everything the session screen needs from the rest of the app, behind small interfaces so the
 * view model is testable with fakes and the feature packages can be wired in one place.
 */
interface SessionDeps {
    val pose: PoseSource
    val coach: CoachPort
    val sfx: SfxPort
    val haptics: HapticsPort
    val speech: SpeechPort
}

interface CoachPort {
    /** Never throws, never exceeds the timeout, always returns usable lines. */
    suspend fun speakFor(telemetry: SetTelemetry, timeoutMs: Long = 5_000): CoachOutput
}

interface SfxPort {
    fun rep(verdict: Verdict, comboMultiplier: Float)
    fun milestone()
    fun phase()
    fun bossDown()
    fun playerHit()
    fun framingLost()
    fun tick()
    fun band(band: FatigueBand)
}

interface HapticsPort {
    fun rep(verdict: Verdict)
    fun milestone()
    fun phase()
    fun playerHit()
}

interface SpeechPort {
    fun speak(text: String, flush: Boolean = false)
    fun stop()
}

/** Silent implementations for previews and tests. */
object NoSfx : SfxPort {
    override fun rep(verdict: Verdict, comboMultiplier: Float) {}
    override fun milestone() {}
    override fun phase() {}
    override fun bossDown() {}
    override fun playerHit() {}
    override fun framingLost() {}
    override fun tick() {}
    override fun band(band: FatigueBand) {}
}

object NoHaptics : HapticsPort {
    override fun rep(verdict: Verdict) {}
    override fun milestone() {}
    override fun phase() {}
    override fun playerHit() {}
}

object NoSpeech : SpeechPort {
    override fun speak(text: String, flush: Boolean) {}
    override fun stop() {}
}
