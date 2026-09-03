package com.clashfit.meta

import com.clashfit.meta.Achievement
import com.clashfit.meta.Tier as Tier

/**
 * The full catalog of achievements. Ids are stable (snake_case) for persistence.
 */
object AchievementCatalog {

    val all: List<Achievement> = listOf(
        // Fights
        Achievement(
            id = "first_fight",
            title = "First Blood",
            description = "Complete your first session",
            tier = Tier.BRONZE
        ),
        Achievement(
            id = "ten_fights",
            title = "Committed",
            description = "Complete 10 sessions",
            tier = Tier.SILVER
        ),
        Achievement(
            id = "fifty_fights",
            title = "Obsessed",
            description = "Complete 50 sessions",
            tier = Tier.GOLD
        ),

        // Wins
        Achievement(
            id = "first_win",
            title = "Victory",
            description = "Win your first boss fight",
            tier = Tier.BRONZE
        ),

        // Streaks
        Achievement(
            id = "streak_7",
            title = "Week Strong",
            description = "Maintain a 7-day streak",
            tier = Tier.SILVER
        ),
        Achievement(
            id = "streak_30",
            title = "Unstoppable",
            description = "Maintain a 30-day streak",
            tier = Tier.GOLD
        ),

        // Form
        Achievement(
            id = "clean_100",
            title = "Precision",
            description = "Complete 100 clean reps",
            tier = Tier.SILVER
        ),
        Achievement(
            id = "clean_1000",
            title = "Master",
            description = "Complete 1000 clean reps",
            tier = Tier.GOLD
        ),

        // Damage
        Achievement(
            id = "damage_10k",
            title = "Powerful",
            description = "Deal 10,000 total damage",
            tier = Tier.SILVER
        ),
        Achievement(
            id = "damage_100k",
            title = "Legendary Power",
            description = "Deal 100,000 total damage",
            tier = Tier.GOLD
        ),

        // Versatility
        Achievement(
            id = "five_families",
            title = "Versatile",
            description = "Play all 5 movement families",
            tier = Tier.SILVER
        ),

        // Personal Bests
        Achievement(
            id = "pb_5",
            title = "Progressing",
            description = "Set 5 personal bests",
            tier = Tier.SILVER
        ),

        // Weekly Challenges
        Achievement(
            id = "weekly_first",
            title = "Challenge Accepted",
            description = "Complete your first weekly challenge",
            tier = Tier.BRONZE
        ),

        // Modes
        Achievement(
            id = "versus_first",
            title = "Competitive",
            description = "Complete your first versus match",
            tier = Tier.BRONZE
        ),
        Achievement(
            id = "raid_first",
            title = "Team Player",
            description = "Complete your first raid",
            tier = Tier.BRONZE
        ),
        Achievement(
            id = "clinic_first",
            title = "Calibrated",
            description = "Complete your first clinic session",
            tier = Tier.BRONZE
        ),

        // Levels
        Achievement(
            id = "level_10",
            title = "Fighter",
            description = "Reach level 10",
            tier = Tier.SILVER
        ),
        Achievement(
            id = "level_25",
            title = "Titan",
            description = "Reach level 25",
            tier = Tier.GOLD
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]
}
