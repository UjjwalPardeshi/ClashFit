package com.clashfit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesized sound effects using AudioTrack: rep impacts (pitch varies with combo),
 * milestone chime, phase hit, boss death, framing-lost two-tone, and countdown ticks.
 * Low latency, precomputed PCM buffers, honours a mute flag.
 * All methods are non-blocking; audio playback happens on a background thread.
 */
class Sfx {
    private val tag = "ClashFit/audio"
    private val sampleRate = 44100
    private val muted = false // Set via prefs later if needed
    private val audioExecutor = Executors.newSingleThreadExecutor { t ->
        Thread(t).apply { isDaemon = true; name = "ClashFit-Audio" }
    }

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
            audioExecutor.submit { playTone(frequency, duration.toLong()) }
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
            // Quick ascending chime: 800Hz -> 1000Hz -> 1200Hz on background thread
            audioExecutor.submit {
                playTone(800f, 100)
                Thread.sleep(50)
                playTone(1000f, 100)
                Thread.sleep(50)
                playTone(1200f, 100)
            }
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
            audioExecutor.submit { playTone(300f, 150) }
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
            // Deep descending sweep on background thread
            audioExecutor.submit {
                playTone(400f, 150)
                Thread.sleep(100)
                playTone(300f, 150)
                Thread.sleep(100)
                playTone(200f, 200)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing boss death sound", e)
        }
    }

    /**
     * Player hit by boss — a short low thud with fast decay.
     */
    fun playerHit() {
        if (muted) return
        try {
            // Low thud (90 Hz sine, 120 ms) plus brief noise burst
            audioExecutor.submit {
                playDecayingTone(90f, 120)
                Thread.sleep(60)
                playNoiseBurst(100)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing player hit sound", e)
        }
    }

    /**
     * Framing lost — soft descending two-tone.
     */
    fun framingLost() {
        if (muted) return
        try {
            // Soft descending two-tone on background thread
            audioExecutor.submit {
                playTone(600f, 100)
                Thread.sleep(50)
                playTone(400f, 150)
            }
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
            audioExecutor.submit { playTone(1000f, 100) }
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

        try {
            audioTrack.play()
            audioTrack.write(samples, 0, numSamples)
            // Wait for playback to complete before releasing (50ms safety margin for playback latency)
            // This runs on the audio background thread, so it doesn't block callers
            Thread.sleep(durationMs + 50)
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e(tag, "Error releasing AudioTrack", e)
            }
        }
    }

    private fun playDecayingTone(frequency: Float, durationMs: Long) {
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val samples = ShortArray(numSamples)

        // Generate sine wave with exponential decay envelope
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * frequency * i / sampleRate
            // Exponential decay: starts at 1.0, falls to ~0.1 by end
            val decay = kotlin.math.exp(-3.0f * i / numSamples)
            samples[i] = (32767 * sin(angle) * decay).toInt().toShort()
        }

        playBuffer(samples, durationMs)
    }

    private fun playNoiseBurst(durationMs: Long) {
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val samples = ShortArray(numSamples)
        val rng = java.util.Random(System.nanoTime())

        // White noise with quick decay
        for (i in 0 until numSamples) {
            val noise = (rng.nextInt(32768) - 16384).toShort()
            val decay = kotlin.math.exp(-5.0f * i / numSamples)
            samples[i] = (noise * decay).toInt().toShort()
        }

        playBuffer(samples, durationMs)
    }

    private fun playBuffer(samples: ShortArray, durationMs: Long) {
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
            .setBufferSizeInBytes(samples.size * 2)
            .build()

        try {
            audioTrack.play()
            audioTrack.write(samples, 0, samples.size)
            Thread.sleep(durationMs + 30)
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e(tag, "Error releasing AudioTrack", e)
            }
        }
    }
}
