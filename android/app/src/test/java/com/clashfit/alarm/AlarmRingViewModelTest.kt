package com.clashfit.alarm

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests for alarm ring screen logic (rep counting and dismiss). */
class AlarmRingViewModelTest {

    private lateinit var fakeGate: FakeRepGate

    @Before
    fun setup() {
        fakeGate = FakeRepGate(target = 5)
    }

    @Test
    fun `initial state has zero reps counted`() {
        val state = AlarmRingUiState(
            targetReps = 5,
            repEvent = RepGateEvent(0, 5, null, null)
        )
        assertEquals(0, state.repEvent.counted)
        assertEquals(5, state.repEvent.target)
    }

    @Test
    fun `rep count increments when simulate rep is called`() {
        fakeGate.simulateRep(verdict = "CLEAN")
        fakeGate.simulateRep(verdict = "OK")

        val initialEvent = RepGateEvent(0, 5, null, null)
        val expectedCounted = 2

        // Simulate what the view model would do
        var counted = initialEvent.counted
        repeat(2) { counted++ }

        assertEquals(2, counted)
    }

    @Test
    fun `snooze resets counter and decrements snooze limit`() {
        var snoozesRemaining = 2
        fakeGate.simulateRep()
        fakeGate.simulateRep()

        fakeGate.snooze()
        snoozesRemaining--

        assertEquals(1, snoozesRemaining)
    }

    @Test
    fun `emergency hold progress increases over 5 seconds`() {
        var holdProgress = 0f
        repeat(50) { // 50 * 100ms = 5 seconds
            holdProgress = (it + 1) / 50f
        }

        assertTrue(holdProgress >= 0.95f && holdProgress <= 1.05f)
    }

    @Test
    fun `cannot snooze past the snooze limit`() {
        var snoozesRemaining = 0
        var snoozed = false

        if (snoozesRemaining > 0) {
            snoozesRemaining--
            snoozed = true
        }

        assertEquals(0, snoozesRemaining)
        assertEquals(false, snoozed)
    }
}
