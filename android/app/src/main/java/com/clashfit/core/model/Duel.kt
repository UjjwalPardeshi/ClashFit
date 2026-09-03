package com.clashfit.core.model

import kotlinx.serialization.Serializable

/** `(seq, damage)`: the eight-event tail that repairs a dropped packet without ACKs. */
@Serializable
data class CompactEvent(val seq: Int, val damage: Int)

/**
 * Wire format for duels, raids and rep races. ~120 bytes of JSON, at most one per rep.
 * Control messages reuse the envelope with `seq = 0`.
 */
@Serializable
data class DuelMessage(
    val v: Int = 1,
    val type: String,
    val playerId: String,
    val name: String = "",
    val seq: Int = 0,
    val tMs: Long = 0,
    val damage: Int = 0,
    val reps: Int = 0,
    val formScore: Float = 0f,
    val exerciseId: String = "",
    val fatigueBand: String = "FRESH",
    val recent: List<CompactEvent> = emptyList(),
    val payload: String = "",
) {
    companion object {
        const val HELLO = "HELLO"
        const val WELCOME = "WELCOME"
        const val PING = "PING"
        const val REP = "REP"
        const val READY = "READY"
        const val START = "START"
        const val BYE = "BYE"
        const val OUT = "OUT"
    }
}
