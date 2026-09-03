package com.clashfit.run

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Unit tests for RunTracker state management. */
class RunTrackerTest {
    @Test
    fun getInstance_returnsSingleton() {
        val tracker1 = RunTracker.getInstance()
        val tracker2 = RunTracker.getInstance()
        assertEquals(tracker1, tracker2)
    }

    @Test
    fun setState_updatesStateFlow() = runTest {
        val tracker = RunTracker()
        val initialState = tracker.state.value

        assertFalse(initialState.isRunning)

        val newState = RunState(
            runId = 1L,
            startedAtMs = System.currentTimeMillis(),
        )
        tracker.setState(newState)

        assertEquals(newState, tracker.state.value)
    }

    @Test
    fun resetState_clearsState() = runTest {
        val tracker = RunTracker()
        tracker.setState(RunState(
            runId = 1L,
            startedAtMs = System.currentTimeMillis(),
        ))

        tracker.resetState()

        val reset = tracker.state.value
        assertEquals(RunState(), reset)
    }
}
