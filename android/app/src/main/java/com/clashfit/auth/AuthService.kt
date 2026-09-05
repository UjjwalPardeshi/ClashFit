package com.clashfit.auth

import kotlinx.coroutines.flow.StateFlow

/** Who is signed in. `displayName` is what the leaderboard shows. */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean = false,
) {
    /**
     * The name to print, given that [displayName] can be blank.
     *
     * It is blank for the whole of the sign-up session unless the auth state is re-published after
     * the profile write, and an empty string is not null — so `displayName ?: "Athlete"` printed
     * nothing at all beside the rank, and the avatar drew a question mark. Every screen that shows
     * a name goes through this, so the fallback is one decision rather than three.
     */
    fun shownName(fallback: String = "Athlete"): String = displayName.trim().ifBlank { fallback }
}

sealed interface AuthState {
    /** Not yet known: the splash waits on this before choosing a first screen. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

enum class AuthError {
    INVALID_EMAIL, WEAK_PASSWORD, EMAIL_IN_USE, WRONG_PASSWORD, USER_NOT_FOUND,
    TOO_MANY_ATTEMPTS, NETWORK, NOT_CONFIGURED, UNKNOWN;

    /** Copy the form shows under the field. Short, specific, never a stack trace. */
    val message: String
        get() = when (this) {
            INVALID_EMAIL -> "That does not look like an email address."
            WEAK_PASSWORD -> "Use at least 8 characters."
            EMAIL_IN_USE -> "There is already an account with that email. Sign in instead."
            WRONG_PASSWORD -> "Wrong password."
            USER_NOT_FOUND -> "No account with that email."
            TOO_MANY_ATTEMPTS -> "Too many tries. Wait a minute and try again."
            NETWORK -> "No connection. Check your network and try again."
            NOT_CONFIGURED -> "Cloud accounts are not set up in this build."
            UNKNOWN -> "Something went wrong. Try again."
        }
}

sealed interface AuthResult {
    data class Ok(val user: AuthUser) : AuthResult
    data class Failed(val error: AuthError) : AuthResult
}

/**
 * The account seam. One implementation talks to Firebase; one keeps an account on the device
 * for builds with no cloud keys. Screens only ever see this interface.
 */
interface AuthService {
    val state: StateFlow<AuthState>

    /** True when a real backend is behind this service. The UI says so when it is not. */
    val isCloud: Boolean

    suspend fun signUp(email: String, password: String, displayName: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult

    /** Null on success. */
    suspend fun sendPasswordReset(email: String): AuthError?
    suspend fun updateDisplayName(name: String): AuthError?
    suspend fun signOut()
    suspend fun deleteAccount(): AuthError?
}

/** Validation the form runs before it talks to any backend. */
object AuthRules {
    const val MIN_PASSWORD = 8
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun emailError(email: String): AuthError? = if (EMAIL.matches(email.trim())) null else AuthError.INVALID_EMAIL
    fun passwordError(password: String): AuthError? = if (password.length >= MIN_PASSWORD) null else AuthError.WEAK_PASSWORD
    fun nameOk(name: String): Boolean = name.trim().length in 2..24
}
