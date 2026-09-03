package com.clashfit.meta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What each finished session earned, kept in memory for the summary that follows it. The XP is
 * also in the ledger table; the lines, the level-up and the new badges are only worth showing once.
 */
class RewardStore {
    private val _byId = MutableStateFlow<Map<Long, SessionReward>>(emptyMap())
    val byId: StateFlow<Map<Long, SessionReward>> = _byId.asStateFlow()

    fun put(sessionId: Long, reward: SessionReward) = _byId.update { it + (sessionId to reward) }
    fun get(sessionId: Long): SessionReward? = _byId.value[sessionId]
}
