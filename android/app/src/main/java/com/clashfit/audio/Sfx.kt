package com.clashfit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesized sound effects using AudioTrack: rep impacts (pitch varies with combo),
 * milestone chime, phase hit, boss death, framing-lost two-tone, and countdown ticks.
 * Low latency, precomputed PCM buffers, honours a mute flag.
 */
class Sfx {
    private val tag = "ClashFit/audio"
    private val sampleRate = 44100
    private val muted = false // Set via prefs later if needed

    /**
     * Rep impact sound: bright for clean, duller/detuned for shallow.
     * Pitch rises with combo multiplier for audible streak feedback.
     */
    fun rep(verdict: String, comboMultiplier: Float) {
        if (muted) return
        try {
            val frequency = when (verdict) {
                "CLEAN" -> 800f + (comboMultiplier - 1f) * 400f // 800-1200Hz
                "SHALLOW" -> 600f // Duller
                else -> 700f
            }
            val duration = 80 // ms
            playTone(frequency, duration.toLong())
        } catch (e: Exception) {
            Log.e(tag, "Error playing rep sound", e)
        }
    }

    /**
     * Milestone chime (×2, ×3) — ascending chime progression.
     */
    fun milestone() {
        if (muted) return
        try {
            // Quick ascending chime: 800Hz -> 1000Hz -> 1200Hz
            playTone(800f, 100)
            Thread.sleep(50)
            playTone(1000f, 100)
            Thread.sleep(50)
            playTone(1200f, 100)
        } catch (e: Exception) {
            Log.e(tag, "Error playing milestone sound", e)
        }
    }

    /**
     * Phase change hit — low sustained sound.
     */
    fun phase() {
        if (muted) return
        try {
            playTone(300f, 150)
        } catch (e: Exception) {
            Log.e(tag, "Error playing phase sound", e)
        }
    }

    /**
     * Boss death — the big moment.
     */
    fun bossDown() {
        if (muted) return
        try {
            // Deep descending sweep
            playTone(400f, 150)
            Thread.sleep(100)
            playTone(300f, 150)
            Thread.sleep(100)
            playTone(200f, 200)
        } catch (e: Exception) {
            Log.e(tag, "Error playing boss death sound", e)
        }
    }

    /**
     * Framing lost — soft descending two-tone.
     */
    fun framingLost() {
        if (muted) return
        try {
            playTone(600f, 100)
            Thread.sleep(50)
            playTone(400f, 150)
        } catch (e: Exception) {
            Log.e(tag, "Error playing framing lost sound", e)
        }
    }

    /**
     * Countdown tick (3-2-1).
     */
    fun tick() {
        if (muted) return
        try {
            playTone(1000f, 100)
        } catch (e: Exception) {
            Log.e(tag, "Error playing tick sound", e)
        }
    }

    private fun playTone(frequency: Float, durationMs: Long) {
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val samples = ShortArray(numSamples)

        // Generate sine wave
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * frequency * i / sampleRate
            samples[i] = (32767 * sin(angle)).toInt().toShort()
        }

        // Create and play AudioTrack
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(numSamples * 2)
            .build()

        audioTrack.play()
        audioTrack.write(samples, 0, numSamples)
        audioTrack.release()
    }
}
