package com.clashfit.cloud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendCodesTest {

    @Test
    fun `friend code is deterministic for same uid`() {
        val uid = "user-123-abc"
        val code1 = FriendCodes.forUid(uid)
        val code2 = FriendCodes.forUid(uid)
        assertEquals(code1, code2)
    }

    @Test
    fun `friend code is 6 characters`() {
        val uid = "user-123-abc"
        val code = FriendCodes.forUid(uid)
        assertEquals(6, code.length)
    }

    @Test
    fun `friend code is uppercase`() {
        val uid = "user-123-abc"
        val code = FriendCodes.forUid(uid)
        assertTrue(code.all { it.isUpperCase() || it.isDigit() })
    }

    @Test
    fun `friend code uses crockford base32 alphabet`() {
        val uid = "user-123-abc"
        val code = FriendCodes.forUid(uid)
        val validChars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        assertTrue(code.all { it in validChars })
    }

    @Test
    fun `a code is read back however it was typed`() {
        val code = FriendCodes.forUid("user-123-abc")
        for (typed in listOf(code, code.lowercase(), "${code.take(3)} ${code.drop(3)}", "${code.take(3)}-${code.drop(3)}")) {
            assertEquals(code, FriendCodes.normalize(typed), "typed as \"$typed\"")
            assertTrue(FriendCodes.isValid(typed), "typed as \"$typed\"")
        }
    }

    @Test
    fun `the letters the alphabet leaves out are folded onto the digits they look like`() {
        // Crockford drops I, L, O and U so that nobody has to tell them from 1 and 0.
        assertEquals("012345", FriendCodes.normalize("OI2345"))
        assertEquals("112345", FriendCodes.normalize("lI2345"))
        assertEquals("A0B1C1", FriendCodes.normalize("aObIcL"))
    }

    @Test
    fun `a code of the wrong length is refused rather than trimmed to fit`() {
        // Trimming would turn a seven-character typo into somebody else's six-character code and
        // add a stranger. The lookup used to send the whole thing and report "not found" instead.
        assertTrue(!FriendCodes.isValid("ABC1234"), "seven characters is not a code")
        assertTrue(!FriendCodes.isValid("ABC12"), "five characters is not a code")
        assertTrue(!FriendCodes.isValid(""), "nothing is not a code")
        assertEquals(7, FriendCodes.normalize("ABC1234").length, "the extra character is kept, so the length check can see it")
    }

    @Test
    fun `a character outside the alphabet is kept so that it fails the check`() {
        // U is not in Crockford base32. Dropping it would leave a valid-looking code for the wrong
        // player; keeping it makes the code invalid, which is the honest answer.
        assertTrue(!FriendCodes.isValid("ABCU123"))
        assertTrue(!FriendCodes.isValid("AB!123"))
    }

    @Test
    fun `every generated code passes its own validity check`() {
        for (i in 0 until 500) {
            val code = FriendCodes.forUid("uid-$i")
            assertTrue(FriendCodes.isValid(code), "generated code $code must be accepted")
            assertEquals(code, FriendCodes.normalize(code), "a generated code survives normalising")
        }
    }

    @Test
    fun `different uids produce different codes`() {
        val code1 = FriendCodes.forUid("uid-1")
        val code2 = FriendCodes.forUid("uid-2")
        assertTrue(code1 != code2)
    }
}
