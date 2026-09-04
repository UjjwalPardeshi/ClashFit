package com.clashfit.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "clashfit_account")

class LocalAuthService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences> = context.accountDataStore
) : AuthService {

    override val state: StateFlow<AuthState> = dataStore.data
        .map { prefs ->
            val signedIn = prefs[SIGNED_IN] ?: false
            if (signedIn) {
                val uid = prefs[UID] ?: return@map AuthState.SignedOut
                val email = prefs[EMAIL] ?: return@map AuthState.SignedOut
                val displayName = prefs[DISPLAY_NAME] ?: return@map AuthState.SignedOut
                AuthState.SignedIn(
                    AuthUser(
                        uid = uid,
                        email = email,
                        displayName = displayName,
                        emailVerified = false
                    )
                )
            } else {
                AuthState.SignedOut
            }
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), AuthState.SignedOut)

    override val isCloud: Boolean = false

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        val emailError = AuthRules.emailError(email)
        if (emailError != null) return AuthResult.Failed(emailError)

        val passwordError = AuthRules.passwordError(password)
        if (passwordError != null) return AuthResult.Failed(passwordError)

        if (!AuthRules.nameOk(displayName)) {
            return AuthResult.Failed(AuthError.UNKNOWN)
        }

        return try {
            dataStore.edit { prefs ->
                val existingEmail = prefs[EMAIL]
                val trimmedEmail = email.trim()

                // Allow re-registering the same email, but not a different one
                if (existingEmail != null && existingEmail != trimmedEmail) {
                    throw Exception(AuthError.EMAIL_IN_USE.name)
                }

                if (existingEmail == null) {
                    // New account
                    val uid = UUID.randomUUID().toString()
                    val salt = generateSalt()
                    val hash = hashPassword(password, salt)

                    prefs[UID] = uid
                    prefs[EMAIL] = trimmedEmail
                    prefs[DISPLAY_NAME] = displayName
                    prefs[PASSWORD_HASH] = hash
                    prefs[PASSWORD_SALT] = salt
                    prefs[SIGNED_IN] = true
                } else {
                    // Re-registering same email, update display name and password
                    val salt = generateSalt()
                    val hash = hashPassword(password, salt)
                    prefs[DISPLAY_NAME] = displayName
                    prefs[PASSWORD_HASH] = hash
                    prefs[PASSWORD_SALT] = salt
                    prefs[SIGNED_IN] = true
                }
            }

            // A one-shot read has to suspend. `stateIn(Eagerly, null).value` returns the seed
            // immediately, before the upstream has emitted, so this used to report a failure
            // for an account it had just written.
            val uid = dataStore.data.first()[UID]
                ?: return AuthResult.Failed(AuthError.UNKNOWN)
            val user = AuthUser(
                uid = uid,
                email = email.trim(),
                displayName = displayName,
                emailVerified = false
            )
            AuthResult.Ok(user)
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            if (e.message == AuthError.EMAIL_IN_USE.name) {
                AuthResult.Failed(AuthError.EMAIL_IN_USE)
            } else {
                AuthResult.Failed(AuthError.UNKNOWN)
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val emailError = AuthRules.emailError(email)
        if (emailError != null) return AuthResult.Failed(emailError)

        return try {
            val prefs = dataStore.data.first()

            val storedEmail = prefs[EMAIL]
            val storedHash = prefs[PASSWORD_HASH]
            val storedSalt = prefs[PASSWORD_SALT]
            val uid = prefs[UID]
            val displayName = prefs[DISPLAY_NAME]

            if (storedEmail == null || storedHash == null || storedSalt == null || uid == null || displayName == null) {
                return AuthResult.Failed(AuthError.USER_NOT_FOUND)
            }

            if (storedEmail != email.trim()) {
                return AuthResult.Failed(AuthError.USER_NOT_FOUND)
            }

            val hash = hashPassword(password, storedSalt)
            if (hash != storedHash) {
                return AuthResult.Failed(AuthError.WRONG_PASSWORD)
            }

            dataStore.edit {
                it[SIGNED_IN] = true
            }

            val user = AuthUser(
                uid = uid,
                email = storedEmail,
                displayName = displayName,
                emailVerified = false
            )
            AuthResult.Ok(user)
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthResult.Failed(AuthError.UNKNOWN)
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthError? {
        return try {
            val prefs = dataStore.data.first()

            val storedEmail = prefs[EMAIL]
            if (storedEmail == null || storedEmail != email.trim()) {
                return AuthError.USER_NOT_FOUND
            }

            null
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthError.UNKNOWN
        }
    }

    override suspend fun updateDisplayName(name: String): AuthError? {
        if (!AuthRules.nameOk(name)) {
            return AuthError.UNKNOWN
        }

        return try {
            dataStore.edit {
                it[DISPLAY_NAME] = name
            }
            null
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthError.UNKNOWN
        }
    }

    override suspend fun signOut() {
        dataStore.edit {
            it[SIGNED_IN] = false
        }
    }

    override suspend fun deleteAccount(): AuthError? {
        return try {
            dataStore.edit {
                it.clear()
            }
            null
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthError.UNKNOWN
        }
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hashPassword(password: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltBytes)
        val hash = digest.digest(password.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    private companion object {
        val UID = stringPreferencesKey("uid")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PASSWORD_HASH = stringPreferencesKey("password_hash")
        val PASSWORD_SALT = stringPreferencesKey("password_salt")
        val SIGNED_IN = booleanPreferencesKey("signed_in")
    }
}
