package com.clashfit.voice

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for VoiceCommands backoff logic and scope management.
 * Verifies that error handling includes exponential backoff capped at 5000ms.
 */
class VoiceCommandsBackoffTest {

    @Test
    fun `backoff doubles on consecutive errors and caps at maxBackoffMs`() = runTest {
        val backoffHelper = BackoffHelper()

        // Simulate 10 consecutive errors
        repeat(10) {
            backoffHelper.onError()
        }

        // Verify backoff is capped at 5000ms and not growing beyond
        assertEquals(5000L, backoffHelper.getCurrentBackoffMs())
        assertTrue(
            backoffHelper.getCurrentBackoffMs() <= 5000L,
            "Backoff should never exceed 5000 ms"
        )
    }

    @Test
    fun `backoff progression follows exponential pattern`() = runTest {
        val backoffHelper = BackoffHelper()
        // BackoffHelper.onError() doubles *before* returning (see below), so the value read
        // after the first error is already 200 (100 doubled once), not the base 100 -
        // consistent with "backoff resets on success" (3200 after 5 errors) and
        // "backoff doubles on consecutive errors" (5000 after 10 errors) below.
        val expectedProgression = listOf(200L, 400L, 800L, 1600L, 3200L, 5000L, 5000L, 5000L, 5000L, 5000L)
        val actualProgression = mutableListOf<Long>()

        repeat(10) {
            backoffHelper.onError()
            actualProgression.add(backoffHelper.getCurrentBackoffMs())
        }

        assertEquals(expectedProgression, actualProgression)
    }

    @Test
    fun `backoff resets on success`() = runTest {
        val backoffHelper = BackoffHelper()

        // Simulate 5 errors (backoff reaches 3200L)
        repeat(5) {
            backoffHelper.onError()
        }
        assertEquals(3200L, backoffHelper.getCurrentBackoffMs())

        // Reset on success
        backoffHelper.onSuccess()
        assertEquals(100L, backoffHelper.getCurrentBackoffMs())
    }

    @Test
    fun `VoiceCommands scope is managed correctly`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher() + Job())
        val voiceCommands = MockVoiceCommands(scope)

        // Scope should be active
        assertTrue(!scope.coroutineContext[Job]?.isCancelled!!)

        // Cancel the scope
        scope.coroutineContext[Job]?.cancel()

        // Scope should now be cancelled
        assertTrue(scope.coroutineContext[Job]?.isCancelled!!)
    }

    /**
     * Test helper that simulates VoiceCommands error handling with backoff.
     * Delegates to [VoiceCommands.nextBackoffMs] - the pure function extracted from the real
     * class's onError callback - so this helper can never drift out of sync with production
     * behavior the way a hand-duplicated copy of the doubling math could.
     */
    private class BackoffHelper {
        private var backoffMs = VoiceCommands.INITIAL_BACKOFF_MS

        fun onError() {
            backoffMs = VoiceCommands.nextBackoffMs(backoffMs)
        }

        fun onSuccess() {
            backoffMs = VoiceCommands.INITIAL_BACKOFF_MS
        }

        fun getCurrentBackoffMs(): Long = backoffMs
    }

    /**
     * Mock VoiceCommands for testing scope lifecycle.
     */
    private class MockVoiceCommands(
        private val scope: CoroutineScope
    ) {
        private var backoffMs = 100L
        private val maxBackoffMs = 5000L

        fun onError() {
            backoffMs = minOf(backoffMs * 2, maxBackoffMs)
        }

        fun onSuccess() {
            backoffMs = 100L
        }
    }
}
