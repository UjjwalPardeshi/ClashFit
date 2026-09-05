package com.clashfit.perception.vision

/**
 * The last few seconds of something, by timestamp, with a way to ask for the one nearest a moment.
 *
 * The referee's eyes want the camera frame from the bottom of the worst rep. The engine only knows
 * which rep was worst once the set is over, and it only knows the rep's deepest moment once the rep
 * completes — a second or two after the frame it wants has gone past. So the camera keeps a short
 * ring of small frames, and when a rep completes the one nearest its deepest timestamp is lifted
 * out and kept if that rep is the worst so far. Nothing is written to disk; the ring lives and dies
 * with the session.
 *
 * Generic over the payload so it can be tested with integers on the JVM, where there are no Bitmaps.
 * Not thread-safe by itself; the camera thread writes and reads it.
 */
class FrameRing<T>(private val capacity: Int) {
    init { require(capacity > 0) { "a ring needs room for at least one frame" } }

    private val times = LongArray(capacity)
    private val items = arrayOfNulls<Any>(capacity)
    private var head = 0
    var size: Int = 0
        private set

    /** Newest frame in, oldest out. Returns whatever it evicted, so a caller can recycle it. */
    @Suppress("UNCHECKED_CAST")
    fun push(tMs: Long, frame: T): T? {
        val evicted = if (size == capacity) items[head] as T? else null
        times[head] = tMs
        items[head] = frame
        head = (head + 1) % capacity
        if (size < capacity) size++
        return evicted
    }

    /** The frame whose timestamp is closest to [tMs], if one is within [toleranceMs]. */
    @Suppress("UNCHECKED_CAST")
    fun nearest(tMs: Long, toleranceMs: Long = Long.MAX_VALUE): T? {
        var best = -1
        var bestGap = Long.MAX_VALUE
        for (i in 0 until size) {
            val gap = kotlin.math.abs(times[i] - tMs)
            if (gap < bestGap) { bestGap = gap; best = i }
        }
        return if (best >= 0 && bestGap <= toleranceMs) items[best] as T? else null
    }

    /** Every frame, oldest first, so a caller can recycle them. */
    @Suppress("UNCHECKED_CAST")
    fun drain(): List<T> {
        val out = ArrayList<T>(size)
        val start = if (size == capacity) head else 0
        for (k in 0 until size) {
            val i = (start + k) % capacity
            (items[i] as T?)?.let { out += it }
            items[i] = null
        }
        head = 0; size = 0
        return out
    }
}
