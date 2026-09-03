package com.clashfit.engine.core

import com.clashfit.core.config.GhostData

/** Replays a recorded rep timeline as an opponent. */
class GhostSource(ghost: GhostData) {
    val meta = ghost.meta
    private val events = ghost.events.sortedBy { it.t }
    private var i = 0
    private var t0: Long? = null

    /** Start replay at a given timestamp. */
    fun start(tMs: Long) {
        t0 = tMs
        i = 0
    }

    /** Get events due by the given timestamp. Returns (seq, damage) pairs. Idempotent downstream. */
    fun due(tMs: Long): List<GhostEvent> {
        val t0 = t0 ?: return emptyList()
        val elapsed = tMs - t0
        val out = mutableListOf<GhostEvent>()
        while (i < events.size && events[i].t <= elapsed) {
            out += GhostEvent(seq = i + 1, damage = events[i].damage)
            i++
        }
        return out
    }

    /** Whether all events have been emitted. */
    val finished: Boolean get() = i >= events.size

    /** Total damage over all events. */
    val totalDamage: Int get() = events.sumOf { it.damage }

    /** Duration of the entire ghost in milliseconds. */
    val durationMs: Long get() = if (events.isNotEmpty()) events.last().t else 0L

    /** Reset to the initial state. */
    fun reset() {
        i = 0
        t0 = null
    }
}

/** One ghost event. */
data class GhostEvent(val seq: Int, val damage: Int)
