package com.clashfit.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.clashfit.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Haptic feedback with VibrationEffect primitives: rep tick, shallow thud, milestone pulse,
 * phase hit, and breathing patterns. Honours prefs.haptics setting.
 */
class Haptics(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope? = null,
) {
    private val tag = "ClashFit/audio"
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var hapticsEnabled = true

    /**
     * The switch in Settings, actually connected.
     *
     * This class documented that it honoured `prefs.haptics` and did not: the observer was a
     * comment saying a real implementation would add one, `setEnabled` was never called from
     * anywhere, and the flag stayed true forever. Turning haptics off in Settings changed
     * nothing, which is worse than not offering the switch.
     *
     * The scope is optional so a test can construct this class without one; when it is absent
     * the setting is simply not observed and [setEnabled] remains the way in.
     */
    init {
        scope?.launch {
            prefs.settings.map { it.haptics }.distinctUntilChanged().collectLatest {
                hapticsEnabled = it
            }
        }
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

    /**
     * Player hit by boss — a heavy double buzz pattern conveying impact.
     */
    fun playerHit() {
        if (!hapticsEnabled) return
        try {
            // Heavy double buzz: strong pulse, brief pause, stronger pulse
            val timings = longArrayOf(0, 120, 80, 140)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(tag, "Error vibrating player hit", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
    }
}
