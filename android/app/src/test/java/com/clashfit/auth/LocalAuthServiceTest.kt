package com.clashfit.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The on-device account, used when a build has no cloud keys. Every screen in the app has to work
 * against this, so it is tested as hard as the Firebase path would be if it could be.
 *
 * Both the DataStore and the service run on `backgroundScope`, which `runTest` cancels when the
 * test body ends. Giving either of them a scope of its own leaves a live coroutine behind and
 * `runTest` fails the test with `UncompletedCoroutinesError` rather than reporting the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalAuthServiceTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.newAuth(): LocalAuthService {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("account.preferences_pb") }
        return LocalAuthService(
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            dataStore = store,
        )
    }

    @Test
    fun `sign up creates an account and signs in`() = runTest {
        val auth = newAuth()
        val result = auth.signUp("alice@example.com", "password123", "Alice")
        assertIs<AuthResult.Ok>(result)
        assertEquals("alice@example.com", result.user.email)
        assertEquals("Alice", result.user.displayName)

        // The state is a StateFlow seeded with SignedOut, so waiting merely for "not Loading"
        // can pass before the write has propagated. Wait for the state the write produces.
        val state = auth.state.first { it is AuthState.SignedIn }
        assertIs<AuthState.SignedIn>(state)
        assertEquals("alice@example.com", state.user.email)
    }

    @Test
    fun `a short password is refused before anything is stored`() = runTest {
        val auth = newAuth()
        val result = auth.signUp("alice@example.com", "short", "Alice")
        assertIs<AuthResult.Failed>(result)
        assertEquals(AuthError.WEAK_PASSWORD, result.error)
    }

    @Test
    fun `a malformed email is refused`() = runTest {
        val auth = newAuth()
        val result = auth.signUp("notanemail", "password123", "Alice")
        assertIs<AuthResult.Failed>(result)
        assertEquals(AuthError.INVALID_EMAIL, result.error)
    }

    @Test
    fun `the right password signs in`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        assertIs<AuthResult.Ok>(auth.signIn("alice@example.com", "password123"))
    }

    @Test
    fun `the wrong password does not`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        val result = auth.signIn("alice@example.com", "wrongpassword")
        assertIs<AuthResult.Failed>(result)
        assertEquals(AuthError.WRONG_PASSWORD, result.error)
    }

    @Test
    fun `an unknown email does not`() = runTest {
        val auth = newAuth()
        val result = auth.signIn("nobody@example.com", "password123")
        assertIs<AuthResult.Failed>(result)
        assertEquals(AuthError.USER_NOT_FOUND, result.error)
    }

    @Test
    fun `a reset is offered for the stored email and refused for any other`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        assertEquals(null, auth.sendPasswordReset("alice@example.com"))
        assertEquals(AuthError.USER_NOT_FOUND, auth.sendPasswordReset("bob@example.com"))
    }

    @Test
    fun `signing out clears the signed-in state`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        auth.state.first { it is AuthState.SignedIn }
        auth.signOut()
        assertIs<AuthState.SignedOut>(auth.state.first { it is AuthState.SignedOut })
    }

    @Test
    fun `deleting the account removes the credentials with it`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        assertEquals(null, auth.deleteAccount())

        val after = auth.signIn("alice@example.com", "password123")
        assertIs<AuthResult.Failed>(after)
        assertEquals(AuthError.USER_NOT_FOUND, after.error)
    }

    @Test
    fun `re-registering the same email changes the password`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        assertIs<AuthResult.Ok>(auth.signUp("alice@example.com", "newpassword456", "Alice Updated"))
        assertIs<AuthResult.Ok>(auth.signIn("alice@example.com", "newpassword456"))
        // The old password must not still work.
        assertIs<AuthResult.Failed>(auth.signIn("alice@example.com", "password123"))
    }

    @Test
    fun `a second account on the same device is refused`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        val result = auth.signUp("bob@example.com", "password456", "Bob")
        assertIs<AuthResult.Failed>(result)
        assertEquals(AuthError.EMAIL_IN_USE, result.error)
    }

    @Test
    fun `the password is not stored in the clear`() = runTest {
        val auth = newAuth()
        auth.signUp("alice@example.com", "password123", "Alice")
        val onDisk = tmp.root.walkTopDown().filter { it.isFile }.joinToString("") { it.readText(Charsets.ISO_8859_1) }
        assertEquals(false, onDisk.contains("password123"), "the raw password reached the disk")
    }
}
