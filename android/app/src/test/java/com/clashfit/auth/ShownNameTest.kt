package com.clashfit.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A blank name is shown as a name, not as nothing.
 *
 * Firebase hands the app a user the instant the account exists, before the display name has been
 * written to it, and the app used `displayName ?: "Athlete"`. An empty string is not null. For the
 * whole of the sign-up session the profile card printed nothing beside "Recruit" and the avatar
 * drew a question mark — and it healed on the next launch, so nobody saw it until an account was
 * made on a fresh phone with a judge watching.
 */
class ShownNameTest {
    private fun user(name: String) = AuthUser(uid = "u", email = "e@x.y", displayName = name)

    @Test fun `a real name is shown as itself`() = assertEquals("Sahil", user("Sahil").shownName())
    @Test fun `an empty name falls back`() = assertEquals("Athlete", user("").shownName())
    @Test fun `whitespace is not a name either`() = assertEquals("Athlete", user("   ").shownName())
    @Test fun `the caller chooses the fallback`() = assertEquals("You", user("").shownName("You"))
    @Test fun `surrounding whitespace is trimmed off a real name`() = assertEquals("Sahil", user("  Sahil ").shownName())
}
