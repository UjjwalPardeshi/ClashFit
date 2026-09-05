package com.clashfit.cloud

import com.clashfit.auth.AuthService
import com.clashfit.auth.AuthState
import com.clashfit.core.util.Clock
import com.clashfit.meta.MetaRepository
import com.clashfit.meta.MetaState
import com.clashfit.meta.WeeklyChallenges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** This week's totals, read from the session history rather than from the challenge counter. */
/**
 * The three weekly numbers the boards rank on.
 *
 * [chaseScore] defaults so that every existing caller and test keeps compiling and simply
 * publishes a zero, which is the truth for a player who has not run from anything.
 */
data class WeeklyTotals(val damage: Long, val cleanReps: Long, val chaseScore: Long = 0)

/**
 * Publishes the player's standing to the leaderboard whenever it changes.
 *
 * Three things this gets deliberately right:
 *
 * - The week key comes from [WeeklyChallenges.weekKey], the same ISO calculation the challenge
 *   uses. Computing it separately with `Calendar.WEEK_OF_YEAR` is locale-dependent and would file
 *   scores under a different week than the one the app is tracking.
 * - The weekly damage and clean reps are read from the session history, not from the challenge's
 *   single counter. The challenge only counts one metric per week; the board ranks two.
 * - It debounces with `collectLatest`, so a burst of database writes at the end of a session
 *   produces one upload rather than five.
 */
class ScoreSync(
    private val auth: AuthService,
    private val leaderboard: LeaderboardRepository,
    private val meta: MetaRepository,
    private val streakFlow: Flow<Int?>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.SYSTEM,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** This week's totals, given the millisecond the week started. */
    private val weeklyTotals: suspend (sinceMs: Long) -> WeeklyTotals = { WeeklyTotals(0, 0) },
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            combine(auth.state, meta.state, streakFlow) { authState, metaState, streak ->
                Triple(authState, metaState, streak ?: 0)
            }.collectLatest { (authState, metaState, streak) ->
                val user = (authState as? AuthState.SignedIn)?.user ?: return@collectLatest
                // Cancelled and restarted by the next emission, which is the debounce.
                delay(DEBOUNCE_MS)
                runCatching { submit(user.displayName, metaState, streak) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun submit(displayName: String, metaState: MetaState, streak: Int) {
        val now = clock.nowMs()
        val totals = weeklyTotals(weekStartMs(now))
        leaderboard.submit(
            ScoreSnapshot(
                weekKey = WeeklyChallenges.weekKey(now, zone),
                displayName = displayName,
                level = metaState.progress.level,
                xp = metaState.xp,
                weeklyDamage = totals.damage,
                weeklyCleanReps = totals.cleanReps,
                weeklyChaseScore = totals.chaseScore,
                bestStreak = streak,
            ),
        )
    }

    /** Monday, 00:00, in the phone's own zone. The ISO week the key names. */
    private fun weekStartMs(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}
