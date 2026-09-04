package com.clashfit.ui.screens

import com.clashfit.core.config.ExerciseSpec
import kotlinx.serialization.json.JsonObject
import com.clashfit.core.model.Family
import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import com.clashfit.ui.screens.character.CharacterStats
import com.clashfit.ui.screens.character.Domain
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The character sheet turns a session history into seven domains. Two of them need sleep and food
 * data this build does not collect, and the rule that matters most is that those two stay at zero
 * rather than being filled in with something plausible. docs/22-HEALTH-DOMAINS.md §1
 */
class CharacterStatsTest {

    private fun session(
        id: Long = 1,
        exerciseId: String = "squat",
        reps: Int = 10,
        damage: Int = 500,
        form: Float = 0.8f,
        band: String = "WORKING",
    ) = SessionEntity(
        id = id, startedAtMs = id * 60_000, endedAtMs = id * 60_000 + 60_000,
        mode = "BOSS_FIGHT", exerciseId = exerciseId, bossId = "pacemaker", outcome = "BOSS_DOWN",
        totalDamage = damage, totalReps = reps, formMean = form, peakFatigue = 0.5f, peakBand = band,
    )

    /** A minimal library: one exercise per family, so family-derived domains can be exercised. */
    private fun library(): Map<String, ExerciseSpec> = mapOf(
        "squat" to spec("squat", Family.REP_CYCLE),
        "push_up" to spec("push_up", Family.REP_CYCLE),
        "plank" to spec("plank", Family.ISOMETRIC_HOLD),
        "warrior" to spec("warrior", Family.POSE_MATCH),
        "jumping_jack" to spec("jumping_jack", Family.CADENCE),
        "box_jump" to spec("box_jump", Family.BALLISTIC),
    )

    private fun spec(id: String, family: Family) = ExerciseSpec(
        id = id, name = id.replace('_', ' '), family = family.name, framing = "side", difficulty = 1,
        detector = JsonObject(emptyMap()),
    )

    @Test
    fun `an empty history is all zeros`() {
        val s = CharacterStats.compute(emptyList(), null, library())
        assertEquals(0f, s.power)
        assertEquals(0f, s.stamina)
        assertEquals(0, s.totalReps)
        assertEquals(0, s.sessions)
        assertEquals(0f, s.formAvg)
    }

    @Test
    fun `totals come straight from the sessions`() {
        val s = CharacterStats.compute(
            listOf(session(1, reps = 10, form = 0.8f), session(2, reps = 12, form = 0.75f)),
            null, library(),
        )
        assertEquals(2, s.sessions)
        assertEquals(22, s.totalReps)
        assertTrue(s.formAvg > 0.7f && s.formAvg < 0.8f, "form average was ${s.formAvg}")
    }

    @Test
    fun `strength work earns power`() {
        val s = CharacterStats.compute(listOf(session(damage = 5_000, form = 0.9f)), null, library())
        assertTrue(s.power > 0f, "power was ${s.power}")
    }

    @Test
    fun `cardio and jumps earn stamina, and a pure strength session earns less of it`() {
        val cardio = CharacterStats.compute(listOf(session(exerciseId = "jumping_jack", reps = 40)), null, library())
        val strength = CharacterStats.compute(listOf(session(exerciseId = "squat", reps = 40)), null, library())
        assertTrue(cardio.stamina > strength.stamina, "cardio ${cardio.stamina} should beat strength ${strength.stamina}")
    }

    @Test
    fun `holds and yoga earn mobility, and so does variety`() {
        val narrow = CharacterStats.compute(List(4) { session(id = it + 1L, exerciseId = "squat") }, null, library())
        val varied = CharacterStats.compute(
            listOf(
                session(1, "squat"), session(2, "plank"), session(3, "warrior"), session(4, "jumping_jack"),
            ),
            null, library(),
        )
        assertTrue(varied.mobility > narrow.mobility, "varied ${varied.mobility} should beat narrow ${narrow.mobility}")
    }

    @Test
    fun `focus rewards holding form while tired, not an easy session`() {
        val tiredButClean = CharacterStats.compute(listOf(session(form = 0.9f, band = "GASSED")), null, library())
        val easy = CharacterStats.compute(listOf(session(form = 0.9f, band = "FRESH")), null, library())
        assertTrue(
            tiredButClean.focus > easy.focus,
            "form held under fatigue (${tiredButClean.focus}) should beat the same form when fresh (${easy.focus})",
        )
    }

    @Test
    fun `resilience is the current streak, and it survives an empty history`() {
        val withSessions = CharacterStats.compute(listOf(session()), StreakEntity(current = 5, best = 10, freezes = 1), library())
        assertEquals(5f, withSessions.resilience)
        val noSessions = CharacterStats.compute(emptyList(), StreakEntity(current = 5, best = 10, freezes = 1), library())
        assertEquals(5f, noSessions.resilience, "a streak counts even before the first session is banked")
    }

    @Test
    fun `energy and nourishment stay at zero because nothing measures them`() {
        val s = CharacterStats.compute(List(20) { session(id = it + 1L, damage = 9_000, form = 1f) }, StreakEntity(current = 90), library())
        assertEquals(0f, s.value(Domain.ENERGY))
        assertEquals(0f, s.value(Domain.NOURISHMENT))
        assertTrue(Domain.entries.filterNot { it.measured }.map { it.name }.containsAll(listOf("ENERGY", "NOURISHMENT")))
    }

    @Test
    fun `every domain stays inside nought to a hundred however hard you train`() {
        val huge = List(200) { session(id = it + 1L, reps = 500, damage = 100_000, form = 1f) }
        val s = CharacterStats.compute(huge, StreakEntity(current = 9_999), library())
        Domain.entries.forEach { d ->
            val v = s.value(d)
            assertTrue(v in 0f..100f, "${d.name} was $v")
        }
    }

    @Test
    fun `an unknown exercise does not crash the sheet`() {
        val s = CharacterStats.compute(listOf(session(exerciseId = "exercise_from_a_later_build")), null, library())
        assertEquals(1, s.sessions)
        assertTrue(s.power >= 0f)
    }
}
