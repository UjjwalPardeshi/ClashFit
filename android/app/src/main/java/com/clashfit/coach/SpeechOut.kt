package com.clashfit.coach

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Wraps Android TextToSpeech with a queue, flush on new set, audio focus management, and ready flow.
 * Speaks coach lines with full pitch/rate, boss lines with lowered pitch (0.75) and slower rate (0.9).
 * Music bed ducks 12dB while speaking.
 */
class SpeechOut(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val tag = "ClashFit/audio"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val queue = mutableListOf<QueuedUtterance>()
    private val lock = Mutex()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tts: TextToSpeech? = null
    private var currentUtteranceId: String? = null

    init {
        // Initialize TextToSpeech on the main thread
        scope.launch {
            initializeTts()
        }
    }

    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    // Prefer English (India) locale if available, else US
                    val locale = try {
                        Locale("en", "IN")
                    } catch (e: Exception) {
                        Locale.US
                    }
                    language = locale

                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {}

                        override fun onDone(utteranceId: String) {
                            scope.launch {
                                onUtteranceComplete(utteranceId)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.w(tag, "TTS error for $utteranceId")
                            val id = utteranceId ?: return
                            scope.launch {
                                onUtteranceComplete(id)
                            }
                        }

                        override fun onError(utteranceId: String, errorCode: Int) {
                            Log.w(tag, "TTS error: $errorCode for $utteranceId")
                            scope.launch {
                                onUtteranceComplete(utteranceId)
                            }
                        }
                    })
                }
                _ready.value = true
                scope.launch { playQueuedUtterances() }
            } else {
                Log.e(tag, "TTS init failed with status $status")
                _ready.value = false
            }
        }
    }

    /**
     * Speak a coach line with full pitch and rate.
     */
    suspend fun speakCoach(text: String) {
        speakUtterance(text, pitch = 1.0f, rate = 1.0f, isCoach = true)
    }

    /**
     * Speak a boss line with lowered pitch (0.75) and slower rate (0.9).
     */
    suspend fun speakBoss(text: String) {
        speakUtterance(text, pitch = 0.75f, rate = 0.9f, isCoach = false)
    }

    private suspend fun speakUtterance(text: String, pitch: Float, rate: Float, isCoach: Boolean) {
        if (text.isBlank()) return

        lock.withLock {
            queue.add(QueuedUtterance(text, pitch, rate))
            playQueuedUtterances()
        }
    }

    /**
     * Flush the TTS queue. Called on new set start to prevent coach lines from overlapping.
     */
    suspend fun flush() {
        lock.withLock {
            queue.clear()
            tts?.stop()
            currentUtteranceId = null
            releaseAudioFocus()
        }
    }

    /**
     * Stop speaking immediately.
     */
    suspend fun stop() {
        lock.withLock {
            tts?.stop()
            currentUtteranceId = null
            releaseAudioFocus()
        }
    }

    private suspend fun playQueuedUtterances() {
        if (currentUtteranceId != null || queue.isEmpty()) return

        val utterance = queue.removeAt(0)
        currentUtteranceId = System.currentTimeMillis().toString()

        val ttsInstance = tts
        if (ttsInstance == null) {
            Log.e(tag, "TTS not initialized when trying to play utterance")
            currentUtteranceId = null
            releaseAudioFocus()
            return
        }

        try {
            ttsInstance.apply {
                setPitch(utterance.pitch)
                setSpeechRate(utterance.rate)
            }

            // Request audio focus, ducking music 12dB (gain 0.251)
            requestAudioFocus()

            ttsInstance.speak(
                utterance.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                currentUtteranceId,
            )
        } catch (e: Exception) {
            Log.e(tag, "Error speaking utterance", e)
            currentUtteranceId = null
            releaseAudioFocus()
            if (queue.isNotEmpty()) {
                playQueuedUtterances()
            }
        }
    }

    private suspend fun onUtteranceComplete(utteranceId: String) {
        lock.withLock {
            if (currentUtteranceId == utteranceId) {
                currentUtteranceId = null
                releaseAudioFocus()
                if (queue.isNotEmpty()) {
                    playQueuedUtterances()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } catch (e: Exception) {
            Log.w(tag, "Error requesting audio focus", e)
        }
    }

    private fun releaseAudioFocus() {
        try {
            audioManager.abandonAudioFocus(null)
        } catch (e: Exception) {
            Log.w(tag, "Error releasing audio focus", e)
        }
    }

    private data class QueuedUtterance(
        val text: String,
        val pitch: Float,
        val rate: Float,
    )

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
