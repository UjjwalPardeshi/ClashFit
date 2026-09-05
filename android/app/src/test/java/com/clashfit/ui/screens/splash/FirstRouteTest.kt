package com.clashfit.ui.screens.splash

import com.clashfit.auth.AuthState
import com.clashfit.auth.AuthUser
import com.clashfit.data.Prefs
import com.clashfit.ui.nav.Home
import com.clashfit.ui.nav.Onboarding
import com.clashfit.ui.nav.ProfileSetup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that decides whether the app can be opened at all.
 *
 * Both doors on the welcome screen talk to Firebase. Before the third one existed, a signed-out
 * launch went there and stopped: a fresh install on a venue's wifi, on a plane, or on a phone
 * handed over with mobile data off could not get into the app, and nothing on the screen said why.
 * That is the worst possible failure to find out about while somebody is holding the phone.
 */
class FirstRouteTest {

    private val signedIn = AuthState.SignedIn(AuthUser(uid = "u1", email = "a@b.c", displayName = "A"))
    private val fresh = Prefs.Settings()

    @Test
    fun `a stranger is shown the welcome`() {
        assertEquals(Onboarding, firstRoute(AuthState.SignedOut, fresh))
    }

    @Test
    fun `a guest goes straight in and is never asked again`() {
        val guest = fresh.copy(guest = true, onboarded = true)
        assertEquals(Home, firstRoute(AuthState.SignedOut, guest))
    }

    @Test
    fun `a guest who has not finished the profile finishes it first`() {
        val guest = fresh.copy(guest = true, onboarded = false)
        assertEquals(ProfileSetup, firstRoute(AuthState.SignedOut, guest))
    }

    @Test
    fun `a signed-in player who has not onboarded lands on the profile`() {
        assertEquals(ProfileSetup, firstRoute(signedIn, fresh))
    }

    @Test
    fun `a returning player lands on Train`() {
        assertEquals(Home, firstRoute(signedIn, fresh.copy(onboarded = true)))
    }

    @Test
    fun `signing out after being a guest does not throw them back to the carousel`() {
        // They have been here before and their training is on this phone. Sending them to a
        // three-page welcome would read as the app having forgotten them.
        val after = fresh.copy(guest = true, onboarded = true)
        assertEquals(Home, firstRoute(AuthState.SignedOut, after))
    }
}
