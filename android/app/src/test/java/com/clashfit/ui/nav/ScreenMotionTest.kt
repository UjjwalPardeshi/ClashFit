package com.clashfit.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transition a screen gets is decided by whether both ends are tab roots.
 *
 * That decision used to read from a hand-written list, and a Workout tab and a Profile tab
 * arrived without it: two of five tabs pushed sideways like a stacked screen instead of fading
 * like the siblings they are. The list is derived now, so these assert the invariants that are
 * still capable of breaking.
 */
class ScreenMotionTest {

    @Test
    fun `every tab has a root, and every root is distinct`() {
        // A shared root would make two tabs the same destination: the bar would light the wrong
        // one, and a switch between them would animate as if nothing had happened.
        assertEquals(Tab.entries.size, ScreenMotion.TAB_ROOTS.size)
    }

    @Test
    fun `every tab owns its own root`() {
        // owning() drives the bottom bar's highlight; TAB_ROOTS drives the transition. They read
        // the same four destinations, and disagreeing would light one tab while animating another.
        Tab.entries.forEach { tab ->
            assertTrue("${tab.label} does not own its own root", tab.owns.contains(tab.root::class))
        }
    }
}
