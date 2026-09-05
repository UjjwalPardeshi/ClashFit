package com.clashfit.engine.core

import com.clashfit.core.model.FatigueBand
import kotlin.math.round
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * CombatEngine player HP and attack mechanics tests.
 */
class CombatEngineAttackTest {

    private fun mockCombatEngine(playerMaxHp: Int = 100): CombatEngine {
        val boss = BossConfig(
            id = "test", name = "Test Boss", maxHp = 1000,
            phases = listOf(
                BossPhase(1f, 1f, "phase1", 1f),
                BossPhase(0.5f, 1.2f, "phase2", 1.25f),
                BossPhase(0.25f, 1.5f, "phase3", 1.5f),
            )
        )
        val responses = mapOf(
            FatigueBand.FRESH to FatigueResponse(modifier = 0.92f, regenPerRep = 8),
            FatigueBand.WORKING to FatigueResponse(modifier = 1f, regenPerRep = 0),
            FatigueBand.FADING to FatigueResponse(modifier = 1.2f, staggerReps = 5),
            FatigueBand.GASSED to FatigueResponse(modifier = 1f, mercyRepsToFinish = 4),
        )
        val combo = ComboTracker(ComboConfig())
        return CombatEngine(
            baseDamage = 100, formFloor = 0.35f, formExponent = 1.2f,
            boss = boss, responses = responses, combo = combo, casual = false,
            casualConfig = CasualConfig(),
            playerMaxHpDefault = playerMaxHp,
        ).apply {
            this.playerMaxHp = playerMaxHp
            this.playerHp = playerMaxHp
        }
    }

    @Test
    fun `bossAttack deals base damage unmodified in phase1`() {
        val engine = mockCombatEngine()
        val damage = engine.bossAttack(baseDamage = 8)
        assertEquals(8, damage, "phase1 has attackModifier 1.0")
        assertEquals(92, engine.playerHp, "player HP reduced by damage")
    }

    @Test
    fun `bossAttack scales by phase attackModifier`() {
        val engine = mockCombatEngine()
        engine.hp = 500  // Move to phase2 (fromHpPct 0.5 = 50%)
        val damage = engine.bossAttack(baseDamage = 8)
        val expected = round(8 * 1.25f).toInt()
        assertEquals(expected, damage, "phase2 has attackModifier 1.25")
        assertEquals(100 - expected, engine.playerHp)
    }

    @Test
    fun `bossAttack never goes negative`() {
        val engine = mockCombatEngine()
        engine.playerHp = 5
        val damage = engine.bossAttack(baseDamage = 10)
        assertEquals(0, engine.playerHp, "capped at 0")
        assertTrue(engine.playerDead, "player marked dead when HP reaches 0")
    }

    @Test
    fun `bossAttack sets playerDead when HP reaches 0`() {
        val engine = mockCombatEngine(playerMaxHp = 10)
        assertFalse(engine.playerDead)
        engine.bossAttack(baseDamage = 15)
        assertTrue(engine.playerDead)
    }

    @Test
    fun `bossAttack records lastPlayerHit`() {
        val engine = mockCombatEngine()
        val damage = engine.bossAttack(baseDamage = 8)
        assertEquals(damage, engine.lastPlayerHit)
    }

    @Test
    fun `healPlayer increases HP capped at max`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.playerHp = 50
        engine.healPlayer(30)
        assertEquals(80, engine.playerHp)
    }

    @Test
    fun `healPlayer capped at playerMaxHp`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.playerHp = 90
        engine.healPlayer(20)
        assertEquals(100, engine.playerHp, "capped at max")
    }

    @Test
    fun `healPlayer is no-op when already at max`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.playerHp = 100
        engine.healPlayer(10)
        assertEquals(100, engine.playerHp)
    }

    private val nextBoss = BossConfig(id = "next", name = "Next Boss", maxHp = 100, phases = listOf(BossPhase(1f, 1f, "phase1")))

    @Test
    fun `reset preserves player HP across boss resets`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.bossAttack(50)
        engine.reset(nextBoss)
        assertEquals(50, engine.playerHp, "player HP preserved on reset")
        assertEquals(100, engine.hp, "boss HP reset")
    }

    @Test
    fun `a boss reset keeps the player down and resetPlayer restores them`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.bossAttack(100)
        assertTrue(engine.playerDead)
        engine.reset(nextBoss)
        // A new boss in the same fight does not revive the player; only a new fight does.
        assertEquals(0, engine.playerHp)
        assertTrue(engine.playerDead, "playerDead flag not reset by a boss change")
        engine.resetPlayer()
        assertEquals(100, engine.playerHp)
        assertEquals(false, engine.playerDead)
        assertEquals(null, engine.lastPlayerHit)
    }

    @Test
    fun `state() includes player HP fields`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.bossAttack(25)
        val state = engine.state()
        assertEquals(75, state.playerHp)
        assertEquals(100, state.playerMaxHp)
        assertEquals(false, state.playerDead)
        assertEquals(25, state.lastPlayerHit)
    }

    @Test
    fun `playerHpPct computed correctly`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.playerHp = 50
        assertEquals(0.5f, engine.playerHpPct)
    }

    @Test
    fun `playerHpPct with zero maxHp`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.playerMaxHp = 0
        engine.playerHp = 0
        assertEquals(0f, engine.playerHpPct, "0/0 case")
    }

    @Test
    fun `multiple attacks and heals in sequence`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        engine.bossAttack(baseDamage = 10)
        assertEquals(90, engine.playerHp)
        engine.bossAttack(baseDamage = 10)
        assertEquals(80, engine.playerHp)
        engine.healPlayer(15)
        assertEquals(95, engine.playerHp)
        engine.bossAttack(baseDamage = 10)
        assertEquals(85, engine.playerHp)
    }

    @Test
    fun `attack damage never goes below 0 after rounding`() {
        val engine = mockCombatEngine(playerMaxHp = 100)
        // Small attack with high phase modifier that might round to negative
        engine.hp = 1  // phase3 with attackModifier 1.5
        val baseDmg = 0  // edge case
        val damage = engine.bossAttack(baseDamage = baseDmg)
        assertEquals(0, damage, "negative damage clamped to 0")
    }

    @Test
    fun `currentPhase accumulates to latest matching phase at exact threshold`() {
        val engine = mockCombatEngine()
        // Set boss HP to exactly 50% (phase2 threshold)
        engine.hp = 500  // 500/1000 = 0.5 = 50%
        // With phases [1.0→1.0x, 0.5→1.25x, 0.25→1.5x], at 50% both phase1 and phase2 match
        // but currentPhase should return phase2 (the last/deepest match), not phase1
        val damage = engine.bossAttack(baseDamage = 8)
        val expectedDamage = round(8 * 1.25f).toInt()  // phase2 attackModifier
        assertEquals(expectedDamage, damage, "currentPhase should return phase2 at exactly 50% HP")
    }

    @Test
    fun `currentPhase uses deepest matching phase for attack calculation`() {
        val engine = mockCombatEngine()
        // 20% HP is under every threshold, so the last phase (fromHpPct 0.25) is the one in force
        engine.hp = 200  // 200/1000 = 0.2
        val damage = engine.bossAttack(baseDamage = 8)
        val expectedDamage = round(8 * 1.5f).toInt()  // phase3 attackModifier
        assertEquals(expectedDamage, damage, "currentPhase should return phase3 at 20% HP")
        assertEquals(100 - expectedDamage, engine.playerHp)
    }
}
