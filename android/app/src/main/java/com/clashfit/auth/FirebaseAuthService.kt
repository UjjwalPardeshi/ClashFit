package com.clashfit.auth

import android.util.Log
import com.clashfit.cloud.FriendCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseAuthService(private val scope: CoroutineScope) : AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Fed by Firebase's AuthStateListener — and, separately, by [publish].
     *
     * The listener fires the instant createUser returns, which is before updateProfile has run,
     * so the user it hands over has no display name yet. Profile updates never fire the listener
     * again. For the whole of the sign-up session the app therefore held a user whose name was
     * the empty string: the You screen showed a blank beside "Recruit", the avatar showed "?",
     * and the score sync published a nameless row. It healed itself on the next launch, because
     * Firebase caches the profile — which is exactly why nobody had seen it, and exactly when it
     * would have been seen: the moment somebody makes an account in front of a judge.
     */
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    // Declared before `state` on purpose. Property initialisers run in declaration order, and
    // createAuthStateFlow() registers a listener that Firebase fires synchronously — which
    // writes to _state. Below `state`, _state was still null at that moment and every
    // collector in the app hit a NullPointerException on a delegate that did not exist yet.
    override val state: StateFlow<AuthState> = createAuthStateFlow()

    override val isCloud: Boolean = true

    private fun createAuthStateFlow(): StateFlow<AuthState> {
        auth.addAuthStateListener { firebase -> publish(firebase.currentUser) }
        return _state.asStateFlow()
    }

    /** The current Firebase user, as the app sees it. Called by the listener and after any profile write. */
    private fun publish(user: FirebaseUser?) {
        _state.value = if (user == null) AuthState.SignedOut else AuthState.SignedIn(
            AuthUser(
                uid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                emailVerified = user.isEmailVerified,
            ),
        )
    }

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
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: return AuthResult.Failed(AuthError.UNKNOWN)

            // Update display name
            val profileUpdates = userProfileChangeRequest {
                this.displayName = displayName
            }
            firebaseUser.updateProfile(profileUpdates).await()
            // The listener has already emitted a nameless user. Emit the named one.
            publish(auth.currentUser)

            // Generate friend code from uid
            val friendCode = FriendCodes.forUid(firebaseUser.uid)

            // The public profile: what the leaderboard shows and every signed-in player can
            // read. Deliberately no email. Firebase Auth already holds it, nothing here reads
            // it back, and Firestore cannot hide one field from a document-level read rule.
            val userDoc = hashMapOf(
                "displayName" to displayName,
                "level" to 1L,
                "xp" to 0L,
                "bestStreak" to 0L,
                "friendCode" to friendCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            // The account exists the moment Firebase Auth returns. The profile document is how
            // the leaderboard learns your name, and it is written again on every score sync, so
            // losing this one write is recoverable. Failing the whole sign-up over it is not:
            // before the Firestore rules are deployed this write is denied, and the player would
            // be told sign-up failed while their account quietly existed.
            runCatching {
                firestore.collection("users").document(firebaseUser.uid)
                    .set(userDoc)
                    .await()
            }.onFailure { Log.w(TAG, "profile document not written; the next score sync will create it", it) }

            val user = AuthUser(
                uid = firebaseUser.uid,
                email = email.trim(),
                displayName = displayName,
                emailVerified = false
            )
            AuthResult.Ok(user)
        } catch (e: FirebaseAuthException) {
            AuthResult.Failed(mapFirebaseError(e.errorCode))
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthResult.Failed(mapException(e))
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val emailError = AuthRules.emailError(email)
        if (emailError != null) return AuthResult.Failed(emailError)

        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: return AuthResult.Failed(AuthError.UNKNOWN)

            val user = AuthUser(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                emailVerified = firebaseUser.isEmailVerified
            )
            AuthResult.Ok(user)
        } catch (e: FirebaseAuthException) {
            AuthResult.Failed(mapFirebaseError(e.errorCode))
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            AuthResult.Failed(mapException(e))
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthError? {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            null
        } catch (e: FirebaseAuthException) {
            mapFirebaseError(e.errorCode)
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            mapException(e)
        }
    }

    override suspend fun updateDisplayName(name: String): AuthError? {
        if (!AuthRules.nameOk(name)) {
            return AuthError.UNKNOWN
        }

        return try {
            val user = auth.currentUser ?: return AuthError.UNKNOWN
            val profileUpdates = userProfileChangeRequest {
                this.displayName = name
            }
            user.updateProfile(profileUpdates).await()
            publish(auth.currentUser)

            // Also update in Firestore
            firestore.collection("users").document(user.uid)
                .update("displayName", name, "updatedAt", FieldValue.serverTimestamp())
                .await()

            null
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            mapException(e)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount(): AuthError? {
        return try {
            val user = auth.currentUser ?: return AuthError.UNKNOWN
            val uid = user.uid

            // Delete Firestore document
            firestore.collection("users").document(uid).delete().await()

            // Delete auth account
            user.delete().await()

            null
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            mapException(e)
        }
    }

    private fun mapFirebaseError(code: String?): AuthError = when (code) {
        "ERROR_INVALID_EMAIL" -> AuthError.INVALID_EMAIL
        "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_IN_USE
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.WRONG_PASSWORD
        "ERROR_USER_NOT_FOUND" -> AuthError.USER_NOT_FOUND
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_ATTEMPTS
        else -> AuthError.UNKNOWN
    }

    private fun mapException(e: Exception): AuthError = when (e) {
        is FirebaseNetworkException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }
}

private const val TAG = "ClashFit/auth"
