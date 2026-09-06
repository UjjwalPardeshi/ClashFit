package com.clashfit.duel

import com.clashfit.core.model.DuelMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a phone that links late is owed. The queue is a catch-up, not an outbox, so what it keeps
 * matters more than how much.
 */
class CatchUpQueueTest {

    private fun setup(exerciseId: String, seconds: Int, from: String = "HOST") =
        DuelMessage(type = DuelMessage.SETUP, playerId = from, exerciseId = exerciseId, reps = seconds)

    private fun ping(from: String = "HOST") = DuelMessage(type = DuelMessage.PING, playerId = from)

    @Test
    fun `a beat is not worth replaying`() {
        val q = CatchUpQueue()
        assertFalse(q.offer(ping()), "A PING is liveness; nobody handles a stale one")
        assertEquals(0, q.size)
    }

    @Test
    fun `the newest settings replace the older ones`() {
        val q = CatchUpQueue()
        q.offer(setup("squat", 30))
        q.offer(setup("bicep_curl", 60))

        assertEquals(1, q.size, "A host that changed its mind leaves one answer, not two")
        assertEquals("bicep_curl", q.backlog().single().exerciseId)
        assertEquals(60, q.backlog().single().reps)
    }

    @Test
    fun `two hosts in a room keep their own settings`() {
        val q = CatchUpQueue()
        q.offer(setup("squat", 30, from = "A"))
        q.offer(setup("bicep_curl", 60, from = "B"))

        assertEquals(2, q.size, "Collapsing is per player, not per type")
    }

    /**
     * The regression this queue was rebuilt for. The lobby heartbeat sends a PING every 1200ms,
     * so on the old queue 64 of them filled it in 76.8 seconds and evicted the only message a
     * late guest actually needed. A host advertising for five minutes handed the guest a bag of
     * stale keepalives and no movement.
     */
    @Test
    fun `a long wait does not evict the settings`() {
        val q = CatchUpQueue()
        q.offer(setup("bicep_curl", 60))
        repeat(250) { q.offer(ping()) }  // five minutes of lobby heartbeat

        assertEquals(1, q.size)
        assertEquals("bicep_curl", q.backlog().single().exerciseId, "The late guest still learns the movement")
    }

    @Test
    fun `the oldest events fall off the end`() {
        val q = CatchUpQueue(cap = 4)
        repeat(6) { i -> q.offer(DuelMessage(type = DuelMessage.REP, playerId = "P", seq = i, reps = i)) }

        assertEquals(4, q.size)
        assertEquals(listOf(2, 3, 4, 5), q.backlog().map { it.seq }, "Oldest dropped first")
    }

    @Test
    fun `events keep their order against the settings`() {
        val q = CatchUpQueue()
        q.offer(setup("squat", 30))
        q.offer(DuelMessage(type = DuelMessage.REP, playerId = "P", seq = 1))
        q.offer(setup("bicep_curl", 60))

        assertEquals(
            listOf(DuelMessage.REP, DuelMessage.SETUP),
            q.backlog().map { it.type },
            "Replacing the settings must not float them back to the front",
        )
    }

    @Test
    fun `clearing empties it`() {
        val q = CatchUpQueue()
        q.offer(setup("squat", 30))
        q.clear()
        assertTrue(q.backlog().isEmpty())
    }
}
