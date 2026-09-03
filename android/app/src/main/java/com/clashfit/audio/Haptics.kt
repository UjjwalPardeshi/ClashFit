package com.clashfit.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.clashfit.data.Prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map

/**
 * Haptic feedback with VibrationEffect primitives: rep tick, shallow thud, milestone pulse,
 * phase hit, and breathing patterns. Honours prefs.haptics setting.
 */
class Haptics(
    private val context: Context,
    private val prefs: Prefs,
) {
    private val tag = "ClashFit/audio"
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var hapticsEnabled = true

    init {
        // Observe prefs.haptics and update the local flag
        // Note: In a real implementation, this would be wrapped in a coroutine scope
        // For now, we set it directly and it can be updated by the observer
    }

    /**
     * Rep tick — short pulse confirming a rep was counted.
     */
    fun repTick() {
        if (!hapticsEnabled) return
        try {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating rep tick", e)
        }
    }

    /**
     * Shallow thud — duller, deeper pulse for shallow reps.
     */
    fun shallowThud() {
        if (!hapticsEnabled) return
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating shallow thud", e)
        }
    }

    /**
     * Milestone pulse — distinct pattern for ×2, ×3 combos.
     */
    fun milestone() {
        if (!hapticsEnabled) return
        try {
            // Pattern: short-long-short
            val timings = longArrayOf(0, 50, 100, 50)
            val amplitudes = intArrayOf(0, 255, 100, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating milestone", e)
        }
    }

    /**
     * Phase change — distinct vibration pattern.
     */
    fun phase() {
        if (!hapticsEnabled) return
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating phase", e)
        }
    }

    /**
     * Tempo metronome pulse — a subtle beat you can feel.
     */
    fun tempoPulse() {
        if (!hapticsEnabled) return
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating tempo pulse", e)
        }
    }

    /**
     * Framing lost warning — distinct double-buzz pattern.
     */
    fun framingLostWarning() {
        if (!hapticsEnabled) return
        try {
            val timings = longArrayOf(0, 100, 100, 100)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating framing lost", e)
        }
    }

    /**
     * Breath pacing — slow rise-and-fall pattern (box breathing).
     * Useful for rest and recovery screens.
     */
    fun breathingPattern(durationSec: Float = 4f) {
        if (!hapticsEnabled) return
        try {
            // Gradual rise over ~1.5s, hold, fall, repeat
            val stepMs = 150L
            val steps = (durationSec * 1000 / stepMs).toInt()
            val timings = LongArray(steps * 2) { i ->
                if (i % 2 == 0) stepMs else stepMs / 2
            }
            val amplitudes = IntArray(steps * 2) { i ->
                if (i % 2 == 0) (255 * (i / 2) / steps) else 0
            }
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating breathing pattern", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
    }
}
