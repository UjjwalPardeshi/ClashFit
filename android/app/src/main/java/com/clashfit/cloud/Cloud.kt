package com.clashfit.cloud

import kotlinx.coroutines.flow.Flow

/*
 * The social layer: leaderboards and friends. Backed by Firestore when the build has cloud keys,
 * and by an honest "not available" implementation when it does not. Camera frames, pose data and
 * rep timelines never go through here; only scores, names and levels do.
 */

enum class Board(val title: String, val unit: String) {
    WEEKLY_DAMAGE("This week · damage", "dmg"),
    WEEKLY_CLEAN_REPS("This week · clean reps", "reps"),
    ALL_TIME_XP("All time · XP", "xp"),
    STREAK("Longest streak", "days"),
    WEEKLY_CHASE("This week · Zombie Run", "pts"),
}

data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    val level: Int,
    val value: Long,
    val rank: Int,
    val isMe: Boolean,
)

/** One player's standing, written after every session and read by the boards. */
data class ScoreSnapshot(
    val weekKey: String,
    val displayName: String,
    val level: Int,
    val xp: Long,
    val weeklyDamage: Long,
    val weeklyCleanReps: Long,
    val bestStreak: Int,
    val weeklyChaseScore: Long = 0,
)

sealed interface CloudAvailability {
    data object Available : CloudAvailability
    /** Why the boards are empty: no keys, signed out, or offline. Shown to the player as-is. */
    data class Unavailable(val reason: String) : CloudAvailability
}

interface LeaderboardRepository {
    val availability: Flow<CloudAvailability>
    fun top(board: Board, limit: Int = 50): Flow<List<LeaderboardEntry>>
    fun friends(board: Board): Flow<List<LeaderboardEntry>>
    suspend fun submit(snapshot: ScoreSnapshot)
}

data class Friend(val uid: String, val displayName: String, val level: Int)

sealed interface FriendResult {
    data class Added(val friend: Friend) : FriendResult
    data class Failed(val message: String) : FriendResult
}

interface FriendsRepository {
    val friends: Flow<List<Friend>>
    /** Short code others type to add me. Null until signed in. */
    val myCode: Flow<String?>
    suspend fun addByCode(code: String): FriendResult
    suspend fun remove(uid: String)
}
