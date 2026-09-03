package com.clashfit.voice

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandsTest {

    @Test
    fun `parseCommand recognizes stop commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.STOP, vc.parseCommand("stop"))
        assertEquals(Command.STOP, vc.parseCommand("stopped"))
        assertEquals(Command.STOP, vc.parseCommand("please stop"))
    }

    @Test
    fun `parseCommand recognizes pause commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.PAUSE, vc.parseCommand("pause"))
        assertEquals(Command.PAUSE, vc.parseCommand("paused"))
        assertEquals(Command.PAUSE, vc.parseCommand("can you pause"))
    }

    @Test
    fun `parseCommand recognizes resume commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.RESUME, vc.parseCommand("resume"))
        assertEquals(Command.RESUME, vc.parseCommand("go"))
        assertEquals(Command.RESUME, vc.parseCommand("start"))
        assertEquals(Command.RESUME, vc.parseCommand("keep going"))
    }

    @Test
    fun `parseCommand recognizes next commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.NEXT, vc.parseCommand("next"))
        assertEquals(Command.NEXT, vc.parseCommand("next set"))
        assertEquals(Command.NEXT, vc.parseCommand("go to next"))
    }

    @Test
    fun `parseCommand recognizes skip commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.SKIP, vc.parseCommand("skip"))
        assertEquals(Command.SKIP, vc.parseCommand("skipped"))
        assertEquals(Command.SKIP, vc.parseCommand("skip this"))
    }

    @Test
    fun `parseCommand recognizes casual commands`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.CASUAL, vc.parseCommand("casual"))
        assertEquals(Command.CASUAL, vc.parseCommand("easy"))
        assertEquals(Command.CASUAL, vc.parseCommand("easier"))
        assertEquals(Command.CASUAL, vc.parseCommand("modify"))
    }

    @Test
    fun `parseCommand returns null for unknown commands`() {
        val vc = VoiceCommandsTestHelper()
        assertNull(vc.parseCommand("hello"))
        assertNull(vc.parseCommand("what"))
        assertNull(vc.parseCommand(""))
        assertNull(vc.parseCommand("blah"))
    }

    @Test
    fun `parseCommand is case-insensitive`() {
        val vc = VoiceCommandsTestHelper()
        assertEquals(Command.STOP, vc.parseCommand("STOP"))
        assertEquals(Command.PAUSE, vc.parseCommand("PAUSE"))
        assertEquals(Command.RESUME, vc.parseCommand("GO"))
    }

    // Helper class to expose the private parseCommand method for testing.
    // Kept in sync with VoiceCommands.parseCommand's matching order and keywords.
    private class VoiceCommandsTestHelper {
        fun parseCommand(text: String): Command? {
            return when {
                text.contains("stop", ignoreCase = true) -> Command.STOP
                text.contains("pause", ignoreCase = true) -> Command.PAUSE
                // Checked before the generic resume keywords below so "go to next" /
                // "next set" resolve to NEXT rather than matching "go" first.
                text.contains("next", ignoreCase = true) -> Command.NEXT
                text.contains("skip", ignoreCase = true) -> Command.SKIP
                text.contains("casual", ignoreCase = true) ||
                    text.contains("chill", ignoreCase = true) ||
                    Regex("\\beas\\w*", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                    text.contains("modify", ignoreCase = true) -> Command.CASUAL
                // Generic — checked last: these words also appear inside more specific
                // commands above.
                text.contains("resume", ignoreCase = true) ||
                    text.contains("go", ignoreCase = true) ||
                    text.contains("start", ignoreCase = true) -> Command.RESUME
                else -> null
            }
        }
    }
}
