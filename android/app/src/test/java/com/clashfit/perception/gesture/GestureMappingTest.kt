package com.clashfit.perception.gesture

import com.clashfit.ui.screens.session.meaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Three shapes, one meaning each per phase, and everything else ignored.
 *
 * The recogniser knows seven gestures. Only three are wired, and each means one thing while
 * fighting and one thing (or nothing) while resting. A wave, a peace sign or a pointed finger
 * during a set must never fire a command — the mapping is where that promise lives.
 */
class GestureMappingTest {

    @Test
    fun `MediaPipe category names map to the gestures they name`() {
        assertEquals(HandGesture.OPEN_PALM, HandGesture.fromMediaPipe("Open_Palm"))
        assertEquals(HandGesture.THUMB_UP, HandGesture.fromMediaPipe("Thumb_Up"))
        assertEquals(HandGesture.CLOSED_FIST, HandGesture.fromMediaPipe("Closed_Fist"))
        assertEquals(HandGesture.VICTORY, HandGesture.fromMediaPipe("Victory"))
    }

    @Test
    fun `an unknown or missing label is no gesture, not a crash`() {
        assertEquals(HandGesture.NONE, HandGesture.fromMediaPipe(null))
        assertEquals(HandGesture.NONE, HandGesture.fromMediaPipe("Jazz_Hands"))
        assertEquals(HandGesture.NONE, HandGesture.fromMediaPipe(""))
    }

    @Test
    fun `while fighting a palm pauses and a thumb ends the set`() {
        assertEquals("pause", HandGesture.OPEN_PALM.meaning(resting = false))
        assertEquals("set done", HandGesture.THUMB_UP.meaning(resting = false))
        assertNull(HandGesture.CLOSED_FIST.meaning(resting = false))
    }

    @Test
    fun `while resting a thumb or a fist starts the next set and a palm does nothing`() {
        assertEquals("next set", HandGesture.THUMB_UP.meaning(resting = true))
        assertEquals("next set", HandGesture.CLOSED_FIST.meaning(resting = true))
        assertNull(HandGesture.OPEN_PALM.meaning(resting = true))
    }

    @Test
    fun `the unwired shapes mean nothing in either phase`() {
        for (g in listOf(HandGesture.VICTORY, HandGesture.POINTING_UP, HandGesture.I_LOVE_YOU, HandGesture.THUMB_DOWN, HandGesture.NONE)) {
            assertNull("$g while fighting", g.meaning(resting = false))
            assertNull("$g while resting", g.meaning(resting = true))
        }
    }
}
