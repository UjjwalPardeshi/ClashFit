package com.clashfit.duel

import com.clashfit.core.model.DuelMessage

/**
 * What a phone that joins late is owed.
 *
 * A transport with nobody connected has nowhere to put a message, so it parks it here and
 * replays the lot to each endpoint as it arrives. The queue is not a general outbox — it exists
 * so a guest that links thirty seconds after the host picked the movement still learns what it
 * is about to do — and that shapes two rules:
 *
 * - **Beats are dropped.** [DuelMessage.PING] is liveness and nothing else; nobody handles it on
 *   arrival. Queued, it was the only thing in here: the lobby heartbeat sends one every 1200ms,
 *   so 64 of them filled the queue in 76.8 seconds and evicted the settings the guest actually
 *   needed. A host advertising for longer than that handed every late joiner a bag of stale
 *   keepalives and no answer.
 * - **Settings collapse.** A host that changes its mind four times should leave one [SETUP]
 *   behind, not four. Only the newest of each collapsing type is kept, in its original position,
 *   so ordering against other messages still holds.
 *
 * Everything else is kept in order up to [CAP], oldest dropped first: a catch-up only needs the
 * recent past, and without a cap a lobby left open with nobody in it would grow forever.
 *
 * Synchronised: a session sends from a coroutine while Play Services delivers connection results
 * on its own callback threads, and both reach this.
 */
class CatchUpQueue(private val cap: Int = CAP) {

    private val queued = mutableListOf<DuelMessage>()

    /** Parks [msg] for whoever links next. Returns false when the message was not worth keeping. */
    @Synchronized
    fun offer(msg: DuelMessage): Boolean {
        if (msg.type in TRANSIENT) return false
        if (msg.type in COLLAPSING) queued.removeAll { it.type == msg.type && it.playerId == msg.playerId }
        queued.add(msg)
        while (queued.size > cap) queued.removeAt(0)
        return true
    }

    /** What a phone linking right now should be told, oldest first. */
    @Synchronized
    fun backlog(): List<DuelMessage> = queued.toList()

    @Synchronized
    fun clear() = queued.clear()

    val size: Int @Synchronized get() = queued.size

    private companion object {
        /** Sixty-four is enough recent past for a catch-up and small enough to replay instantly. */
        const val CAP = 64

        /** Handled by nobody on arrival, so replaying one is pure noise. */
        val TRANSIENT = setOf(DuelMessage.PING)

        /** State, not events: only the newest reading of each is worth replaying. */
        val COLLAPSING = setOf(DuelMessage.SETUP, DuelMessage.START, DuelMessage.HELLO, DuelMessage.WELCOME)
    }
}
