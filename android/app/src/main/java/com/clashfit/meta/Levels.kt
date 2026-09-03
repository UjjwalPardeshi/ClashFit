package com.clashfit.meta

import com.clashfit.meta.LevelProgress
import kotlin.math.pow
import kotlin.math.round

/**
 * Cumulative XP thresholds and titles by level.
 * Level progression: 100 * (L-1)^1.5 for level L >= 2.
 */
object Levels {

    private val TITLES = mapOf(
        1 to "Recruit",
        3 to "Contender",
        5 to "Brawler",
        8 to "Fighter",
        12 to "Warrior",
        16 to "Gladiator",
        20 to "Champion",
        30 to "Titan",
        40 to "Legend",
    )

    /**
     * Where the player stands given their total XP. Never decreases.
     */
    fun forXp(xp: Long): LevelProgress {
        var level = 1
        while (xpForLevel(level + 1) <= xp) {
            level++
        }
        val xpAtThisLevel = xpForLevel(level)
        val xpAtNextLevel = xpForLevel(level + 1)
        val xpIntoLevel = xp - xpAtThisLevel
        val xpForNextLevel = xpAtNextLevel - xpAtThisLevel

        val title = TITLES.entries
            .filter { it.key <= level }
            .maxByOrNull { it.key }
            ?.value ?: "Recruit"

        return LevelProgress(
            level = level,
            title = title,
            xpIntoLevel = xpIntoLevel,
            xpForLevel = xpForNextLevel,
        )
    }

    /**
     * Cumulative XP needed to reach the given level. Level 1 = 0 XP, level 2 = 100 XP, etc.
     */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        return round(100.0 * (level.toLong() - 1).toDouble().pow(1.5)).toLong()
    }
}
