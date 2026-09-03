package com.clashfit.duel

import kotlin.random.Random

/**
 * Generate a random 4-character player ID for duel/raid/rep-race sessions.
 * IDs are uppercase alphanumeric, suitable for display in a leaderboard.
 */
fun newPlayerId(): String {
    // Generate 4 random chars from base36 (0-9a-z), then uppercase
    return (0..3)
        .map { Random.nextInt(36).toString(36).uppercase() }
        .joinToString("")
}
