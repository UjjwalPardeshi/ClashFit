package com.clashfit.ui.screens.history

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * History outlives the config, so it needs a readable name for an id nothing knows any more.
 *
 * A session recorded against an exercise that has since been renamed or dropped falls back to its
 * stored id. That fallback used to swap underscores for spaces and stop, which put "jumping jack"
 * in a list where every other row said "Squat" and "Plank".
 */
class ExerciseIdNameTest {

    @Test
    fun `an underscored id reads as a name`() {
        assertEquals("Jumping Jack", "jumping_jack".titleCaseFromId())
        assertEquals("Glute Bridge", "glute_bridge".titleCaseFromId())
    }

    @Test
    fun `a single word is still capitalised`() {
        assertEquals("Squat", "squat".titleCaseFromId())
    }

    @Test
    fun `hyphens and stray separators do not leave gaps`() {
        assertEquals("Sit Up", "sit-up".titleCaseFromId())
        assertEquals("Wall Push Up", "wall__push_up".titleCaseFromId())
    }

    @Test
    fun `an id that is already a name is left alone`() {
        assertEquals("Squat", "Squat".titleCaseFromId())
    }

    @Test
    fun `an empty id does not throw`() {
        assertEquals("", "".titleCaseFromId())
    }
}
