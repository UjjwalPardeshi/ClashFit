package com.clashfit.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transition a screen gets is decided by whether both ends are tab roots, and that decision
 * reads from a hand-written set. Add a fifth tab and forget this file and the new tab pushes
 * sideways like a stacked screen instead of fading like a sibling — a bug nobody would think to
 * look for here, so the drift is caught in a test instead.
 */
class ScreenMotionTest {

    @Test
    fun `the tab roots the motion knows about are exactly the tabs`() {
        assertEquals(Tab.entries.map { it.root::class }.toSet(), ScreenMotion.TAB_ROOTS)
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
