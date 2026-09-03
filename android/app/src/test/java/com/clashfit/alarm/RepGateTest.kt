package com.clashfit.alarm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests for RepGateEvent data structure. */
class RepGateTest {

    @Test
    fun `RepGateEvent contains all required fields`() {
        val event = RepGateEvent(
            counted = 3,
            target = 5,
            lastVerdict = "CLEAN",
            cue = "Go deeper"
        )

        assertEquals(3, event.counted)
        assertEquals(5, event.target)
        assertEquals("CLEAN", event.lastVerdict)
        assertEquals("Go deeper", event.cue)
    }

    @Test
    fun `RepGateEvent with null verdict and cue`() {
        val event = RepGateEvent(
            counted = 0,
            target = 5,
            lastVerdict = null,
            cue = null
        )

        assertEquals(0, event.counted)
        assertEquals(5, event.target)
        assertEquals(null, event.lastVerdict)
        assertEquals(null, event.cue)
    }

    @Test
    fun `FakeRepGate implements RepGate interface`() {
        val gate: RepGate = FakeRepGate(target = 5)
        assertNotNull(gate)
    }
}
