package com.clashfit.meta

import androidx.room.withTransaction
import com.clashfit.core.util.Clock
import com.clashfit.data.AchievementEntity
import com.clashfit.data.ClashDb
import com.clashfit.data.MetaEntity
import com.clashfit.data.WeeklyEntity
import com.clashfit.data.XpLedgerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/**
 * Meta-progression backed by Room. The rules in [XpRules], [Levels] and [AchievementRules] are
 * pure; this class loads state, runs them once inside a transaction, and writes the result.
 *
 * Banking is idempotent: the XP ledger is keyed by session id, so a session that is somehow
 * finished twice is counted once.
 */
class RoomMetaRepository(private val db: ClashDb, private val clock: Clock) : MetaRepository {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    override val state: Flow<MetaState> = combine(
        db.meta().observe(),
        db.achievements().all(),
        db.weekly().observe(WeeklyChallenges.weekKey(clock.nowMs(), ZoneId.systemDefault())),
    ) { metaEntity, achievements, weeklyEntity ->
        val meta = metaEntity ?: MetaEntity()
        val challenge = WeeklyChallenges.forWeek(WeeklyChallenges.weekKey(clock.nowMs(), zone))
        MetaState(
            xp = meta.xp,
            progress = Levels.forXp(meta.xp),
            unlocked = achievements.map { Unlocked(it.id, it.unlockedAtMs) },
            weekly = WeeklyProgress(challenge, weeklyEntity?.value ?: 0, weeklyEntity?.completedAtMs),
        )
    }

    override suspend fun current(): MetaState {
        val meta = db.meta().get() ?: MetaEntity()
        val weekKey = WeeklyChallenges.weekKey(clock.nowMs(), zone)
        val weeklyEntity = db.weekly().get(weekKey)
        return MetaState(
            xp = meta.xp,
            progress = Levels.forXp(meta.xp),
            unlocked = db.achievements().allIds().map { Unlocked(it, 0) },
            weekly = WeeklyProgress(WeeklyChallenges.forWeek(weekKey), weeklyEntity?.value ?: 0, weeklyEntity?.completedAtMs),
        )
    }

    override suspend fun onSessionFinished(facts: SessionFacts): SessionReward {
        val now = clock.nowMs()
        val weekKey = WeeklyChallenges.weekKey(now, zone)

        // Already banked. Report what it earned without counting it again.
        db.xpLedger().get(facts.sessionId)?.let { ledger ->
            val state = current()
            return SessionReward(
                xp = ledger.xp,
                lines = emptyList(),
                before = state.progress,
                after = state.progress,
                newAchievements = emptyList(),
                weekly = state.weekly,
            )
        }

        return db.withTransaction {
            val meta = db.meta().get() ?: MetaEntity()
            val challenge = WeeklyChallenges.forWeek(weekKey)
            val weeklyRow = db.weekly().get(weekKey)
            val weeklyBefore = WeeklyProgress(challenge, weeklyRow?.value ?: 0, weeklyRow?.completedAtMs)

            val beforeProgress = Levels.forXp(meta.xp)
            val lines = XpRules.reward(facts, weeklyBefore, beforeProgress)
            val totalXp = lines.sumOf { it.xp }
            val afterProgress = Levels.forXp(meta.xp + totalXp)

            // The weekly target. Damage, clean reps and sessions accumulate across the week;
            // a streak length is a high-water mark, so it is a max and never a sum.
            // Through XpRules, which is the one place that knows casual damage does not count.
            // This used to add facts.damage directly, so the weekly bar moved on a casual session
            // while the XP for it did not — the same session, two answers.
            val weeklyValue = when (challenge.metric) {
                Metric.STREAK_DAYS -> maxOf(weeklyBefore.value, facts.streakAfter)
                else -> weeklyBefore.value + XpRules.factsValue(challenge.metric, facts)
            }
            val completedNow = if (!weeklyBefore.done && weeklyValue >= challenge.target) now else null
            val weeklyAfter = WeeklyProgress(challenge, weeklyValue, completedNow ?: weeklyBefore.completedAtMs)

            // One arithmetic, shared with the badge rules, so the two can never disagree.
            val countersBefore = meta.toCounters()
            val countersAfter = AchievementRules.advance(
                before = countersBefore,
                facts = facts,
                levelAfter = afterProgress.level,
                weeklyCompleted = completedNow != null,
            )

            db.meta().upsert(countersAfter.toEntity(xp = meta.xp + totalXp))
            db.weekly().upsert(WeeklyEntity(weekKey, challenge.metric.name, weeklyValue, weeklyAfter.completedAtMs))

            val alreadyUnlocked = db.achievements().allIds().toSet()
            val earned = AchievementRules.newlyUnlocked(countersBefore, countersAfter, facts, alreadyUnlocked)
            earned.forEach { db.achievements().insert(AchievementEntity(it.id, now)) }

            db.xpLedger().insert(XpLedgerEntity(facts.sessionId, totalXp, now))

            SessionReward(
                xp = totalXp,
                lines = lines,
                before = beforeProgress,
                after = afterProgress,
                newAchievements = earned,
                weekly = weeklyAfter,
            )
        }
    }
}

private fun MetaEntity.toCounters() = AchievementRules.MetaCounters(
    sessions = sessions,
    wins = wins,
    totalReps = totalReps,
    cleanReps = cleanReps,
    totalDamage = totalDamage,
    familiesPlayedMask = familiesPlayedMask,
    pbCount = pbCount,
    weeklyChallengesDone = weeklyChallengesDone,
    bestStreak = bestStreak,
    level = level,
)

private fun AchievementRules.MetaCounters.toEntity(xp: Long) = MetaEntity(
    id = 1,
    xp = xp,
    level = level,
    sessions = sessions,
    wins = wins,
    totalReps = totalReps,
    cleanReps = cleanReps,
    totalDamage = totalDamage,
    familiesPlayedMask = familiesPlayedMask,
    pbCount = pbCount,
    weeklyChallengesDone = weeklyChallengesDone,
    bestStreak = bestStreak,
)
