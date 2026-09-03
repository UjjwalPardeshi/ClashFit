package com.clashfit.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.min

/** Recognized voice commands: stop, pause, resume, next, skip, casual. */
enum class Command { STOP, PAUSE, RESUME, NEXT, SKIP, CASUAL }

/**
 * Recognizes voice commands using offline SpeechRecognizer.
 * Exposes commands as a SharedFlow; RECORD_AUDIO permission handled by composable gate.
 * Continuous listening with exponential backoff on errors.
 */
class VoiceCommands(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val tag = "ClashFit/voice"
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    private val _commands = MutableSharedFlow<Command>(replay = 0)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    private var backoffMs = 100L
    private val maxBackoffMs = 5000L
    private var listening = false

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(tag, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(tag, "Speech beginning")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(tag, "Speech end")
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No permission"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error"
                }
                Log.w(tag, "Speech error: $msg (code $error)")
                // Restart with backoff
                scope.launch {
                    kotlinx.coroutines.delay(backoffMs)
                    startListening()
                }
                backoffMs = min(backoffMs * 2, maxBackoffMs)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognized = matches[0].lowercase()
                    parseCommand(recognized)?.let { cmd ->
                        scope.launch {
                            _commands.emit(cmd)
                        }
                    }
                }
                // Restart listening
                startListening()
                backoffMs = 100L // Reset backoff on success
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    /**
     * Start continuous listening for voice commands.
     * Uses EXTRA_PREFER_OFFLINE for offline mode.
     */
    fun startListening() {
        if (listening) return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
            listening = true
            Log.d(tag, "Started listening")
        } catch (e: Exception) {
            Log.e(tag, "Error starting listening", e)
        }
    }

    /**
     * Stop listening.
     */
    fun stopListening() {
        listening = false
        try {
            recognizer.stopListening()
            Log.d(tag, "Stopped listening")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping listening", e)
        }
    }

    private fun parseCommand(text: String): Command? {
        return when {
            // stop / stopped / stops
            text.contains("stop") -> Command.STOP
            // pause / paused / pausing
            text.contains("pause") -> Command.PAUSE
            // next / skip to next — checked before the generic resume keywords below so
            // "go to next" / "next set" resolve to NEXT rather than matching "go" first.
            text.contains("next") -> Command.NEXT
            // skip / skipped / skipping
            text.contains("skip") -> Command.SKIP
            // casual / chill / easy / easier / easiest / easy mode / modify / adjust
            text.contains("casual") || text.contains("chill") ||
                Regex("\\beas\\w*").containsMatchIn(text) || text.contains("modify") -> Command.CASUAL
            // resume / resumed / resuming / go / start — generic, so it's checked last:
            // these words also appear inside more specific commands above.
            text.contains("resume") || text.contains("go") || text.contains("start") -> Command.RESUME
            else -> null
        }
    }

    fun shutdown() {
        stopListening()
        recognizer.destroy()
    }
}
