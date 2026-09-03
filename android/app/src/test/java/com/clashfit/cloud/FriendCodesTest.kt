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
    fun `different uids produce different codes`() {
        val code1 = FriendCodes.forUid("uid-1")
        val code2 = FriendCodes.forUid("uid-2")
        assertTrue(code1 != code2)
    }
}
