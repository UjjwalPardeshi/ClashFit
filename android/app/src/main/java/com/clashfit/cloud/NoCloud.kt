package com.clashfit.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NoCloudLeaderboard(private val reason: String) : LeaderboardRepository {
    override val availability: Flow<CloudAvailability> = flowOf(
        CloudAvailability.Unavailable(reason)
    )

    override fun top(board: Board, limit: Int): Flow<List<LeaderboardEntry>> =
        flowOf(emptyList())

    override fun friends(board: Board): Flow<List<LeaderboardEntry>> =
        flowOf(emptyList())

    override suspend fun submit(snapshot: ScoreSnapshot) {
        // No-op
    }
}

class NoCloudFriends(private val reason: String) : FriendsRepository {
    override val friends: Flow<List<Friend>> = flowOf(emptyList())

    override val myCode: Flow<String?> = flowOf(null)

    override suspend fun addByCode(code: String): FriendResult =
        FriendResult.Failed(reason)

    override suspend fun remove(uid: String) {
        // No-op
    }
}
