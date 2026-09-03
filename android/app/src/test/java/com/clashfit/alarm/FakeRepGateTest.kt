package com.clashfit.alarm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for FakeRepGate. */
class FakeRepGateTest {

    private lateinit var gate: FakeRepGate

    @Before
    fun setup() {
        gate = FakeRepGate(target = 5)
    }

    @Test
    fun `initial state is zero reps`() = runBlocking {
        val event = gate.observe("squat").first()
        assertEquals(0, event.counted)
        assertEquals(5, event.target)
        assertNull(event.lastVerdict)
        assertNull(event.cue)
    }

    @Test
    fun `simulate rep increments counter`() = runBlocking {
        gate.simulateRep(verdict = "CLEAN")
        val event = gate.observe("squat").first()
        assertEquals(1, event.counted)
        assertEquals("CLEAN", event.lastVerdict)
    }

    @Test
    fun `multiple reps accumulate`() = runBlocking {
        repeat(3) {
            gate.simulateRep(verdict = "OK")
        }
        val event = gate.observe("squat").first()
        assertEquals(3, event.counted)
        assertEquals("OK", event.lastVerdict)
    }

    @Test
    fun `cannot exceed target`() = runBlocking {
        repeat(10) {
            gate.simulateRep()
        }
        val event = gate.observe("squat").first()
        assertEquals(5, event.counted) // Should stop at target
    }

    @Test
    fun `reset clears counter`() = runBlocking {
        gate.simulateRep()
        gate.simulateRep()
        gate.reset()
        val event = gate.observe("squat").first()
        assertEquals(0, event.counted)
        assertNull(event.lastVerdict)
    }

    @Test
    fun `snooze resets the gate`() = runBlocking {
        gate.simulateRep()
        gate.simulateRep()
        gate.snooze()
        val event = gate.observe("squat").first()
        assertEquals(0, event.counted)
    }
}
