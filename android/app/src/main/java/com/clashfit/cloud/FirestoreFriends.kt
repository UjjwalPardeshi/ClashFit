package com.clashfit.cloud

import com.clashfit.auth.AuthService
import com.clashfit.auth.AuthState
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException

class FirestoreFriends(
    private val auth: AuthService,
    private val scope: CoroutineScope
) : FriendsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * The friends list, live.
     *
     * Three things were wrong here, and the first one crashed the app the moment anyone opened
     * the Friends screen.
     *
     * `awaitClose` was called from inside a nested `collect`. It may only be called from the
     * producer scope itself, and calling it anywhere else throws — a hard crash, on a background
     * dispatcher, so nothing caught it.
     *
     * The listener was launched on the application scope, so it outlived the screen that wanted
     * it and was never removed when the signed-in user changed.
     *
     * And the profile fetch matched on a `uid` field that no profile document has: the leaderboard
     * writes displayName, level, xp and bestStreak, and the document id IS the uid. The query
     * found nothing, so the friends list was always empty even when friends existed.
     */
    override val friends: Flow<List<Friend>> = callbackFlow {
        var listener: ListenerRegistration? = null

        // Launched on this flow's own scope, so it dies with the flow.
        val job = launch {
            auth.state.collect { authState ->
                // A new sign-in means a different friends list; drop the old listener first.
                listener?.remove()
                listener = null

                val uid = (authState as? AuthState.SignedIn)?.user?.uid
                if (uid == null) {
                    trySend(emptyList())
                    return@collect
                }

                listener = firestore.collection("users").document(uid)
                    .collection("friends")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        val friendUids = snapshot?.documents?.map { it.id }.orEmpty()
                        if (friendUids.isEmpty()) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        launch { trySend(profilesOf(friendUids)) }
                    }
            }
        }

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    /**
     * The profiles behind a set of uids, looked up by document id.
     *
     * `whereIn` takes at most thirty values, hence the chunking. A friend whose profile document
     * does not exist yet is skipped rather than shown as a blank row.
     */
    private suspend fun profilesOf(uids: List<String>): List<Friend> = runCatching {
        uids.chunked(30).flatMap { chunk ->
            firestore.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
                .documents
                .map { doc ->
                    Friend(
                        uid = doc.id,
                        displayName = doc.getString("displayName").orEmpty(),
                        level = (doc.getLong("level") ?: 1L).toInt(),
                    )
                }
        }.sortedBy { it.displayName.lowercase() }
    }.getOrElse { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList()
    }

    override val myCode: Flow<String?> = auth.state.map { authState ->
        if (authState is AuthState.SignedIn) {
            FriendCodes.forUid(authState.user.uid)
        } else {
            null
        }
    }

    override suspend fun addByCode(code: String): FriendResult {
        val currentAuth = auth.state.value as? AuthState.SignedIn
            ?: return FriendResult.Failed("Sign in to add friends.")
        val currentUid = currentAuth.user.uid

        // Folded the way Crockford base32 says to read a code in, and checked before it is sent.
        // A code of any other length cannot match a stored one, so querying for it only ever
        // reported "Friend code not found" for what was really a typo.
        if (!FriendCodes.isValid(code)) {
            return FriendResult.Failed("A friend code is ${FriendCodes.LENGTH} characters, like ABC123.")
        }
        val normalizedCode = FriendCodes.normalize(code)

        try {
            // Find user with this friend code
            val userSnapshot = firestore.collection("users")
                .whereEqualTo("friendCode", normalizedCode)
                .get()
                .await()

            val targetDoc = userSnapshot.documents.firstOrNull()
                ?: return FriendResult.Failed("Friend code not found.")

            val targetUid = targetDoc.id
            val targetDisplayName = targetDoc.getString("displayName") ?: ""
            val targetLevel = (targetDoc.getLong("level") ?: 1L).toInt()

            // Check if it's the user's own code
            if (targetUid == currentUid) {
                return FriendResult.Failed("You cannot add yourself.")
            }

            // Check if already friends
            val alreadyFriends = firestore.collection("users").document(currentUid)
                .collection("friends").document(targetUid)
                .get()
                .await()
                .exists()

            if (alreadyFriends) {
                return FriendResult.Failed("You are already friends.")
            }

            // Both edges in one batch, because a friendship is mutual or it is nothing. Written
            // one after the other, a connection lost in between left you holding a friend who did
            // not have you, and the retry was refused as "You are already friends" by the check
            // above. That state could not be reached again from the app, in either direction.
            firestore.batch().apply {
                set(
                    firestore.collection("users").document(currentUid)
                        .collection("friends").document(targetUid),
                    mapOf("addedAt" to FieldValue.serverTimestamp()),
                )
                set(
                    firestore.collection("users").document(targetUid)
                        .collection("friends").document(currentUid),
                    mapOf("addedAt" to FieldValue.serverTimestamp()),
                )
            }.commit().await()

            return FriendResult.Added(Friend(targetUid, targetDisplayName, targetLevel))
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            // A raw Firestore message ("PERMISSION_DENIED: Missing or insufficient
            // permissions.") tells a player nothing they can act on.
            return FriendResult.Failed(
                if (e is java.net.UnknownHostException) "No connection. Try again when you are online."
                else "Could not add that friend. Check the code and try again.",
            )
        }
    }

    override suspend fun remove(uid: String) {
        val currentAuth = auth.state.value as? AuthState.SignedIn
            ?: return

        try {
            firestore.collection("users").document(currentAuth.user.uid)
                .collection("friends").document(uid)
                .delete()
                .await()

            firestore.collection("users").document(uid)
                .collection("friends").document(currentAuth.user.uid)
                .delete()
                .await()
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            // Error removing friend
        }
    }
}
