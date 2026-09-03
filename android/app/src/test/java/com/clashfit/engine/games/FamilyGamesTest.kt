package com.clashfit.engine.games

import com.clashfit.core.config.CombatConfig
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FamilyGamesTest {
    @Test
    fun `every family game is constructible from its mode name`() {
        val cfg = CombatConfig()
        for (mode in listOf("SIEGE", "PURSUIT", "BREAKER", "SIGIL")) {
            val game = FamilyGames.create(mode, cfg)
            assertNotNull(game, "$mode not constructible")
        }
        val invalid = FamilyGames.create("BOSS_FIGHT", cfg)
        assertNull(invalid)
    }
}
