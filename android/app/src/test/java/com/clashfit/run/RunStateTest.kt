package com.clashfit.run

import com.clashfit.data.RunEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for RunState. */
class RunStateTest {
    @Test
    fun initialState_isNotRunning() {
        val state = RunState()
        assertFalse(state.isRunning)
        assertFalse(state.isActive)
    }

    @Test
    fun withRunId_isRunning() {
        val state = RunState(
            runId = 1L,
            startedAtMs = System.currentTimeMillis(),
        )
        assertTrue(state.isRunning)
        assertTrue(state.isActive)
    }

    @Test
    fun paused_notActive() {
        val state = RunState(
            runId = 1L,
            startedAtMs = System.currentTimeMillis(),
            isPaused = true,
        )
        assertTrue(state.isRunning)
        assertFalse(state.isActive)
    }

    @Test
    fun asEntity_withValidState() {
        val startMs = System.currentTimeMillis()
        val state = RunState(
            runId = 5L,
            startedAtMs = startMs,
            distanceM = 1500f,
            movingMs = 60_000L,
            avgPaceSecPerKm = 240f,
            cadenceSpm = 170,
            elevationGainM = 25f,
            splits = listOf(240f, 240f, 240f),
        )

        val entity = state.asEntity()
        assertEquals(5L, entity?.id)
        assertEquals(startMs, entity?.startedAtMs)
        assertEquals(1500f, entity?.distanceM)
    }

    @Test
    fun asEntity_nullWithoutRunId() {
        val state = RunState(startedAtMs = System.currentTimeMillis())
        assertNull(state.asEntity())
    }

    @Test
    fun asEntity_nullWithoutStartedAtMs() {
        val state = RunState(runId = 1L)
        assertNull(state.asEntity())
    }
}
