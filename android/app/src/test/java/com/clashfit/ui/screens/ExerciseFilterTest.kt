package com.clashfit.ui.screens

import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseFilterTest {

    private fun createExercise(id: String, name: String, family: String = "REP_CYCLE"): ExerciseSpec {
        return ExerciseSpec(
            id = id,
            name = name,
            family = family,
            tags = emptyList(),
            difficulty = 1,
            detector = JsonObject(emptyMap())
        )
    }

    @Test
    fun `filter by mode with specific family`() {
        val exercises = mapOf(
            "squat" to createExercise("squat", "Squat", "REP_CYCLE"),
            "pushup" to createExercise("pushup", "Push-up", "REP_CYCLE"),
            "plank" to createExercise("plank", "Plank", "ISOMETRIC_HOLD"),
        )

        val mode = GameMode.BOSS_FIGHT
        val filtered = exercises.values
            .filter { mode.family == null || it.familyEnum == mode.family }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.familyEnum == Family.REP_CYCLE })
    }

    @Test
    fun `filter by mode with null family includes all`() {
        val exercises = mapOf(
            "squat" to createExercise("squat", "Squat", "REP_CYCLE"),
            "plank" to createExercise("plank", "Plank", "ISOMETRIC_HOLD"),
            "run" to createExercise("run", "Run", "CADENCE"),
        )

        val mode = GameMode.REP_RACE // null family
        val filtered = exercises.values
            .filter { mode.family == null || it.familyEnum == mode.family }

        assertEquals(3, filtered.size)
    }

    @Test
    fun `search by exercise name`() {
        val exercises = listOf(
            createExercise("squat", "Squat"),
            createExercise("pushup", "Push-up"),
            createExercise("deadlift", "Deadlift"),
        )

        val query = "push"
        val filtered = exercises.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }

        assertEquals(1, filtered.size)
        assertEquals("pushup", filtered.first().id)
    }

    @Test
    fun `search is case insensitive`() {
        val exercises = listOf(
            createExercise("squat", "Squat"),
            createExercise("pushup", "Push-up"),
        )

        val query = "PUSH"
        val filtered = exercises.filter { it.name.contains(query, ignoreCase = true) }

        assertEquals(1, filtered.size)
        assertEquals("Push-up", filtered.first().name)
    }

    @Test
    fun `empty search returns all`() {
        val exercises = listOf(
            createExercise("squat", "Squat"),
            createExercise("pushup", "Push-up"),
            createExercise("deadlift", "Deadlift"),
        )

        val query = ""
        val filtered = exercises.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }

        assertEquals(3, filtered.size)
    }

    @Test
    fun `exercises are sorted by family`() {
        val exercises = listOf(
            createExercise("plank", "Plank", "ISOMETRIC_HOLD"),
            createExercise("squat", "Squat", "REP_CYCLE"),
            createExercise("run", "Run", "CADENCE"),
        )

        val sorted = exercises.sortedBy { it.familyEnum }

        // Family's declaration order is the product's family order (docs/19-EXERCISE-LIBRARY.md
        // lists F1..F5 as REP_CYCLE, ISOMETRIC_HOLD, POSE_MATCH, CADENCE, BALLISTIC), so the
        // picker's plain sortedBy { it.familyEnum } (ordinal order) puts REP_CYCLE — the main
        // family — first, not alphabetically.
        assertEquals(Family.REP_CYCLE, sorted[0].familyEnum)
        assertEquals(Family.ISOMETRIC_HOLD, sorted[1].familyEnum)
        assertEquals(Family.CADENCE, sorted[2].familyEnum)
    }
}
