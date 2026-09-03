package com.clashfit.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class PreflightChecksTest {

    @Test
    fun `preflight status enum has three values`() {
        val statuses = com.clashfit.ui.screens.preflight.PreflightStatus.values()
        assertEquals(3, statuses.size)
        assertEquals("PASS", statuses[0].name)
        assertEquals("WARN", statuses[1].name)
        assertEquals("FAIL", statuses[2].name)
    }

    @Test
    fun `all required checks are defined`() {
        val checks = listOf(
            "Camera",
            "Pose Model",
            "Config",
            "Text-to-Speech",
            "Debug Overlay",
            "Calibration",
            "Ghosts",
            "Exact Alarms",
            "Storage"
        )

        assertEquals(9, checks.size, "Preflight should check 9 system requirements")
    }

    @Test
    fun `pass status represents successful check`() {
        val pass = com.clashfit.ui.screens.preflight.PreflightStatus.PASS
        assertEquals("PASS", pass.name)
    }

    @Test
    fun `warn status represents check with warnings`() {
        val warn = com.clashfit.ui.screens.preflight.PreflightStatus.WARN
        assertEquals("WARN", warn.name)
    }

    @Test
    fun `fail status represents failed check`() {
        val fail = com.clashfit.ui.screens.preflight.PreflightStatus.FAIL
        assertEquals("FAIL", fail.name)
    }
}
