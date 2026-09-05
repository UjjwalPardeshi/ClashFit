package com.clashfit.coach.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The answer given when there is no model at all.
 *
 * On a phone with no weights and no cloud switch this IS the coach, and it is the rung that must
 * never be the moment a demo goes quiet. It cannot be clever. It has to be correct: every word it
 * says comes back off the fact sheet it was handed.
 */
class TemplateAnswersTest {

    private val setFacts = listOf(
        "exercise: squat",
        "reps this set: 12",
        "form average: 78 out of 100",
        "form last three reps: 64",
        "depth: 46 cm",
        "depth lost across the set: 7 cm",
        "speed lost across the set: 22 percent",
        "fatigue at the end: fading",
        "worst rep: number 11 at 55 out of 100, weakest part was depth",
        "left-right difference: 12 percent, weaker side left",
        "boss health left: 41 percent",
        "rest prescribed: 55 seconds",
    )

    @Test
    fun `a depth question is answered with the depth lines`() {
        val a = TemplateAnswers.answer("why did my depth drop?", setFacts)
        assertTrue(a, a.contains("46 cm") || a.contains("7 cm"))
    }

    @Test
    fun `a fatigue question is answered with fatigue and rest`() {
        val a = TemplateAnswers.answer("how tired am I", setFacts)
        assertTrue(a, a.contains("fading") || a.contains("55 seconds") || a.contains("22 percent"))
    }

    @Test
    fun `an asymmetry question names the weaker side`() {
        val a = TemplateAnswers.answer("am I even left to right?", setFacts)
        assertTrue(a, a.contains("12 percent"))
        assertTrue(a, a.contains("left"))
    }

    @Test
    fun `the same topic is found however it is worded`() {
        for (q in listOf("depth?", "am i going deep enough", "was that too shallow", "my range")) {
            val a = TemplateAnswers.answer(q, setFacts)
            assertTrue("'$q' -> $a", a.contains("cm") || a.contains("range"))
        }
    }

    @Test
    fun `a question nothing measures gets an admission, not an invention`() {
        val a = TemplateAnswers.answer("what should I eat for dinner?", setFacts)
        assertTrue(a, a.startsWith("I can only answer from what was measured"))
        // And it must not have made up a number that is not on the sheet.
        Regex("""\d+""").findAll(a).map { it.value }.forEach { n ->
            assertTrue("invented the number $n", setFacts.any { it.contains(n) })
        }
    }

    @Test
    fun `with nothing measured it says so rather than guessing`() {
        assertEquals(
            "I have nothing measured yet. Finish a set and ask me again.",
            TemplateAnswers.answer("how am I doing?", emptyList()),
        )
    }

    @Test
    fun `every number in an answer came off the sheet`() {
        val questions = listOf("how did that go", "depth", "tired", "left or right", "am i improving", "boss", "combo", "streak")
        for (q in questions) {
            val a = TemplateAnswers.answer(q, setFacts)
            Regex("""\d+""").findAll(a).map { it.value }.forEach { n ->
                assertTrue("'$q' produced $n, which is on no fact line:\n$a", setFacts.any { it.contains(n) })
            }
        }
    }

    @Test
    fun `the starters differ before and after a set`() {
        val after = TemplateAnswers.starters(afterSet = true)
        val idle = TemplateAnswers.starters(afterSet = false)
        assertEquals(6, after.size)
        assertEquals(6, idle.size)
        assertTrue(after.any { it.contains("set") })
        assertTrue(idle.any { it.contains("better") || it.contains("streak") })
    }
}
