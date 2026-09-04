package com.clashfit.cloud

import android.util.Log
import com.clashfit.auth.AuthService
import com.clashfit.auth.AuthState
import com.clashfit.core.util.Clock
import com.clashfit.meta.WeeklyChallenges
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

/**
 * The leaderboard, on Firestore.
 *
 * Three things this deliberately does NOT do, each of which was a bug worth naming:
 *
 * - It does not query a `collectionGroup("entries")` filtered by a `week` field. The week is the
 *   parent document id, `scores/{weekKey}/entries/{uid}`, and no `week` field is ever written, so
 *   that filter matched nothing and every weekly board came back empty. Reading the week's own
 *   subcollection is simpler, needs no composite index, and is correct.
 * - It does not compute its own week key from `Calendar.WEEK_OF_YEAR`, which is locale-dependent
 *   and disagrees with the ISO key the rest of the app writes. [WeeklyChallenges.weekKey] is the
 *   single definition.
 * - It does not filter friends on a `uid` field, which the profile documents do not have. The uid
 *   is the document id, so [FieldPath.documentId] is what selects them.
 */
class FirestoreLeaderboard(
    private val auth: AuthService,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.SYSTEM,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : LeaderboardRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun weekKey(): String = WeeklyChallenges.weekKey(clock.nowMs(), zone)

    override val availability: Flow<CloudAvailability> =
        auth.state.combine(probe()) { authState, reachable ->
            if (authState !is AuthState.SignedIn) {
                CloudAvailability.Unavailable("Sign in to see the leaderboard.")
            } else {
                reachable
            }
        }

    /** One cheap listener that tells us whether Firestore is answering at all. */
    private fun probe(): Flow<CloudAvailability> = callbackFlow {
        trySend(CloudAvailability.Available)
        val listener = firestore.collection("users").limit(1).addSnapshotListener { _, error ->
            trySend(
                when {
                    error == null -> CloudAvailability.Available
                    error.message?.contains("UNAVAILABLE") == true ->
                        CloudAvailability.Unavailable("No connection to the leaderboard.")
                    else -> CloudAvailability.Unavailable("The leaderboard is not answering.")
                },
            )
        }
        awaitClose { listener.remove() }
    }

    /** The board's own collection and the field it ranks on. */
    private fun queryFor(board: Board, limit: Int): Query = when (board) {
        Board.WEEKLY_DAMAGE -> weekEntries().orderBy("weeklyDamage", Query.Direction.DESCENDING).limit(limit.toLong())
        Board.WEEKLY_CLEAN_REPS -> weekEntries().orderBy("weeklyCleanReps", Query.Direction.DESCENDING).limit(limit.toLong())
        Board.ALL_TIME_XP -> firestore.collection("users").orderBy("xp", Query.Direction.DESCENDING).limit(limit.toLong())
        Board.STREAK -> firestore.collection("users").orderBy("bestStreak", Query.Direction.DESCENDING).limit(limit.toLong())
    }

    private fun weekEntries() = firestore.collection("scores").document(weekKey()).collection("entries")

    override fun top(board: Board, limit: Int): Flow<List<LeaderboardEntry>> =
        auth.state.combine(snapshots(board, limit)) { authState, entries ->
            val uid = (authState as? AuthState.SignedIn)?.user?.uid
            entries.map { it.copy(isMe = it.uid == uid) }
        }

    private fun snapshots(board: Board, limit: Int): Flow<List<LeaderboardEntry>> = callbackFlow {
        val listener = queryFor(board, limit).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // An empty board reads better than a crash; availability reports the reason.
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapIndexedNotNull { i, doc -> parseEntry(doc, board, i + 1) })
        }
        awaitClose { listener.remove() }
    }

    override fun friends(board: Board): Flow<List<LeaderboardEntry>> = callbackFlow {
        val me = (auth.state.value as? AuthState.SignedIn)?.user?.uid
        if (me == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        // Watch the friend list; refetch their standings whenever it changes.
        val listener = firestore.collection("users").document(me).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val uids = (snapshot?.documents.orEmpty().map { it.id } + me).distinct()
                // Launched on this flow's own scope, not the application scope. On the application
                // scope the fetch outlived the screen that wanted it, and two quick friend-list
                // changes could land out of order and show a stale board.
                launch {
                    trySend(runCatching { standingsFor(uids, board, me) }.getOrDefault(emptyList()))
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * The standings for a set of players, fetched by document id. `whereIn` takes at most 30 ids,
     * and combining it with an `orderBy` on another field would need a composite index, so the
     * ordering happens here instead.
     */
    private suspend fun standingsFor(uids: List<String>, board: Board, me: String): List<LeaderboardEntry> {
        val source = when (board) {
            Board.WEEKLY_DAMAGE, Board.WEEKLY_CLEAN_REPS -> weekEntries()
            Board.ALL_TIME_XP, Board.STREAK -> firestore.collection("users")
        }
        val found = uids.chunked(30).flatMap { chunk ->
            source.whereIn(FieldPath.documentId(), chunk).get().await().documents
        }
        return found
            .mapNotNull { parseEntry(it, board, rank = 0) }
            .sortedByDescending { it.value }
            .mapIndexed { i, e -> e.copy(rank = i + 1, isMe = e.uid == me) }
    }

    private fun parseEntry(doc: DocumentSnapshot, board: Board, rank: Int): LeaderboardEntry? {
        val displayName = doc.getString("displayName") ?: return null
        return LeaderboardEntry(
            uid = doc.id,
            displayName = displayName,
            level = (doc.getLong("level") ?: 1L).toInt(),
            value = when (board) {
                Board.WEEKLY_DAMAGE -> doc.getLong("weeklyDamage") ?: 0L
                Board.WEEKLY_CLEAN_REPS -> doc.getLong("weeklyCleanReps") ?: 0L
                Board.ALL_TIME_XP -> doc.getLong("xp") ?: 0L
                Board.STREAK -> doc.getLong("bestStreak") ?: 0L
            },
            rank = rank,
            isMe = false,
        )
    }

    /**
     * Write the profile row and this week's score row.
     *
     * The two writes are ordered, and a cancellation between them used to be swallowed by the
     * surrounding runCatching: the profile committed, the week's score never did, and nothing said
     * so. ScoreSync deliberately cancels an in-flight submit when a newer score arrives, so that
     * was not a rare race — it was the normal path at the end of a session. Cancellation now
     * propagates, which lets the newer submit replace this one wholesale, and a genuine failure is
     * logged instead of discarded.
     */
    override suspend fun submit(snapshot: ScoreSnapshot) {
        val uid = (auth.state.value as? AuthState.SignedIn)?.user?.uid ?: return
        runCatching {
            // Merge, not update: update() throws when the profile document does not exist yet,
            // and a score should never be lost because the profile write raced it.
            firestore.collection("users").document(uid).set(
                mapOf(
                    "displayName" to snapshot.displayName,
                    "level" to snapshot.level,
                    "xp" to snapshot.xp,
                    "bestStreak" to snapshot.bestStreak,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()

            firestore.collection("scores").document(snapshot.weekKey)
                .collection("entries").document(uid)
                .set(
                    mapOf(
                        "displayName" to snapshot.displayName,
                        "level" to snapshot.level,
                        "weeklyDamage" to snapshot.weeklyDamage,
                        "weeklyCleanReps" to snapshot.weeklyCleanReps,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Score sync failed; the next finished session retries it", e)
        }
    }
}

private const val TAG = "ClashFit/board"
