package com.clashfit.engine.games

/** Roster modes for group play. */
enum class RosterMode {
    /** Fixed turn length, boss HP carries. */
    PASS_THE_PHONE,
    /** Eliminated when fatigue hits GASSED. */
    LAST_STANDING,
    /** Teams take turns, boss HP carries. */
    RELAY,
}

/** One player in the roster. */
data class RosterPlayer(
    val id: String,
    val name: String,
    val team: String?,
    var reps: Int = 0,
    var damage: Int = 0,
    var turns: Int = 0,
    var bestForm: Float = 0f,
    var out: Boolean = false,
    var outReason: String? = null,
)

/** Turn-taking for group play. */
class Roster(names: List<String>, opts: RosterOptions = RosterOptions()) {
    data class RosterOptions(
        val mode: RosterMode = RosterMode.PASS_THE_PHONE,
        val turnSec: Int = 30,
        val teams: List<String>? = null,
    )

    val mode: RosterMode
    val turnSec: Int
    val players: MutableList<RosterPlayer>
    var index: Int = 0
    var turnStartMs: Long? = null
    var finished: Boolean = false
    var winner: RosterPlayer? = null

    init {
        mode = opts.mode
        turnSec = opts.turnSec
        players = names.mapIndexed { i, name ->
            RosterPlayer(
                id = "P${i + 1}",
                name = name,
                team = opts.teams?.getOrNull(i),
            )
        }.toMutableList()
    }

    /** Current player, or null if none. */
    val current: RosterPlayer? get() = players.getOrNull(index)

    /** Players still in (not eliminated). */
    val standing: List<RosterPlayer> get() = players.filter { !it.out }

    /** Start the turn for the current player. */
    fun startTurn(tMs: Long) {
        turnStartMs = tMs
        current?.let { it.turns += 1 }
    }

    /** Milliseconds left in the current turn, or null if no fixed turn time. */
    fun turnLeftMs(tMs: Long): Long? {
        if (mode == RosterMode.LAST_STANDING) return null
        if (turnStartMs == null) return (turnSec * 1000).toLong()
        return Math.max(0L, turnSec * 1000L - (tMs - turnStartMs!!))
    }

    /** Record stats for the current player. */
    fun record(stats: PlayerStats = PlayerStats()) {
        val p = current ?: return
        p.reps += stats.reps
        p.damage += stats.damage
        if (stats.bestForm > p.bestForm) p.bestForm = stats.bestForm
    }

    data class PlayerStats(
        val reps: Int = 0,
        val damage: Int = 0,
        val bestForm: Float = 0f,
    )

    /** Eliminate the current player (e.g., on GASSED). */
    fun eliminateCurrent(reason: String = "GASSED") {
        val p = current ?: return
        if (p.out) return
        p.out = true
        p.outReason = reason
        if (standing.size <= 1) {
            finished = true
            winner = standing.getOrNull(0)
        }
    }

    /** Advance to the next player who is still in. Returns the new current player or null. */
    fun next(tMs: Long): RosterPlayer? {
        if (finished) return null
        val n = players.size
        for (step in 1..n) {
            val i = (index + step) % n
            if (!players[i].out) {
                index = i
                startTurn(tMs)
                return current
            }
        }
        finished = true
        winner = standing.getOrNull(0)
        return null
    }

    /** Finish the round and determine the winner by highest damage. */
    fun finish(): RosterPlayer? {
        finished = true
        if (winner == null) {
            winner = players.sortedByDescending { it.damage }.firstOrNull()
        }
        return winner
    }

    /** Leaderboard sorted by out status, then damage, then reps. */
    fun leaderboard(): List<RosterPlayer> {
        return players.sortedWith(
            compareBy<RosterPlayer> { it.out }
                .thenByDescending { it.damage }
                .thenByDescending { it.reps }
        )
    }

    /** Current state. */
    fun state(): RosterState {
        return RosterState(
            mode = mode,
            current = current?.copy(),
            players = players.map { it.copy() },
            standing = standing.size,
            finished = finished,
            winner = winner?.copy(),
        )
    }
}

/** Snapshot of the roster. */
data class RosterState(
    val mode: RosterMode,
    val current: RosterPlayer?,
    val players: List<RosterPlayer>,
    val standing: Int,
    val finished: Boolean,
    val winner: RosterPlayer?,
)
