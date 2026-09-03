package com.clashfit.cloud

import com.clashfit.auth.AuthService
import com.clashfit.auth.AuthState
import com.clashfit.auth.AuthUser
import com.clashfit.meta.SessionFacts
import com.clashfit.meta.SessionReward
import com.clashfit.meta.LevelProgress
import com.clashfit.meta.MetaRepository
import com.clashfit.meta.MetaState
import com.clashfit.meta.Metric
import com.clashfit.meta.WeeklyChallenge
import com.clashfit.meta.WeeklyProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeMetaRepository : MetaRepository {
    override val state: Flow<MetaState> = flowOf(
        MetaState(
            xp = 1000L,
            progress = LevelProgress(
                level = 5,
                title = "Level 5",
                xpIntoLevel = 200L,
                xpForLevel = 500L
            ),
            unlocked = emptyList(),
            weekly = WeeklyProgress(
                challenge = WeeklyChallenge(
                    weekKey = "2026-W36",
                    title = "Weekly Challenge",
                    description = "Complete challenges",
                    metric = Metric.DAMAGE,
                    target = 100
                ),
                value = 75,
                completedAtMs = null
            )
        )
    )

    override suspend fun current(): MetaState {
        return MetaState(
            xp = 1000L,
            progress = LevelProgress(
                level = 5,
                title = "Level 5",
                xpIntoLevel = 200L,
                xpForLevel = 500L
            ),
            unlocked = emptyList(),
            weekly = WeeklyProgress(
                challenge = WeeklyChallenge(
                    weekKey = "2026-W36",
                    title = "Weekly Challenge",
                    description = "Complete challenges",
                    metric = Metric.DAMAGE,
                    target = 100
                ),
                value = 75,
                completedAtMs = null
            )
        )
    }

    override suspend fun onSessionFinished(facts: com.clashfit.meta.SessionFacts): com.clashfit.meta.SessionReward {
        throw NotImplementedError()
    }
}

class FakeAuthService : AuthService {
    private val _state = MutableStateFlow<AuthState>(
        AuthState.SignedIn(
            AuthUser(
                uid = "test-uid",
                email = "test@example.com",
                displayName = "Test User",
                emailVerified = false
            )
        )
    )

    override val state: StateFlow<AuthState> = _state.asStateFlow()
    override val isCloud: Boolean = true

    override suspend fun signUp(email: String, password: String, displayName: String) =
        throw NotImplementedError()

    override suspend fun signIn(email: String, password: String) =
        throw NotImplementedError()

    override suspend fun sendPasswordReset(email: String) =
        throw NotImplementedError()

    override suspend fun updateDisplayName(name: String) =
        throw NotImplementedError()

    override suspend fun signOut() =
        throw NotImplementedError()

    override suspend fun deleteAccount() =
        throw NotImplementedError()
}

class FakeLeaderboardRepository : LeaderboardRepository {
    var lastSubmittedSnapshot: ScoreSnapshot? = null
        private set

    override val availability: Flow<CloudAvailability> = flowOf(CloudAvailability.Available)

    override fun top(board: Board, limit: Int): Flow<List<LeaderboardEntry>> = flowOf(emptyList())

    override fun friends(board: Board): Flow<List<LeaderboardEntry>> = flowOf(emptyList())

    override suspend fun submit(snapshot: ScoreSnapshot) {
        lastSubmittedSnapshot = snapshot
    }
}

class ScoreSyncTest {

    @Test
    fun `score sync builds expected snapshot from meta state`() = runTest {
        val auth = FakeAuthService()
        val leaderboard = FakeLeaderboardRepository()
        val meta = FakeMetaRepository()
        val streak = flowOf(10)

        val scope = CoroutineScope(StandardTestDispatcher())
        val sync = ScoreSync(auth, leaderboard, meta, streak, scope)

        sync.start()

        kotlinx.coroutines.delay(2100) // Wait for debounce

        val submitted = leaderboard.lastSubmittedSnapshot
        assertIs<ScoreSnapshot?>(submitted)

        if (submitted != null) {
            assertEquals("Test User", submitted.displayName)
            assertEquals(5, submitted.level)
            assertEquals(1000L, submitted.xp)
            assertEquals(75L, submitted.weeklyDamage) // Metric is DAMAGE
            assertEquals(0L, submitted.weeklyCleanReps) // Not CLEAN_REPS
            assertEquals(10, submitted.bestStreak)
            assertEquals("2026-W36", submitted.weekKey)
        }
    }
}
