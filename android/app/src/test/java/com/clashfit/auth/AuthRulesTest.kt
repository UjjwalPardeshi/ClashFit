package com.clashfit.auth

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthRulesTest {

    @Test
    fun `valid email passes validation`() {
        assertNull(AuthRules.emailError("user@example.com"))
        assertNull(AuthRules.emailError("test.user+tag@company.co.uk"))
    }

    @Test
    fun `invalid email fails validation`() {
        assertEquals(AuthError.INVALID_EMAIL, AuthRules.emailError("notanemail"))
        assertEquals(AuthError.INVALID_EMAIL, AuthRules.emailError("@example.com"))
        assertEquals(AuthError.INVALID_EMAIL, AuthRules.emailError("user@"))
        assertEquals(AuthError.INVALID_EMAIL, AuthRules.emailError(""))
    }

    @Test
    fun `password with 8+ characters passes`() {
        assertNull(AuthRules.passwordError("12345678"))
        assertNull(AuthRules.passwordError("longerpassword"))
    }

    @Test
    fun `password with less than 8 characters fails`() {
        assertEquals(AuthError.WEAK_PASSWORD, AuthRules.passwordError("1234567"))
        assertEquals(AuthError.WEAK_PASSWORD, AuthRules.passwordError(""))
    }

    @Test
    fun `display name validation`() {
        assert(AuthRules.nameOk("Alice"))
        assert(AuthRules.nameOk("ab"))
        assert(AuthRules.nameOk("a".repeat(24)))
        assert(!AuthRules.nameOk("a"))
        assert(!AuthRules.nameOk("a".repeat(25)))
        assert(!AuthRules.nameOk("  "))
    }
}
