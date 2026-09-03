package com.clashfit.cloud

import com.clashfit.auth.AuthService
import com.clashfit.auth.AuthState
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreFriends(
    private val auth: AuthService,
    private val scope: CoroutineScope
) : FriendsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    override val friends: Flow<List<Friend>> = callbackFlow {
        val job = scope.launch {
            auth.state.collect { authState ->
                if (authState !is AuthState.SignedIn) {
                    send(emptyList())
                    return@collect
                }

                val uid = authState.user.uid

                val friendListener = firestore
                    .collection("users").document(uid)
                    .collection("friends")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        val friendUids = snapshot?.documents?.map { it.id } ?: emptyList()
                        if (friendUids.isEmpty()) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        // Fetch friend details in background
                        scope.launch {
                            try {
                                val chunks = friendUids.chunked(30)
                                val allFriends = mutableListOf<Friend>()

                                for (chunk in chunks) {
                                    val docs = firestore.collection("users")
                                        .whereIn("uid", chunk)
                                        .get()
                                        .await()

                                    docs.documents.forEach { doc ->
                                        val friendUid = doc.getString("uid") ?: doc.id
                                        val displayName = doc.getString("displayName") ?: ""
                                        val level = (doc.getLong("level") ?: 1L).toInt()
                                        allFriends.add(Friend(friendUid, displayName, level))
                                    }
                                }

                                trySend(allFriends.sortedBy { it.displayName })
                            } catch (e: Exception) {
                                trySend(emptyList())
                            }
                        }
                    }

                awaitClose {
                    friendListener.remove()
                }
            }
        }

        awaitClose {
            job.cancel()
        }
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

        val normalizedCode = code.replace(" ", "").replace("-", "").uppercase()

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

            // Add mutual friendship
            firestore.collection("users").document(currentUid)
                .collection("friends").document(targetUid)
                .set(mapOf("addedAt" to FieldValue.serverTimestamp()))
                .await()

            firestore.collection("users").document(targetUid)
                .collection("friends").document(currentUid)
                .set(mapOf("addedAt" to FieldValue.serverTimestamp()))
                .await()

            return FriendResult.Added(Friend(targetUid, targetDisplayName, targetLevel))
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
        } catch (e: Exception) {
            // Error removing friend
        }
    }
}
