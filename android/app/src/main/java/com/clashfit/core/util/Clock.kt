package com.clashfit.core.util

/** Injectable time. Nothing in the engine calls the system clock directly. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
        /** Monotonic, for durations; never for timestamps that get stored. */
        val MONOTONIC: Clock = Clock { System.nanoTime() / 1_000_000L }
    }
}

/** A clock you can move by hand in tests. */
class FakeClock(private var t: Long = 0L) : Clock {
    override fun nowMs(): Long = t
    fun advance(ms: Long) { t += ms }
    fun set(ms: Long) { t = ms }
}
